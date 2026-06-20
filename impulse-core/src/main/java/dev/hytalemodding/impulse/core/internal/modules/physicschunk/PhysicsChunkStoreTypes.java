package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkComponentSyncResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkSettingsIndexResource;
import dev.hytalemodding.impulse.core.internal.systems.ChunkCollisionComponentSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.ChunkCollisionMutationDrainSystem;
import dev.hytalemodding.impulse.core.internal.systems.ChunkCollisionVoxelStitchingSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsChunkSettingsIndexSystem;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * PhysicsStore-side type registration owned by the PhysicsChunk module.
 */
public final class PhysicsChunkStoreTypes {

    private PhysicsChunkStoreTypes() {
    }

    public static void registerPhysicsStoreResourceTypes(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        PhysicsChunkCollisionMutationQueueResource.setResourceType(registry.registerResource(
            PhysicsChunkCollisionMutationQueueResource.class,
            PhysicsChunkCollisionMutationQueueResource::new));
        PhysicsChunkCollisionPayloadResource.setResourceType(registry.registerResource(
            PhysicsChunkCollisionPayloadResource.class,
            PhysicsChunkCollisionPayloadResource::new));
        PhysicsChunkSettingsIndexResource.setResourceType(registry.registerResource(
            PhysicsChunkSettingsIndexResource.class,
            PhysicsChunkSettingsIndexResource::new));
        PhysicsChunkComponentSyncResource.setResourceType(registry.registerResource(
            PhysicsChunkComponentSyncResource.class,
            PhysicsChunkComponentSyncResource::new));
    }

    public static void clearPhysicsStoreResourceTypes() {
        PhysicsChunkCollisionMutationQueueResource.clearResourceType();
        PhysicsChunkCollisionPayloadResource.clearResourceType();
        PhysicsChunkSettingsIndexResource.clearResourceType();
        PhysicsChunkComponentSyncResource.clearResourceType();
    }

    public static void registerSpaceBindingSystems(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        registry.registerSystem(new PhysicsChunkSettingsIndexSystem());
    }

    public static void registerPreBodyBindingSystems(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        registry.registerSystem(new ChunkCollisionMutationDrainSystem());
    }

    public static void registerPostBodyBindingSystems(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        registry.registerSystem(new ChunkCollisionComponentSyncSystem());
        registry.registerSystem(new ChunkCollisionVoxelStitchingSystem());
    }

    public static void clearPhysicsStoreRuntimeResources(@Nonnull Store<PhysicsStore> store) {
        clearIfPresent(store,
            PhysicsChunkCollisionMutationQueueResource.getResourceType(),
            PhysicsChunkCollisionMutationQueueResource::clear);
        clearIfPresent(store,
            PhysicsChunkCollisionPayloadResource.getResourceType(),
            PhysicsChunkCollisionPayloadResource::clear);
        clearIfPresent(store,
            PhysicsChunkSettingsIndexResource.getResourceType(),
            PhysicsChunkSettingsIndexResource::clear);
        clearIfPresent(store,
            PhysicsChunkComponentSyncResource.getResourceType(),
            PhysicsChunkComponentSyncResource::clear);
    }

    private static <T extends Resource<PhysicsStore>> void clearIfPresent(
        @Nonnull Store<PhysicsStore> store,
        @Nullable ResourceType<PhysicsStore, T> type,
        @Nonnull Consumer<T> clear) {
        if (type == null) {
            return;
        }
        T resource;
        try {
            type.validate();
            resource = store.getResource(type);
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | IllegalStateException _) {
            // Optional PhysicsChunk resources can be unregistered before the core shutdown hook runs.
            return;
        }
        if (resource != null) {
            clear.accept(resource);
        }
    }
}
