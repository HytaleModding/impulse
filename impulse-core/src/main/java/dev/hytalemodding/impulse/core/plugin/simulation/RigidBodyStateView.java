package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied rigid body state returned by owner-lane queries.
 *
 * <p>This value is a live-state query result, not a published snapshot frame entry. Use snapshot
 * APIs when reader-side systems need frame-coherent body data.</p>
 */
public record RigidBodyStateView(@Nonnull RigidBodyKey bodyKey,
                                 @Nonnull PhysicsBodyType bodyType,
                                 @Nonnull RigidBodyPose pose) {

    public RigidBodyStateView {
        Objects.requireNonNull(bodyKey, "bodyKey");
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(pose, "pose");
    }
}
