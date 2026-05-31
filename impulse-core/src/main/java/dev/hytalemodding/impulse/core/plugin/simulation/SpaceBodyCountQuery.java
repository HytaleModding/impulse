package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Owner-lane query for the current live rigid body count in one physics space.
 */
public record SpaceBodyCountQuery(@Nonnull SpaceId spaceId) implements PhysicsQuery<Integer> {

    public SpaceBodyCountQuery {
        Objects.requireNonNull(spaceId, "spaceId");
    }
}
