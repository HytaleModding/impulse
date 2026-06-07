package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class PhysicsWorldRuntimeResourceTest {

    @Test
    void registersRuntimeImplementationBehindPublicResourceType() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, PhysicsWorldResource> resourceType =
            registry.registerResource(PhysicsWorldResource.class,
                PhysicsWorldRuntimeResource::new);

        assertSame(PhysicsWorldResource.class, resourceType.getTypeClass());

        Store<EntityStore> store = registry.addStore(testEntityStore("runtime-resource-test"),
            EmptyResourceStorage.get());
        PhysicsWorldResource resource = store.getResource(resourceType);

        PhysicsWorldRuntimeResource runtime =
            assertInstanceOf(PhysicsWorldRuntimeResource.class, resource);
        assertSame(runtime, PhysicsWorldRuntimeResource.require(resource));

        registry.removeStore(store);
        registry.shutdown();
    }

    @Nonnull
    private static EntityStore testEntityStore(@Nonnull String worldName) {
        return new EntityStore(TestInstanceFactory.world(worldName));
    }
}
