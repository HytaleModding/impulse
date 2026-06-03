package dev.hytalemodding.impulse.core.plugin.simulation.query;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.simulation.SolverCapabilitySummary;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Owner-lane query for backend solver-tuning support in one physics space.
 */
public record SolverCapabilityQuery(@Nonnull SpaceId spaceId)
    implements PhysicsQuery<SolverCapabilitySummary> {

    public SolverCapabilityQuery {
        Objects.requireNonNull(spaceId, "spaceId");
    }
}
