package dev.hytalemodding.impulse.core.internal.simulation.query;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.simulation.view.PhysicsSpaceRuntimeStatsView;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Internal owner-lane query for live backend and registry counters in one physics space.
 */
public record PhysicsSpaceRuntimeStatsQuery(@Nonnull SpaceId spaceId)
    implements PhysicsInternalQuery<PhysicsSpaceRuntimeStatsView> {

    public PhysicsSpaceRuntimeStatsQuery {
        Objects.requireNonNull(spaceId, "spaceId");
    }
}
