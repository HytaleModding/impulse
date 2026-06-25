package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStoreTypes;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.systems.binding.BodyBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.SpaceBindingSystem;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import java.util.ArrayList;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class BodyCommandApplicationSystemTest {

    @Test
    void copiedComponentCommandsMutateThroughCommandBufferDuringStoreTick() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes(proxy);
        proxy.registerSystem(new PersistenceHydrationSystem());
        proxy.registerSystem(new IdentityIndexSystem());
        proxy.registerSystem(new SpaceBindingSystem());
        proxy.registerSystem(new SpaceSettingsApplicationSystem());
        proxy.registerSystem(new BodyBindingSystem());
        proxy.registerSystem(new BodyCommandApplicationSystem());
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("body-command-application-test")),
            EmptyResourceStorage.get());
        try {
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            restore.markComplete();
            restore.markHydrated();
            Ref<PhysicsStore> bodyRef = addCommandedBody(store);

            store.tick(0.0f);

            DynamicsComponent dynamics = store.getComponent(bodyRef,
                DynamicsComponent.getComponentType());
            assertNotNull(dynamics);
            assertEquals(PhysicsBodyType.KINEMATIC, dynamics.getBodyType());
            CollisionFilterComponent filter = store.getComponent(bodyRef,
                CollisionFilterComponent.getComponentType());
            assertNotNull(filter);
            assertEquals(0x40, filter.getCollisionGroup());
            assertEquals(0x07, filter.getCollisionMask());
            assertNull(store.getComponent(bodyRef, BodyCommandComponent.getComponentType()));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static Ref<PhysicsStore> addCommandedBody(@Nonnull Store<PhysicsStore> store) {
        Holder<PhysicsStore> holder = PhysicsEntities.entityHolder(store, UUID.randomUUID());
        holder.addComponent(DynamicsComponent.getComponentType(),
            new DynamicsComponent(PhysicsBodyType.DYNAMIC, 1.0f, 0.0f, 0.0f, false));
        holder.addComponent(CollisionFilterComponent.getComponentType(),
            new CollisionFilterComponent(0x01, 0x02));
        holder.addComponent(BodyCommandComponent.getComponentType(),
            BodyCommandComponent.setType(PhysicsBodyType.KINEMATIC, false)
                .append(BodyCommandComponent.setCollisionFilter(0x40, 0x07, false)));
        return store.addEntity(holder, AddReason.SPAWN);
    }
}
