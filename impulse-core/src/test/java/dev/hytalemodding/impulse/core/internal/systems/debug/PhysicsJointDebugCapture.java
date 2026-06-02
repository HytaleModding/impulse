package dev.hytalemodding.impulse.core.internal.systems.debug;

import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

final class PhysicsJointDebugCapture {

    private PhysicsJointDebugCapture() {
    }

    @Nonnull
    static List<PhysicsDebugRenderer.JointDebugPrimitive> collectVisibleJointPrimitives(
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d center,
        double radius,
        int maxJoints) {
        if (maxJoints <= 0) {
            return List.of();
        }
        List<PhysicsDebugRenderer.JointDebugPrimitive> primitives = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (PhysicsJoint joint : space.getJoints()) {
            Vector3d anchorA = worldAnchor(joint.getBodyA().getPosition(), joint.getAnchorA());
            Vector3d anchorB = worldAnchor(joint.getBodyB().getPosition(), joint.getAnchorB());
            Vector3d midpoint = new Vector3d(anchorA).add(anchorB).mul(0.5);
            if (midpoint.distanceSquared(center) <= radiusSquared) {
                Vector3f axis = joint.getAxis();
                Vector3d axisDebug = axis != null
                    ? new Vector3d(axis.x, axis.y, axis.z).normalize().mul(0.9)
                    : null;
                primitives.add(new PhysicsDebugRenderer.JointDebugPrimitive(anchorA, anchorB, axisDebug));
                if (primitives.size() >= maxJoints) {
                    break;
                }
            }
        }
        return List.copyOf(primitives);
    }

    @Nonnull
    private static Vector3d worldAnchor(@Nonnull Vector3f bodyPosition,
        @Nonnull Vector3f localAnchor) {
        return new Vector3d(bodyPosition.x + localAnchor.x,
            bodyPosition.y + localAnchor.y,
            bodyPosition.z + localAnchor.z);
    }
}
