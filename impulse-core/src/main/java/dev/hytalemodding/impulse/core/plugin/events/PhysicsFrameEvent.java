package dev.hytalemodding.impulse.core.plugin.events;

import javax.annotation.Nonnull;

/**
 * Stable Impulse event copied into a physics event frame.
 *
 * @deprecated Physics event plugin API is deprecated without replacement.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public interface PhysicsFrameEvent {

    @Nonnull
    PhysicsFrameEventKind kind();
}
