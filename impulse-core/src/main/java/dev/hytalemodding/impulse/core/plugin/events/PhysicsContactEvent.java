package dev.hytalemodding.impulse.core.plugin.events;

import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Stable contact event copied from a backend event batch.
 */
public record PhysicsContactEvent(@Nonnull SpaceId spaceId,
                                  @Nonnull PhysicsContactPhase phase,
                                  @Nonnull RigidBodyKey bodyAKey,
                                  @Nonnull RigidBodyKey bodyBKey,
                                  @Nonnull Vector3f pointOnA,
                                  @Nonnull Vector3f pointOnB,
                                  @Nonnull Vector3f normalOnB,
                                  float distance,
                                  float impulse) implements PhysicsFrameEvent {

    public PhysicsContactEvent {
        spaceId = Objects.requireNonNull(spaceId, "spaceId");
        phase = Objects.requireNonNull(phase, "phase");
        bodyAKey = Objects.requireNonNull(bodyAKey, "bodyAKey");
        bodyBKey = Objects.requireNonNull(bodyBKey, "bodyBKey");
        pointOnA = new Vector3f(Objects.requireNonNull(pointOnA, "pointOnA"));
        pointOnB = new Vector3f(Objects.requireNonNull(pointOnB, "pointOnB"));
        normalOnB = new Vector3f(Objects.requireNonNull(normalOnB, "normalOnB"));
    }

    @Nonnull
    @Override
    public PhysicsFrameEventKind kind() {
        return PhysicsFrameEventKind.CONTACT;
    }

    @Nonnull
    @Override
    public Vector3f pointOnA() {
        return new Vector3f(pointOnA);
    }

    @Nonnull
    @Override
    public Vector3f pointOnB() {
        return new Vector3f(pointOnB);
    }

    @Nonnull
    @Override
    public Vector3f normalOnB() {
        return new Vector3f(normalOnB);
    }
}
