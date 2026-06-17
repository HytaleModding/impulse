package dev.hytalemodding.impulse.core.internal.modules.control.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlTypeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsControlSessionComponentTest {

    @AfterEach
    void clearRegistration() {
        ControlTypeRegistry.clearComponentTypes();
    }

    @Test
    void componentTypeCanBeClearedWhenControlModuleUnloads() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(registry);
        ComponentType<EntityStore, PhysicsControlSessionComponent> type =
            PhysicsControlSessionComponent.getComponentType();

        assertTrue(PhysicsControlSessionComponent.isComponentTypeRegistered());
        assertSame(type, PhysicsControlSessionComponent.getComponentType());

        ControlTypeRegistry.clearComponentTypes();

        assertFalse(PhysicsControlSessionComponent.isComponentTypeRegistered());
        assertThrows(IllegalStateException.class, PhysicsControlSessionComponent::getComponentType);
    }
}
