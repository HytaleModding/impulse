package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.resources.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Keeps registered dynamic physics bodies from drifting into unloaded chunks.
 *
 * <p>The body id is the identity boundary here. Entity views may be absent, stale,
 * or generated later, so this system uses the body's last known safe pose instead
 * of entity transforms.</p>
 */
public class PhysicsChunkBoundarySystem extends TickingSystem<EntityStore> {

    private static final ComponentType<ChunkStore, WorldChunk> WORLD_CHUNK_TYPE =
        WorldChunk.getComponentType();

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.BEFORE, PhysicsWorldCollisionStreamingSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class),
        new SystemDependency<>(Order.BEFORE, UpdateLocationSystems.TickingSystem.class)
    );

    private static final int TICKING_CHUNK_REQUEST_FLAGS = 4;

    private final LongSet requestedChunkIndices = new LongOpenHashSet();
    private final Vector3f bodyPositionScratch = new Vector3f();
    private final Quaternionf bodyRotationScratch = new Quaternionf();
    private final Vector3f linearVelocityScratch = new Vector3f();
    private final Vector3f angularVelocityScratch = new Vector3f();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        requestedChunkIndices.clear();

        World world = store.getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        for (PhysicsWorldResource.BodyRegistration registration
            : resource.getBodyRegistrations(PhysicsBodyKind.BODY)) {
            processBody(registration, resource, chunkStore, chunkComponentStore);
        }
    }

    private void processBody(@Nonnull PhysicsWorldResource.BodyRegistration registration,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        if (resource.getSpace(registration.spaceId()) == null) {
            return;
        }

        PhysicsBodyId bodyId = registration.id();
        PhysicsBody body = registration.body();
        if (body.isStatic()) {
            return;
        }

        PhysicsSpaceSettings settings = resource.getSpaceSettings(registration.spaceId());
        EntityChunkBoundaryMode mode = settings.getEntityChunkBoundaryMode();
        PhysicsWorldResource.ChunkBoundaryPauseState pauseState =
            resource.getChunkBoundaryPauseState(bodyId);
        if (pauseState != null) {
            handlePausedBody(bodyId, body, pauseState, mode, resource, chunkStore, chunkComponentStore);
            return;
        }

        body.getPosition(bodyPositionScratch);
        long targetChunkIndex = chunkIndex(bodyPositionScratch.x, bodyPositionScratch.z);
        if (isChunkTicking(targetChunkIndex, chunkStore, chunkComponentStore)) {
            recordSafePose(bodyId, body, resource);
            return;
        }

        if (mode == EntityChunkBoundaryMode.LOAD_TICKING_CHUNK) {
            requestTickingChunk(chunkStore, targetChunkIndex);
            return;
        }

        pauseBody(bodyId, body, targetChunkIndex, resource);
    }

    private void handlePausedBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBody body,
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
        resource.clearChunkBoundaryPauseState(bodyId);
        recordSafePose(bodyId, body, resource);
    }

    private void pauseBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBody body,
        long targetChunkIndex,
        @Nonnull PhysicsWorldResource resource) {
        PhysicsWorldResource.ChunkBoundarySafeState safeState =
            resource.getChunkBoundarySafeState(bodyId);
        if (safeState != null) {
            body.setPosition(safeState.getPosition());
            body.setRotation(safeState.getRotation());
        }

        body.getLinearVelocity(linearVelocityScratch);
        body.getAngularVelocity(angularVelocityScratch);
        resource.pauseChunkBoundaryBody(bodyId,
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

    private void recordSafePose(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsWorldResource resource) {
        body.getPosition(bodyPositionScratch);
        body.getRotation(bodyRotationScratch);
        resource.updateChunkBoundarySafeState(bodyId, bodyPositionScratch, bodyRotationScratch);
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
        var chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        WorldChunk worldChunk = chunkComponentStore.getComponent(chunkRef, WORLD_CHUNK_TYPE);
        return worldChunk != null && worldChunk.is(ChunkFlag.TICKING);
    }

    private long chunkIndex(double x, double z) {
        int chunkX = MathUtil.floor(x) >> ChunkUtil.BITS;
        int chunkZ = MathUtil.floor(z) >> ChunkUtil.BITS;
        return ChunkUtil.indexChunk(chunkX, chunkZ);
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }
}
