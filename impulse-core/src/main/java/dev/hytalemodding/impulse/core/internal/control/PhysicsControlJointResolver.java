package dev.hytalemodding.impulse.core.internal.control;

import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerAccess;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PhysicsControlJointResolver {

    private PhysicsControlJointResolver() {
    }

    public static boolean removeControlJoint(@Nonnull PhysicsOwnerAccess access,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodyId anchorBodyId) {
        for (PhysicsJoint joint : new ArrayList<>(space.getJoints())) {
            PhysicsBodyId bodyAId = access.getBodyId(joint.getBodyA());
            PhysicsBodyId bodyBId = access.getBodyId(joint.getBodyB());
            if (isControlJoint(bodyAId, bodyBId, bodyId, anchorBodyId)) {
                space.removeJoint(joint);
                return true;
            }
        }
        return false;
    }

    private static boolean isControlJoint(@Nullable PhysicsBodyId bodyAId,
        @Nullable PhysicsBodyId bodyBId,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodyId anchorBodyId) {
        return (anchorBodyId.equals(bodyAId) && bodyId.equals(bodyBId))
            || (anchorBodyId.equals(bodyBId) && bodyId.equals(bodyAId));
    }
}
