package dev.hytalemodding.impulse.core.plugin.events;

import com.hypixel.hytale.component.system.EcsEvent;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Hytale world event published once for each Impulse physics event frame.
 *
 * @deprecated Physics event plugin API is deprecated without replacement.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class PhysicsEventFramePublishedEvent extends EcsEvent {

    @Nonnull
    private final PhysicsEventFrame frame;

    public PhysicsEventFramePublishedEvent(@Nonnull PhysicsEventFrame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
    }

    @Nonnull
    public PhysicsEventFrame frame() {
        return frame;
    }

    @Nonnull
    public PhysicsEventFrame getFrame() {
        return frame;
    }
}
