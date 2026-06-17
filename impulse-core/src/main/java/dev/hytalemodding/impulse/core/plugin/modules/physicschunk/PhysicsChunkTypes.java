package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.PhysicsStoreWorldCollisionProducerSystem;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldCollisionIndexResource;
import dev.hytalemodding.impulse.core.internal.systems.TerrainColliderBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.TerrainMutationDrainSystem;
import dev.hytalemodding.impulse.core.internal.systems.WorldCollisionIndexSystem;
import dev.hytalemodding.impulse.core.plugin.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.WorldCollisionComponent;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered PhysicsStore type handles owned by the PhysicsChunk integration module.
 */
public final class PhysicsChunkTypes {

    @Nullable
    private static ComponentType<PhysicsStore, TerrainColliderComponent> terrainColliderComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, WorldCollisionComponent> worldCollisionComponentType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsTerrainMutationQueueResource>
        terrainMutationQueueResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsTerrainPayloadResource>
        terrainPayloadResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource>
        worldCollisionIndexResourceType;

    private PhysicsChunkTypes() {
    }

    public static void registerComponentTypes(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        terrainColliderComponentType = registry.registerComponent(
            TerrainColliderComponent.class,
            "TerrainCollider",
            TerrainColliderComponent.CODEC);
        worldCollisionComponentType = registry.registerComponent(
            WorldCollisionComponent.class,
            "WorldCollision",
            WorldCollisionComponent.CODEC);
    }

    public static void registerResourceTypes(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        terrainMutationQueueResourceType = registry.registerResource(
            PhysicsTerrainMutationQueueResource.class,
            PhysicsTerrainMutationQueueResource::new);
        terrainPayloadResourceType = registry.registerResource(
            PhysicsTerrainPayloadResource.class,
            PhysicsTerrainPayloadResource::new);
        worldCollisionIndexResourceType = registry.registerResource(
            PhysicsWorldCollisionIndexResource.class,
            PhysicsWorldCollisionIndexResource::new);
    }

    public static void registerSystems(@Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        registry.registerSystem(new TerrainMutationDrainSystem());
        registry.registerSystem(new WorldCollisionIndexSystem());
        registry.registerSystem(new TerrainColliderBindingSystem());
    }

    public static void registerEntityStoreResourceTypes(
        @Nonnull ComponentRegistryProxy<EntityStore> registry) {
        WorldCollisionProfilingResource.setResourceType(registry.registerResource(
            WorldCollisionProfilingResource.class,
            WorldCollisionProfilingResource::new));
        PhysicsStoreWorldCollisionStreamingResource.setResourceType(registry.registerResource(
            PhysicsStoreWorldCollisionStreamingResource.class,
            PhysicsStoreWorldCollisionStreamingResource::new));
    }

    public static void registerEntityStoreSystems(
        @Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new PhysicsStoreWorldCollisionProducerSystem());
    }

    public static void clearEntityStoreResourceTypes() {
        WorldCollisionProfilingResource.clearResourceType();
        PhysicsStoreWorldCollisionStreamingResource.clearResourceType();
    }

    public static void clearRuntimeStateBeforeShutdown(@Nonnull PhysicsStore physicsStore) {
        Store<PhysicsStore> store = physicsStore.getStore();
        if (store.isShutdown()) {
            return;
        }
        cleanupResource(store,
            PhysicsTerrainMutationQueueResource.getResourceType(),
            PhysicsTerrainMutationQueueResource::clear);
        cleanupResource(store,
            PhysicsTerrainPayloadResource.getResourceType(),
            PhysicsTerrainPayloadResource::clear);
        cleanupResource(store,
            PhysicsWorldCollisionIndexResource.getResourceType(),
            PhysicsWorldCollisionIndexResource::clear);
    }

    private static <T extends Resource<PhysicsStore>> void cleanupResource(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull ResourceType<PhysicsStore, T> type,
        @Nonnull Consumer<T> cleanup) {
        cleanup.accept(store.getResource(type));
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TerrainColliderComponent>
    terrainColliderComponentType() {
        return terrainColliderComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, WorldCollisionComponent>
    worldCollisionComponentType() {
        return worldCollisionComponentType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsTerrainMutationQueueResource>
    terrainMutationQueueResourceType() {
        return terrainMutationQueueResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsTerrainPayloadResource>
    terrainPayloadResourceType() {
        return terrainPayloadResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource>
    worldCollisionIndexResourceType() {
        return worldCollisionIndexResourceType;
    }
}
