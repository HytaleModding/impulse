package dev.hytalemodding.impulse.core.internal.control;

import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsOwnerAccess;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PhysicsControlJointResolver {

    private PhysicsControlJointResolver() {
    }

    public static boolean removeControlJoint(@Nonnull PhysicsOwnerAccess access,
        @Nonnull PhysicsSpace space,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyKey anchorBodyKey) {
        for (PhysicsJoint joint : new ArrayList<>(space.getJoints())) {
            RigidBodyKey bodyAKey = access.getBodyKey(joint.getBodyA());
            RigidBodyKey bodyBKey = access.getBodyKey(joint.getBodyB());
            if (isControlJoint(bodyAKey, bodyBKey, bodyKey, anchorBodyKey)) {
                space.removeJoint(joint);
                return true;
            }
        }
        return false;
    }

    private static boolean isControlJoint(@Nullable RigidBodyKey bodyAKey,
        @Nullable RigidBodyKey bodyBKey,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyKey anchorBodyKey) {
        return (anchorBodyKey.equals(bodyAKey) && bodyKey.equals(bodyBKey))
            || (anchorBodyKey.equals(bodyBKey) && bodyKey.equals(bodyAKey));
    }
}
