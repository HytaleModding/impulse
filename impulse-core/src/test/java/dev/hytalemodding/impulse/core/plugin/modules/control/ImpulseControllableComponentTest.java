package dev.hytalemodding.impulse.core.plugin.modules.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ImpulseControllableComponentTest {

    @AfterEach
    void clearRegistration() {
        ImpulseControllableComponent.clearComponentType();
    }

    @Test
    void componentTypeIsOwnedByControlModuleRegistration() {
        assertFalse(ImpulseControllableComponent.isComponentTypeRegistered());
        assertThrows(IllegalStateException.class, ImpulseControllableComponent::getComponentType);

        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ComponentType<EntityStore, ImpulseControllableComponent> type =
            registry.registerComponent(ImpulseControllableComponent.class,
                "ImpulseControllable",
                ImpulseControllableComponent.CODEC);

        ImpulseControllableComponent.setComponentType(type);

        assertTrue(ImpulseControllableComponent.isComponentTypeRegistered());
        assertSame(type, ImpulseControllableComponent.getComponentType());

        ImpulseControllableComponent.clearComponentType();

        assertFalse(ImpulseControllableComponent.isComponentTypeRegistered());
    }
}
