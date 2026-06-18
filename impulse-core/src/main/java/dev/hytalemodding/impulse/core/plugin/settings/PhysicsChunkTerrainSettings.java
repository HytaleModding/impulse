package dev.hytalemodding.impulse.core.plugin.settings;

import javax.annotation.Nonnull;

/**
 * @deprecated Use
 * {@link dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings}.
 */
@Deprecated(forRemoval = false)
public class PhysicsChunkTerrainSettings
    extends dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings
        .PhysicsChunkTerrainSettings {

    public PhysicsChunkTerrainSettings() {
    }

    public PhysicsChunkTerrainSettings(
        @Nonnull dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings
            .PhysicsChunkTerrainSettings settings) {
        super(settings);
    }

    public PhysicsChunkTerrainSettings(@Nonnull PhysicsChunkTerrainSettings settings) {
        super(settings);
    }
}
