package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Owner-thread query for one rigid body's current live state copied by stable body key.
 */
public record RigidBodyStateQuery(@Nonnull RigidBodyKey bodyKey)
    implements PhysicsQuery<Optional<RigidBodyStateView>> {

    public RigidBodyStateQuery {
        Objects.requireNonNull(bodyKey, "bodyKey");
    }
}
