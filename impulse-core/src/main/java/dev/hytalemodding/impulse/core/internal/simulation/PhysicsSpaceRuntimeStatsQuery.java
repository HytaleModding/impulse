package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
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
