package dev.hytalemodding.impulse.core.plugin.events;

import javax.annotation.Nonnull;

/**
 * Stable Impulse event copied into a physics event frame.
 */
public interface PhysicsFrameEvent {

    @Nonnull
    PhysicsFrameEventKind kind();
}
