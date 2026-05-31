package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Internal owner-lane query for stress benchmark health counters.
 */
public record BenchmarkSpaceStatsQuery(@Nonnull SpaceId spaceId,
                                       float groundY,
                                       float belowPlaneTolerance,
                                       float bodyWorldMinY,
                                       float bodyVoidY,
                                       boolean includeTerrainProbe)
    implements PhysicsInternalQuery<BenchmarkSpaceStatsView> {

    public BenchmarkSpaceStatsQuery {
        Objects.requireNonNull(spaceId, "spaceId");
    }
}
