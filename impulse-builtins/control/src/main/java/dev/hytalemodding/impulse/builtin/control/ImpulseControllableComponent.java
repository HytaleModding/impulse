package dev.hytalemodding.impulse.builtin.control;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.builtin.control.internal.ControlTypeRegistry;
import javax.annotation.Nonnull;

public class ImpulseControllableComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<ImpulseControllableComponent> CODEC = BuilderCodec.builder(
            ImpulseControllableComponent.class,
            ImpulseControllableComponent::new)
        .build();

    public static boolean isComponentTypeRegistered() {
        return ControlTypeRegistry.isControllableComponentTypeRegistered();
    }

    @Nonnull
    public static ComponentType<EntityStore, ImpulseControllableComponent> getComponentType() {
        return ControlTypeRegistry.controllableComponentType();
    }

    @Nonnull
    @Override
    public ImpulseControllableComponent clone() {
        return new ImpulseControllableComponent();
    }
}
