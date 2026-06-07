package dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems;

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
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsChunkBoundaryRuntime;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsChunkBoundaryRuntime.ChunkBoundarySafeState;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsSnapshotPublicationSystem;
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
import org.joml.Vector2d;

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
        new SystemDependency<>(Order.AFTER, PhysicsSnapshotPublicationSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsWorldCollisionStreamingSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class),
        new SystemDependency<>(Order.BEFORE, UpdateLocationSystems.TickingSystem.class)
    );

    private static final int TICKING_CHUNK_REQUEST_FLAGS = 4;
    private final LongSet requestedChunkIndices = new LongOpenHashSet();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (!WorldCollisionLifecycle.isEnabled()) {
            return;
        }
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

        RigidBodyKey bodyKey = registration.bodyKey();
        PhysicsBodySnapshot snapshot = resource.getBodySnapshotIfRegistered(bodyKey);
        if (snapshot == null) {
            return;
        }
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

        long[] targetChunkIndices = chunkIndices(snapshot);
        if (areChunksTicking(targetChunkIndices, chunkStore, chunkComponentStore)) {
            recordSafePose(bodyKey, snapshot, resource);
            return;
        }

        if (mode == EntityChunkBoundaryMode.LOAD_TICKING_CHUNK) {
            requestTickingChunks(chunkStore, targetChunkIndices);
            return;
        }

        PhysicsOwnerBridge.run(store, "pause chunk-boundary physics body",
            () -> pauseBody(bodyKey, snapshot, targetChunkIndices, resource));
    }

    private void handlePausedBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState pauseState,
        @Nonnull EntityChunkBoundaryMode mode,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        long[] targetChunkIndices = pauseState.getTargetChunkIndices();
        if (mode == EntityChunkBoundaryMode.LOAD_TICKING_CHUNK) {
            requestTickingChunks(chunkStore, targetChunkIndices);
        }

        if (!areChunksTicking(targetChunkIndices, chunkStore, chunkComponentStore)) {
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
                .setBodyType(space.backendSpaceHandle().value(),
                    registration.backendBodyHandle().value(),
                    BackendRuntimeCodes.bodyTypeCode(
                        pauseState.getOriginalBodyType()));
            space.runtime().setBodyVelocity(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value(),
                pauseState.getLinearVelocity().x,
                pauseState.getLinearVelocity().y,
                pauseState.getLinearVelocity().z,
                pauseState.getAngularVelocity().x,
                pauseState.getAngularVelocity().y,
                pauseState.getAngularVelocity().z);
            space.runtime().activateBody(space.backendSpaceHandle().value(), registration.backendBodyHandle().value());
            resource.clearChunkBoundaryPauseState(bodyKey);
            recordSafePose(bodyKey, snapshot, resource);
        });
    }

    static void pauseBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        long targetChunkIndex,
        @Nonnull PhysicsWorldRuntimeResource resource) {
        pauseBody(bodyKey, snapshot, new long[] {targetChunkIndex}, resource);
    }

    static void pauseBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull long[] targetChunkIndices,
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
            primaryChunkIndex(targetChunkIndices, snapshot),
            targetChunkIndices,
            snapshot);

        if (safeState != null) {
            space.runtime().setBodyTransform(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value(),
                safeState.getPosition().x,
                safeState.getPosition().y,
                safeState.getPosition().z,
                safeState.getRotation().x,
                safeState.getRotation().y,
                safeState.getRotation().z,
                safeState.getRotation().w);
        }

        if (snapshot.bodyType() != PhysicsBodyType.KINEMATIC) {
            space.runtime().setBodyType(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value(),
                BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.KINEMATIC));
        }
        space.runtime().setBodyVelocity(space.backendSpaceHandle().value(),
            registration.backendBodyHandle().value(),
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

    private void requestTickingChunks(@Nonnull ChunkStore chunkStore,
        @Nonnull long[] chunkIndices) {
        for (long chunkIndex : chunkIndices) {
            requestTickingChunk(chunkStore, chunkIndex);
        }
    }

    private boolean areChunksTicking(@Nonnull long[] chunkIndices,
        @Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore) {
        for (long chunkIndex : chunkIndices) {
            if (!isChunkTicking(chunkIndex, chunkStore, chunkComponentStore)) {
                return false;
            }
        }
        return true;
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

    private static long primaryChunkIndex(@Nonnull long[] chunkIndices,
        @Nonnull PhysicsBodySnapshot snapshot) {
        return chunkIndices.length > 0
            ? chunkIndices[0]
            : chunkIndex(snapshot.positionX(), snapshot.positionZ());
    }

    static long[] chunkIndices(@Nonnull PhysicsBodySnapshot snapshot) {
        Vector2d extents = horizontalHalfExtents(snapshot);
        int minChunkX = chunkCoordinate(snapshot.positionX() - extents.x);
        int maxChunkX = chunkCoordinate(snapshot.positionX() + extents.x);
        int minChunkZ = chunkCoordinate(snapshot.positionZ() - extents.y);
        int maxChunkZ = chunkCoordinate(snapshot.positionZ() + extents.y);
        int count = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        long[] chunks = new long[count];
        int index = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks[index++] = ChunkUtil.indexChunk(chunkX, chunkZ);
            }
        }
        return chunks;
    }

    @Nonnull
    private static Vector2d horizontalHalfExtents(@Nonnull PhysicsBodySnapshot snapshot) {
        return switch (snapshot.shapeType()) {
            case BOX -> boxHorizontalHalfExtents(snapshot);
            case SPHERE -> roundHorizontalHalfExtents(snapshot.sphereRadius());
            case CAPSULE -> roundHeightHorizontalHalfExtents(snapshot,
                finitePositive(snapshot.sphereRadius()),
                finitePositive(snapshot.halfHeight()) + finitePositive(snapshot.sphereRadius()));
            case CYLINDER, CONE -> roundHeightHorizontalHalfExtents(snapshot,
                finitePositive(snapshot.sphereRadius()),
                finitePositive(snapshot.halfHeight()));
            case PLANE, VOXELS, UNKNOWN -> new Vector2d();
        };
    }

    @Nonnull
    private static Vector2d boxHorizontalHalfExtents(@Nonnull PhysicsBodySnapshot snapshot) {
        if (!snapshot.hasBoxHalfExtents()) {
            return new Vector2d();
        }
        return rotatedHorizontalHalfExtents(snapshot,
            finitePositive(snapshot.boxHalfExtentX()),
            finitePositive(snapshot.boxHalfExtentY()),
            finitePositive(snapshot.boxHalfExtentZ()));
    }

    @Nonnull
    private static Vector2d roundHorizontalHalfExtents(float radius) {
        double halfExtent = finitePositive(radius);
        return new Vector2d(halfExtent, halfExtent);
    }

    @Nonnull
    private static Vector2d roundHeightHorizontalHalfExtents(@Nonnull PhysicsBodySnapshot snapshot,
        double radius,
        double axisHalfExtent) {
        double halfX = radius;
        double halfY = radius;
        double halfZ = radius;
        switch (snapshot.shapeAxis()) {
            case X -> halfX = axisHalfExtent;
            case Y -> halfY = axisHalfExtent;
            case Z -> halfZ = axisHalfExtent;
        }
        return rotatedHorizontalHalfExtents(snapshot, halfX, halfY, halfZ);
    }

    @Nonnull
    private static Vector2d rotatedHorizontalHalfExtents(@Nonnull PhysicsBodySnapshot snapshot,
        double halfX,
        double halfY,
        double halfZ) {
        double x = snapshot.rotationX();
        double y = snapshot.rotationY();
        double z = snapshot.rotationZ();
        double w = snapshot.rotationW();
        double lengthSquared = x * x + y * y + z * z + w * w;
        if (!Double.isFinite(lengthSquared) || lengthSquared <= 0.0) {
            return new Vector2d(halfX, halfZ);
        }

        double scale = 2.0 / lengthSquared;
        double xs = x * scale;
        double ys = y * scale;
        double zs = z * scale;
        double wx = w * xs;
        double wy = w * ys;
        double wz = w * zs;
        double xx = x * xs;
        double xy = x * ys;
        double xz = x * zs;
        double yy = y * ys;
        double yz = y * zs;
        double zz = z * zs;
        double m00 = 1.0 - (yy + zz);
        double m01 = xy - wz;
        double m02 = xz + wy;
        double m20 = xz - wy;
        double m21 = yz + wx;
        double m22 = 1.0 - (xx + yy);
        return new Vector2d(Math.abs(m00) * halfX + Math.abs(m01) * halfY + Math.abs(m02) * halfZ,
            Math.abs(m20) * halfX + Math.abs(m21) * halfY + Math.abs(m22) * halfZ);
    }

    private static double finitePositive(float value) {
        return Float.isFinite(value) && value > 0.0f ? value : 0.0;
    }

    private static int chunkCoordinate(double coordinate) {
        return MathUtil.floor(coordinate) >> ChunkUtil.BITS;
    }

    private static long chunkIndex(double x, double z) {
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
