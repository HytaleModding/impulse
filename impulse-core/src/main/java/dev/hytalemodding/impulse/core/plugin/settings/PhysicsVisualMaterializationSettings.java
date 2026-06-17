package dev.hytalemodding.impulse.core.plugin.settings;

import javax.annotation.Nonnull;

/**
 * @deprecated Use
 * {@link dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings}.
 */
@Deprecated(forRemoval = false)
public class PhysicsVisualMaterializationSettings
    extends dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings
        .PhysicsVisualMaterializationSettings {

    public PhysicsVisualMaterializationSettings() {
    }

    public PhysicsVisualMaterializationSettings(
        @Nonnull dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings
            .PhysicsVisualMaterializationSettings settings) {
        super(settings);
    }

    public PhysicsVisualMaterializationSettings(
        @Nonnull PhysicsVisualMaterializationSettings settings) {
        super(settings);
    }
}
