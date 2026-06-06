package dev.hytalemodding.impulse.core.plugin.events;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stable joint-break event copied from a backend event batch.
 */
public record PhysicsJointBreakEvent(@Nonnull SpaceId spaceId,
                                     @Nonnull JointKey jointKey,
                                     @Nullable RigidBodyKey bodyAKey,
                                     @Nullable RigidBodyKey bodyBKey) implements PhysicsFrameEvent {

    public PhysicsJointBreakEvent {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(jointKey, "jointKey");
    }

    @Nonnull
    @Override
    public PhysicsFrameEventKind kind() {
        return PhysicsFrameEventKind.JOINT_BREAK;
    }
}
