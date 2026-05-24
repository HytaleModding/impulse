package dev.hytalemodding.impulse.core.plugin.settings;

import javax.annotation.Nonnull;

final class PhysicsSettingsValidation {

    private PhysicsSettingsValidation() {
    }

    static int requirePositiveAtMost(@Nonnull String label, int value, int maxValue) {
        if (value < 1 || value > maxValue) {
            throw new IllegalArgumentException(label + " must be between 1 and " + maxValue);
        }
        return value;
    }

    static float requireFiniteAtLeast(@Nonnull String label, float value, float minValue) {
        if (!Float.isFinite(value) || value < minValue) {
            throw new IllegalArgumentException(label + " must be finite and >= " + minValue);
        }
        return value;
    }
}
