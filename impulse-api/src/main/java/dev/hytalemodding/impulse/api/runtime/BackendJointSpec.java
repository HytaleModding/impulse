package dev.hytalemodding.impulse.api.runtime;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied backend joint creation request.
 */
public record BackendJointSpec(@Nonnull BackendJointType type,
                               long bodyAId,
                               long bodyBId,
                               float anchorAX,
                               float anchorAY,
                               float anchorAZ,
                               float anchorBX,
                               float anchorBY,
                               float anchorBZ,
                               float axisX,
                               float axisY,
                               float axisZ,
                               float restLength,
                               float stiffness,
                               float damping,
                               float lowerLimit,
                               float upperLimit,
                               boolean motorEnabled,
                               float motorTargetVelocity,
                               float motorMaxForce) {

    public BackendJointSpec {
        Objects.requireNonNull(type, "type");
    }

    @Nonnull
    public static BackendJointSpec point(long bodyAId,
        long bodyBId,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ) {
        return new BackendJointSpec(BackendJointType.POINT,
            bodyAId,
            bodyBId,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            false,
            0.0f,
            0.0f);
    }
}
