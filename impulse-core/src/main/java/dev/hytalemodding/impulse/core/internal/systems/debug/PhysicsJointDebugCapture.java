package dev.hytalemodding.impulse.core.internal.systems.debug;

import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.internal.systems.debug.PhysicsDebugRenderer.JointDebugPrimitive;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

final class PhysicsJointDebugCapture {

    private PhysicsJointDebugCapture() {
    }

    @Nonnull
    static List<JointDebugPrimitive> collectVisibleJointPrimitives(@Nonnull PhysicsSpace space,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxJoints) {
        int limit = Math.max(0, maxJoints);
        if (limit == 0) {
            return List.of();
        }

        List<JointDebugPrimitive> visible = new ArrayList<>();
        double maxDistanceSquared = viewRadius * viewRadius;
        space.forEachJoint(joint -> {
            if (visible.size() >= limit) {
                return;
            }
            JointDebugPrimitive primitive = PhysicsDebugRenderer.captureJoint(joint);
            Vector3d midpoint = new Vector3d(primitive.anchorA()).add(primitive.anchorB())
                .mul(0.5);
            if (viewerPosition.distanceSquared(midpoint) > maxDistanceSquared) {
                return;
            }

            visible.add(primitive);
        });
        return visible;
    }
}
