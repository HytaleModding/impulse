package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

final class ExampleBlockEntityVisuals {

    private ExampleBlockEntityVisuals() {
    }

    @Nonnull
    static Holder<EntityStore> impulseOwnedBlockVisual(@Nonnull TimeResource time,
        @Nullable String blockType,
        @Nonnull Vector3d position) {
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(time,
            resolveBlockType(blockType),
            new Vector3d(position));
        stripHytaleRuntimeComponents(holder,
            DespawnComponent.getComponentType(),
            Velocity.getComponentType());
        return holder;
    }

    static void stripHytaleRuntimeComponents(@Nonnull Holder<EntityStore> holder,
        @Nonnull ComponentType<EntityStore, DespawnComponent> despawnType,
        @Nonnull ComponentType<EntityStore, Velocity> velocityType) {
        holder.tryRemoveComponent(despawnType);
        holder.tryRemoveComponent(velocityType);
    }

    @Nonnull
    static String resolveBlockType(@Nullable String blockType) {
        return blockType == null || blockType.isBlank()
            ? PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE
            : blockType.trim();
    }
}
