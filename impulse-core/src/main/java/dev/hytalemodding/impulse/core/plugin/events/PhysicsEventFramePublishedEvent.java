package dev.hytalemodding.impulse.core.plugin.events;

import com.hypixel.hytale.component.system.EcsEvent;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Hytale world event published once for each Impulse physics event frame.
 */
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
