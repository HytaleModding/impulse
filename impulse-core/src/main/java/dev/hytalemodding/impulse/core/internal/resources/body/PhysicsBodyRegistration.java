package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Store tick registration for a stable body UUID and backend-local body handle.
 */
public record PhysicsBodyRegistration(@Nonnull UUID bodyUuid,
    @Nonnull BackendBodyHandle backendBodyHandle,
    @Nonnull SpaceId spaceId,
    @Nonnull PhysicsBodyKind kind,
    @Nonnull PhysicsBodyPersistenceMode persistenceMode) {

    public PhysicsBodyRegistration {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(backendBodyHandle, "backendBodyHandle");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(persistenceMode, "persistenceMode");
    }
}
