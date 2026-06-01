package dev.hytalemodding.impulse.bullet;

import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

record BulletNativeContactEvent(@Nonnull PhysicsContactPhase phase,
                                long bodyAId,
                                long bodyBId,
                                @Nonnull Vector3f pointOnA,
                                @Nonnull Vector3f pointOnB,
                                @Nonnull Vector3f normalOnB,
                                float distance,
                                float impulse) {

    BulletNativeContactEvent {
        phase = Objects.requireNonNull(phase, "phase");
        pointOnA = new Vector3f(Objects.requireNonNull(pointOnA, "pointOnA"));
        pointOnB = new Vector3f(Objects.requireNonNull(pointOnB, "pointOnB"));
        normalOnB = new Vector3f(Objects.requireNonNull(normalOnB, "normalOnB"));
    }

    @Nonnull
    BulletNativeContactEvent withPhase(@Nonnull PhysicsContactPhase phase) {
        return new BulletNativeContactEvent(phase,
            bodyAId,
            bodyBId,
            pointOnA,
            pointOnB,
            normalOnB,
            distance,
            impulse);
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
