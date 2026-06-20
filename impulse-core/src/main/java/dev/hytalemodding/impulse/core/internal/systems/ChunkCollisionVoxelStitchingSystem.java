package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload;
import dev.hytalemodding.impulse.core.internal.systems.binding.BodyBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.TargetBindingSystem;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies native voxel terrain adjacency hints for generated PhysicsChunk body rows.
 */
public final class ChunkCollisionVoxelStitchingSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, BodyBindingSystem.class),
        new SystemDependency<>(Order.BEFORE, TargetBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        PhysicsChunkCollisionPayloadResource payloads = store.getResource(
            PhysicsChunkCollisionPayloadResource.getResourceType());
        Set<BodyPair> stitchedPairs = new ObjectOpenHashSet<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> stitchChunk(runtime, identity, payloads, restore, stitchedPairs, chunk);
        store.forEachChunk(systemIndex, collector);
    }

    private static void stitchChunk(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsChunkCollisionPayloadResource payloads,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Set<BodyPair> stitchedPairs,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            ChunkCollisionSourceComponent source = chunk.getComponent(index,
                ChunkCollisionSourceComponent.getComponentType());
            if (source == null || source.getPartKind() != PartKind.NATIVE_VOXELS) {
                continue;
            }
            Ref<PhysicsStore> bodyRef = chunk.getReferenceTo(index);
            if (runtime.isChunkCollisionPayloadBound(bodyRef, source.getPayloadResourceKey())) {
                continue;
            }
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body == null) {
                continue;
            }
            stitchBody(runtime,
                identity,
                payloads,
                restore,
                stitchedPairs,
                bodyRef,
                body,
                source);
        }
    }

    private static void stitchBody(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsChunkCollisionPayloadResource payloads,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Set<BodyPair> stitchedPairs,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull BodyComponent body,
        @Nonnull ChunkCollisionSourceComponent source) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyRef);
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(bodyRef);
        if (bodyHandle == null || spaceHandle == null) {
            return;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForBodyRef(bodyRef);
        if (backendRuntime == null) {
            restore.recordSoftSkip("Voxel terrain backend runtime is missing: "
                + source.getSourceKey());
            return;
        }
        ChunkCollisionPayload payload = payloads.get(source.getPayloadResourceKey());
        if (payload == null) {
            restore.recordSoftSkip("Voxel chunk collision payload is missing: " + source.getSourceKey());
            return;
        }
        for (ChunkCollisionPayload.Neighbor neighbor : payload.neighbors()) {
            stitchNeighbor(runtime,
                identity,
                backendRuntime,
                spaceHandle,
                body.getSpaceUuid(),
                bodyHandle,
                neighbor,
                stitchedPairs);
        }
        runtime.markChunkCollisionPayloadBound(bodyRef, source.getPayloadResourceKey());
    }

    private static void stitchNeighbor(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsBackendRuntime backendRuntime,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull UUID spaceUuid,
        @Nonnull BackendBodyHandle bodyHandle,
        @Nonnull ChunkCollisionPayload.Neighbor neighbor,
        @Nonnull Set<BodyPair> stitchedPairs) {
        Ref<PhysicsStore> neighborRef = neighborRef(identity, spaceUuid, neighbor.sourceKey());
        if (neighborRef == null) {
            return;
        }
        BackendBodyHandle neighborBody = runtime.getBodyHandle(neighborRef);
        BackendSpaceHandle neighborSpace = runtime.getBodySpaceHandle(neighborRef);
        if (neighborBody == null
            || neighborSpace == null
            || neighborSpace.value() != spaceHandle.value()) {
            return;
        }
        BodyPair pair = BodyPair.of(bodyHandle.value(), neighborBody.value());
        if (!stitchedPairs.add(pair)) {
            return;
        }
        backendRuntime.combineVoxelTerrains(spaceHandle.value(),
            bodyHandle.value(),
            neighborBody.value(),
            neighbor.shiftX(),
            neighbor.shiftY(),
            neighbor.shiftZ());
    }

    @Nullable
    private static Ref<PhysicsStore> neighborRef(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID spaceUuid,
        @Nonnull String sourceKey) {
        UUID neighborUuid = ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
            sourceKey,
            PartKind.NATIVE_VOXELS,
            0);
        return PhysicsStoreSystemSupport.refForUuid(identity, neighborUuid);
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.uuidQuery();
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private record BodyPair(long first, long second) {

        private static BodyPair of(long first, long second) {
            return first <= second ? new BodyPair(first, second) : new BodyPair(second, first);
        }
    }
}
