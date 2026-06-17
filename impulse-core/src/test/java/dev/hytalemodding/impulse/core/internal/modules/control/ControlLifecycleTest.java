package dev.hytalemodding.impulse.core.internal.modules.control;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ControlLifecycleTest {

    @BeforeEach
    @AfterEach
    void disableLifecycle() {
        ControlLifecycle.disable();
        ControlTypeRegistry.clearComponentTypes();
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
        ControlTypeRegistry.clearComponentTypes();
        ControlLifecycle.enable();

        assertDoesNotThrow(ControlLifecycle::disable);

        assertFalse(ControlLifecycle.isEnabled());
    }

    @Test
    void disablingLifecycleClearsRegisteredControlledBodies() {
        ControlLifecycle.enable();
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        Ref<PhysicsStore> bodyRef = new TestPhysicsRef(7);
        resource.markBodyControlled(bodyRef);

        assertTrue(resource.isBodyControlled(bodyRef));

        ControlLifecycle.disable();

        assertFalse(resource.isBodyControlled(bodyRef));
    }

    @Test
    void controlSessionsAreAvailableOnlyWhenLifecycleAndComponentTypesAreRegistered() {
        assertFalse(PhysicsControlSessions.isAvailable());

        ControlLifecycle.enable();
        assertFalse(PhysicsControlSessions.isAvailable());

        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(registry);

        assertTrue(PhysicsControlSessions.isAvailable());

        ControlLifecycle.disable();

        assertFalse(PhysicsControlSessions.isAvailable());
    }

    @Test
    void disablingLifecycleSkipsStoresWhoseWorldThreadHasStopped() {
        ControlLifecycle.enable();
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(registry);
        Store<EntityStore> store = registry.addStore(
            new EntityStore(TestInstanceFactory.world("stopped-control-world")),
            EmptyResourceStorage.get());
        ControlLifecycle.registerStore(store);

        assertDoesNotThrow(ControlLifecycle::disable);

        assertFalse(ControlLifecycle.isEnabled());
        registry.shutdown();
    }

    private static final class TestPhysicsRef extends Ref<PhysicsStore> {

        private TestPhysicsRef(int index) {
            super(null, index);
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }
}
