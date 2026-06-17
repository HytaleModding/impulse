package dev.hytalemodding.impulse.api.capability;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Public metadata for an optional backend capability.
 *
 * @param id stable capability identifier
 * @param displayName concise user-facing name
 * @param description concise capability description
 */
public record PhysicsCapabilityDescriptor(@Nonnull PhysicsCapabilityId id,
                                          @Nonnull String displayName,
                                          @Nonnull String description) {

    public PhysicsCapabilityDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description cannot be blank");
        }
    }
}
