package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;

/**
 * Physics backend identifier used for provider selection and persistence.
 *
 * @param value the identifier, follows [backend_provider]:[backend_name], e.g. impulse:rapier
 */
public record BackendId(@Nonnull String value) {

    public BackendId {
        if (value.isBlank()) {
            throw new IllegalArgumentException("BackendId value cannot be blank");
        }
    }

    @Override
    @Nonnull
    public String toString() {
        return value;
    }
}
