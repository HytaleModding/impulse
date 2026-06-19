package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Store tick registration for a stable body UUID and backend-local body handle.
 */
public record PhysicsBodyRegistration(@Nonnull UUID bodyUuid,
    @Nonnull BackendBodyHandle backendBodyHandle,
    @Nonnull SpaceId spaceId) {

    public PhysicsBodyRegistration {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(backendBodyHandle, "backendBodyHandle");
        Objects.requireNonNull(spaceId, "spaceId");
    }
}
