package dev.hytalemodding.impulse.core.plugin.settings;

import javax.annotation.Nonnull;

/**
 * @deprecated Use
 * {@link dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings}.
 */
@Deprecated(forRemoval = false)
public class PhysicsWorldCollisionSettings extends PhysicsChunkTerrainSettings {

    public PhysicsWorldCollisionSettings() {
    }

    public PhysicsWorldCollisionSettings(
        @Nonnull dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings
            .PhysicsChunkTerrainSettings settings) {
        super(settings);
    }

    public PhysicsWorldCollisionSettings(@Nonnull PhysicsChunkTerrainSettings settings) {
        super(settings);
    }

    public PhysicsWorldCollisionSettings(@Nonnull PhysicsWorldCollisionSettings settings) {
        super(settings);
    }
}
