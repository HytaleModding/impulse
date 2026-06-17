package dev.hytalemodding.impulse.core.plugin.settings;

import javax.annotation.Nonnull;

/**
 * @deprecated Use {@link PhysicsChunkTerrainSettings}.
 */
@Deprecated(forRemoval = false)
public class PhysicsWorldCollisionSettings extends PhysicsChunkTerrainSettings {

    public PhysicsWorldCollisionSettings() {
    }

    public PhysicsWorldCollisionSettings(@Nonnull PhysicsChunkTerrainSettings settings) {
        super(settings);
    }

    public PhysicsWorldCollisionSettings(@Nonnull PhysicsWorldCollisionSettings settings) {
        super(settings);
    }
}
