package dev.hytalemodding.impulse.core.plugin.modules.control;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ImpulseControllableComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<ImpulseControllableComponent> CODEC = BuilderCodec.builder(
            ImpulseControllableComponent.class,
            ImpulseControllableComponent::new)
        .build();

    @Nullable
    private static ComponentType<EntityStore, ImpulseControllableComponent> componentType;

    public static void setComponentType(
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> type) {
        componentType = Objects.requireNonNull(type, "type");
    }

    public static void clearComponentType() {
        componentType = null;
    }

    public static boolean isComponentTypeRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, ImpulseControllableComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("Impulse controllable component is not registered");
        }
        return componentType;
    }

    @Nonnull
    @Override
    public ImpulseControllableComponent clone() {
        return new ImpulseControllableComponent();
    }
}
