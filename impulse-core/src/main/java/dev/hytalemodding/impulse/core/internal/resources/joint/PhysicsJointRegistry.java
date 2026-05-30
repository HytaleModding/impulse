package dev.hytalemodding.impulse.core.internal.resources.joint;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
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

    private final Map<JointKey, PhysicsJointRegistration> registrationsByKey =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<PhysicsJoint, JointKey> jointKeysByJoint = new Reference2ObjectOpenHashMap<>();

    @Nonnull
    public PhysicsJointRegistration registerJoint(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsJoint joint) {
        JointKey existingKey = jointKeysByJoint.get(joint);
        if (existingKey != null && !existingKey.equals(jointKey)) {
            throw new IllegalArgumentException("Physics joint is already registered as " + existingKey);
        }
        PhysicsJointRegistration existingRegistration = registrationsByKey.get(jointKey);
        if (existingRegistration != null && existingRegistration.joint() != joint) {
            throw new IllegalArgumentException("Physics joint key=" + jointKey
                + " is already registered to another backend joint");
        }
        PhysicsJointRegistration registration = new PhysicsJointRegistration(jointKey, joint, spaceId);
        registrationsByKey.put(jointKey, registration);
        jointKeysByJoint.put(joint, jointKey);
        return registration;
    }

    @Nullable
    public PhysicsJointRegistration unregisterJoint(@Nonnull JointKey jointKey) {
        PhysicsJointRegistration registration = registrationsByKey.remove(jointKey);
        if (registration == null) {
            return null;
        }

        jointKeysByJoint.remove(registration.joint());
        return registration;
    }

    @Nullable
    public PhysicsJointRegistration unregisterJoint(@Nonnull PhysicsJoint joint) {
        JointKey jointKey = jointKeysByJoint.get(joint);
        return jointKey != null ? unregisterJoint(jointKey) : null;
    }

    public void unregisterJointsForBody(@Nonnull PhysicsBody body) {
        ArrayList<JointKey> removed = new ArrayList<>();
        for (PhysicsJointRegistration registration : registrationsByKey.values()) {
            PhysicsJoint joint = registration.joint();
            if (joint.getBodyA() == body || joint.getBodyB() == body) {
                removed.add(registration.id());
            }
        }
        for (JointKey jointKey : removed) {
            unregisterJoint(jointKey);
        }
    }

    public void unregisterSpace(@Nonnull SpaceId spaceId) {
        ArrayList<JointKey> removed = new ArrayList<>();
        for (PhysicsJointRegistration registration : registrationsByKey.values()) {
            if (registration.spaceId().equals(spaceId)) {
                removed.add(registration.id());
            }
        }
        for (JointKey jointKey : removed) {
            unregisterJoint(jointKey);
        }
    }

    @Nullable
    public PhysicsJointRegistration getRegistration(@Nonnull JointKey jointKey) {
        return registrationsByKey.get(jointKey);
    }

    @Nullable
    public JointKey getJointKey(@Nonnull PhysicsJoint joint) {
        return jointKeysByJoint.get(joint);
    }

    public void clear() {
        registrationsByKey.clear();
        jointKeysByJoint.clear();
    }
}
