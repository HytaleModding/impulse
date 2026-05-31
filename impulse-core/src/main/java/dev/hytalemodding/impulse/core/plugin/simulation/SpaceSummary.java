package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied owner-lane space diagnostics.
 */
public record SpaceSummary(@Nonnull SpaceId spaceId,
                           @Nonnull BackendId backendId,
                           int bodyCount,
                           int jointCount) {

    public SpaceSummary {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(backendId, "backendId");
        bodyCount = Math.max(0, bodyCount);
        jointCount = Math.max(0, jointCount);
    }
}
