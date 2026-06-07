package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsControlSystemRegistrationTest {

    @AfterEach
    void clearRegistrations() {
        ImpulseControllableComponent.clearComponentType();
        PhysicsControlSessionComponent.clearComponentType();
    }

    @Test
    void sessionCleanupSystemCapturesCurrentSessionComponentType() {
        ComponentType<EntityStore, PhysicsControlSessionComponent> first = registerSessionType();
        PhysicsControlSessionComponent.setComponentType(first);
        PhysicsControlSessionCleanupSystem firstSystem = new PhysicsControlSessionCleanupSystem();

        ComponentType<EntityStore, PhysicsControlSessionComponent> second = registerSessionType();
        PhysicsControlSessionComponent.setComponentType(second);
        PhysicsControlSessionCleanupSystem secondSystem = new PhysicsControlSessionCleanupSystem();

        assertSame(first, firstSystem.componentType());
        assertSame(second, secondSystem.componentType());
    }

    @Test
    void controllableLifecycleSystemCapturesCurrentControllableComponentType() {
        ComponentType<EntityStore, ImpulseControllableComponent> first =
            registerControllableType();
        ImpulseControllableComponent.setComponentType(first);
        PhysicsControllableLifecycleSystem firstSystem = new PhysicsControllableLifecycleSystem();

        ComponentType<EntityStore, ImpulseControllableComponent> second =
            registerControllableType();
        ImpulseControllableComponent.setComponentType(second);
        PhysicsControllableLifecycleSystem secondSystem = new PhysicsControllableLifecycleSystem();

        assertSame(first, firstSystem.componentType());
        assertSame(second, secondSystem.componentType());
    }

    @Test
    void holderSystemCapturesCurrentControlComponentTypes() throws ReflectiveOperationException {
        ComponentType<EntityStore, ImpulseControllableComponent> firstControllable =
            registerControllableType();
        ComponentType<EntityStore, PhysicsControlSessionComponent> firstSession =
            registerSessionType();
        ImpulseControllableComponent.setComponentType(firstControllable);
        PhysicsControlSessionComponent.setComponentType(firstSession);
        PhysicsControlRuntimeHolderSystem firstSystem = new PhysicsControlRuntimeHolderSystem();

        ComponentType<EntityStore, ImpulseControllableComponent> secondControllable =
            registerControllableType();
        ComponentType<EntityStore, PhysicsControlSessionComponent> secondSession =
            registerSessionType();
        ImpulseControllableComponent.setComponentType(secondControllable);
        PhysicsControlSessionComponent.setComponentType(secondSession);
        PhysicsControlRuntimeHolderSystem secondSystem = new PhysicsControlRuntimeHolderSystem();

        assertSame(firstControllable, field(firstSystem, "controllableType"));
        assertSame(firstSession, field(firstSystem, "sessionType"));
        assertSame(secondControllable, field(secondSystem, "controllableType"));
        assertSame(secondSession, field(secondSystem, "sessionType"));
    }

    private static ComponentType<EntityStore, ImpulseControllableComponent> registerControllableType() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        return registry.registerComponent(ImpulseControllableComponent.class,
            "ImpulseControllable",
            ImpulseControllableComponent.CODEC);
    }

    private static ComponentType<EntityStore, PhysicsControlSessionComponent> registerSessionType() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        return registry.registerComponent(PhysicsControlSessionComponent.class,
            PhysicsControlSessionComponent::new);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
