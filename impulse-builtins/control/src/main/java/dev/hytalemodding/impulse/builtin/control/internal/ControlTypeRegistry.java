package dev.hytalemodding.impulse.builtin.control.internal;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.IComponentRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.builtin.control.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.builtin.control.ImpulseControllableComponent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Internal registration owner for the ImpulseControl subplugin component types.
 */
public final class ControlTypeRegistry {

    @Nullable
    private static ComponentType<EntityStore, ImpulseControllableComponent> controllableComponentType;
    @Nullable
    private static ComponentType<EntityStore, PhysicsControlSessionComponent> sessionComponentType;

    private ControlTypeRegistry() {
    }

    public static void registerComponentTypes(@Nonnull IComponentRegistry<EntityStore> registry) {
        controllableComponentType = registry.registerComponent(
            ImpulseControllableComponent.class,
            "ImpulseControllable",
            ImpulseControllableComponent.CODEC);
        sessionComponentType = registry.registerComponent(
            PhysicsControlSessionComponent.class,
            PhysicsControlSessionComponent::new);
    }

    public static void clearComponentTypes() {
        controllableComponentType = null;
        sessionComponentType = null;
    }

    public static boolean isControllableComponentTypeRegistered() {
        return controllableComponentType != null;
    }

    public static boolean isSessionComponentTypeRegistered() {
        return sessionComponentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, ImpulseControllableComponent> controllableComponentType() {
        if (controllableComponentType == null) {
            throw new IllegalStateException("Impulse controllable component is not registered");
        }
        return controllableComponentType;
    }

    @Nonnull
    public static ComponentType<EntityStore, PhysicsControlSessionComponent> sessionComponentType() {
        if (sessionComponentType == null) {
            throw new IllegalStateException("Physics control session component is not registered");
        }
        return sessionComponentType;
    }
}
