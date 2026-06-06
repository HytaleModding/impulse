package dev.hytalemodding.impulse.core.internal.math;

import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Transform helpers for physics bodies with Hytale visual attachments.
 */
public final class PhysicsVisualPoseMath {

    private PhysicsVisualPoseMath() {
    }

    @Nonnull
    public static Vector3f visualPositionFromBodyPose(@Nonnull Vector3f bodyPosition,
        @Nonnull Quaternionf bodyRotation,
        float centerOfMassOffsetY,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Vector3f destination) {
        return visualPositionFromBodyPose(bodyPosition,
            bodyRotation,
            centerOfMassOffsetY,
            localPositionOffset,
            destination,
            new Vector3f());
    }

    @Nonnull
    public static Vector3f visualPositionFromBodyPose(@Nonnull Vector3f bodyPosition,
        @Nonnull Quaternionf bodyRotation,
        float centerOfMassOffsetY,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Vector3f destination,
        @Nonnull Vector3f scratchOffset) {
        scratchOffset.set(localPositionOffset);
        bodyRotation.transform(scratchOffset);
        destination.set(bodyPosition).add(scratchOffset);
        return destination.sub(0.0f, centerOfMassOffsetY, 0.0f);
    }

    @Nonnull
    public static Vector3d bodyCenterFromVisualPose(@Nonnull Vector3d visualPosition,
        @Nonnull Quaterniond bodyRotation,
        float centerOfMassOffsetY,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Vector3d destination) {
        Vector3d visualOffset = new Vector3d(localPositionOffset.x,
            localPositionOffset.y,
            localPositionOffset.z);
        bodyRotation.transform(visualOffset);
        return destination.set(visualPosition)
            .add(0.0, centerOfMassOffsetY, 0.0)
            .sub(visualOffset);
    }
}
