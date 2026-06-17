package dev.hytalemodding.impulse.core.plugin.settings;

import javax.annotation.Nonnull;

/**
 * @deprecated Use
 * {@link dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings}.
 */
@Deprecated(forRemoval = false)
public class PhysicsVisualSyncSettings
    extends dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings
        .PhysicsVisualSyncSettings {

    public PhysicsVisualSyncSettings() {
    }

    public PhysicsVisualSyncSettings(
        @Nonnull dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings
            .PhysicsVisualSyncSettings settings) {
        super(settings);
    }

    public PhysicsVisualSyncSettings(@Nonnull PhysicsVisualSyncSettings settings) {
        super(settings);
    }
}
