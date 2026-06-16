package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Factories for direct PhysicsStore joint components.
 */
public final class PhysicsJointEntities {

    private PhysicsJointEntities() {
    }

    @Nonnull
    public static JointComponent joint(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyAUuid,
        @Nonnull UUID bodyBUuid,
        @Nonnull JointType type,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        JointComponent joint = new JointComponent();
        joint.setSpaceUuid(Objects.requireNonNull(spaceUuid, "spaceUuid"));
        joint.setBodyAUuid(Objects.requireNonNull(bodyAUuid, "bodyAUuid"));
        joint.setBodyBUuid(Objects.requireNonNull(bodyBUuid, "bodyBUuid"));
        joint.setType(Objects.requireNonNull(type, "type"));
        joint.setAnchorA(anchorA);
        joint.setAnchorB(anchorB);
        joint.setAxis(axis);
        joint.setEnabled(true);
        return joint;
    }

    @Nonnull
    public static JointComponent joint(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Ref<PhysicsStore> bodyARef,
        @Nonnull Ref<PhysicsStore> bodyBRef,
        @Nonnull JointType type,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        PhysicsStoreEntityRefs.requireSameStore(spaceRef, bodyARef, "bodyARef");
        PhysicsStoreEntityRefs.requireSameStore(spaceRef, bodyBRef, "bodyBRef");
        JointComponent joint = joint(PhysicsStoreEntityRefs.entityUuid(spaceRef),
            PhysicsStoreEntityRefs.entityUuid(bodyARef),
            PhysicsStoreEntityRefs.entityUuid(bodyBRef),
            type,
            anchorA,
            anchorB,
            axis);
        joint.setSpaceRef(spaceRef);
        joint.setBodyARef(bodyARef);
        joint.setBodyBRef(bodyBRef);
        return joint;
    }
}
