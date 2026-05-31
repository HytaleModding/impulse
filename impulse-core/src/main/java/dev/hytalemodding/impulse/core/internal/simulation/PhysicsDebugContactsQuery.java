package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Internal owner-lane query for nearby contact points used by debug rendering.
 */
public record PhysicsDebugContactsQuery(@Nonnull SpaceId spaceId,
                                        double viewerX,
                                        double viewerY,
                                        double viewerZ,
                                        double viewRadius,
                                        int maxContacts)
    implements PhysicsInternalQuery<List<PhysicsDebugContactView>> {

    public PhysicsDebugContactsQuery {
        Objects.requireNonNull(spaceId, "spaceId");
    }
}
