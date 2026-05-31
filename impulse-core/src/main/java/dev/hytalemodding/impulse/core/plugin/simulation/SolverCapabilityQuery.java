package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
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
