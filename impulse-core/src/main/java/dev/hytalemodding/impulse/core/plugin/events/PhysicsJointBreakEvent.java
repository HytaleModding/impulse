package dev.hytalemodding.impulse.core.plugin.events;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stable joint-break event copied from a backend event batch.
 */
public record PhysicsJointBreakEvent(@Nonnull SpaceId spaceId,
                                     @Nonnull UUID jointUuid,
                                     @Nullable UUID bodyAUuid,
                                     @Nullable UUID bodyBUuid) implements PhysicsFrameEvent {

    public PhysicsJointBreakEvent {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(jointUuid, "jointUuid");
    }

    public PhysicsJointBreakEvent(@Nonnull SpaceId spaceId,
        @Nonnull JointKey jointKey,
        @Nullable RigidBodyKey bodyAKey,
        @Nullable RigidBodyKey bodyBKey) {
        this(spaceId,
            Objects.requireNonNull(jointKey, "jointKey").value(),
            bodyAKey != null ? bodyAKey.value() : null,
            bodyBKey != null ? bodyBKey.value() : null);
    }

    @Nonnull
    public JointKey jointKey() {
        return JointKey.of(jointUuid);
    }

    @Nullable
    public RigidBodyKey bodyAKey() {
        return bodyAUuid != null ? RigidBodyKey.of(bodyAUuid) : null;
    }

    @Nullable
    public RigidBodyKey bodyBKey() {
        return bodyBUuid != null ? RigidBodyKey.of(bodyBUuid) : null;
    }

    @Nonnull
    @Override
    public PhysicsFrameEventKind kind() {
        return PhysicsFrameEventKind.JOINT_BREAK;
    }
}
