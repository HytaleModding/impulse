package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied request that updates represented PhysicsStore settings for one space.
 */
public record SpaceSettingsRequest(@Nonnull UUID requestUuid,
                                   @Nonnull UUID spaceUuid,
                                   @Nonnull WorldCollisionComponent worldCollision,
                                   @Nonnull SolverSettingsComponent solverSettings,
                                   @Nonnull VisualSyncSettingsComponent visualSyncSettings,
                                   @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
                                   @Nonnull CollisionLodSettingsComponent collisionLodSettings,
                                   @Nonnull ExtensionSettingsComponent extensionSettings)
    implements PhysicsStoreRequest {

    public SpaceSettingsRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        worldCollision = Objects.requireNonNull(worldCollision, "worldCollision").clone();
        solverSettings = Objects.requireNonNull(solverSettings, "solverSettings").clone();
        visualSyncSettings = Objects.requireNonNull(visualSyncSettings,
            "visualSyncSettings").clone();
        visualMaterializationSettings = Objects.requireNonNull(visualMaterializationSettings,
            "visualMaterializationSettings").clone();
        collisionLodSettings = Objects.requireNonNull(collisionLodSettings,
            "collisionLodSettings").clone();
        extensionSettings = Objects.requireNonNull(extensionSettings, "extensionSettings").clone();
    }

    @Nonnull
    public static SpaceSettingsRequest of(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsSpaceSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new SpaceSettingsRequest(UUID.randomUUID(),
            spaceUuid,
            new WorldCollisionComponent(settings.getWorldCollisionSettings()),
            new SolverSettingsComponent(settings.getSolverSettings()),
            new VisualSyncSettingsComponent(settings.getVisualSyncSettings()),
            new VisualMaterializationSettingsComponent(settings.getVisualMaterializationSettings()),
            new CollisionLodSettingsComponent(settings.getCollisionLodSettings()),
            new ExtensionSettingsComponent(settings.getExtensionSettings()));
    }
}
