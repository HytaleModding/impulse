package dev.hytalemodding.impulse.core.plugin.events;

import dev.hytalemodding.impulse.api.SpaceId;
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

    @Nonnull
    @Override
    public PhysicsFrameEventKind kind() {
        return PhysicsFrameEventKind.JOINT_BREAK;
    }
}
