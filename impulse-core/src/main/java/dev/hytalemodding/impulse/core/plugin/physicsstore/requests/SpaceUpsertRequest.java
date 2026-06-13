package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
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
                                 @Nonnull WorldCollisionComponent worldCollision,
                                 @Nonnull SolverSettingsComponent solverSettings,
                                 @Nonnull VisualSyncSettingsComponent visualSyncSettings,
                                 @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
                                 @Nonnull CollisionLodSettingsComponent collisionLodSettings,
                                 @Nonnull ExtensionSettingsComponent extensionSettings)
    implements PhysicsStoreRequest {

    public SpaceUpsertRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(compatibilitySpaceId, "compatibilitySpaceId");
        space = Objects.requireNonNull(space, "space").clone();
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
    public static SpaceUpsertRequest of(@Nonnull UUID spaceUuid,
        @Nonnull SpaceId compatibilitySpaceId,
        @Nonnull BackendId backendId,
        @Nonnull PhysicsSpaceSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new SpaceUpsertRequest(UUID.randomUUID(),
            spaceUuid,
            compatibilitySpaceId,
            new SpaceComponent(backendId, new Vector3f(0.0f, -9.81f, 0.0f)),
            new WorldCollisionComponent(settings.getWorldCollisionSettings()),
            new SolverSettingsComponent(settings.getSolverSettings()),
            new VisualSyncSettingsComponent(settings.getVisualSyncSettings()),
            new VisualMaterializationSettingsComponent(settings.getVisualMaterializationSettings()),
            new CollisionLodSettingsComponent(settings.getCollisionLodSettings()),
            new ExtensionSettingsComponent(settings.getExtensionSettings()));
    }
}
