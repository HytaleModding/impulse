package dev.hytalemodding.impulse.core.internal.resources;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Runtime binding between a stable core space id and backend-local execution handles.
 */
public record PhysicsSpaceBinding(@Nonnull BackendId backendId,
                                  @Nonnull SpaceId spaceId,
                                  @Nonnull BackendSpaceHandle backendSpaceHandle,
                                  @Nonnull PhysicsBackendRuntime runtime) {

    public PhysicsSpaceBinding {
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(backendSpaceHandle, "backendSpaceHandle");
        Objects.requireNonNull(runtime, "runtime");
    }
}
