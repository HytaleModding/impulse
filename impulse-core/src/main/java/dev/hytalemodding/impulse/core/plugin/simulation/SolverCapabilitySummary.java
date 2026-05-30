package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied backend capability flags for one physics space.
 */
public record SolverCapabilitySummary(@Nonnull SpaceId spaceId,
                                      @Nonnull String backendId,
                                      boolean solverTuningSupported,
                                      boolean activationTuningSupported) {

    public SolverCapabilitySummary {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(backendId, "backendId");
    }
}
