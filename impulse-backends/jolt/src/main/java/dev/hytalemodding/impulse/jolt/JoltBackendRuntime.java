package dev.hytalemodding.impulse.jolt;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.runtime.BackendBodyIdSource;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendContactSink;
import dev.hytalemodding.impulse.api.runtime.BackendExtensionSettingsSource;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.BackendRayHitSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeStatsSink;
import dev.hytalemodding.impulse.api.runtime.BackendStepPhaseStatsSink;
import dev.hytalemodding.impulse.api.runtime.BackendVec3Sink;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class JoltBackendRuntime implements PhysicsBackendRuntime {

    private static final String UNSUPPORTED_MESSAGE =
        "Jolt runtime operation is not implemented yet";

    private final JoltBackend backend;
    @Nullable
    private final JoltNativeLibrary fixedNativeLibrary;
    private final Map<Integer, RuntimeSpace> spaces = new HashMap<>();
    private final float[] gravityScratch = new float[3];
    private final long[] closestRayBodyHandleScratch = new long[1];
    private final float[] closestRayHitScratch = new float[JoltNativeLibrary.RAY_HIT_FLOAT_COUNT];
    private final JoltBodySnapshot bodySnapshotScratch = new JoltBodySnapshot();

    JoltBackendRuntime(@Nonnull JoltBackend backend) {
        this(backend, null);
    }

    JoltBackendRuntime(@Nonnull JoltBackend backend, @Nullable JoltNativeLibrary nativeLibrary) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.fixedNativeLibrary = nativeLibrary;
    }

    @Override
    public int createSpace(@Nonnull SpaceId requestedId) {
        Objects.requireNonNull(requestedId, "requestedId");
        int spaceId = requestedId.value();
        if (spaces.containsKey(spaceId)) {
            throw new IllegalStateException("Jolt space already exists: " + spaceId);
        }
        long handle = nativeLibrary().createSpace();
        if (handle == 0L) {
            throw new IllegalStateException("Jolt native library returned a null space handle");
        }
        spaces.put(spaceId, new RuntimeSpace(handle));
        return spaceId;
    }

    @Override
    public void destroySpace(int spaceId) {
        RuntimeSpace space = spaces.remove(spaceId);
        if (space != null) {
            nativeLibrary().destroySpace(space.handle);
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (Integer spaceId : new ArrayList<>(spaces.keySet())) {
            try {
                destroySpace(spaceId);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void step(int spaceId, float dt) {
        long handle = requireSpaceHandle(spaceId);
        if (dt <= 0.0f) {
            return;
        }
        nativeLibrary().step(handle, dt);
    }

    @Override
    public void setGravity(int spaceId, float x, float y, float z) {
        nativeLibrary().setGravity(requireSpaceHandle(spaceId), x, y, z);
    }

    @Override
    public void getGravity(int spaceId, @Nonnull BackendVec3Sink sink) {
        Objects.requireNonNull(sink, "sink");
        nativeLibrary().getGravity(requireSpaceHandle(spaceId), gravityScratch);
        sink.accept(gravityScratch[0], gravityScratch[1], gravityScratch[2]);
    }

    @Override
    public long createBody(int spaceId,
        int shapeTypeCode,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        int axisCode,
        float groundY,
        float mass,
        int bodyTypeCode,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW) {
        RuntimeSpace space = requireSpace(spaceId);
        validateBodyShapeCode(shapeTypeCode);
        BackendRuntimeCodes.axis(axisCode);
        BackendRuntimeCodes.bodyType(bodyTypeCode);
        long bodyHandle = nativeLibrary().createBody(space.handle,
            shapeTypeCode,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axisCode,
            groundY,
            mass,
            bodyTypeCode,
            positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW);
        if (bodyHandle == 0L) {
            throw new IllegalStateException("Jolt native library returned a null body handle");
        }
        long bodyId = space.nextBodyId++;
        space.bodyHandles.put(bodyId, bodyHandle);
        space.bodyIdsByHandle.put(bodyHandle, bodyId);
        return bodyId;
    }

    @Override
    public boolean supportsVoxelTerrain(int spaceId) {
        requireSpaceHandle(spaceId);
        return false;
    }

    @Override
    public long createVoxelTerrain(int spaceId,
        float voxelSizeX,
        float voxelSizeY,
        float voxelSizeZ,
        @Nonnull int[] voxelCoordinates,
        float positionX,
        float positionY,
        float positionZ,
        float friction,
        float restitution,
        int collisionGroup,
        int collisionMask) {
        Objects.requireNonNull(voxelCoordinates, "voxelCoordinates");
        requireSpaceHandle(spaceId);
        throw unsupported();
    }

    @Override
    public void combineVoxelTerrains(int spaceId,
        long bodyAId,
        long bodyBId,
        int shiftX,
        int shiftY,
        int shiftZ) {
        requireSpaceHandle(spaceId);
        throw unsupported();
    }

    @Override
    public void removeBody(int spaceId, long bodyId) {
        RuntimeSpace space = requireSpace(spaceId);
        Long bodyHandle = space.bodyHandles.remove(bodyId);
        if (bodyHandle != null) {
            space.bodyIdsByHandle.remove(bodyHandle);
            nativeLibrary().removeBody(space.handle, bodyHandle);
            removeAttachedJointState(space, bodyId);
        }
    }

    @Override
    public int bodyCount(int spaceId) {
        return nativeLibrary().bodyCount(requireSpaceHandle(spaceId));
    }

    @Override
    public boolean containsBody(int spaceId, long bodyId) {
        RuntimeSpace space = requireSpace(spaceId);
        Long bodyHandle = space.bodyHandles.get(bodyId);
        return bodyHandle != null && nativeLibrary().containsBody(space.handle, bodyHandle);
    }

    @Override
    public boolean bodySnapshot(int spaceId, long bodyId, @Nonnull BackendBodySnapshotSink sink) {
        Objects.requireNonNull(sink, "sink");
        RuntimeSpace space = requireSpace(spaceId);
        Long bodyHandle = space.bodyHandles.get(bodyId);
        if (bodyHandle == null) {
            return false;
        }
        bodySnapshotScratch.clear();
        if (!nativeLibrary().bodySnapshot(space.handle, bodyHandle, bodySnapshotScratch)) {
            return false;
        }
        bodySnapshotScratch.emit(bodyId, sink);
        return true;
    }

    @Override
    public void snapshotBodies(int spaceId,
        @Nonnull BackendBodyIdSource bodyIds,
        @Nonnull BackendBodySnapshotSink sink) {
        Objects.requireNonNull(bodyIds, "bodyIds");
        Objects.requireNonNull(sink, "sink");
        RuntimeSpace space = requireSpace(spaceId);
        bodyIds.forEachBodyId(bodyId -> emitBodySnapshotIfPresent(space, bodyId, sink));
    }

    @Override
    public void setBodyTransform(int spaceId,
        long bodyId,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().setBodyTransform(space.handle,
            requireBodyHandle(space, bodyId),
            positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW);
    }

    @Override
    public void setBodyPosition(int spaceId, long bodyId, float x, float y, float z) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().setBodyPosition(space.handle, requireBodyHandle(space, bodyId), x, y, z);
    }

    @Override
    public void setBodyVelocity(int spaceId,
        long bodyId,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().setBodyVelocity(space.handle,
            requireBodyHandle(space, bodyId),
            linearX,
            linearY,
            linearZ,
            angularX,
            angularY,
            angularZ);
    }

    @Override
    public void setBodyType(int spaceId, long bodyId, int bodyTypeCode) {
        RuntimeSpace space = requireSpace(spaceId);
        BackendRuntimeCodes.bodyType(bodyTypeCode);
        nativeLibrary().setBodyType(space.handle, requireBodyHandle(space, bodyId), bodyTypeCode);
    }

    @Override
    public void setBodyDamping(int spaceId, long bodyId, float linearDamping, float angularDamping) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().setBodyDamping(space.handle,
            requireBodyHandle(space, bodyId),
            linearDamping,
            angularDamping);
    }

    @Override
    public void setBodyFriction(int spaceId, long bodyId, float friction) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().setBodyFriction(space.handle, requireBodyHandle(space, bodyId), friction);
    }

    @Override
    public void setBodyRestitution(int spaceId, long bodyId, float restitution) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().setBodyRestitution(space.handle,
            requireBodyHandle(space, bodyId),
            restitution);
    }

    @Override
    public void setBodyCollisionFilter(int spaceId, long bodyId, int group, int mask) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().setBodyCollisionFilter(space.handle,
            requireBodyHandle(space, bodyId),
            group,
            mask);
    }

    @Override
    public void setBodySensor(int spaceId, long bodyId, boolean sensor) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().setBodySensor(space.handle, requireBodyHandle(space, bodyId), sensor);
    }

    @Override
    public void setBodyContinuousCollision(int spaceId, long bodyId, boolean enabled) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().setBodyContinuousCollision(space.handle,
            requireBodyHandle(space, bodyId),
            enabled);
    }

    @Override
    public boolean isBodyContinuousCollisionEnabled(int spaceId, long bodyId) {
        RuntimeSpace space = requireSpace(spaceId);
        return nativeLibrary().isBodyContinuousCollisionEnabled(space.handle,
            requireBodyHandle(space, bodyId));
    }

    @Override
    public void activateBody(int spaceId, long bodyId) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().activateBody(space.handle, requireBodyHandle(space, bodyId));
    }

    @Override
    public void sleepBody(int spaceId, long bodyId) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().sleepBody(space.handle, requireBodyHandle(space, bodyId));
    }

    @Override
    public void applyBodyImpulse(int spaceId,
        long bodyId,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().applyBodyImpulse(space.handle,
            requireBodyHandle(space, bodyId),
            x,
            y,
            z,
            hasOffset,
            offsetX,
            offsetY,
            offsetZ,
            torque);
    }

    @Override
    public void applyBodyForce(int spaceId,
        long bodyId,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        RuntimeSpace space = requireSpace(spaceId);
        nativeLibrary().applyBodyForce(space.handle,
            requireBodyHandle(space, bodyId),
            x,
            y,
            z,
            hasOffset,
            offsetX,
            offsetY,
            offsetZ,
            torque);
    }

    @Override
    public long createJoint(int spaceId,
        int jointTypeCode,
        long bodyAId,
        long bodyBId,
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
        RuntimeSpace space = requireSpace(spaceId);
        BackendJointType type = BackendRuntimeCodes.jointType(jointTypeCode);
        if (bodyAId == bodyBId) {
            throw new IllegalArgumentException("Jolt joint endpoints must reference distinct bodies");
        }
        long bodyAHandle = requireBodyHandle(space, bodyAId);
        long bodyBHandle = requireBodyHandle(space, bodyBId);
        NormalizedAxis axis = normalizeAxis(axisX, axisY, axisZ);
        long jointHandle = nativeLibrary().createJoint(space.handle,
            BackendRuntimeCodes.jointTypeCode(type),
            bodyAHandle,
            bodyBHandle,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axis.x,
            axis.y,
            axis.z,
            restLength,
            stiffness,
            damping,
            lowerLimit,
            upperLimit,
            motorEnabled,
            motorTargetVelocity,
            motorMaxForce);
        if (jointHandle == 0L) {
            throw new IllegalStateException("Jolt native library returned a null joint handle");
        }
        long jointId = space.nextJointId++;
        space.jointsById.put(jointId, new JointState(jointHandle,
            jointTypeCode,
            bodyAId,
            bodyBId));
        return jointId;
    }

    @Override
    public void removeJoint(int spaceId, long jointId) {
        RuntimeSpace space = requireSpace(spaceId);
        JointState joint = space.jointsById.get(jointId);
        if (joint != null) {
            nativeLibrary().removeJoint(space.handle, joint.nativeJointHandle);
            space.jointsById.remove(jointId);
        }
    }

    @Override
    public int jointCount(int spaceId) {
        return requireSpace(spaceId).jointsById.size();
    }

    @Override
    public int jointType(int spaceId, long jointId) {
        return requireJoint(requireSpace(spaceId), jointId).jointTypeCode;
    }

    @Override
    public long jointBodyA(int spaceId, long jointId) {
        return requireJoint(requireSpace(spaceId), jointId).bodyAId;
    }

    @Override
    public long jointBodyB(int spaceId, long jointId) {
        return requireJoint(requireSpace(spaceId), jointId).bodyBId;
    }

    @Override
    public boolean raycastClosest(int spaceId,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        @Nonnull BackendRayHitSink sink) {
        Objects.requireNonNull(sink, "sink");
        RuntimeSpace space = requireSpace(spaceId);
        int hitCount = nativeLibrary().raycastClosest(space.handle,
            fromX,
            fromY,
            fromZ,
            toX,
            toY,
            toZ,
            closestRayBodyHandleScratch,
            closestRayHitScratch);
        return hitCount > 0 && emitRayHit(space,
            closestRayBodyHandleScratch[0],
            closestRayHitScratch,
            0,
            sink);
    }

    @Override
    public int raycastAll(int spaceId,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        @Nonnull BackendRayHitSink sink) {
        Objects.requireNonNull(sink, "sink");
        RuntimeSpace space = requireSpace(spaceId);
        int maxHits = nativeLibrary().bodyCount(space.handle);
        if (maxHits <= 0) {
            return 0;
        }
        long[] bodyHandles = new long[maxHits];
        float[] hits = new float[maxHits * JoltNativeLibrary.RAY_HIT_FLOAT_COUNT];
        int nativeHits = nativeLibrary().raycastAll(space.handle,
            fromX,
            fromY,
            fromZ,
            toX,
            toY,
            toZ,
            maxHits,
            bodyHandles,
            hits);
        int emitted = 0;
        int boundedHits = Math.clamp(nativeHits, 0, maxHits);
        for (int index = 0; index < boundedHits; index++) {
            if (emitRayHit(space,
                bodyHandles[index],
                hits,
                index * JoltNativeLibrary.RAY_HIT_FLOAT_COUNT,
                sink)) {
                emitted++;
            }
        }
        return emitted;
    }

    @Override
    public int contacts(int spaceId, @Nonnull BackendContactSink sink) {
        Objects.requireNonNull(sink, "sink");
        RuntimeSpace space = requireSpace(spaceId);
        int maxContacts = nativeLibrary().contactCount(space.handle);
        return emitContacts(space, maxContacts, sink);
    }

    @Override
    public int contacts(int spaceId, int maxContacts, @Nonnull BackendContactSink sink) {
        Objects.requireNonNull(sink, "sink");
        RuntimeSpace space = requireSpace(spaceId);
        return emitContacts(space, maxContacts, sink);
    }

    @Override
    public int contactCount(int spaceId) {
        return nativeLibrary().contactCount(requireSpaceHandle(spaceId));
    }

    @Override
    public void runtimeStats(int spaceId, @Nonnull BackendRuntimeStatsSink sink) {
        Objects.requireNonNull(sink, "sink");
        long handle = requireSpaceHandle(spaceId);
        int bodyCount = nativeLibrary().bodyCount(handle);
        int jointCount = nativeLibrary().jointCount(handle);
        sink.accept(bodyCount,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            jointCount,
            true);
    }

    @Override
    public void resetStepPhaseStats(int spaceId) {
        requireSpaceHandle(spaceId);
    }

    @Override
    public void stepPhaseStats(int spaceId, @Nonnull BackendStepPhaseStatsSink sink) {
        Objects.requireNonNull(sink, "sink");
        requireSpaceHandle(spaceId);
        sink.accept(0L, 0L, 0L, 0L, 0L, 0L, false);
    }

    @Override
    public boolean supportsContinuousCollision(int spaceId) {
        requireSpaceHandle(spaceId);
        return true;
    }

    @Override
    public boolean supportsSolverTuning(int spaceId) {
        requireSpaceHandle(spaceId);
        return false;
    }

    @Override
    public boolean supportsActivationTuning(int spaceId) {
        requireSpaceHandle(spaceId);
        return false;
    }

    @Override
    public void applySolverTuning(int spaceId, @Nonnull PhysicsSolverTuning tuning) {
        Objects.requireNonNull(tuning, "tuning");
        requireSpaceHandle(spaceId);
    }

    @Override
    public void applyActivationTuning(int spaceId, @Nonnull PhysicsActivationTuning tuning) {
        Objects.requireNonNull(tuning, "tuning");
        requireSpaceHandle(spaceId);
    }

    @Override
    public void applyExtensionSettings(int spaceId,
        @Nonnull PhysicsCapabilityId capabilityId,
        @Nonnull BackendExtensionSettingsSource settings) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(settings, "settings");
        requireSpaceHandle(spaceId);
    }

    @Nonnull
    JoltBackend backend() {
        return backend;
    }

    private long requireSpaceHandle(int spaceId) {
        return requireSpace(spaceId).handle;
    }

    @Nonnull
    private RuntimeSpace requireSpace(int spaceId) {
        RuntimeSpace space = spaces.get(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Unknown Jolt space id: " + spaceId);
        }
        return space;
    }

    private long requireBodyHandle(@Nonnull RuntimeSpace space, long bodyId) {
        Long bodyHandle = space.bodyHandles.get(bodyId);
        if (bodyHandle == null) {
            throw new IllegalArgumentException("Unknown Jolt body id: " + bodyId);
        }
        return bodyHandle;
    }

    @Nonnull
    private static JointState requireJoint(@Nonnull RuntimeSpace space, long jointId) {
        JointState joint = space.jointsById.get(jointId);
        if (joint == null) {
            throw new IllegalArgumentException("Unknown Jolt joint id: " + jointId);
        }
        return joint;
    }

    private static void removeAttachedJointState(@Nonnull RuntimeSpace space, long bodyId) {
        space.jointsById.values().removeIf(joint -> joint.bodyAId == bodyId
            || joint.bodyBId == bodyId);
    }

    private void emitBodySnapshotIfPresent(@Nonnull RuntimeSpace space,
        long bodyId,
        @Nonnull BackendBodySnapshotSink sink) {
        Long bodyHandle = space.bodyHandles.get(bodyId);
        if (bodyHandle == null) {
            return;
        }
        bodySnapshotScratch.clear();
        if (nativeLibrary().bodySnapshot(space.handle, bodyHandle, bodySnapshotScratch)) {
            bodySnapshotScratch.emit(bodyId, sink);
        }
    }

    private boolean emitRayHit(@Nonnull RuntimeSpace space,
        long bodyHandle,
        @Nonnull float[] hit,
        int offset,
        @Nonnull BackendRayHitSink sink) {
        Long bodyId = space.bodyIdsByHandle.get(bodyHandle);
        if (bodyId == null) {
            return false;
        }
        sink.accept(bodyId,
            hit[offset],
            hit[offset + 1],
            hit[offset + 2],
            hit[offset + 3],
            hit[offset + 4],
            hit[offset + 5],
            hit[offset + 6],
            hit[offset + 7]);
        return true;
    }

    private int emitContacts(@Nonnull RuntimeSpace space,
        int maxContacts,
        @Nonnull BackendContactSink sink) {
        if (maxContacts <= 0) {
            return 0;
        }
        long[] bodyHandles = new long[maxContacts * JoltNativeLibrary.CONTACT_BODY_HANDLE_COUNT];
        float[] contacts = new float[maxContacts * JoltNativeLibrary.CONTACT_FLOAT_COUNT];
        int nativeContacts = nativeLibrary().contacts(space.handle,
            maxContacts,
            bodyHandles,
            contacts);
        int emitted = 0;
        int boundedContacts = Math.clamp(nativeContacts, 0, maxContacts);
        for (int index = 0; index < boundedContacts; index++) {
            int bodyOffset = index * JoltNativeLibrary.CONTACT_BODY_HANDLE_COUNT;
            Long bodyAId = space.bodyIdsByHandle.get(bodyHandles[bodyOffset]);
            Long bodyBId = space.bodyIdsByHandle.get(bodyHandles[bodyOffset + 1]);
            if (bodyAId == null || bodyBId == null) {
                continue;
            }
            int contactOffset = index * JoltNativeLibrary.CONTACT_FLOAT_COUNT;
            sink.accept(bodyAId,
                bodyBId,
                contacts[contactOffset],
                contacts[contactOffset + 1],
                contacts[contactOffset + 2],
                contacts[contactOffset + 3],
                contacts[contactOffset + 4],
                contacts[contactOffset + 5],
                contacts[contactOffset + 6],
                contacts[contactOffset + 7],
                contacts[contactOffset + 8],
                contacts[contactOffset + 9],
                contacts[contactOffset + 10]);
            emitted++;
        }
        return emitted;
    }

    private static void validateBodyShapeCode(int shapeTypeCode) {
        switch (BackendRuntimeCodes.shapeType(shapeTypeCode)) {
            case BOX, SPHERE, CAPSULE, CYLINDER, CONE, PLANE -> {
            }
            case VOXELS, UNKNOWN -> throw new IllegalArgumentException(
                "Unsupported Jolt body shape code: " + shapeTypeCode);
        }
    }

    @Nonnull
    private static NormalizedAxis normalizeAxis(float axisX, float axisY, float axisZ) {
        float lengthSquared = axisX * axisX + axisY * axisY + axisZ * axisZ;
        if (lengthSquared == 0.0f) {
            return new NormalizedAxis(0.0f, 1.0f, 0.0f);
        }
        float inverseLength = (float) (1.0 / Math.sqrt(lengthSquared));
        return new NormalizedAxis(axisX * inverseLength,
            axisY * inverseLength,
            axisZ * inverseLength);
    }

    @Nonnull
    private JoltNativeLibrary nativeLibrary() {
        return fixedNativeLibrary != null ? fixedNativeLibrary : backend.nativeLibrary();
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    private record NormalizedAxis(float x, float y, float z) {
    }

    private record JointState(long nativeJointHandle,
                              int jointTypeCode,
                              long bodyAId,
                              long bodyBId) {
    }

    private static final class RuntimeSpace {

        private final long handle;
        private final Map<Long, Long> bodyHandles = new HashMap<>();
        private final Map<Long, Long> bodyIdsByHandle = new HashMap<>();
        private final Map<Long, JointState> jointsById = new HashMap<>();
        private long nextBodyId = 1L;
        private long nextJointId = 1L;

        private RuntimeSpace(long handle) {
            this.handle = handle;
        }
    }
}
