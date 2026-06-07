package dev.hytalemodding.impulse.core.internal.modules.control;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class ControlLifecycleTest {

    @BeforeEach
    @AfterEach
    void disableLifecycle() {
        ControlLifecycle.disable();
        ImpulseControllableComponent.clearComponentType();
        PhysicsControlSessionComponent.clearComponentType();
    }

    @Test
    void lifecycleStartsDisabled() {
        assertFalse(ControlLifecycle.isEnabled());
    }

    @Test
    void lifecycleGenerationChangesWhenLifecycleIsDisabled() {
        ControlLifecycle.enable();
        long enabledGeneration = ControlLifecycle.generation();

        ControlLifecycle.disable();

        assertTrue(ControlLifecycle.generation() > enabledGeneration);
    }

    @Test
    void disablingLifecycleWithoutRegisteredSessionComponentDoesNotThrow() {
        PhysicsControlSessionComponent.clearComponentType();
        ControlLifecycle.enable();

        assertDoesNotThrow(ControlLifecycle::disable);

        assertFalse(ControlLifecycle.isEnabled());
    }

    @Test
    void disablingLifecycleClearsRegisteredControlledBodies() {
        ControlLifecycle.enable();
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        RigidBodyKey bodyKey = RigidBodyKey.random();
        resource.markBodyControlled(bodyKey);

        assertTrue(resource.isBodyControlled(bodyKey));

        ControlLifecycle.disable();

        assertFalse(resource.isBodyControlled(bodyKey));
    }

    @Test
    void controlSessionsAreAvailableOnlyWhenLifecycleAndComponentTypesAreRegistered() {
        assertFalse(PhysicsControlSessions.isAvailable());

        ControlLifecycle.enable();
        assertFalse(PhysicsControlSessions.isAvailable());

        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ImpulseControllableComponent.setComponentType(registry.registerComponent(
            ImpulseControllableComponent.class,
            "ImpulseControllable",
            ImpulseControllableComponent.CODEC));
        PhysicsControlSessionComponent.setComponentType(registry.registerComponent(
            PhysicsControlSessionComponent.class,
            PhysicsControlSessionComponent::new));

        assertTrue(PhysicsControlSessions.isAvailable());

        ControlLifecycle.disable();

        assertFalse(PhysicsControlSessions.isAvailable());
    }

    @Test
    void disablingLifecycleSkipsStoresWhoseWorldThreadHasStopped() {
        ControlLifecycle.enable();
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ImpulseControllableComponent.setComponentType(registry.registerComponent(
            ImpulseControllableComponent.class,
            "ImpulseControllable",
            ImpulseControllableComponent.CODEC));
        PhysicsControlSessionComponent.setComponentType(registry.registerComponent(
            PhysicsControlSessionComponent.class,
            PhysicsControlSessionComponent::new));
        Store<EntityStore> store = registry.addStore(
            new EntityStore(testWorld("stopped-control-world")),
            EmptyResourceStorage.get());
        ControlLifecycle.registerStore(store);

        assertDoesNotThrow(ControlLifecycle::disable);

        assertFalse(ControlLifecycle.isEnabled());
        registry.shutdown();
    }

    @Nonnull
    private static World testWorld(@Nonnull String worldName) {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            World world = (World) unsafe.allocateInstance(World.class);
            Field nameField = World.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(world, worldName);
            return world;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to create test world", exception);
        }
    }
}
