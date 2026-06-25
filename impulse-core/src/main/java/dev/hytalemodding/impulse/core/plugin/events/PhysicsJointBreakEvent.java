package dev.hytalemodding.impulse.core.plugin.events;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Stable joint-break event copied from a backend event batch.
 *
 * @deprecated Physics event plugin API is deprecated without replacement.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
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
