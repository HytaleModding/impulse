package dev.hytalemodding.impulse.core.internal.resources.joint;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.joint.PhysicsJointId;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime identity index for backend physics joints.
 */
public final class PhysicsJointRegistry {

    private final Map<PhysicsJointId, PhysicsJointRegistration> registrationsById =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<PhysicsJoint, PhysicsJointId> jointIdsByJoint = new Reference2ObjectOpenHashMap<>();

    @Nonnull
    public PhysicsJointRegistration registerJoint(@Nonnull PhysicsJointId jointId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsJoint joint) {
        PhysicsJointId existingId = jointIdsByJoint.get(joint);
        if (existingId != null && !existingId.equals(jointId)) {
            throw new IllegalArgumentException("Physics joint is already registered as " + existingId);
        }
        PhysicsJointRegistration existingRegistration = registrationsById.get(jointId);
        if (existingRegistration != null && existingRegistration.joint() != joint) {
            throw new IllegalArgumentException("Physics joint id=" + jointId
                + " is already registered to another backend joint");
        }
        PhysicsJointRegistration registration = new PhysicsJointRegistration(jointId, joint, spaceId);
        registrationsById.put(jointId, registration);
        jointIdsByJoint.put(joint, jointId);
        return registration;
    }

    @Nullable
    public PhysicsJointRegistration unregisterJoint(@Nonnull PhysicsJointId jointId) {
        PhysicsJointRegistration registration = registrationsById.remove(jointId);
        if (registration == null) {
            return null;
        }

        jointIdsByJoint.remove(registration.joint());
        return registration;
    }

    @Nullable
    public PhysicsJointRegistration unregisterJoint(@Nonnull PhysicsJoint joint) {
        PhysicsJointId jointId = jointIdsByJoint.get(joint);
        return jointId != null ? unregisterJoint(jointId) : null;
    }

    public void unregisterJointsForBody(@Nonnull PhysicsBody body) {
        ArrayList<PhysicsJointId> removed = new ArrayList<>();
        for (PhysicsJointRegistration registration : registrationsById.values()) {
            PhysicsJoint joint = registration.joint();
            if (joint.getBodyA() == body || joint.getBodyB() == body) {
                removed.add(registration.id());
            }
        }
        for (PhysicsJointId jointId : removed) {
            unregisterJoint(jointId);
        }
    }

    public void unregisterSpace(@Nonnull SpaceId spaceId) {
        ArrayList<PhysicsJointId> removed = new ArrayList<>();
        for (PhysicsJointRegistration registration : registrationsById.values()) {
            if (registration.spaceId().equals(spaceId)) {
                removed.add(registration.id());
            }
        }
        for (PhysicsJointId jointId : removed) {
            unregisterJoint(jointId);
        }
    }

    @Nullable
    public PhysicsJointRegistration getRegistration(@Nonnull PhysicsJointId jointId) {
        return registrationsById.get(jointId);
    }

    @Nullable
    public PhysicsJointId getJointId(@Nonnull PhysicsJoint joint) {
        return jointIdsByJoint.get(joint);
    }

    public void clear() {
        registrationsById.clear();
        jointIdsByJoint.clear();
    }
}
