package dev.hytalemodding.impulse.rapier;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.runtime.BackendBodyIdSource;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendContactSink;
import dev.hytalemodding.impulse.api.runtime.BackendExtensionSettingsSource;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRayHitSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeStatsSink;
import dev.hytalemodding.impulse.api.runtime.BackendStepPhaseStatsSink;
import dev.hytalemodding.impulse.api.runtime.BackendVec3Sink;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class RapierBackendRuntime implements PhysicsBackendRuntime {

    private static final Cleaner CLEANER = Cleaner.create();
    private static final float DEFAULT_DYNAMIC_MASS = 1.0f;
    private static final float DEFAULT_BODY_FRICTION = 0.5f;
    private static final int DEFAULT_BODY_COLLISION_GROUP = 1;
    private static final int DEFAULT_BODY_COLLISION_MASK = 1;
    private static final int DEFAULT_SOLVER_ITERATIONS = 4;
    private static final int DEFAULT_INTERNAL_PGS_ITERATIONS = 1;
    private static final int DEFAULT_STABILIZATION_ITERATIONS = 1;
    private static final int DEFAULT_MIN_ISLAND_SIZE = 128;
    private static final int BODY_SNAPSHOT_FLOATS = 16;
    private static final int CONTACT_FLOATS = 15;
    private static final int RAY_HIT_FLOATS = 10;
    private static final int RUNTIME_STATS_VALUES = 10;
    private static final int STEP_PHASE_STATS_VALUES = 6;
    private static final PhysicsCapabilityId RAPIER_SOLVER_EXTENSION_ID =
        new PhysicsCapabilityId("impulse:rapier_solver");
    private static final String INTERNAL_PGS_ITERATIONS = "internalPgsIterations";
    private static final String MIN_ISLAND_SIZE = "minIslandSize";

    private final Map<Integer, SpaceState> spaces = new HashMap<>();
    private long nextBodyId = 1L;
    private long nextJointId = 1L;

    @Override
    public int createSpace(@Nonnull SpaceId requestedId) {
        Objects.requireNonNull(requestedId, "requestedId");
        int spaceId = requestedId.value();
        if (spaces.containsKey(spaceId)) {
            throw new IllegalArgumentException("Physics space id=" + requestedId
                + " is already registered");
        }
        long nativeSpaceHandle = RapierNative.createSpaceNative();
        if (nativeSpaceHandle == 0L) {
            throw new IllegalStateException("Rapier returned a null native space handle");
        }
        spaces.put(spaceId, new SpaceState(spaceId, nativeSpaceHandle));
        return spaceId;
    }

    @Override
    public void destroySpace(int spaceId) {
        SpaceState state = spaces.remove(spaceId);
        if (state != null) {
            state.close();
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
        if (dt <= 0.0f) {
            return;
        }
        SpaceState state = requireSpace(spaceId);
        if (!RapierNative.stepNative(state.nativeSpaceHandle, dt)) {
            throw new IllegalStateException("Rapier native step failed");
        }
    }

    @Override
    public void setGravity(int spaceId, float x, float y, float z) {
        SpaceState state = requireSpace(spaceId);
        RapierNative.setGravityNative(state.nativeSpaceHandle, x, y, z);
    }

    @Override
    public void getGravity(int spaceId, @Nonnull BackendVec3Sink sink) {
        Objects.requireNonNull(sink, "sink");
        SpaceState state = requireSpace(spaceId);
        float[] out = new float[3];
        RapierNative.getGravityNative(state.nativeSpaceHandle, out);
        sink.accept(out[0], out[1], out[2]);
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
        return createBodyInternal(spaceId,
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
            rotationW,
            0.0f,
            0.0f,
            DEFAULT_BODY_FRICTION,
            0.0f,
            DEFAULT_BODY_COLLISION_GROUP,
            DEFAULT_BODY_COLLISION_MASK,
            false,
            false);
    }

    @Override
    public long createBodyWithInitialState(int spaceId,
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
        float rotationW,
        float linearDamping,
        float angularDamping,
        float friction,
        float restitution,
        int collisionGroup,
        int collisionMask,
        boolean sensor,
        boolean continuousCollisionEnabled) {
        return createBodyInternal(spaceId,
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
            rotationW,
            linearDamping,
            angularDamping,
            friction,
            restitution,
            collisionGroup,
            collisionMask,
            sensor,
            continuousCollisionEnabled);
    }

    private long createBodyInternal(int spaceId,
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
        float rotationW,
        float linearDamping,
        float angularDamping,
        float friction,
        float restitution,
        int collisionGroup,
        int collisionMask,
        boolean sensor,
        boolean continuousCollisionEnabled) {
        SpaceState state = requireSpace(spaceId);
        ShapeType shapeType = BackendRuntimeCodes.shapeType(shapeTypeCode);
        if (shapeType == ShapeType.VOXELS || shapeType == ShapeType.UNKNOWN) {
            throw new IllegalArgumentException("Unsupported Rapier body shape " + shapeType);
        }
        PhysicsAxis axis = BackendRuntimeCodes.axis(axisCode);
        PhysicsBodyType bodyType = BackendRuntimeCodes.bodyType(bodyTypeCode);
        float storedMass = adjustedMass(mass, bodyType);
        int storedBodyTypeCode = adjustedBodyTypeCode(bodyType, storedMass);
        BodyState body = BodyState.regular(nextBodyId++,
            shapeTypeCode,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axisCode,
            centerOfMassOffsetY(shapeType, axis, halfExtentY, radius, halfHeight),
            storedMass,
            storedBodyTypeCode,
            positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW);
        body.linearDamping = linearDamping;
        body.angularDamping = angularDamping;
        body.friction = friction;
        body.restitution = restitution;
        body.collisionGroup = collisionGroup;
        body.collisionMask = collisionMask;
        body.sensor = sensor;
        body.continuousCollisionEnabled = continuousCollisionEnabled;
        long handle = RapierNative.addBodyNative(state.nativeSpaceHandle,
            shapeType.ordinal(),
            body.halfExtentX,
            body.halfExtentY,
            body.halfExtentZ,
            body.radius,
            body.halfHeight,
            axis.index(),
            BackendRuntimeCodes.bodyType(body.bodyTypeCode).ordinal(),
            body.mass,
            body.positionX,
            body.positionY,
            body.positionZ,
            body.rotationX,
            body.rotationY,
            body.rotationZ,
            body.rotationW,
            body.linearVelocityX,
            body.linearVelocityY,
            body.linearVelocityZ,
            body.angularVelocityX,
            body.angularVelocityY,
            body.angularVelocityZ,
            body.friction,
            body.restitution,
            body.linearDamping,
            body.angularDamping,
            body.sensor,
            body.collisionGroup,
            body.collisionMask,
            body.continuousCollisionEnabled);
        attachBody(state, body, handle);
        return body.bodyId;
    }

    @Override
    public boolean supportsVoxelTerrain(int spaceId) {
        requireSpace(spaceId);
        return true;
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
        SpaceState state = requireSpace(spaceId);
        BodyState body = BodyState.voxels(nextBodyId++,
            voxelSizeX,
            voxelSizeY,
            voxelSizeZ,
            positionX,
            positionY,
            positionZ,
            friction,
            restitution,
            collisionGroup,
            collisionMask);
        long handle = RapierNative.addVoxelTerrainNative(state.nativeSpaceHandle,
            voxelSizeX,
            voxelSizeY,
            voxelSizeZ,
            voxelCoordinates,
            positionX,
            positionY,
            positionZ,
            friction,
            restitution,
            collisionGroup,
            collisionMask);
        attachBody(state, body, handle);
        return body.bodyId;
    }

    @Override
    public void combineVoxelTerrains(int spaceId,
        long bodyAId,
        long bodyBId,
        int shiftX,
        int shiftY,
        int shiftZ) {
        SpaceState state = requireSpace(spaceId);
        BodyState bodyA = requireBody(state, bodyAId);
        BodyState bodyB = requireBody(state, bodyBId);
        if (bodyA == bodyB) {
            throw new IllegalArgumentException("Cannot combine a voxel terrain body with itself");
        }
        requireVoxelTerrain(bodyA);
        requireVoxelTerrain(bodyB);
        if (!RapierNative.combineVoxelTerrainNative(state.nativeSpaceHandle,
            bodyA.nativeBodyHandle,
            bodyB.nativeBodyHandle,
            shiftX,
            shiftY,
            shiftZ)) {
            throw new IllegalStateException("Rapier native voxel terrain combine failed");
        }
    }

    @Override
    public void removeBody(int spaceId, long bodyId) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = state.bodiesById.get(bodyId);
        if (body == null) {
            return;
        }
        RapierNative.removeBodyNative(state.nativeSpaceHandle, body.nativeBodyHandle);
        state.bodiesById.remove(bodyId);
        state.bodyIdsByNativeHandle.remove(body.nativeBodyHandle);
        removeAttachedJointState(state, bodyId);
    }

    @Override
    public int bodyCount(int spaceId) {
        return requireSpace(spaceId).bodiesById.size();
    }

    @Override
    public boolean containsBody(int spaceId, long bodyId) {
        return requireSpace(spaceId).bodiesById.containsKey(bodyId);
    }

    @Override
    public boolean bodySnapshot(int spaceId, long bodyId, @Nonnull BackendBodySnapshotSink sink) {
        Objects.requireNonNull(sink, "sink");
        SpaceState state = requireSpace(spaceId);
        BodyState body = state.bodiesById.get(bodyId);
        if (body == null) {
            return false;
        }
        state.ensureSnapshotCapacity(1);
        state.snapshotBodyHandles[0] = body.nativeBodyHandle;
        int written = RapierNative.snapshotBodiesNative(state.nativeSpaceHandle,
            state.snapshotBodyHandles,
            1,
            state.snapshotBodyData);
        if (written > 0) {
            body.updateFromNative(state.snapshotBodyData, 0);
        }
        body.emit(sink);
        return true;
    }

    @Override
    public void snapshotBodies(int spaceId,
        @Nonnull BackendBodyIdSource bodyIds,
        @Nonnull BackendBodySnapshotSink sink) {
        Objects.requireNonNull(bodyIds, "bodyIds");
        Objects.requireNonNull(sink, "sink");
        SpaceState state = requireSpace(spaceId);
        SnapshotSelection selection = new SnapshotSelection(state);
        bodyIds.forEachBodyId(selection::add);
        if (selection.count == 0) {
            return;
        }
        int written = RapierNative.snapshotBodiesNative(state.nativeSpaceHandle,
            state.snapshotBodyHandles,
            selection.count,
            state.snapshotBodyData);
        int limit = Math.clamp(written, 0, selection.count);
        for (int index = 0; index < limit; index++) {
            BodyState body = selection.bodies[index];
            body.updateFromNative(state.snapshotBodyData, index * BODY_SNAPSHOT_FLOATS);
            body.emit(sink);
        }
        for (int index = limit; index < selection.count; index++) {
            selection.bodies[index].emit(sink);
        }
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
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        NormalizedRotation rotation = normalizeRotation(rotationX, rotationY, rotationZ, rotationW);
        RapierNative.setBodyPositionNative(state.nativeSpaceHandle,
            body.nativeBodyHandle,
            positionX,
            positionY,
            positionZ);
        body.setPosition(positionX, positionY, positionZ);
        RapierNative.setBodyRotationNative(state.nativeSpaceHandle,
            body.nativeBodyHandle,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w);
        body.setRotation(rotation.x, rotation.y, rotation.z, rotation.w);
    }

    @Override
    public void setBodyPosition(int spaceId, long bodyId, float x, float y, float z) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        RapierNative.setBodyPositionNative(state.nativeSpaceHandle, body.nativeBodyHandle, x, y, z);
        body.setPosition(x, y, z);
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
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        RapierNative.setBodyLinearVelocityNative(state.nativeSpaceHandle,
            body.nativeBodyHandle,
            linearX,
            linearY,
            linearZ);
        body.linearVelocityX = linearX;
        body.linearVelocityY = linearY;
        body.linearVelocityZ = linearZ;
        RapierNative.setBodyAngularVelocityNative(state.nativeSpaceHandle,
            body.nativeBodyHandle,
            angularX,
            angularY,
            angularZ);
        body.angularVelocityX = angularX;
        body.angularVelocityY = angularY;
        body.angularVelocityZ = angularZ;
    }

    @Override
    public void setBodyType(int spaceId, long bodyId, int bodyTypeCode) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        PhysicsBodyType bodyType = BackendRuntimeCodes.bodyType(bodyTypeCode);
        float adjustedMass = adjustedMass(body.mass, bodyType);
        if (Float.compare(adjustedMass, body.mass) != 0) {
            RapierNative.setBodyMassNative(state.nativeSpaceHandle,
                body.nativeBodyHandle,
                adjustedMass);
            body.mass = adjustedMass;
        }
        int adjustedBodyTypeCode = adjustedBodyTypeCode(bodyType, adjustedMass);
        RapierNative.setBodyTypeNative(state.nativeSpaceHandle,
            body.nativeBodyHandle,
            BackendRuntimeCodes.bodyType(adjustedBodyTypeCode).ordinal());
        body.bodyTypeCode = adjustedBodyTypeCode;
    }

    @Override
    public void setBodyDamping(int spaceId, long bodyId, float linearDamping, float angularDamping) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        RapierNative.setBodyDampingNative(state.nativeSpaceHandle,
            body.nativeBodyHandle,
            linearDamping,
            angularDamping);
        body.linearDamping = linearDamping;
        body.angularDamping = angularDamping;
    }

    @Override
    public void setBodyFriction(int spaceId, long bodyId, float friction) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        RapierNative.setBodyFrictionNative(state.nativeSpaceHandle, body.nativeBodyHandle, friction);
        body.friction = friction;
    }

    @Override
    public void setBodyRestitution(int spaceId, long bodyId, float restitution) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        RapierNative.setBodyRestitutionNative(state.nativeSpaceHandle,
            body.nativeBodyHandle,
            restitution);
        body.restitution = restitution;
    }

    @Override
    public void setBodyCollisionFilter(int spaceId, long bodyId, int group, int mask) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        RapierNative.setBodyCollisionFilterNative(state.nativeSpaceHandle,
            body.nativeBodyHandle,
            group,
            mask);
        body.collisionGroup = group;
        body.collisionMask = mask;
    }

    @Override
    public void setBodySensor(int spaceId, long bodyId, boolean sensor) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        RapierNative.setBodySensorNative(state.nativeSpaceHandle, body.nativeBodyHandle, sensor);
        body.sensor = sensor;
    }

    @Override
    public void setBodyContinuousCollision(int spaceId, long bodyId, boolean enabled) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        RapierNative.setBodyCcdNative(state.nativeSpaceHandle, body.nativeBodyHandle, enabled);
        body.continuousCollisionEnabled = enabled;
    }

    @Override
    public boolean isBodyContinuousCollisionEnabled(int spaceId, long bodyId) {
        SpaceState state = requireSpace(spaceId);
        BodyState body = requireBody(state, bodyId);
        body.continuousCollisionEnabled =
            RapierNative.isBodyCcdNative(state.nativeSpaceHandle, body.nativeBodyHandle);
        return body.continuousCollisionEnabled;
    }

    @Override
    public void activateBody(int spaceId, long bodyId) {
        SpaceState state = requireSpace(spaceId);
        RapierNative.activateBodyNative(state.nativeSpaceHandle,
            requireBody(state, bodyId).nativeBodyHandle);
    }

    @Override
    public void sleepBody(int spaceId, long bodyId) {
        SpaceState state = requireSpace(spaceId);
        RapierNative.sleepBodyNative(state.nativeSpaceHandle,
            requireBody(state, bodyId).nativeBodyHandle);
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
        SpaceState state = requireSpace(spaceId);
        long handle = requireBody(state, bodyId).nativeBodyHandle;
        if (torque) {
            RapierNative.applyBodyTorqueImpulseNative(state.nativeSpaceHandle, handle, x, y, z);
        } else if (hasOffset) {
            RapierNative.applyBodyImpulseNative(state.nativeSpaceHandle,
                handle,
                x,
                y,
                z,
                offsetX,
                offsetY,
                offsetZ);
        } else {
            RapierNative.applyBodyCentralImpulseNative(state.nativeSpaceHandle, handle, x, y, z);
        }
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
        SpaceState state = requireSpace(spaceId);
        long handle = requireBody(state, bodyId).nativeBodyHandle;
        if (torque) {
            RapierNative.applyBodyTorqueNative(state.nativeSpaceHandle, handle, x, y, z);
        } else if (hasOffset) {
            RapierNative.applyBodyForceNative(state.nativeSpaceHandle,
                handle,
                x,
                y,
                z,
                offsetX,
                offsetY,
                offsetZ);
        } else {
            RapierNative.applyBodyCentralForceNative(state.nativeSpaceHandle, handle, x, y, z);
        }
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
        SpaceState state = requireSpace(spaceId);
        BodyState bodyA = requireBody(state, bodyAId);
        BodyState bodyB = requireBody(state, bodyBId);
        NormalizedAxis axis = normalizeAxis(axisX, axisY, axisZ);
        long handle = RapierNative.addJointNative(state.nativeSpaceHandle,
            BackendRuntimeCodes.jointType(jointTypeCode).ordinal(),
            bodyA.nativeBodyHandle,
            bodyB.nativeBodyHandle,
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
            damping);
        if (handle == 0L) {
            throw new IllegalStateException("Rapier returned a null native joint handle");
        }
        BackendJointType type = BackendRuntimeCodes.jointType(jointTypeCode);
        if (type == BackendJointType.HINGE || type == BackendJointType.SLIDER) {
            try {
                RapierNative.setJointLimitsNative(state.nativeSpaceHandle,
                    handle,
                    lowerLimit,
                    upperLimit);
                RapierNative.setJointMotorNative(state.nativeSpaceHandle,
                    handle,
                    motorEnabled,
                    motorTargetVelocity,
                    motorMaxForce);
            } catch (RuntimeException exception) {
                try {
                    RapierNative.removeJointNative(state.nativeSpaceHandle, handle);
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                throw exception;
            }
        }
        long jointId = nextJointId++;
        JointState joint = new JointState(jointId,
            handle,
            jointTypeCode,
            bodyAId,
            bodyBId);
        state.jointsById.put(jointId, joint);
        return jointId;
    }

    @Override
    public void removeJoint(int spaceId, long jointId) {
        SpaceState state = requireSpace(spaceId);
        JointState joint = state.jointsById.get(jointId);
        if (joint != null) {
            RapierNative.removeJointNative(state.nativeSpaceHandle, joint.nativeJointHandle);
            state.jointsById.remove(jointId);
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
        SpaceState state = requireSpace(spaceId);
        float[] raw = RapierNative.raycastAllNative(state.nativeSpaceHandle,
            fromX,
            fromY,
            fromZ,
            toX,
            toY,
            toZ);
        int closestOffset = -1;
        float closestFraction = Float.POSITIVE_INFINITY;
        for (int offset = 0; raw != null && offset + RAY_HIT_FLOATS <= raw.length;
             offset += RAY_HIT_FLOATS) {
            long bodyHandle = rawBitFloatPairToLong(raw[offset], raw[offset + 1]);
            if (!state.bodyIdsByNativeHandle.containsKey(bodyHandle)) {
                continue;
            }
            float fraction = raw[offset + 8];
            if (fraction < closestFraction) {
                closestFraction = fraction;
                closestOffset = offset;
            }
        }
        if (closestOffset < 0) {
            return false;
        }
        emitRayHit(state, raw, closestOffset, sink);
        return true;
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
        SpaceState state = requireSpace(spaceId);
        float[] raw = RapierNative.raycastAllNative(state.nativeSpaceHandle,
            fromX,
            fromY,
            fromZ,
            toX,
            toY,
            toZ);
        int hits = 0;
        for (int offset = 0; raw != null && offset + RAY_HIT_FLOATS <= raw.length;
             offset += RAY_HIT_FLOATS) {
            if (emitRayHit(state, raw, offset, sink)) {
                hits++;
            }
        }
        return hits;
    }

    @Override
    public int contacts(int spaceId, @Nonnull BackendContactSink sink) {
        SpaceState state = requireSpace(spaceId);
        return emitContacts(state, RapierNative.getContactsNative(state.nativeSpaceHandle), sink);
    }

    @Override
    public int contacts(int spaceId, int maxContacts, @Nonnull BackendContactSink sink) {
        if (maxContacts <= 0) {
            return 0;
        }
        SpaceState state = requireSpace(spaceId);
        return emitContacts(state,
            RapierNative.getContactsLimitedNative(state.nativeSpaceHandle, maxContacts),
            sink);
    }

    @Override
    public int contactCount(int spaceId) {
        SpaceState state = requireSpace(spaceId);
        float[] raw = RapierNative.getContactsNative(state.nativeSpaceHandle);
        return raw != null ? raw.length / CONTACT_FLOATS : 0;
    }

    @Override
    public void runtimeStats(int spaceId, @Nonnull BackendRuntimeStatsSink sink) {
        SpaceState state = requireSpace(spaceId);
        int[] values = RapierNative.getRuntimeStatsNative(state.nativeSpaceHandle);
        if (values == null || values.length < RUNTIME_STATS_VALUES) {
            sink.accept(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
            return;
        }
        sink.accept(values[0],
            values[1],
            values[2],
            values[3],
            values[4],
            values[5],
            values[6],
            values[7],
            values[8],
            values[9],
            true);
    }

    @Override
    public void resetStepPhaseStats(int spaceId) {
        RapierNative.resetStepPhaseStatsNative(requireSpace(spaceId).nativeSpaceHandle);
    }

    @Override
    public void stepPhaseStats(int spaceId, @Nonnull BackendStepPhaseStatsSink sink) {
        SpaceState state = requireSpace(spaceId);
        long[] values = RapierNative.getStepPhaseStatsNative(state.nativeSpaceHandle);
        if (values == null || values.length < STEP_PHASE_STATS_VALUES) {
            sink.accept(0L, 0L, 0L, 0L, 0L, 0L, false);
            return;
        }
        sink.accept(values[0], values[1], values[2], values[3], values[4], values[5], true);
    }

    @Override
    public boolean supportsContinuousCollision(int spaceId) {
        requireSpace(spaceId);
        return true;
    }

    @Override
    public boolean supportsSolverTuning(int spaceId) {
        requireSpace(spaceId);
        return true;
    }

    @Override
    public boolean supportsActivationTuning(int spaceId) {
        requireSpace(spaceId);
        return true;
    }

    @Override
    public void applySolverTuning(int spaceId, @Nonnull PhysicsSolverTuning tuning) {
        Objects.requireNonNull(tuning, "tuning");
        SpaceState state = requireSpace(spaceId);
        state.applySolverTuning(tuning.solverIterations(),
            state.internalPgsIterations,
            tuning.stabilizationIterations(),
            state.minIslandSize);
        state.solverIterations = tuning.solverIterations();
        state.stabilizationIterations = tuning.stabilizationIterations();
    }

    @Override
    public void applyActivationTuning(int spaceId, @Nonnull PhysicsActivationTuning tuning) {
        Objects.requireNonNull(tuning, "tuning");
        SpaceState state = requireSpace(spaceId);
        RapierNative.setDynamicSleepTuningNative(state.nativeSpaceHandle,
            tuning.linearSleepThreshold(),
            tuning.angularSleepThreshold(),
            tuning.timeUntilSleep());
    }

    @Override
    public void applyExtensionSettings(int spaceId,
        @Nonnull PhysicsCapabilityId capabilityId,
        @Nonnull BackendExtensionSettingsSource settings) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(settings, "settings");
        SpaceState state = requireSpace(spaceId);
        if (!RAPIER_SOLVER_EXTENSION_ID.equals(capabilityId)) {
            return;
        }
        int[] internalPgsIterations = { state.internalPgsIterations };
        int[] minIslandSize = { state.minIslandSize };
        settings.forEachSetting((key, value) -> {
            if (INTERNAL_PGS_ITERATIONS.equals(key)) {
                internalPgsIterations[0] = parsePositive(value, key);
            } else if (MIN_ISLAND_SIZE.equals(key)) {
                minIslandSize[0] = parsePositive(value, key);
            }
        });
        state.applySolverTuning(state.solverIterations,
            internalPgsIterations[0],
            state.stabilizationIterations,
            minIslandSize[0]);
        state.internalPgsIterations = internalPgsIterations[0];
        state.minIslandSize = minIslandSize[0];
    }

    @Nonnull
    private SpaceState requireSpace(int spaceId) {
        SpaceState state = spaces.get(spaceId);
        if (state == null || state.closed) {
            throw new IllegalArgumentException("Physics space id=" + spaceId
                + " is not registered");
        }
        return state;
    }

    @Nonnull
    private static BodyState requireBody(@Nonnull SpaceState state, long bodyId) {
        BodyState body = state.bodiesById.get(bodyId);
        if (body == null) {
            throw new IllegalArgumentException("Physics body id=" + bodyId
                + " is not registered in space " + state.spaceId);
        }
        return body;
    }

    @Nonnull
    private static JointState requireJoint(@Nonnull SpaceState state, long jointId) {
        JointState joint = state.jointsById.get(jointId);
        if (joint == null) {
            throw new IllegalArgumentException("Physics joint id=" + jointId
                + " is not registered");
        }
        return joint;
    }

    private static void requireVoxelTerrain(@Nonnull BodyState body) {
        if (body.shapeTypeCode != BackendRuntimeCodes.SHAPE_VOXELS) {
            throw new IllegalArgumentException("Body must be a voxel terrain");
        }
    }

    private static void attachBody(@Nonnull SpaceState state,
        @Nonnull BodyState body,
        long nativeBodyHandle) {
        if (nativeBodyHandle == 0L) {
            throw new IllegalStateException("Rapier returned a null native body handle");
        }
        body.nativeBodyHandle = nativeBodyHandle;
        state.bodiesById.put(body.bodyId, body);
        state.bodyIdsByNativeHandle.put(nativeBodyHandle, body.bodyId);
    }

    private static void removeAttachedJointState(@Nonnull SpaceState state, long bodyId) {
        Iterator<Map.Entry<Long, JointState>> iterator = state.jointsById.entrySet().iterator();
        while (iterator.hasNext()) {
            JointState joint = iterator.next().getValue();
            if (joint.bodyAId != bodyId && joint.bodyBId != bodyId) {
                continue;
            }
            iterator.remove();
        }
    }

    private static boolean emitRayHit(@Nonnull SpaceState state,
        @Nullable float[] raw,
        int offset,
        @Nonnull BackendRayHitSink sink) {
        if (raw == null || offset + RAY_HIT_FLOATS > raw.length) {
            return false;
        }
        Long bodyId = state.bodyIdsByNativeHandle.get(rawBitFloatPairToLong(raw[offset],
            raw[offset + 1]));
        if (bodyId == null) {
            return false;
        }
        sink.accept(bodyId,
            raw[offset + 2],
            raw[offset + 3],
            raw[offset + 4],
            raw[offset + 5],
            raw[offset + 6],
            raw[offset + 7],
            raw[offset + 8],
            raw[offset + 9]);
        return true;
    }

    private static int emitContacts(@Nonnull SpaceState state,
        @Nullable float[] raw,
        @Nonnull BackendContactSink sink) {
        if (raw == null) {
            return 0;
        }
        int contacts = 0;
        for (int offset = 0; offset + CONTACT_FLOATS <= raw.length; offset += CONTACT_FLOATS) {
            Long bodyAId = state.bodyIdsByNativeHandle.get(rawBitFloatPairToLong(raw[offset],
                raw[offset + 1]));
            Long bodyBId = state.bodyIdsByNativeHandle.get(rawBitFloatPairToLong(raw[offset + 2],
                raw[offset + 3]));
            if (bodyAId == null || bodyBId == null) {
                continue;
            }
            sink.accept(bodyAId,
                bodyBId,
                raw[offset + 4],
                raw[offset + 5],
                raw[offset + 6],
                raw[offset + 7],
                raw[offset + 8],
                raw[offset + 9],
                raw[offset + 10],
                raw[offset + 11],
                raw[offset + 12],
                raw[offset + 13],
                raw[offset + 14]);
            contacts++;
        }
        return contacts;
    }

    private static long rawBitFloatPairToLong(float upper, float lower) {
        long upperBits = Float.floatToRawIntBits(upper);
        long lowerBits = Float.floatToRawIntBits(lower) & 0xFFFFFFFFL;
        return (upperBits << 32) | lowerBits;
    }

    private static int parsePositive(@Nonnull String value, @Nonnull String key) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Rapier extension setting " + key
                + " must be an integer", exception);
        }
        if (parsed < 1) {
            throw new IllegalArgumentException("Rapier extension setting " + key
                + " must be positive");
        }
        return parsed;
    }

    private static float adjustedMass(float mass, @Nonnull PhysicsBodyType bodyType) {
        if (bodyType == PhysicsBodyType.STATIC) {
            return 0.0f;
        }
        return mass <= 0.0f ? DEFAULT_DYNAMIC_MASS : mass;
    }

    private static int adjustedBodyTypeCode(@Nonnull PhysicsBodyType bodyType, float mass) {
        if (mass <= 0.0f) {
            return BackendRuntimeCodes.BODY_STATIC;
        }
        return BackendRuntimeCodes.bodyTypeCode(bodyType);
    }

    private static float centerOfMassOffsetY(@Nonnull ShapeType shapeType,
        @Nonnull PhysicsAxis axis,
        float halfExtentY,
        float radius,
        float halfHeight) {
        return switch (shapeType) {
            case BOX -> halfExtentY;
            case SPHERE -> radius;
            case CAPSULE -> axis == PhysicsAxis.Y ? halfHeight + radius : radius;
            case CYLINDER, CONE -> axis == PhysicsAxis.Y ? halfHeight : radius;
            case PLANE, VOXELS, UNKNOWN -> 0.0f;
        };
    }

    @Nonnull
    private static NormalizedRotation normalizeRotation(float x, float y, float z, float w) {
        float lengthSquared = x * x + y * y + z * z + w * w;
        if (lengthSquared == 0.0f) {
            return new NormalizedRotation(0.0f, 0.0f, 0.0f, 1.0f);
        }
        float inverseLength = (float) (1.0 / Math.sqrt(lengthSquared));
        return new NormalizedRotation(x * inverseLength,
            y * inverseLength,
            z * inverseLength,
            w * inverseLength);
    }

    private record NormalizedRotation(float x, float y, float z, float w) {
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

    private record NormalizedAxis(float x, float y, float z) {
    }

    private static final class SnapshotSelection {

        private final SpaceState state;
        private BodyState[] bodies = new BodyState[16];
        private int count;

        private SnapshotSelection(@Nonnull SpaceState state) {
            this.state = state;
        }

        private void add(long bodyId) {
            BodyState body = state.bodiesById.get(bodyId);
            if (body == null) {
                return;
            }
            if (bodies.length <= count) {
                BodyState[] grown = new BodyState[bodies.length * 2];
                System.arraycopy(bodies, 0, grown, 0, bodies.length);
                bodies = grown;
            }
            state.ensureSnapshotCapacity(count + 1);
            bodies[count] = body;
            state.snapshotBodyHandles[count] = body.nativeBodyHandle;
            count++;
        }
    }

    private static final class SpaceState {

        private final int spaceId;
        private long nativeSpaceHandle;
        private final Cleaner.Cleanable cleanable;
        private final Map<Long, BodyState> bodiesById = new HashMap<>();
        private final Map<Long, Long> bodyIdsByNativeHandle = new HashMap<>();
        private final Map<Long, JointState> jointsById = new HashMap<>();
        private long[] snapshotBodyHandles = new long[0];
        private float[] snapshotBodyData = new float[0];
        private int solverIterations = DEFAULT_SOLVER_ITERATIONS;
        private int internalPgsIterations = DEFAULT_INTERNAL_PGS_ITERATIONS;
        private int stabilizationIterations = DEFAULT_STABILIZATION_ITERATIONS;
        private int minIslandSize = DEFAULT_MIN_ISLAND_SIZE;
        private boolean closed;

        private SpaceState(int spaceId, long nativeSpaceHandle) {
            this.spaceId = spaceId;
            this.nativeSpaceHandle = nativeSpaceHandle;
            this.cleanable = CLEANER.register(this, new NativeSpaceCleanup(nativeSpaceHandle));
        }

        private void ensureSnapshotCapacity(int bodyCount) {
            if (snapshotBodyHandles.length < bodyCount) {
                int capacity = Math.max(bodyCount, Math.max(1, snapshotBodyHandles.length * 2));
                long[] grown = new long[capacity];
                System.arraycopy(snapshotBodyHandles, 0, grown, 0, snapshotBodyHandles.length);
                snapshotBodyHandles = grown;
            }
            int floats = bodyCount * BODY_SNAPSHOT_FLOATS;
            if (snapshotBodyData.length < floats) {
                int capacity = Math.max(floats, Math.max(BODY_SNAPSHOT_FLOATS,
                    snapshotBodyData.length * 2));
                snapshotBodyData = new float[capacity];
            }
        }

        private void applySolverTuning(int solverIterations,
            int internalPgsIterations,
            int stabilizationIterations,
            int minIslandSize) {
            RapierNative.setSolverTuningNative(nativeSpaceHandle,
                solverIterations,
                internalPgsIterations,
                stabilizationIterations,
                minIslandSize);
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            bodiesById.clear();
            bodyIdsByNativeHandle.clear();
            jointsById.clear();
            cleanable.clean();
            nativeSpaceHandle = 0L;
        }
    }

    private static final class BodyState {

        private final long bodyId;
        private long nativeBodyHandle;
        private final int shapeTypeCode;
        private final int axisCode;
        private final float halfExtentX;
        private final float halfExtentY;
        private final float halfExtentZ;
        private final float radius;
        private final float halfHeight;
        private final float centerOfMassOffsetY;
        private int bodyTypeCode;
        private float positionX;
        private float positionY;
        private float positionZ;
        private float rotationX;
        private float rotationY;
        private float rotationZ;
        private float rotationW;
        private float linearVelocityX;
        private float linearVelocityY;
        private float linearVelocityZ;
        private float angularVelocityX;
        private float angularVelocityY;
        private float angularVelocityZ;
        private boolean sleeping;
        private boolean sensor;
        private float mass;
        private float friction = DEFAULT_BODY_FRICTION;
        private float restitution;
        private float linearDamping;
        private float angularDamping;
        private int collisionGroup = DEFAULT_BODY_COLLISION_GROUP;
        private int collisionMask = DEFAULT_BODY_COLLISION_MASK;
        private boolean continuousCollisionEnabled;

        private BodyState(long bodyId,
            int shapeTypeCode,
            int axisCode,
            float halfExtentX,
            float halfExtentY,
            float halfExtentZ,
            float radius,
            float halfHeight,
            float centerOfMassOffsetY,
            float mass,
            int bodyTypeCode) {
            this.bodyId = bodyId;
            this.shapeTypeCode = shapeTypeCode;
            this.axisCode = axisCode;
            this.halfExtentX = halfExtentX;
            this.halfExtentY = halfExtentY;
            this.halfExtentZ = halfExtentZ;
            this.radius = radius;
            this.halfHeight = halfHeight;
            this.centerOfMassOffsetY = centerOfMassOffsetY;
            this.mass = mass;
            this.bodyTypeCode = bodyTypeCode;
            setRotation(0.0f, 0.0f, 0.0f, 1.0f);
        }

        @Nonnull
        private static BodyState regular(long bodyId,
            int shapeTypeCode,
            float halfExtentX,
            float halfExtentY,
            float halfExtentZ,
            float radius,
            float halfHeight,
            int axisCode,
            float centerOfMassOffsetY,
            float mass,
            int bodyTypeCode,
            float positionX,
            float positionY,
            float positionZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW) {
            BodyState body = new BodyState(bodyId,
                shapeTypeCode,
                axisCode,
                halfExtentX,
                halfExtentY,
                halfExtentZ,
                radius,
                halfHeight,
                centerOfMassOffsetY,
                mass,
                bodyTypeCode);
            body.setPosition(positionX, positionY, positionZ);
            body.setRotation(rotationX, rotationY, rotationZ, rotationW);
            return body;
        }

        @Nonnull
        private static BodyState voxels(long bodyId,
            float voxelSizeX,
            float voxelSizeY,
            float voxelSizeZ,
            float positionX,
            float positionY,
            float positionZ,
            float friction,
            float restitution,
            int collisionGroup,
            int collisionMask) {
            BodyState body = new BodyState(bodyId,
                BackendRuntimeCodes.SHAPE_VOXELS,
                BackendRuntimeCodes.AXIS_Y,
                voxelSizeX,
                voxelSizeY,
                voxelSizeZ,
                -1.0f,
                -1.0f,
                0.0f,
                0.0f,
                BackendRuntimeCodes.BODY_STATIC);
            body.setPosition(positionX, positionY, positionZ);
            body.friction = friction;
            body.restitution = restitution;
            body.collisionGroup = collisionGroup;
            body.collisionMask = collisionMask;
            return body;
        }

        private void setPosition(float x, float y, float z) {
            positionX = x;
            positionY = y;
            positionZ = z;
        }

        private void setRotation(float x, float y, float z, float w) {
            NormalizedRotation rotation = normalizeRotation(x, y, z, w);
            rotationX = rotation.x;
            rotationY = rotation.y;
            rotationZ = rotation.z;
            rotationW = rotation.w;
        }

        private void updateFromNative(@Nonnull float[] values, int offset) {
            sleeping = values[offset + 14] != 0.0f;
            positionX = values[offset];
            positionY = values[offset + 1];
            positionZ = values[offset + 2];
            setRotation(values[offset + 3],
                values[offset + 4],
                values[offset + 5],
                values[offset + 6]);
            linearVelocityX = values[offset + 7];
            linearVelocityY = values[offset + 8];
            linearVelocityZ = values[offset + 9];
            angularVelocityX = values[offset + 10];
            angularVelocityY = values[offset + 11];
            angularVelocityZ = values[offset + 12];
            int bodyTypeOrdinal = Math.round(values[offset + 13]);
            PhysicsBodyType[] bodyTypes = PhysicsBodyType.values();
            if (bodyTypeOrdinal >= 0 && bodyTypeOrdinal < bodyTypes.length) {
                bodyTypeCode = BackendRuntimeCodes.bodyTypeCode(bodyTypes[bodyTypeOrdinal]);
            }
            sensor = values[offset + 15] != 0.0f;
        }

        private void emit(@Nonnull BackendBodySnapshotSink sink) {
            sink.accept(bodyId,
                shapeTypeCode,
                bodyTypeCode,
                positionX,
                positionY,
                positionZ,
                rotationX,
                rotationY,
                rotationZ,
                rotationW,
                linearVelocityX,
                linearVelocityY,
                linearVelocityZ,
                angularVelocityX,
                angularVelocityY,
                angularVelocityZ,
                sleeping,
                sensor,
                mass,
                friction,
                restitution,
                linearDamping,
                angularDamping,
                collisionGroup,
                collisionMask,
                continuousCollisionEnabled,
                centerOfMassOffsetY,
                shapeTypeCode == BackendRuntimeCodes.SHAPE_BOX,
                shapeTypeCode == BackendRuntimeCodes.SHAPE_BOX ? halfExtentX : 0.0f,
                shapeTypeCode == BackendRuntimeCodes.SHAPE_BOX ? halfExtentY : 0.0f,
                shapeTypeCode == BackendRuntimeCodes.SHAPE_BOX ? halfExtentZ : 0.0f,
                radius,
                halfHeight,
                axisCode);
        }
    }

    private record JointState(long jointId,
                              long nativeJointHandle,
                              int jointTypeCode,
                              long bodyAId,
                              long bodyBId) {
    }

    private static final class NativeSpaceCleanup implements Runnable {

        private final long nativeSpaceHandle;

        private NativeSpaceCleanup(long nativeSpaceHandle) {
            this.nativeSpaceHandle = nativeSpaceHandle;
        }

        @Override
        public void run() {
            RapierNative.destroySpaceNative(nativeSpaceHandle);
        }
    }
}
