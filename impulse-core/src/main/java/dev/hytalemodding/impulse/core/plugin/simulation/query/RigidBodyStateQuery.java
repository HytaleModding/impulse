package dev.hytalemodding.impulse.core.plugin.simulation.query;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RigidBodyStateView;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Owner-lane query for one rigid body's current live state copied by stable body key.
 */
public record RigidBodyStateQuery(@Nonnull RigidBodyKey bodyKey)
    implements PhysicsQuery<Optional<RigidBodyStateView>> {

    public RigidBodyStateQuery {
        Objects.requireNonNull(bodyKey, "bodyKey");
    }
}
