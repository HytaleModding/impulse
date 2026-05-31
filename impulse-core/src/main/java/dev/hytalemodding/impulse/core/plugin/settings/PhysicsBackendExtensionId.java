package dev.hytalemodding.impulse.core.plugin.settings;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Stable plugin-facing identifier for backend extension settings.
 *
 * @param value namespaced extension identifier, for example {@code example:solver}
 */
public record PhysicsBackendExtensionId(@Nonnull String value) {

    public PhysicsBackendExtensionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("PhysicsBackendExtensionId value cannot be blank");
        }
    }

    @Nonnull
    @Override
    public String toString() {
        return value;
    }
}
