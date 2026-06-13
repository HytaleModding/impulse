package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Copied request that authors one PhysicsStore space row and its compatibility token.
 */
public record SpaceUpsertRequest(@Nonnull UUID requestUuid,
                                 @Nonnull UUID spaceUuid,
                                 @Nonnull SpaceId compatibilitySpaceId,
                                 @Nonnull SpaceComponent space,
                                 @Nonnull WorldCollisionComponent worldCollision)
    implements PhysicsStoreRequest {

    public SpaceUpsertRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(compatibilitySpaceId, "compatibilitySpaceId");
        space = Objects.requireNonNull(space, "space").clone();
        worldCollision = Objects.requireNonNull(worldCollision, "worldCollision").clone();
    }

    @Nonnull
    public static SpaceUpsertRequest of(@Nonnull UUID spaceUuid,
        @Nonnull SpaceId compatibilitySpaceId,
        @Nonnull BackendId backendId,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsWorldCollisionSettings worldCollisionSettings =
            Objects.requireNonNull(settings, "settings").getWorldCollisionSettings();
        WorldCollisionComponent worldCollision = new WorldCollisionComponent(
            worldCollisionSettings.getWorldCollisionMode(),
            worldCollisionSettings.isNativeVoxelTerrainEnabled(),
            worldCollisionSettings.getWorldCollisionRadius(),
            worldCollisionSettings.getWorldCollisionBodyRadius(),
            worldCollisionSettings.getWorldCollisionTtlTicks(),
            worldCollisionSettings.getTerrainFriction(),
            worldCollisionSettings.getTerrainRestitution());
        return new SpaceUpsertRequest(UUID.randomUUID(),
            spaceUuid,
            compatibilitySpaceId,
            new SpaceComponent(backendId, new Vector3f(0.0f, -9.81f, 0.0f)),
            worldCollision);
    }
}
