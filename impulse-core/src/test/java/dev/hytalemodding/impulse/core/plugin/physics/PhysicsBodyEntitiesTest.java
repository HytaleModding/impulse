package dev.hytalemodding.impulse.core.plugin.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsBodyEntitiesTest {

    @Test
    void physicsBodiesDoesNotExposeBodyUuidRefHelper() {
        assertThrows(NoSuchMethodException.class,
            () -> PhysicsBodies.class.getDeclaredMethod("bodyUuid", Store.class, Ref.class));
    }

    @Test
    void dynamicBodyHolderInfersEntityIdentityFromBodyUuidAndSpaceRef() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("physics-body-entities-holder-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(1);
            UUID bodyUuid = uuid(2);
            Ref<PhysicsStore> spaceRef = PhysicsSpaces.create(store,
                spaceUuid,
                new SpaceId(4101),
                new BackendId("test:body-entities"));

            Holder<PhysicsStore> holder = PhysicsBodyEntities.dynamicBodyHolder(spaceRef,
                bodyUuid,
                new Vector3f(1.0f, 2.0f, 3.0f),
                PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
                1.0f,
                RigidBodySpawnSettings.defaults(),
                null);
            Ref<PhysicsStore> bodyRef = store.addEntity(holder, AddReason.SPAWN);

            assertEquals(bodyUuid,
                store.getComponent(bodyRef, UuidComponent.getComponentType()).getUuid());
            BodyComponent body = store.getComponent(bodyRef, BodyComponent.getComponentType());
            assertEquals(spaceUuid, body.getSpaceUuid());
            assertSame(spaceRef, body.getSpaceRef());
            assertEquals(1.0f,
                store.getComponent(bodyRef, DynamicsComponent.getComponentType()).getMass());
            assertEquals(0.5f,
                store.getComponent(bodyRef, ShapeComponent.getComponentType()).getHalfExtentX());
            assertEquals(0.5f,
                store.getComponent(bodyRef, MaterialComponent.getComponentType()).getFriction());
            assertEquals(0.0f,
                store.getComponent(bodyRef, ColliderComponent.getComponentType())
                    .getLocalPosition()
                    .x);
            assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY,
                store.getComponent(bodyRef, CollisionFilterComponent.getComponentType())
                    .getCollisionGroup());
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static UUID uuid(int lowBits) {
        return new UUID(0L, lowBits);
    }

    private static void markCurrentThreadAsWorldThread(@Nonnull Store<PhysicsStore> store) {
        try {
            Method setThread = TickingThread.class.getDeclaredMethod("setThread", Thread.class);
            setThread.setAccessible(true);
            setThread.invoke(store.getExternalData().getWorld(), Thread.currentThread());
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not mark test world thread", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Could not mark test world thread",
                exception.getTargetException());
        }
    }
}
