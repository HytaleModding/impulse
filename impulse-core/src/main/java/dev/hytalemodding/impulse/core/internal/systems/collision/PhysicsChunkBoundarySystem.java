package dev.hytalemodding.impulse.core.internal.systems.collision;

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
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkBoundaryRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkBoundaryRuntime.ChunkBoundarySafeState;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerBridge;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Keeps registered dynamic physics bodies from drifting into unloaded chunks.
 *
 * <p>The body key is the identity boundary here. Entity views may be absent, stale,
 * or generated later, so this system uses the body's last known safe pose instead
 * of entity transforms.</p>
 */
public class PhysicsChunkBoundarySystem extends TickingSystem<EntityStore> {

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.BEFORE, PhysicsWorldCollisionStreamingSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class),
        new SystemDependency<>(Order.BEFORE, UpdateLocationSystems.TickingSystem.class)
    );

    private static final int TICKING_CHUNK_REQUEST_FLAGS = 4;
    private final LongSet requestedChunkIndices = new LongOpenHashSet();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        requestedChunkIndices.clear();

        World world = store.getExternalData().getWorld();
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        for (PhysicsBodyRegistrationView registration
            : resource.getBodyRegistrationViews(PhysicsBodyKind.BODY)) {
            processBody(registration, resource, store, chunkStore, chunkComponentStore);
        }
    }

    private void processBody(@Nonnull PhysicsBodyRegistrationView registration,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        if (resource.getSpaceBinding(registration.spaceId()) == null) {
            return;
        }

        RigidBodyKey bodyKey = registration.id();
        PhysicsBodySnapshot snapshot = resource.getBodySnapshot(bodyKey);
        if (snapshot.isStatic()) {
            return;
        }

        PhysicsSpaceSettings settings = resource.getLiveSpaceSettings(registration.spaceId());
        EntityChunkBoundaryMode mode = settings.getWorldCollisionSettings().getEntityChunkBoundaryMode();
        PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState pauseState =
            resource.getChunkBoundaryPauseState(bodyKey);
        if (pauseState != null) {
            handlePausedBody(bodyKey,
                snapshot,
                pauseState,
                mode,
                resource,
                store,
                chunkStore,
                chunkComponentStore);
            return;
        }

        long targetChunkIndex = chunkIndex(snapshot.positionX(), snapshot.positionZ());
        if (isChunkTicking(targetChunkIndex, chunkStore, chunkComponentStore)) {
            recordSafePose(bodyKey, snapshot, resource);
            return;
        }

        if (mode == EntityChunkBoundaryMode.LOAD_TICKING_CHUNK) {
            requestTickingChunk(chunkStore, targetChunkIndex);
            return;
        }

        PhysicsOwnerBridge.run(store, "pause chunk-boundary physics body",
            () -> pauseBody(bodyKey, snapshot, targetChunkIndex, resource));
    }

    private void handlePausedBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState pauseState,
        @Nonnull EntityChunkBoundaryMode mode,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        long targetChunkIndex = pauseState.getTargetChunkIndex();
        if (mode == EntityChunkBoundaryMode.LOAD_TICKING_CHUNK) {
            requestTickingChunk(chunkStore, targetChunkIndex);
        }

        if (!isChunkTicking(targetChunkIndex, chunkStore, chunkComponentStore)) {
            return;
        }

        PhysicsOwnerBridge.run(entityStore, "resume chunk-boundary physics body", () -> {
            var registration = resource.getRegistration(bodyKey);
            if (registration == null) {
                return;
            }
            PhysicsSpaceBinding space = resource.getSpaceBinding(registration.spaceId());
            if (space == null) {
                return;
            }
            space.runtime()
                .setBodyType(space.backendSpaceId(),
                    registration.backendBodyId(),
                    dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes.bodyTypeCode(
                        pauseState.getOriginalBodyType()));
            space.runtime().setBodyVelocity(space.backendSpaceId(),
                registration.backendBodyId(),
                pauseState.getLinearVelocity().x,
                pauseState.getLinearVelocity().y,
                pauseState.getLinearVelocity().z,
                pauseState.getAngularVelocity().x,
                pauseState.getAngularVelocity().y,
                pauseState.getAngularVelocity().z);
            space.runtime().activateBody(space.backendSpaceId(), registration.backendBodyId());
            resource.clearChunkBoundaryPauseState(bodyKey);
            recordSafePose(bodyKey, snapshot, resource);
        });
    }

    static void pauseBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        long targetChunkIndex,
        @Nonnull PhysicsWorldRuntimeResource resource) {
        var registration = resource.getRegistration(bodyKey);
        if (registration == null) {
            return;
        }
        PhysicsSpaceBinding space = resource.getSpaceBinding(registration.spaceId());
        if (space == null) {
            return;
        }
        ChunkBoundarySafeState safeState =
            resource.getChunkBoundarySafeState(bodyKey);
        resource.pauseChunkBoundaryBody(bodyKey,
            targetChunkIndex,
            snapshot);

        if (safeState != null) {
            space.runtime().setBodyTransform(space.backendSpaceId(),
                registration.backendBodyId(),
                safeState.getPosition().x,
                safeState.getPosition().y,
                safeState.getPosition().z,
                safeState.getRotation().x,
                safeState.getRotation().y,
                safeState.getRotation().z,
                safeState.getRotation().w);
        }

        if (snapshot.bodyType() != PhysicsBodyType.KINEMATIC) {
            space.runtime().setBodyType(space.backendSpaceId(),
                registration.backendBodyId(),
                dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.KINEMATIC));
        }
        space.runtime().setBodyVelocity(space.backendSpaceId(),
            registration.backendBodyId(),
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f);
    }

    static void recordSafePose(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsWorldRuntimeResource resource) {
        resource.updateChunkBoundarySafeState(bodyKey, snapshot);
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

        WorldChunk worldChunk = chunkComponentStore.getComponentConcurrent(chunkRef,
            WorldChunk.getComponentType());
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
