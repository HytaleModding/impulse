package dev.hytalemodding.impulse.core.internal.modules.control.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsControlSessionComponentTest {

    @AfterEach
    void clearRegistration() {
        PhysicsControlSessionComponent.clearComponentType();
    }

    @Test
    void componentTypeCanBeClearedWhenControlModuleUnloads() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ComponentType<EntityStore, PhysicsControlSessionComponent> type =
            registry.registerComponent(PhysicsControlSessionComponent.class,
                PhysicsControlSessionComponent::new);

        PhysicsControlSessionComponent.setComponentType(type);

        assertTrue(PhysicsControlSessionComponent.isComponentTypeRegistered());
        assertSame(type, PhysicsControlSessionComponent.getComponentType());

        PhysicsControlSessionComponent.clearComponentType();

        assertFalse(PhysicsControlSessionComponent.isComponentTypeRegistered());
        assertThrows(IllegalStateException.class, PhysicsControlSessionComponent::getComponentType);
    }
}
