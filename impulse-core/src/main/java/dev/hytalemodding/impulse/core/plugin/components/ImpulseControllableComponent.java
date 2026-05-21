package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;

public class ImpulseControllableComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<ImpulseControllableComponent> CODEC = BuilderCodec.builder(
            ImpulseControllableComponent.class,
            ImpulseControllableComponent::new)
        .build();

    public static ComponentType<EntityStore, ImpulseControllableComponent> getComponentType() {
        return ImpulsePlugin.get().getImpulseControllableComponentType();
    }

    @Nonnull
    @Override
    public ImpulseControllableComponent clone() {
        return new ImpulseControllableComponent();
    }
}
