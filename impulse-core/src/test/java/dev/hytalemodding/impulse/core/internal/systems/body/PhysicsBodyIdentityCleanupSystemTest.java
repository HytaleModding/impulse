package dev.hytalemodding.impulse.core.internal.systems.body;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyIdentityComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import org.junit.jupiter.api.Test;

class PhysicsBodyIdentityCleanupSystemTest {

    @Test
    void removingEntityAuthoredIdentityDestroysBackendBody() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:identity-cleanup");
        Impulse.registerBackend(backend);
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ComponentType<EntityStore, PhysicsBodyIdentityComponent> identityType =
            registry.registerComponent(PhysicsBodyIdentityComponent.class,
                "PhysicsBodyIdentity",
                PhysicsBodyIdentityComponent.CODEC);
        ResourceType<EntityStore, PhysicsWorldResource> resourceType =
            registry.registerResource(PhysicsWorldResource.class,
                LegacyLiveHandleTestResource::new);
        Store<EntityStore> store = registry.addStore(
            new EntityStore(TestInstanceFactory.world("identity-cleanup-test")),
            EmptyResourceStorage.get());
        try {
            LegacyLiveHandleTestResource resource =
                (LegacyLiveHandleTestResource) store.getResource(resourceType);
            PhysicsSpace space = resource.createLiveSpace(backend.getId());
            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            RigidBodyKey bodyKey = RigidBodyKey.random();
            resource.addBody(bodyKey,
                space.id(),
                body,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.PERSISTENT);

            new PhysicsBodyIdentityCleanupSystem(identityType, resourceType).onComponentRemoved(null,
                new PhysicsBodyIdentityComponent(bodyKey,
                    space.id(),
                    PhysicsBodyPersistenceMode.PERSISTENT),
                store,
                null);

            assertNull(resource.getBody(bodyKey));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void replacingEntityAuthoredIdentityDestroysOldBackendBodyOnly() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:identity-replacement-cleanup");
        Impulse.registerBackend(backend);
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ComponentType<EntityStore, PhysicsBodyIdentityComponent> identityType =
            registry.registerComponent(PhysicsBodyIdentityComponent.class,
                "PhysicsBodyIdentity",
                PhysicsBodyIdentityComponent.CODEC);
        ResourceType<EntityStore, PhysicsWorldResource> resourceType =
            registry.registerResource(PhysicsWorldResource.class,
                LegacyLiveHandleTestResource::new);
        Store<EntityStore> store = registry.addStore(
            new EntityStore(TestInstanceFactory.world("identity-replacement-cleanup-test")),
            EmptyResourceStorage.get());
        try {
            LegacyLiveHandleTestResource resource =
                (LegacyLiveHandleTestResource) store.getResource(resourceType);
            PhysicsSpace space = resource.createLiveSpace(backend.getId());
            RigidBodyKey oldKey = RigidBodyKey.random();
            RigidBodyKey newKey = RigidBodyKey.random();
            resource.addBody(oldKey,
                space.id(),
                space.createBox(0.5f, 0.5f, 0.5f, 1.0f),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.PERSISTENT);
            resource.addBody(newKey,
                space.id(),
                space.createBox(0.5f, 0.5f, 0.5f, 1.0f),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.PERSISTENT);

            new PhysicsBodyIdentityCleanupSystem(identityType, resourceType).onComponentSet(null,
                new PhysicsBodyIdentityComponent(oldKey,
                    space.id(),
                    PhysicsBodyPersistenceMode.PERSISTENT),
                new PhysicsBodyIdentityComponent(newKey,
                    space.id(),
                    PhysicsBodyPersistenceMode.PERSISTENT),
                store,
                null);

            assertNull(resource.getBody(oldKey));
            assertNotNull(resource.getBody(newKey));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }
}
