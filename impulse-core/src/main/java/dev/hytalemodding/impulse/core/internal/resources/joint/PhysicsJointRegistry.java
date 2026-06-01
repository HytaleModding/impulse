package dev.hytalemodding.impulse.core.internal.resources.joint;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime identity index for backend physics joints.
 */
public final class PhysicsJointRegistry {

    private final Map<JointKey, PhysicsJointRegistration> registrationsByKey =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<JointKey>> jointKeysByRawBackendId =
        new Int2ObjectOpenHashMap<>();

    @Nonnull
    public PhysicsJointRegistration registerJoint(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendJointHandle backendJointHandle,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB,
        @Nonnull JointType type,
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
        JointKey existingKey = getJointKey(spaceId, backendJointHandle);
        if (existingKey != null && !existingKey.equals(jointKey)) {
            throw new IllegalArgumentException("Physics joint is already registered as " + existingKey);
        }
        PhysicsJointRegistration existingRegistration = registrationsByKey.get(jointKey);
        if (existingRegistration != null
            && (!existingRegistration.backendJointHandle().equals(backendJointHandle)
                || !existingRegistration.spaceId().equals(spaceId))) {
            throw new IllegalArgumentException("Physics joint key=" + jointKey
                + " is already registered to another backend joint");
        }
        PhysicsJointRegistration registration = new PhysicsJointRegistration(jointKey,
            backendJointHandle,
            spaceId,
            bodyA,
            bodyB,
            type,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            restLength,
            stiffness,
            damping,
            lowerLimit,
            upperLimit,
            motorEnabled,
            motorTargetVelocity,
            motorMaxForce);
        registrationsByKey.put(jointKey, registration);
        jointKeysByRawBackendId
            .computeIfAbsent(spaceId.value(), ignored -> new Long2ObjectOpenHashMap<>())
            .put(backendJointHandle.value(), jointKey);
        return registration;
    }

    @Nullable
    public PhysicsJointRegistration unregisterJoint(@Nonnull JointKey jointKey) {
        PhysicsJointRegistration registration = registrationsByKey.remove(jointKey);
        if (registration == null) {
            return null;
        }

        removeBackendIndex(registration);
        return registration;
    }

    @Nullable
    public PhysicsJointRegistration unregisterJoint(@Nonnull SpaceId spaceId, long backendJointId) {
        JointKey jointKey = getJointKey(spaceId, backendJointId);
        return jointKey != null ? unregisterJoint(jointKey) : null;
    }

    @Nonnull
    public Collection<PhysicsJointRegistration> unregisterJointsForBody(@Nonnull RigidBodyKey bodyKey) {
        ArrayList<JointKey> removed = new ArrayList<>();
        for (PhysicsJointRegistration registration : registrationsByKey.values()) {
            if (registration.bodyA().equals(bodyKey) || registration.bodyB().equals(bodyKey)) {
                removed.add(registration.jointKey());
            }
        }
        ArrayList<PhysicsJointRegistration> registrations = new ArrayList<>(removed.size());
        for (JointKey jointKey : removed) {
            PhysicsJointRegistration registration = unregisterJoint(jointKey);
            if (registration != null) {
                registrations.add(registration);
            }
        }
        return registrations;
    }

    public void unregisterSpace(@Nonnull SpaceId spaceId) {
        ArrayList<JointKey> removed = new ArrayList<>();
        for (PhysicsJointRegistration registration : registrationsByKey.values()) {
            if (registration.spaceId().equals(spaceId)) {
                removed.add(registration.jointKey());
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
    public JointKey getJointKey(@Nonnull SpaceId spaceId, long backendJointId) {
        Long2ObjectOpenHashMap<JointKey> jointKeys =
            jointKeysByRawBackendId.get(spaceId.value());
        return jointKeys != null ? jointKeys.get(backendJointId) : null;
    }

    @Nullable
    public JointKey getJointKey(@Nonnull SpaceId spaceId,
        @Nonnull BackendJointHandle backendJointHandle) {
        return getJointKey(spaceId, backendJointHandle.value());
    }

    @Nullable
    public PhysicsJointRegistration findJointBetween(@Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        for (PhysicsJointRegistration registration : registrationsByKey.values()) {
            if (!registration.spaceId().equals(spaceId)) {
                continue;
            }
            if (connects(bodyA, bodyB, registration.bodyA(), registration.bodyB())) {
                return registration;
            }
        }
        return null;
    }

    @Nonnull
    public Collection<PhysicsJointRegistration> getRegistrations() {
        return new ArrayList<>(registrationsByKey.values());
    }

    public void clear() {
        registrationsByKey.clear();
        jointKeysByRawBackendId.clear();
    }

    private void removeBackendIndex(@Nonnull PhysicsJointRegistration registration) {
        Long2ObjectOpenHashMap<JointKey> jointKeys =
            jointKeysByRawBackendId.get(registration.spaceId().value());
        if (jointKeys == null) {
            return;
        }
        jointKeys.remove(registration.backendJointHandle().value());
        if (jointKeys.isEmpty()) {
            jointKeysByRawBackendId.remove(registration.spaceId().value());
        }
    }

    private static boolean connects(@Nonnull RigidBodyKey expectedA,
        @Nonnull RigidBodyKey expectedB,
        @Nonnull RigidBodyKey actualA,
        @Nonnull RigidBodyKey actualB) {
        return (expectedA.equals(actualA) && expectedB.equals(actualB))
            || (expectedA.equals(actualB) && expectedB.equals(actualA));
    }
}
