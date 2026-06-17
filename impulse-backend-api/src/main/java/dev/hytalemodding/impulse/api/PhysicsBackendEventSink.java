package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Sink for copied backend events emitted into a bounded post-step batch.
 */
@Deprecated(forRemoval = true)
public interface PhysicsBackendEventSink {

    int capacity();

    int size();

    int droppedEventCount();

    boolean offer(@Nonnull PhysicsBackendEvent event);

    default boolean contact(@Nonnull PhysicsContactPhase phase,
        @Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f pointOnA,
        @Nonnull Vector3f pointOnB,
        @Nonnull Vector3f normalOnB,
        float distance,
        float impulse) {
        return offer(new PhysicsBackendContactEvent(phase,
            bodyA,
            bodyB,
            pointOnA,
            pointOnB,
            normalOnB,
            distance,
            impulse));
    }

    default boolean bodyActivation(@Nonnull PhysicsBodyActivationPhase phase,
        @Nonnull PhysicsBody body) {
        return offer(new PhysicsBackendBodyActivationEvent(phase, body));
    }

    default boolean jointBreak(@Nonnull PhysicsJoint joint) {
        return offer(new PhysicsBackendJointBreakEvent(joint));
    }
}
