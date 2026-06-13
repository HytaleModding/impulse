package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied request that updates represented PhysicsStore settings for one space.
 */
public record SpaceSettingsRequest(@Nonnull UUID requestUuid,
                                   @Nonnull UUID spaceUuid,
                                   @Nonnull WorldCollisionComponent worldCollision)
    implements PhysicsStoreRequest {

    public SpaceSettingsRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        worldCollision = Objects.requireNonNull(worldCollision, "worldCollision").clone();
    }

    @Nonnull
    public static SpaceSettingsRequest of(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsWorldCollisionSettings worldCollisionSettings =
            Objects.requireNonNull(settings, "settings").getWorldCollisionSettings();
        return new SpaceSettingsRequest(UUID.randomUUID(),
            spaceUuid,
            new WorldCollisionComponent(worldCollisionSettings.getWorldCollisionMode(),
                worldCollisionSettings.isNativeVoxelTerrainEnabled(),
                worldCollisionSettings.getWorldCollisionRadius(),
                worldCollisionSettings.getWorldCollisionBodyRadius(),
                worldCollisionSettings.getWorldCollisionTtlTicks(),
                worldCollisionSettings.getTerrainFriction(),
                worldCollisionSettings.getTerrainRestitution()));
    }
}
