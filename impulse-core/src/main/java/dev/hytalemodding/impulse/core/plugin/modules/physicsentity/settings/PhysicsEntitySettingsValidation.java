package dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings;

import javax.annotation.Nonnull;

final class PhysicsEntitySettingsValidation {

    private PhysicsEntitySettingsValidation() {
    }

    static int requirePositiveAtMost(@Nonnull String label, int value, int maxValue) {
        if (value < 1 || value > maxValue) {
            throw new IllegalArgumentException(label + " must be between 1 and " + maxValue);
        }
        return value;
    }
}
