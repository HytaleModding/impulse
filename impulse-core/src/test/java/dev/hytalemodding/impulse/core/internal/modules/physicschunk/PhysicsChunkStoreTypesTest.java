package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.PhysicsStoreRegistration;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkComponentSyncResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkSettingsIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class PhysicsChunkStoreTypesTest {

    @Test
    void runtimeResourceCleanupIgnoresUnregisteredPhysicsChunkResources() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("physicschunk-store-resource-cleanup")),
            EmptyResourceStorage.get());
        try {
            unregisterPhysicsChunkResources(registry);

            assertDoesNotThrow(() -> PhysicsChunkStoreTypes.clearPhysicsStoreRuntimeResources(store));
        } finally {
            PhysicsChunkStoreTypes.clearPhysicsStoreResourceTypes();
            registry.removeStore(store);
        }
    }

    @Test
    void physicsChunkSystemsRegisterInPluginSetupOrder() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        try {
            PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
            PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes(proxy);
            PhysicsStoreRegistration.register(proxy);
            PhysicsChunkStoreTypes.registerSpaceBindingSystems(proxy);

            assertDoesNotThrow(() -> PhysicsChunkStoreTypes.registerPreBodyBindingSystems(proxy));
            assertDoesNotThrow(() -> PhysicsChunkStoreTypes.registerPostBodyBindingSystems(proxy));
        } finally {
            PhysicsChunkStoreTypes.clearPhysicsStoreResourceTypes();
            registry.shutdown();
        }
    }

    private static void unregisterPhysicsChunkResources(@Nonnull ComponentRegistry<PhysicsStore> registry) {
        registry.unregisterResource(PhysicsChunkCollisionMutationQueueResource.getResourceType());
        registry.unregisterResource(PhysicsChunkCollisionPayloadResource.getResourceType());
        registry.unregisterResource(PhysicsChunkSettingsIndexResource.getResourceType());
        registry.unregisterResource(PhysicsChunkComponentSyncResource.getResourceType());
    }
}
