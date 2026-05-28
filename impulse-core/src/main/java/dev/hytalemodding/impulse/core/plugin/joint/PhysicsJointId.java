package dev.hytalemodding.impulse.core.plugin.joint;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Stable Impulse-side identity for a physics joint.
 *
 * <p>Backend {@code PhysicsJoint} handles are live owner-thread objects. This id is the handle
 * component state and plugin-facing lifecycle code should retain.</p>
 */
public record PhysicsJointId(@Nonnull UUID value) {

    public PhysicsJointId {
    }

    @Nonnull
    public static PhysicsJointId random() {
        return new PhysicsJointId(UUID.randomUUID());
    }

    @Nonnull
    public static PhysicsJointId of(@Nonnull UUID value) {
        return new PhysicsJointId(value);
    }

    @Nonnull
    @Override
    public String toString() {
        return value.toString();
    }
}
