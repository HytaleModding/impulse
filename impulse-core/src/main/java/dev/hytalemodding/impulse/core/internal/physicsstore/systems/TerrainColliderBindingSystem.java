package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderPayload;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderPayload.BoxPayload;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderPayload.TerrainNeighbor;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderRequest;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Binds retained terrain collider rows to backend terrain bodies.
 */
public final class TerrainColliderBindingSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, JointBindingSystem.class),
        new SystemDependency<>(Order.BEFORE, TargetBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsTerrainPayloadResource payloads = store.getResource(
            PhysicsTerrainPayloadResource.getResourceType());
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> bindChunk(runtime, payloads, restore, chunk);
        store.forEachChunk(systemIndex, collector);
    }

    private static void bindChunk(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsTerrainPayloadResource payloads,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            TerrainColliderComponent terrain = chunk.getComponent(index,
                TerrainColliderComponent.getComponentType());
            if (terrain == null) {
                continue;
            }
            UUID terrainUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(terrainUuid)) {
                continue;
            }
            if (!terrain.isRetained()) {
                removeTerrain(runtime, terrainUuid);
                continue;
            }
            if (runtime.isTerrainPayloadBound(terrainUuid, terrain.getPayloadResourceKey())) {
                continue;
            }
            TerrainColliderPayload payload = payloads.get(terrain.getPayloadResourceKey());
            if (payload == null || payload.isEmpty()) {
                restore.recordSoftSkip("Terrain payload is missing: " + terrain.getSourceKey());
                continue;
            }
            bindTerrain(runtime, restore, terrainUuid, terrain, payload);
        }
    }

    private static void bindTerrain(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull UUID terrainUuid,
        @Nonnull TerrainColliderComponent terrain,
        @Nonnull TerrainColliderPayload payload) {
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(terrain.getSpaceUuid());
        if (spaceHandle == null) {
            restore.recordSoftSkip("Terrain references unbound space: " + terrain.getSourceKey());
            return;
        }
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, terrain.getSpaceUuid());
        if (backendRuntime == null) {
            restore.recordSoftSkip("Terrain references missing backend runtime: "
                + terrain.getSourceKey());
            return;
        }
        if (runtime.hasTerrainBodyHandles(terrainUuid)) {
            removeTerrain(runtime, terrainUuid);
        }
        try {
            boolean nativeVoxel = payload.nativeVoxelTerrainEnabled()
                && payload.hasFullCubeVoxels()
                && backendRuntime.supportsVoxelTerrain(spaceHandle.value());
            if (nativeVoxel) {
                addVoxelTerrain(runtime, backendRuntime, spaceHandle, terrainUuid, terrain, payload);
            } else {
                for (BoxPayload box : payload.mergedFullCubeBoxes()) {
                    addStaticBox(runtime, backendRuntime, spaceHandle, terrainUuid, box, payload);
                }
            }
            for (BoxPayload box : payload.detailBoxes()) {
                addStaticBox(runtime, backendRuntime, spaceHandle, terrainUuid, box, payload);
            }
            if (!runtime.hasTerrainBodyHandles(terrainUuid)) {
                restore.recordSoftSkip("Terrain payload produced no backend bodies: "
                    + terrain.getSourceKey());
                return;
            }
            runtime.markTerrainPayloadBound(terrainUuid, terrain.getPayloadResourceKey());
            stitchNeighbors(runtime, backendRuntime, spaceHandle, terrainUuid, terrain, payload);
        } catch (RuntimeException exception) {
            removeTerrain(runtime, terrainUuid);
            throw exception;
        }
    }

    private static void addVoxelTerrain(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsBackendRuntime backendRuntime,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull UUID terrainUuid,
        @Nonnull TerrainColliderComponent terrain,
        @Nonnull TerrainColliderPayload payload) {
        long bodyId = backendRuntime.createVoxelTerrain(spaceHandle.value(),
            payload.voxelSizeX(),
            payload.voxelSizeY(),
            payload.voxelSizeZ(),
            payload.voxelCoordinates(),
            terrain.getChunkX() << ChunkUtil.BITS,
            terrain.getSectionY() << ChunkUtil.BITS,
            terrain.getChunkZ() << ChunkUtil.BITS,
            payload.friction(),
            payload.restitution(),
            payload.collisionGroup(),
            payload.collisionMask());
        runtime.putTerrainBodyHandle(terrainUuid, spaceHandle, new BackendBodyHandle(bodyId), true);
    }

    private static void addStaticBox(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsBackendRuntime backendRuntime,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull UUID terrainUuid,
        @Nonnull BoxPayload box,
        @Nonnull TerrainColliderPayload payload) {
        if (box.halfX() <= 0.0 || box.halfY() <= 0.0 || box.halfZ() <= 0.0) {
            return;
        }
        long bodyId = backendRuntime.createBody(spaceHandle.value(),
            BackendRuntimeCodes.SHAPE_BOX,
            (float) box.halfX(),
            (float) box.halfY(),
            (float) box.halfZ(),
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            0.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.STATIC),
            (float) box.centerX(),
            (float) box.centerY(),
            (float) box.centerZ(),
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        backendRuntime.setBodyFriction(spaceHandle.value(), bodyId, payload.friction());
        backendRuntime.setBodyRestitution(spaceHandle.value(), bodyId, payload.restitution());
        backendRuntime.setBodyCollisionFilter(spaceHandle.value(),
            bodyId,
            payload.collisionGroup(),
            payload.collisionMask());
        runtime.putTerrainBodyHandle(terrainUuid,
            spaceHandle,
            new BackendBodyHandle(bodyId),
            false);
    }

    private static void stitchNeighbors(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsBackendRuntime backendRuntime,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull UUID terrainUuid,
        @Nonnull TerrainColliderComponent terrain,
        @Nonnull TerrainColliderPayload payload) {
        BackendBodyHandle voxelBody = runtime.getTerrainVoxelBodyHandle(terrainUuid);
        if (voxelBody == null) {
            return;
        }
        for (TerrainNeighbor neighbor : payload.neighbors()) {
            UUID neighborUuid = TerrainColliderRequest.terrainColliderUuid(terrain.getSpaceUuid(),
                neighbor.sourceKey());
            BackendBodyHandle neighborBody = runtime.getTerrainVoxelBodyHandle(neighborUuid);
            BackendSpaceHandle neighborSpace = runtime.getTerrainSpaceHandle(neighborUuid);
            if (neighborBody == null
                || neighborSpace == null
                || neighborSpace.value() != spaceHandle.value()) {
                continue;
            }
            backendRuntime.combineVoxelTerrains(spaceHandle.value(),
                voxelBody.value(),
                neighborBody.value(),
                neighbor.shiftX(),
                neighbor.shiftY(),
                neighbor.shiftZ());
        }
    }

    private static void removeTerrain(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID terrainUuid) {
        BackendSpaceHandle spaceHandle = runtime.getTerrainSpaceHandle(terrainUuid);
        if (spaceHandle == null) {
            runtime.removeTerrainHandles(terrainUuid);
            return;
        }
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, spaceHandle);
        if (backendRuntime != null) {
            runtime.forEachTerrainBodyHandle(terrainUuid,
                bodyId -> backendRuntime.removeBody(spaceHandle.value(), bodyId));
        }
        runtime.removeTerrainHandles(terrainUuid);
    }

    @Nullable
    private static PhysicsBackendRuntime runtimeForSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID spaceUuid) {
        var backendId = runtime.getSpaceBackendId(spaceUuid);
        return backendId != null ? runtime.getRuntime(backendId) : null;
    }

    @Nullable
    private static PhysicsBackendRuntime runtimeForSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull BackendSpaceHandle spaceHandle) {
        final PhysicsBackendRuntime[] resolved = new PhysicsBackendRuntime[1];
        runtime.forEachSpaceBinding((_, _, handle, backendRuntime) -> {
            if (handle.value() == spaceHandle.value()) {
                resolved[0] = backendRuntime;
            }
        });
        return resolved[0];
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.UUID_QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
