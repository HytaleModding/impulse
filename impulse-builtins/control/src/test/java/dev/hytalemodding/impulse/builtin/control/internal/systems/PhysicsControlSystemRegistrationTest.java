package dev.hytalemodding.impulse.builtin.control.internal.systems;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.builtin.control.internal.ControlLifecycle;
import dev.hytalemodding.impulse.builtin.control.internal.ControlTypeRegistry;
import dev.hytalemodding.impulse.builtin.control.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.builtin.control.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.builtin.control.ImpulseControllableComponent;
import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsControlSystemRegistrationTest {

    @AfterEach
    void clearRegistrations() {
        ControlLifecycle.disable();
        ControlTypeRegistry.clearComponentTypes();
    }

    @Test
    void sessionCleanupSystemCapturesCurrentSessionComponentType() {
        ComponentRegistry<EntityStore> firstRegistry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(firstRegistry);
        ComponentType<EntityStore, PhysicsControlSessionComponent> first =
            PhysicsControlSessionComponent.getComponentType();
        PhysicsControlSessionCleanupSystem firstSystem = new PhysicsControlSessionCleanupSystem();

        ComponentRegistry<EntityStore> secondRegistry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(secondRegistry);
        ComponentType<EntityStore, PhysicsControlSessionComponent> second =
            PhysicsControlSessionComponent.getComponentType();
        PhysicsControlSessionCleanupSystem secondSystem = new PhysicsControlSessionCleanupSystem();

        assertSame(first, firstSystem.componentType());
        assertSame(second, secondSystem.componentType());
    }

    @Test
    void controllableLifecycleSystemCapturesCurrentControllableComponentType() {
        ComponentRegistry<EntityStore> firstRegistry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(firstRegistry);
        ComponentType<EntityStore, ImpulseControllableComponent> first =
            ImpulseControllableComponent.getComponentType();
        PhysicsControllableLifecycleSystem firstSystem = new PhysicsControllableLifecycleSystem();

        ComponentRegistry<EntityStore> secondRegistry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(secondRegistry);
        ComponentType<EntityStore, ImpulseControllableComponent> second =
            ImpulseControllableComponent.getComponentType();
        PhysicsControllableLifecycleSystem secondSystem = new PhysicsControllableLifecycleSystem();

        assertSame(first, firstSystem.componentType());
        assertSame(second, secondSystem.componentType());
    }

    @Test
    void holderSystemCapturesCurrentControlComponentTypes() throws ReflectiveOperationException {
        ComponentRegistry<EntityStore> firstRegistry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(firstRegistry);
        ComponentType<EntityStore, ImpulseControllableComponent> firstControllable =
            ImpulseControllableComponent.getComponentType();
        ComponentType<EntityStore, PhysicsControlSessionComponent> firstSession =
            PhysicsControlSessionComponent.getComponentType();
        PhysicsControlRuntimeHolderSystem firstSystem = new PhysicsControlRuntimeHolderSystem();

        ComponentRegistry<EntityStore> secondRegistry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(secondRegistry);
        ComponentType<EntityStore, ImpulseControllableComponent> secondControllable =
            ImpulseControllableComponent.getComponentType();
        ComponentType<EntityStore, PhysicsControlSessionComponent> secondSession =
            PhysicsControlSessionComponent.getComponentType();
        PhysicsControlRuntimeHolderSystem secondSystem = new PhysicsControlRuntimeHolderSystem();

        assertSame(firstControllable, field(firstSystem, "controllableType"));
        assertSame(firstSession, field(firstSystem, "sessionType"));
        assertSame(secondControllable, field(secondSystem, "controllableType"));
        assertSame(secondSession, field(secondSystem, "sessionType"));
    }

    @Test
    void holderLoadKeepsControllableMarker() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ComponentType<EntityStore, ImpulseControllableComponent> controllableType =
            registry.registerComponent(ImpulseControllableComponent.class,
                "ImpulseControllable",
                ImpulseControllableComponent.CODEC);
        ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType =
            registry.registerComponent(PhysicsControlSessionComponent.class,
                PhysicsControlSessionComponent::new);
        PhysicsControlRuntimeHolderSystem system = new PhysicsControlRuntimeHolderSystem(
            controllableType,
            sessionType);
        Store<EntityStore> store = registry.addStore(testEntityStore("control-marker-load-test"),
            EmptyResourceStorage.get());
        Holder<EntityStore> holder = registry.newHolder();
        holder.addComponent(controllableType, new ImpulseControllableComponent());

        system.onEntityAdd(holder, AddReason.LOAD, store);

        assertNotNull(holder.getComponent(controllableType));
        registry.shutdown();
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Nonnull
    private static EntityStore testEntityStore(@Nonnull String worldName) {
        return new EntityStore(TestInstanceFactory.world(worldName));
    }
}
