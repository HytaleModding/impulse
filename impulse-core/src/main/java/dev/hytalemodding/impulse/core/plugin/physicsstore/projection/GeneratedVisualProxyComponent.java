package dev.hytalemodding.impulse.core.plugin.physicsstore.projection;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;

/**
 * Durable ownership marker for Impulse-generated visual proxy entities.
 */
public final class GeneratedVisualProxyComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<GeneratedVisualProxyComponent> CODEC = BuilderCodec.builder(
            GeneratedVisualProxyComponent.class,
            GeneratedVisualProxyComponent::new)
        .build();

    public static ComponentType<EntityStore, GeneratedVisualProxyComponent> getComponentType() {
        return ImpulsePlugin.get().getGeneratedVisualProxyComponentType();
    }

    @Nonnull
    @Override
    public GeneratedVisualProxyComponent clone() {
        return new GeneratedVisualProxyComponent();
    }
}
