package dev.hytalemodding.impulse.core.internal.resources.joint;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime identity index for backend physics joints.
 */
public final class PhysicsJointRegistry {

    private final Map<UUID, PhysicsJointRegistration> registrationsByUuid =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<UUID>> jointUuidsByRawBackendId =
        new Int2ObjectOpenHashMap<>();

    @Nonnull
    public PhysicsJointRegistration registerJoint(@Nonnull UUID jointUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendJointHandle backendJointHandle,
        @Nonnull UUID bodyAUuid,
        @Nonnull UUID bodyBUuid,
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
        UUID existingUuid = getJointUuid(spaceId, backendJointHandle);
        if (existingUuid != null && !existingUuid.equals(jointUuid)) {
            throw new IllegalArgumentException("Physics joint is already registered as " + existingUuid);
        }
        PhysicsJointRegistration existingRegistration = registrationsByUuid.get(jointUuid);
        if (existingRegistration != null
            && (!existingRegistration.backendJointHandle().equals(backendJointHandle)
                || !existingRegistration.spaceId().equals(spaceId))) {
            throw new IllegalArgumentException("Physics joint uuid=" + jointUuid
                + " is already registered to another backend joint");
        }
        if (existingRegistration != null) {
            removeBackendIndex(existingRegistration);
        }
        PhysicsJointRegistration registration = new PhysicsJointRegistration(jointUuid,
            backendJointHandle,
            spaceId,
            bodyAUuid,
            bodyBUuid,
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
        registrationsByUuid.put(jointUuid, registration);
        jointUuidsByRawBackendId
            .computeIfAbsent(spaceId.value(), ignored -> new Long2ObjectOpenHashMap<>())
            .put(backendJointHandle.value(), jointUuid);
        return registration;
    }

    @Nullable
    public PhysicsJointRegistration unregisterJoint(@Nonnull UUID jointUuid) {
        PhysicsJointRegistration registration = registrationsByUuid.remove(jointUuid);
        if (registration == null) {
            return null;
        }

        removeBackendIndex(registration);
        return registration;
    }

    @Nullable
    public PhysicsJointRegistration unregisterJoint(@Nonnull SpaceId spaceId, long backendJointId) {
        UUID jointUuid = getJointUuid(spaceId, backendJointId);
        return jointUuid != null ? unregisterJoint(jointUuid) : null;
    }

    @Nonnull
    public Collection<PhysicsJointRegistration> unregisterJointsForBody(@Nonnull UUID bodyUuid) {
        ArrayList<UUID> removed = new ArrayList<>();
        for (PhysicsJointRegistration registration : registrationsByUuid.values()) {
            if (registration.bodyAUuid().equals(bodyUuid) || registration.bodyBUuid().equals(bodyUuid)) {
                removed.add(registration.jointUuid());
            }
        }
        ArrayList<PhysicsJointRegistration> registrations = new ArrayList<>(removed.size());
        for (UUID jointUuid : removed) {
            PhysicsJointRegistration registration = unregisterJoint(jointUuid);
            if (registration != null) {
                registrations.add(registration);
            }
        }
        return registrations;
    }

    public void unregisterSpace(@Nonnull SpaceId spaceId) {
        ArrayList<UUID> removed = new ArrayList<>();
        for (PhysicsJointRegistration registration : registrationsByUuid.values()) {
            if (registration.spaceId().equals(spaceId)) {
                removed.add(registration.jointUuid());
            }
        }
        for (UUID jointUuid : removed) {
            unregisterJoint(jointUuid);
        }
    }

    @Nullable
    public PhysicsJointRegistration getRegistration(@Nonnull UUID jointUuid) {
        return registrationsByUuid.get(jointUuid);
    }

    @Nullable
    public JointKey getJointKey(@Nonnull SpaceId spaceId, long backendJointId) {
        UUID jointUuid = getJointUuid(spaceId, backendJointId);
        return jointUuid != null ? JointKey.of(jointUuid) : null;
    }

    @Nullable
    public UUID getJointUuid(@Nonnull SpaceId spaceId, long backendJointId) {
        Long2ObjectOpenHashMap<UUID> jointUuids =
            jointUuidsByRawBackendId.get(spaceId.value());
        return jointUuids != null ? jointUuids.get(backendJointId) : null;
    }

    @Nullable
    public JointKey getJointKey(@Nonnull SpaceId spaceId,
        @Nonnull BackendJointHandle backendJointHandle) {
        return getJointKey(spaceId, backendJointHandle.value());
    }

    @Nullable
    public UUID getJointUuid(@Nonnull SpaceId spaceId,
        @Nonnull BackendJointHandle backendJointHandle) {
        return getJointUuid(spaceId, backendJointHandle.value());
    }

    @Nonnull
    public Collection<PhysicsJointRegistration> getRegistrations() {
        return new ArrayList<>(registrationsByUuid.values());
    }

    public void clear() {
        registrationsByUuid.clear();
        jointUuidsByRawBackendId.clear();
    }

    private void removeBackendIndex(@Nonnull PhysicsJointRegistration registration) {
        Long2ObjectOpenHashMap<UUID> jointUuids =
            jointUuidsByRawBackendId.get(registration.spaceId().value());
        if (jointUuids == null) {
            return;
        }
        jointUuids.remove(registration.backendJointHandle().value());
        if (jointUuids.isEmpty()) {
            jointUuidsByRawBackendId.remove(registration.spaceId().value());
        }
    }

}
