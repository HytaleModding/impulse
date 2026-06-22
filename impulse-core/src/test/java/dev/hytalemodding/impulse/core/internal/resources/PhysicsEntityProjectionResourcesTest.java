package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.PhysicsEntityTypeRegistry;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.resources.PhysicsBodySyncStateResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsEntityProjectionResourcesTest {

    @AfterEach
    void clearTypes() {
        PhysicsEntityTypeRegistry.clearEntityStoreTypes();
    }

    @Test
    void registersProjectionResourcesWithoutWorldResourceFacade() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<EntityStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);

        PhysicsEntityTypeRegistry.registerComponentTypes(proxy);
        PhysicsEntityTypeRegistry.registerResourceTypes(proxy);
        PhysicsEntityTypeRegistry.registerEventTypes(proxy);
        PhysicsEntityTypeRegistry.registerSystemGroups(proxy);

        assertResourceClass(PhysicsProjectionIndexResource.getResourceType(),
            PhysicsProjectionIndexResource.class);
        assertResourceClass(PhysicsBodySyncStateResource.getResourceType(),
            PhysicsBodySyncStateResource.class);
        assertResourceClass(PhysicsVisualInterestResource.getResourceType(),
            PhysicsVisualInterestResource.class);

        Store<EntityStore> store = registry.addStore(testEntityStore("projection-resources-test"),
            EmptyResourceStorage.get());
        try {
            assertNotNull(store.getResource(PhysicsProjectionIndexResource.getResourceType()));
            assertNotNull(store.getResource(PhysicsBodySyncStateResource.getResourceType()));
            assertNotNull(store.getResource(PhysicsVisualInterestResource.getResourceType()));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    private static <T extends Resource<EntityStore>> void assertResourceClass(
        @Nonnull ResourceType<EntityStore, T> resourceType,
        @Nonnull Class<T> expectedType) {
        assertSame(expectedType, resourceType.getTypeClass());
    }

    @Nonnull
    private static EntityStore testEntityStore(@Nonnull String worldName) {
        return new EntityStore(TestInstanceFactory.world(worldName));
    }
}
