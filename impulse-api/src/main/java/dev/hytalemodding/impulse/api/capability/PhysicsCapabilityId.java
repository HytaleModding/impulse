package dev.hytalemodding.impulse.api.capability;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Stable identifier for a backend capability.
 *
 * @param value namespaced capability identifier, for example {@code impulse:solver_tuning}
 */
public record PhysicsCapabilityId(@Nonnull String value) {

    public PhysicsCapabilityId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("PhysicsCapabilityId value cannot be blank");
        }
    }

    @Override
    @Nonnull
    public String toString() {
        return value;
    }
}
