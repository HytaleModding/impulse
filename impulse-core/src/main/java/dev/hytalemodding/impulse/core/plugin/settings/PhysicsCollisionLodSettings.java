package dev.hytalemodding.impulse.core.plugin.settings;

import javax.annotation.Nonnull;

/**
 * @deprecated Use
 * {@link dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsCollisionLodSettings}.
 */
@Deprecated(forRemoval = false)
public class PhysicsCollisionLodSettings
    extends dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings
        .PhysicsCollisionLodSettings {

    public PhysicsCollisionLodSettings() {
    }

    public PhysicsCollisionLodSettings(
        @Nonnull dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings
            .PhysicsCollisionLodSettings settings) {
        super(settings);
    }

    public PhysicsCollisionLodSettings(@Nonnull PhysicsCollisionLodSettings settings) {
        super(settings);
    }
}
