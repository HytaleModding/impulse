package dev.hytalemodding.impulse.core.internal.resources.joint;

import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.joint.PhysicsJointId;
import javax.annotation.Nonnull;

/**
 * Owner-thread registration for a live backend joint.
 */
public record PhysicsJointRegistration(@Nonnull PhysicsJointId id,
    @Nonnull PhysicsJoint joint,
    @Nonnull SpaceId spaceId) {
}
