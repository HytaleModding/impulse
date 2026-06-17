package dev.hytalemodding.impulse.core.internal.modules.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ImpulseControllableComponentRegistrationTest {

    @AfterEach
    void clearRegistration() {
        ControlTypeRegistry.clearComponentTypes();
    }

    @Test
    void componentTypeIsOwnedByControlSubPluginRegistration() {
        assertFalse(ImpulseControllableComponent.isComponentTypeRegistered());
        assertThrows(IllegalStateException.class, ImpulseControllableComponent::getComponentType);

        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(registry);
        ComponentType<EntityStore, ImpulseControllableComponent> type =
            ImpulseControllableComponent.getComponentType();

        assertTrue(ImpulseControllableComponent.isComponentTypeRegistered());
        assertSame(type, ImpulseControllableComponent.getComponentType());

        ControlTypeRegistry.clearComponentTypes();

        assertFalse(ImpulseControllableComponent.isComponentTypeRegistered());
    }
}
