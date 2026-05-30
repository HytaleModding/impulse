package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Internal owner-thread query for nearby joints used by debug rendering.
 */
public record PhysicsDebugJointsQuery(@Nonnull SpaceId spaceId,
                                      double viewerX,
                                      double viewerY,
                                      double viewerZ,
                                      double viewRadius,
                                      int maxJoints)
    implements PhysicsInternalQuery<List<PhysicsDebugJointView>> {

    public PhysicsDebugJointsQuery {
        Objects.requireNonNull(spaceId, "spaceId");
    }
}
