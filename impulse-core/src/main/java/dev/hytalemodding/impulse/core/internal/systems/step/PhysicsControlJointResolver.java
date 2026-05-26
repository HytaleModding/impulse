package dev.hytalemodding.impulse.core.internal.systems.step;

import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class PhysicsControlJointResolver {

    private PhysicsControlJointResolver() {
    }

    static boolean removeControlJoint(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodyId anchorBodyId) {
        for (PhysicsJoint joint : new ArrayList<>(space.getJoints())) {
            PhysicsBodyId bodyAId = resource.getBodyId(joint.getBodyA());
            PhysicsBodyId bodyBId = resource.getBodyId(joint.getBodyB());
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
