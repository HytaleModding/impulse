package dev.hytalemodding.impulse.core.plugin.settings;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Persistable value for a backend capability extension setting.
 */
public record PhysicsExtensionSettingValue(@Nonnull Kind kind, @Nonnull String value) {

    public PhysicsExtensionSettingValue {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Extension setting value cannot be blank");
        }
    }

    @Nonnull
    public static PhysicsExtensionSettingValue ofString(@Nonnull String value) {
        return new PhysicsExtensionSettingValue(Kind.STRING, value);
    }

    @Nonnull
    public static PhysicsExtensionSettingValue ofInt(int value) {
        return new PhysicsExtensionSettingValue(Kind.INTEGER, Integer.toString(value));
    }

    @Nonnull
    public static PhysicsExtensionSettingValue ofFloat(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Extension setting float value must be finite");
        }
        return new PhysicsExtensionSettingValue(Kind.FLOAT, Float.toString(value));
    }

    @Nonnull
    public static PhysicsExtensionSettingValue ofBoolean(boolean value) {
        return new PhysicsExtensionSettingValue(Kind.BOOLEAN, Boolean.toString(value));
    }

    public enum Kind {
        STRING,
        INTEGER,
        FLOAT,
        BOOLEAN
    }
}
