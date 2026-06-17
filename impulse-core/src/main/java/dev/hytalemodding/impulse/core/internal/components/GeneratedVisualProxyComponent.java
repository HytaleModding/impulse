package dev.hytalemodding.impulse.core.internal.components;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Durable ownership marker for Impulse-generated visual proxy entities.
 */
public final class GeneratedVisualProxyComponent implements Component<EntityStore> {

    @Nullable
    private static ComponentType<EntityStore, GeneratedVisualProxyComponent> componentType;
    @Nonnull
    public static final BuilderCodec<GeneratedVisualProxyComponent> CODEC = BuilderCodec.builder(
            GeneratedVisualProxyComponent.class,
            GeneratedVisualProxyComponent::new)
        .build();

    public static ComponentType<EntityStore, GeneratedVisualProxyComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(
        @Nonnull ComponentType<EntityStore, GeneratedVisualProxyComponent> type) {
        componentType = type;
    }

    @Nonnull
    @Override
    public GeneratedVisualProxyComponent clone() {
        return new GeneratedVisualProxyComponent();
    }
}
