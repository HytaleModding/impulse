package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Keeps entity-backed physics bodies from falling into Hytale's unloaded-chunk migration path.
 *
 * <p>Depending on per-space policy, bodies either pause at the last safe pose until the
 * destination chunk is ticking, or proactively request the destination chunk to load/tick.</p>
 */
public class PhysicsChunkBoundarySystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private static final int TICKING_CHUNK_REQUEST_FLAGS = 4;
    private static final int SLEEPING_BODY_CHECK_INTERVAL_TICKS = 30;
    private static final ComponentType<EntityStore, PhysicsBodyComponent> PHYSICS_BODY_TYPE =
        PhysicsBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final Query<EntityStore> QUERY = Query.and(PHYSICS_BODY_TYPE, TRANSFORM_TYPE);

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.BEFORE, PhysicsWorldCollisionStreamingSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class),
        new SystemDependency<>(Order.BEFORE, UpdateLocationSystems.TickingSystem.class)
    );

    private final LongSet requestedChunkIndices = new LongOpenHashSet();
    private final Vector3f bodyPositionScratch = new Vector3f();
    private final Quaternionf bodyRotationScratch = new Quaternionf();
    private final Quaterniond transformRotationScratch = new Quaterniond();
    private final Quaternionf transformRotationFloatScratch = new Quaternionf();
    private final Vector3f linearVelocityScratch = new Vector3f();
    private final Vector3f angularVelocityScratch = new Vector3f();
    private final Vector3f fallbackPositionScratch = new Vector3f();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        requestedChunkIndices.clear();

        World world = store.getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> collector =
            (chunk, commandBuffer) -> processChunk(chunk, resource, chunkStore, chunkComponentStore);
        store.forEachChunk(systemIndex, collector);
    }

    private void processChunk(@Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        for (int index = 0; index < chunk.size(); index++) {
            PhysicsBodyComponent physicsBody = chunk.getComponent(index, PHYSICS_BODY_TYPE);
            TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
            if (physicsBody == null || transform == null) {
                continue;
            }

            processBody(physicsBody, transform, resource, chunkStore, chunkComponentStore);
        }
    }

    private void processBody(@Nonnull PhysicsBodyComponent physicsBody,
        @Nonnull TransformComponent transform,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        SpaceId spaceId = resolveSpaceId(physicsBody, resource);
        if (spaceId == null || resource.getSpace(spaceId) == null) {
            return;
        }

        PhysicsBody body = physicsBody.getBody();
        if (body.isStatic()) {
            return;
        }

        PhysicsSpaceSettings settings = resource.getSpaceSettings(spaceId);
        EntityChunkBoundaryMode mode = settings.getEntityChunkBoundaryMode();
        PhysicsWorldResource.ChunkBoundaryPauseState pauseState =
            resource.getChunkBoundaryPauseState(body);
        if (pauseState != null) {
            handlePausedBody(body, pauseState, mode, resource, chunkStore, chunkComponentStore);
            return;
        }

        long currentChunkIndex = currentChunkIndex(transform, chunkComponentStore);
        if (body.isSleeping()) {
            boolean canDeferSleepingCheck = resource.getChunkBoundarySafeState(body) != null
                && !resource.isBodyControlled(body)
                && isChunkTicking(currentChunkIndex, chunkStore, chunkComponentStore);
            if (canDeferSleepingCheck) {
                if (physicsBody.shouldDeferSleepingChunkBoundaryCheck(currentChunkIndex,
                    SLEEPING_BODY_CHECK_INTERVAL_TICKS)) {
                    return;
                }
            } else {
                physicsBody.resetSleepingChunkBoundaryCheck();
            }
        } else {
            physicsBody.resetSleepingChunkBoundaryCheck();
        }
        body.getPosition(bodyPositionScratch);
        long targetChunkIndex = chunkIndex(bodyPositionScratch.x, bodyPositionScratch.z);
        if (currentChunkIndex == targetChunkIndex) {
            if (isChunkTicking(currentChunkIndex, chunkStore, chunkComponentStore)) {
                recordSafePose(body, resource);
            }
            return;
        }

        if (isChunkTicking(targetChunkIndex, chunkStore, chunkComponentStore)) {
            recordSafePose(body, resource);
            return;
        }

        if (mode == EntityChunkBoundaryMode.LOAD_TICKING_CHUNK) {
            requestTickingChunk(chunkStore, targetChunkIndex);
            return;
        }

        pauseBody(body, transform, targetChunkIndex, resource);
    }

    private void handlePausedBody(@Nonnull PhysicsBody body,
        @Nonnull PhysicsWorldResource.ChunkBoundaryPauseState pauseState,
        @Nonnull EntityChunkBoundaryMode mode,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        long targetChunkIndex = pauseState.getTargetChunkIndex();
        if (mode == EntityChunkBoundaryMode.LOAD_TICKING_CHUNK) {
            requestTickingChunk(chunkStore, targetChunkIndex);
        }

        if (!isChunkTicking(targetChunkIndex, chunkStore, chunkComponentStore)) {
            return;
        }

        body.setBodyType(pauseState.getOriginalBodyType());
        body.setLinearVelocity(pauseState.getLinearVelocity());
        body.setAngularVelocity(pauseState.getAngularVelocity());
        body.activate();
        resource.clearChunkBoundaryPauseState(body);
    }

    private void pauseBody(@Nonnull PhysicsBody body,
        @Nonnull TransformComponent transform,
        long targetChunkIndex,
        @Nonnull PhysicsWorldResource resource) {
        PhysicsWorldResource.ChunkBoundarySafeState safeState =
            resource.getChunkBoundarySafeState(body);
        if (safeState != null) {
            body.setPosition(safeState.getPosition());
            body.setRotation(safeState.getRotation());
        } else {
            Vector3d transformPosition = transform.getPosition();
            fallbackPositionScratch.set((float) transformPosition.x,
                (float) (transformPosition.y + body.getCenterOfMassOffsetY()),
                (float) transformPosition.z);
            transformRotationFloatScratch.set(
                transform.getRotation().getQuaternion(transformRotationScratch));
            body.setPosition(fallbackPositionScratch);
            body.setRotation(transformRotationFloatScratch);
        }

        body.getLinearVelocity(linearVelocityScratch);
        body.getAngularVelocity(angularVelocityScratch);
        resource.pauseChunkBoundaryBody(body,
            targetChunkIndex,
            body.getBodyType(),
            linearVelocityScratch,
            angularVelocityScratch);

        if (body.getBodyType() != PhysicsBodyType.KINEMATIC) {
            body.setBodyType(PhysicsBodyType.KINEMATIC);
        }
        body.setLinearVelocity(0.0f, 0.0f, 0.0f);
        body.setAngularVelocity(0.0f, 0.0f, 0.0f);
        body.clearForces();
    }

    private void recordSafePose(@Nonnull PhysicsBody body,
        @Nonnull PhysicsWorldResource resource) {
        body.getPosition(bodyPositionScratch);
        body.getRotation(bodyRotationScratch);
        resource.updateChunkBoundarySafeState(body, bodyPositionScratch, bodyRotationScratch);
    }

    private void requestTickingChunk(@Nonnull ChunkStore chunkStore, long chunkIndex) {
        if (!requestedChunkIndices.add(chunkIndex)) {
            return;
        }
        chunkStore.getChunkReferenceAsync(chunkIndex, TICKING_CHUNK_REQUEST_FLAGS);
    }

    private boolean isChunkTicking(long chunkIndex,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        WorldChunk worldChunk = chunkComponentStore.getComponent(chunkRef, WorldChunk.getComponentType());
        return worldChunk != null && worldChunk.is(ChunkFlag.TICKING);
    }

    private long currentChunkIndex(@Nonnull TransformComponent transform,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        Ref<ChunkStore> chunkRef = transform.getChunkRef();
        if (chunkRef != null && chunkRef.isValid()) {
            WorldChunk worldChunk = chunkComponentStore.getComponent(chunkRef, WorldChunk.getComponentType());
            if (worldChunk != null) {
                return worldChunk.getIndex();
            }
        }

        Vector3d position = transform.getPosition();
        return chunkIndex(position.x, position.z);
    }

    private long chunkIndex(double x, double z) {
        int chunkX = MathUtil.floor(x) >> ChunkUtil.BITS;
        int chunkZ = MathUtil.floor(z) >> ChunkUtil.BITS;
        return ChunkUtil.indexChunk(chunkX, chunkZ);
    }

    @Nullable
    private SpaceId resolveSpaceId(@Nonnull PhysicsBodyComponent physicsBody,
        @Nonnull PhysicsWorldResource resource) {
        SpaceId spaceId = physicsBody.getSpaceId();
        return spaceId != null ? spaceId : resource.getDefaultSpaceId();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }
}
