package dev.hytalemodding.impulse.api;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied joint-break event emitted by a backend after a completed step.
 */
@Deprecated(forRemoval = true)
public record PhysicsBackendJointBreakEvent(@Nonnull PhysicsJoint joint) implements PhysicsBackendEvent {

    public PhysicsBackendJointBreakEvent {
        joint = Objects.requireNonNull(joint, "joint");
    }

    @Nonnull
    @Override
    public PhysicsBackendEventKind kind() {
        return PhysicsBackendEventKind.JOINT_BROKEN;
    }
}
