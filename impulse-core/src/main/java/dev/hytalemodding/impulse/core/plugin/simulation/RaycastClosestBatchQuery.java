package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Owner-lane query for closest hits across many ray segments in one physics space.
 *
 * <p>The ray list is copied on construction. Result indexes match the input ray order.</p>
 */
public record RaycastClosestBatchQuery(@Nonnull SpaceId spaceId,
                                       @Nonnull List<RaycastSegment> rays)
    implements PhysicsQuery<RaycastClosestBatchResult> {

    public RaycastClosestBatchQuery {
        Objects.requireNonNull(spaceId, "spaceId");
        rays = List.copyOf(Objects.requireNonNull(rays, "rays"));
    }
}
