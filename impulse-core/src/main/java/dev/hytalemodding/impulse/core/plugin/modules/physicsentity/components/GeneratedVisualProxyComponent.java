package dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import javax.annotation.Nonnull;

/**
 * Durable ownership marker for Impulse-generated visual proxy entities.
 *
 * @deprecated This follows the old physics entity ownership model, the TransformComponent of the
 * entities spawned with BodyAttachmentComponent is by definition owned by the bound PhysicsStore
 * entity anyway
 */
@Deprecated (forRemoval = true)
public final class GeneratedVisualProxyComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<GeneratedVisualProxyComponent> CODEC = BuilderCodec.builder(
            GeneratedVisualProxyComponent.class,
            GeneratedVisualProxyComponent::new)
        .build();

    public static boolean isComponentTypeRegistered() {
        return PhysicsEntityTypes.isGeneratedVisualProxyComponentTypeRegistered();
    }

    @Nonnull
    public static ComponentType<EntityStore, GeneratedVisualProxyComponent> getComponentType() {
        return PhysicsEntityTypes.generatedVisualProxyComponentType();
    }

    @Nonnull
    @Override
    public GeneratedVisualProxyComponent clone() {
        return new GeneratedVisualProxyComponent();
    }
}
