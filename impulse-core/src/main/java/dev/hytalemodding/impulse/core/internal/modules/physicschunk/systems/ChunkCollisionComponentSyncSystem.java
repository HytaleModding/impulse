package dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems;

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
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkComponentSyncResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkComponentSyncResource.ChunkCollisionSurfaceComponents;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.IdentityIndexSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsStoreSystemSupport;
import dev.hytalemodding.impulse.core.internal.systems.binding.BodyBindingSystem;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * Propagates space-row material and collision-filter component changes to generated chunk bodies.
 */
public final class ChunkCollisionComponentSyncSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, IdentityIndexSystem.class),
        new SystemDependency<>(Order.AFTER, ChunkCollisionMutationDrainSystem.class),
        new SystemDependency<>(Order.BEFORE, BodyBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        Map<UUID, ChunkCollisionSurfaceComponents> surfaces = collectSpaceSurfaces(store,
            systemIndex);
        Set<UUID> changedSpaces = store
            .getResource(PhysicsChunkComponentSyncResource.getResourceType())
            .replaceAll(surfaces);
        if (changedSpaces.isEmpty()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, commandBuffer) -> syncChunk(runtime,
                changedSpaces,
                surfaces,
                chunk,
                commandBuffer);
        store.forEachChunk(systemIndex, collector);
    }

    @Nonnull
    private static Map<UUID, ChunkCollisionSurfaceComponents> collectSpaceSurfaces(
        @Nonnull Store<PhysicsStore> store,
        int systemIndex) {
        Map<UUID, ChunkCollisionSurfaceComponents> surfaces = new Object2ObjectOpenHashMap<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectSpaceSurfaces(surfaces, chunk);
        store.forEachChunk(systemIndex, collector);
        return surfaces;
    }

    private static void collectSpaceSurfaces(
        @Nonnull Map<UUID, ChunkCollisionSurfaceComponents> surfaces,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            if (chunk.getComponent(index, SpaceComponent.getComponentType()) == null) {
                continue;
            }
            UUID spaceUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(spaceUuid)) {
                continue;
            }
            surfaces.put(spaceUuid,
                ChunkCollisionSurfaceComponents.of(
                    chunk.getComponent(index, MaterialComponent.getComponentType()),
                    chunk.getComponent(index, CollisionFilterComponent.getComponentType())));
        }
    }

    private static void syncChunk(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Set<UUID> changedSpaces,
        @Nonnull Map<UUID, ChunkCollisionSurfaceComponents> surfaces,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk,
        @Nonnull CommandBuffer<PhysicsStore> commandBuffer) {
        for (int index = 0; index < chunk.size(); index++) {
            if (chunk.getComponent(index, ChunkCollisionSourceComponent.getComponentType()) == null) {
                continue;
            }
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body == null || !changedSpaces.contains(body.getSpaceUuid())) {
                continue;
            }
            ChunkCollisionSurfaceComponents surface = surfaces.get(body.getSpaceUuid());
            if (surface == null) {
                continue;
            }
            Ref<PhysicsStore> bodyRef = chunk.getReferenceTo(index);
            boolean materialChanged = syncMaterial(commandBuffer, bodyRef, surface,
                chunk.getComponent(index, MaterialComponent.getComponentType()));
            boolean filterChanged = syncFilter(commandBuffer, bodyRef, surface,
                chunk.getComponent(index, CollisionFilterComponent.getComponentType()));
            if (materialChanged || filterChanged) {
                syncBackend(runtime, bodyRef, surface, materialChanged, filterChanged);
            }
        }
    }

    private static boolean syncMaterial(@Nonnull CommandBuffer<PhysicsStore> commandBuffer,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull ChunkCollisionSurfaceComponents surface,
        MaterialComponent material) {
        if (material != null
            && Float.compare(material.getFriction(), surface.friction()) == 0
            && Float.compare(material.getRestitution(), surface.restitution()) == 0) {
            return false;
        }
        commandBuffer.putComponent(bodyRef,
            MaterialComponent.getComponentType(),
            surface.material());
        return true;
    }

    private static boolean syncFilter(@Nonnull CommandBuffer<PhysicsStore> commandBuffer,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull ChunkCollisionSurfaceComponents surface,
        CollisionFilterComponent filter) {
        if (filter != null
            && filter.getCollisionGroup() == surface.collisionGroup()
            && filter.getCollisionMask() == surface.collisionMask()) {
            return false;
        }
        commandBuffer.putComponent(bodyRef,
            CollisionFilterComponent.getComponentType(),
            surface.filter());
        return true;
    }

    private static void syncBackend(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull ChunkCollisionSurfaceComponents surface,
        boolean materialChanged,
        boolean filterChanged) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyRef);
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(bodyRef);
        if (bodyHandle == null || spaceHandle == null) {
            return;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForBodyRef(bodyRef);
        if (backendRuntime == null) {
            return;
        }
        if (materialChanged) {
            backendRuntime.setBodyFriction(spaceHandle.value(),
                bodyHandle.value(),
                surface.friction());
            backendRuntime.setBodyRestitution(spaceHandle.value(),
                bodyHandle.value(),
                surface.restitution());
        }
        if (filterChanged) {
            backendRuntime.setBodyCollisionFilter(spaceHandle.value(),
                bodyHandle.value(),
                surface.collisionGroup(),
                surface.collisionMask());
        }
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
}
