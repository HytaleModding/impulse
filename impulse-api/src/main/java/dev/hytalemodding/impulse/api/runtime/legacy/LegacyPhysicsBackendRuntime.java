package dev.hytalemodding.impulse.api.runtime.legacy;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsRuntimeStats;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsExtensionSettingsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsVoxelTerrainCapability;
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Id-only runtime facade over the legacy live-object backend API.
 */
@Deprecated(forRemoval = true)
public final class LegacyPhysicsBackendRuntime implements PhysicsBackendRuntime {

    @Nonnull
    private final PhysicsBackend backend;
    private final Map<Integer, SpaceState> spaces = new HashMap<>();
    private long nextBodyId = 1L;
    private long nextJointId = 1L;

    public LegacyPhysicsBackendRuntime(@Nonnull PhysicsBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public int createSpace(@Nonnull SpaceId requestedId) {
        Objects.requireNonNull(requestedId, "requestedId");
        if (spaces.containsKey(requestedId.value())) {
            throw new IllegalArgumentException("Physics space id=" + requestedId + " is already registered");
        }
        PhysicsSpace space = backend.createSpace(requestedId);
        spaces.put(requestedId.value(), new SpaceState(space));
        return requestedId.value();
    }

    @Override
    public void destroySpace(int spaceId) {
        SpaceState state = spaces.remove(spaceId);
        if (state != null) {
            state.space.close();
        }
    }

    @Override
    public void step(int spaceId, float dt) {
        requireSpace(spaceId).space.step(dt);
    }

    @Override
    public void setGravity(int spaceId, float x, float y, float z) {
        requireSpace(spaceId).space.setGravity(x, y, z);
    }

    @Override
    public void getGravity(int spaceId, @Nonnull BackendVec3Sink sink) {
        Vector3f gravity = requireSpace(spaceId).space.getGravity();
        sink.accept(gravity.x, gravity.y, gravity.z);
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
        SpaceState state = requireSpace(spaceId);
        PhysicsBody body = createLiveBody(state.space,
            shapeTypeCode,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axisCode,
            groundY,
            mass);
        body.setBodyType(BackendRuntimeCodes.bodyType(bodyTypeCode));
        body.setPosition(positionX, positionY, positionZ);
        body.setRotation(rotationX, rotationY, rotationZ, rotationW);
        long bodyId = nextBodyId++;
        state.space.addBody(body);
        state.bodiesById.put(bodyId, body);
        state.bodyIdsByBody.put(body, bodyId);
        return bodyId;
    }

    @Override
    public boolean supportsVoxelTerrain(int spaceId) {
        return requireSpace(spaceId).space.getCapability(PhysicsVoxelTerrainCapability.class).isPresent();
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
        PhysicsVoxelTerrainCapability capability = requireVoxelTerrainCapability(state);
        PhysicsBody body = capability.createVoxelTerrain(voxelSizeX,
            voxelSizeY,
            voxelSizeZ,
            voxelCoordinates);
        body.setBodyType(PhysicsBodyType.STATIC);
        body.setPosition(positionX, positionY, positionZ);
        body.setFriction(friction);
        body.setRestitution(restitution);
        body.setCollisionFilter(collisionGroup, collisionMask);

        long bodyId = nextBodyId++;
        state.space.addBody(body);
        state.bodiesById.put(bodyId, body);
        state.bodyIdsByBody.put(body, bodyId);
        return bodyId;
    }

    @Override
    public void combineVoxelTerrains(int spaceId,
        long bodyAId,
        long bodyBId,
        int shiftX,
        int shiftY,
        int shiftZ) {
        SpaceState state = requireSpace(spaceId);
        PhysicsVoxelTerrainCapability capability = requireVoxelTerrainCapability(state);
        capability.combineVoxelTerrains(requireBody(spaceId, bodyAId),
            requireBody(spaceId, bodyBId),
            shiftX,
            shiftY,
            shiftZ);
    }

    @Override
    public void removeBody(int spaceId, long bodyId) {
        SpaceState state = requireSpace(spaceId);
        PhysicsBody body = state.bodiesById.remove(bodyId);
        if (body == null) {
            return;
        }
        state.bodyIdsByBody.remove(body);
        state.space.removeBody(body);
    }

    @Override
    public int bodyCount(int spaceId) {
        return requireSpace(spaceId).space.bodyCount();
    }

    @Override
    public boolean containsBody(int spaceId, long bodyId) {
        SpaceState state = requireSpace(spaceId);
        PhysicsBody body = state.bodiesById.get(bodyId);
        return body != null && state.space.getBodies().contains(body);
    }

    @Override
    public boolean bodySnapshot(int spaceId,
        long bodyId,
        @Nonnull BackendBodySnapshotSink sink) {
        SpaceState state = requireSpace(spaceId);
        PhysicsBody body = state.bodiesById.get(bodyId);
        if (body == null || !state.space.getBodies().contains(body)) {
            return false;
        }
        emitBodySnapshot(bodyId, PhysicsBodySnapshot.from(body), sink);
        return true;
    }

    @Override
    public void snapshotBodies(int spaceId,
        @Nonnull BackendBodyIdSource bodyIds,
        @Nonnull BackendBodySnapshotSink sink) {
        SpaceState state = requireSpace(spaceId);
        bodyIds.forEachBodyId(bodyId -> {
            PhysicsBody body = state.bodiesById.get(bodyId);
            if (body != null && state.space.getBodies().contains(body)) {
                emitBodySnapshot(bodyId, PhysicsBodySnapshot.from(body), sink);
            }
        });
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
        PhysicsBody body = requireBody(spaceId, bodyId);
        body.setPosition(positionX, positionY, positionZ);
        body.setRotation(rotationX, rotationY, rotationZ, rotationW);
    }

    @Override
    public void setBodyPosition(int spaceId, long bodyId, float x, float y, float z) {
        requireBody(spaceId, bodyId).setPosition(x, y, z);
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
        PhysicsBody body = requireBody(spaceId, bodyId);
        body.setLinearVelocity(linearX, linearY, linearZ);
        body.setAngularVelocity(angularX, angularY, angularZ);
    }

    @Override
    public void setBodyType(int spaceId, long bodyId, int bodyTypeCode) {
        requireBody(spaceId, bodyId).setBodyType(BackendRuntimeCodes.bodyType(bodyTypeCode));
    }

    @Override
    public void setBodyDamping(int spaceId, long bodyId, float linearDamping, float angularDamping) {
        requireBody(spaceId, bodyId).setDamping(linearDamping, angularDamping);
    }

    @Override
    public void setBodyFriction(int spaceId, long bodyId, float friction) {
        requireBody(spaceId, bodyId).setFriction(friction);
    }

    @Override
    public void setBodyRestitution(int spaceId, long bodyId, float restitution) {
        requireBody(spaceId, bodyId).setRestitution(restitution);
    }

    @Override
    public void setBodyCollisionFilter(int spaceId, long bodyId, int group, int mask) {
        requireBody(spaceId, bodyId).setCollisionFilter(group, mask);
    }

    @Override
    public void setBodySensor(int spaceId, long bodyId, boolean sensor) {
        requireBody(spaceId, bodyId).setSensor(sensor);
    }

    @Override
    public void setBodyContinuousCollision(int spaceId, long bodyId, boolean enabled) {
        requireBody(spaceId, bodyId).setContinuousCollisionEnabled(enabled);
    }

    @Override
    public boolean isBodyContinuousCollisionEnabled(int spaceId, long bodyId) {
        return requireBody(spaceId, bodyId).isContinuousCollisionEnabled();
    }

    @Override
    public void activateBody(int spaceId, long bodyId) {
        requireBody(spaceId, bodyId).activate();
    }

    @Override
    public void sleepBody(int spaceId, long bodyId) {
        requireBody(spaceId, bodyId).sleep();
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
        PhysicsBody body = requireBody(spaceId, bodyId);
        if (torque) {
            body.applyTorqueImpulse(x, y, z);
        } else if (hasOffset) {
            body.applyImpulse(x, y, z, offsetX, offsetY, offsetZ);
        } else {
            body.applyCentralImpulse(x, y, z);
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
        PhysicsBody body = requireBody(spaceId, bodyId);
        if (torque) {
            body.applyTorque(x, y, z);
        } else if (hasOffset) {
            body.applyForce(x, y, z, offsetX, offsetY, offsetZ);
        } else {
            body.applyCentralForce(x, y, z);
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
        PhysicsBody bodyA = requireBody(spaceId, bodyAId);
        PhysicsBody bodyB = requireBody(spaceId, bodyBId);
        PhysicsJoint joint = createLiveJoint(state.space,
            bodyA,
            bodyB,
            jointTypeCode,
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
        long jointId = nextJointId++;
        state.jointsById.put(jointId, joint);
        state.jointIdsByJoint.put(joint, jointId);
        return jointId;
    }

    @Override
    public void removeJoint(int spaceId, long jointId) {
        SpaceState state = requireSpace(spaceId);
        PhysicsJoint joint = state.jointsById.remove(jointId);
        if (joint == null) {
            return;
        }
        state.jointIdsByJoint.remove(joint);
        state.space.removeJoint(joint);
    }

    @Override
    public int jointCount(int spaceId) {
        return requireSpace(spaceId).space.jointCount();
    }

    @Override
    public int jointType(int spaceId, long jointId) {
        return BackendRuntimeCodes.jointTypeCode(toBackendJointType(requireJoint(spaceId, jointId).getType()));
    }

    @Override
    public long jointBodyA(int spaceId, long jointId) {
        SpaceState state = requireSpace(spaceId);
        PhysicsBody body = requireJoint(state, jointId).getBodyA();
        Long bodyId = state.bodyIdsByBody.get(body);
        return bodyId != null ? bodyId : -1L;
    }

    @Override
    public long jointBodyB(int spaceId, long jointId) {
        SpaceState state = requireSpace(spaceId);
        PhysicsBody body = requireJoint(state, jointId).getBodyB();
        Long bodyId = state.bodyIdsByBody.get(body);
        return bodyId != null ? bodyId : -1L;
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
        return state.space.raycastClosest(new Vector3f(fromX, fromY, fromZ), new Vector3f(toX, toY, toZ))
            .map(hit -> emitRayHit(state, hit, sink))
            .orElse(false);
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
        int hits = 0;
        for (PhysicsRayHit hit : state.space.raycastAll(new Vector3f(fromX, fromY, fromZ),
            new Vector3f(toX, toY, toZ))) {
            if (emitRayHit(state, hit, sink)) {
                hits++;
            }
        }
        return hits;
    }

    @Override
    public int contacts(int spaceId, @Nonnull BackendContactSink sink) {
        SpaceState state = requireSpace(spaceId);
        int contacts = 0;
        for (PhysicsContact contact : state.space.getContacts()) {
            Long bodyAId = state.bodyIdsByBody.get(contact.bodyA());
            Long bodyBId = state.bodyIdsByBody.get(contact.bodyB());
            if (bodyAId != null && bodyBId != null) {
                Vector3f pointOnA = contact.pointOnA();
                Vector3f pointOnB = contact.pointOnB();
                Vector3f normalOnB = contact.normalOnB();
                sink.accept(bodyAId,
                    bodyBId,
                    pointOnA.x,
                    pointOnA.y,
                    pointOnA.z,
                    pointOnB.x,
                    pointOnB.y,
                    pointOnB.z,
                    normalOnB.x,
                    normalOnB.y,
                    normalOnB.z,
                    contact.distance(),
                    contact.impulse());
                contacts++;
            }
        }
        return contacts;
    }

    @Override
    public int contactCount(int spaceId) {
        return requireSpace(spaceId).space.contactCount();
    }

    @Override
    public void runtimeStats(int spaceId, @Nonnull BackendRuntimeStatsSink sink) {
        PhysicsRuntimeStats stats = requireSpace(spaceId).space.getRuntimeStats();
        sink.accept(stats.bodyCount(),
            stats.colliderCount(),
            stats.activeBodyCount(),
            stats.contactPairCount(),
            stats.contactManifoldCount(),
            stats.contactPointCount(),
            stats.dynamicDynamicContactPairCount(),
            stats.terrainContactPairCount(),
            stats.activeIslandCount(),
            stats.jointCount(),
            stats.available());
    }

    @Override
    public void resetStepPhaseStats(int spaceId) {
        requireSpace(spaceId).space.resetStepPhaseStats();
    }

    @Override
    public void stepPhaseStats(int spaceId, @Nonnull BackendStepPhaseStatsSink sink) {
        PhysicsStepPhaseStats stats = requireSpace(spaceId).space.getStepPhaseStats();
        sink.accept(stats.stepNanos(),
            stats.broadPhaseNanos(),
            stats.narrowPhaseNanos(),
            stats.solverNanos(),
            stats.ccdNanos(),
            stats.snapshotNanos(),
            stats.available());
    }

    @Override
    public boolean supportsContinuousCollision(int spaceId) {
        return requireSpace(spaceId).space.getCapability(PhysicsContinuousCollisionCapability.class).isPresent();
    }

    @Override
    public boolean supportsSolverTuning(int spaceId) {
        return requireSpace(spaceId).space.getCapability(PhysicsSolverTuningCapability.class).isPresent();
    }

    @Override
    public boolean supportsActivationTuning(int spaceId) {
        return requireSpace(spaceId).space.getCapability(PhysicsActivationTuningCapability.class).isPresent();
    }

    @Override
    public void applySolverTuning(int spaceId, @Nonnull PhysicsSolverTuning tuning) {
        requireSpace(spaceId).space.getCapability(PhysicsSolverTuningCapability.class)
            .ifPresent(capability -> capability.setSolverTuning(tuning));
    }

    @Override
    public void applyActivationTuning(int spaceId, @Nonnull PhysicsActivationTuning tuning) {
        requireSpace(spaceId).space.getCapability(PhysicsActivationTuningCapability.class)
            .ifPresent(capability -> capability.setActivationTuning(tuning));
    }

    @Override
    public void applyExtensionSettings(int spaceId,
        @Nonnull PhysicsCapabilityId capabilityId,
        @Nonnull BackendExtensionSettingsSource settings) {
        requireSpace(spaceId).space.getCapability(PhysicsExtensionSettingsCapability.class)
            .ifPresent(capability -> {
                Map<String, String> copied = new HashMap<>();
                settings.forEachSetting(copied::put);
                capability.applyExtensionSettings(capabilityId, copied);
            });
    }

    @Nonnull
    private SpaceState requireSpace(int spaceId) {
        SpaceState state = spaces.get(spaceId);
        if (state == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        return state;
    }

    @Nonnull
    private PhysicsBody requireBody(int spaceId, long bodyId) {
        PhysicsBody body = requireSpace(spaceId).bodiesById.get(bodyId);
        if (body == null) {
            throw new IllegalArgumentException("Physics body id=" + bodyId + " is not registered in space " + spaceId);
        }
        return body;
    }

    @Nonnull
    private static PhysicsVoxelTerrainCapability requireVoxelTerrainCapability(@Nonnull SpaceState state) {
        return state.space.getCapability(PhysicsVoxelTerrainCapability.class)
            .orElseThrow(() -> new UnsupportedOperationException("Legacy backend runtime does not support voxel terrain"));
    }

    @Nonnull
    private PhysicsJoint requireJoint(int spaceId, long jointId) {
        return requireJoint(requireSpace(spaceId), jointId);
    }

    @Nonnull
    private static PhysicsJoint requireJoint(@Nonnull SpaceState state, long jointId) {
        PhysicsJoint joint = state.jointsById.get(jointId);
        if (joint == null) {
            throw new IllegalArgumentException("Physics joint id=" + jointId + " is not registered");
        }
        return joint;
    }

    @Nonnull
    private static PhysicsBody createLiveBody(@Nonnull PhysicsSpace space,
        int shapeTypeCode,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        int axisCode,
        float groundY,
        float mass) {
        ShapeType shapeType = BackendRuntimeCodes.shapeType(shapeTypeCode);
        PhysicsAxis axis = BackendRuntimeCodes.axis(axisCode);
        return switch (shapeType) {
            case BOX -> space.createBox(halfExtentX, halfExtentY, halfExtentZ, mass);
            case SPHERE -> space.createSphere(radius, mass);
            case CAPSULE -> space.createCapsule(radius, halfHeight, axis, mass);
            case CYLINDER -> space.createCylinder(radius, halfHeight, axis, mass);
            case CONE -> space.createCone(radius, halfHeight, axis, mass);
            case PLANE -> space.createStaticPlane(groundY);
            default -> throw new IllegalArgumentException("Unsupported shape " + shapeType);
        };
    }

    @Nonnull
    private static PhysicsJoint createLiveJoint(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        int jointTypeCode,
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
        BackendJointType type = BackendRuntimeCodes.jointType(jointTypeCode);
        PhysicsJoint joint = switch (type) {
            case FIXED -> space.createFixedJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ);
            case POINT -> space.createPointJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ);
            case HINGE -> space.createHingeJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ,
                axisX,
                axisY,
                axisZ);
            case SLIDER -> space.createSliderJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ,
                axisX,
                axisY,
                axisZ);
            case SPRING -> space.createSpringJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ,
                restLength,
                stiffness,
                damping);
        };
        if (type == BackendJointType.HINGE || type == BackendJointType.SLIDER) {
            joint.setLimits(lowerLimit, upperLimit);
            joint.setMotor(motorTargetVelocity, motorMaxForce);
            joint.setMotorEnabled(motorEnabled);
        }
        return joint;
    }

    private static boolean emitRayHit(@Nonnull SpaceState state,
        @Nonnull PhysicsRayHit hit,
        @Nonnull BackendRayHitSink sink) {
        Long bodyId = state.bodyIdsByBody.get(hit.body());
        if (bodyId == null) {
            return false;
        }
        Vector3f point = hit.point();
        Vector3f normal = hit.normal();
        sink.accept(bodyId,
            point.x,
            point.y,
            point.z,
            normal.x,
            normal.y,
            normal.z,
            hit.fraction(),
            hit.distance());
        return true;
    }

    private static void emitBodySnapshot(long bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull BackendBodySnapshotSink sink) {
        sink.accept(bodyId,
            BackendRuntimeCodes.shapeTypeCode(snapshot.shapeType()),
            BackendRuntimeCodes.bodyTypeCode(snapshot.bodyType()),
            snapshot.positionX(),
            snapshot.positionY(),
            snapshot.positionZ(),
            snapshot.rotationX(),
            snapshot.rotationY(),
            snapshot.rotationZ(),
            snapshot.rotationW(),
            snapshot.linearVelocityX(),
            snapshot.linearVelocityY(),
            snapshot.linearVelocityZ(),
            snapshot.angularVelocityX(),
            snapshot.angularVelocityY(),
            snapshot.angularVelocityZ(),
            snapshot.sleeping(),
            snapshot.sensor(),
            snapshot.mass(),
            snapshot.friction(),
            snapshot.restitution(),
            snapshot.linearDamping(),
            snapshot.angularDamping(),
            snapshot.collisionGroup(),
            snapshot.collisionMask(),
            snapshot.continuousCollisionEnabled(),
            snapshot.centerOfMassOffsetY(),
            snapshot.hasBoxHalfExtents(),
            snapshot.boxHalfExtentX(),
            snapshot.boxHalfExtentY(),
            snapshot.boxHalfExtentZ(),
            snapshot.sphereRadius(),
            snapshot.halfHeight(),
            BackendRuntimeCodes.axisCode(snapshot.shapeAxis()));
    }

    @Nonnull
    private static BackendJointType toBackendJointType(@Nonnull PhysicsJointType type) {
        return switch (type) {
            case FIXED -> BackendJointType.FIXED;
            case POINT -> BackendJointType.POINT;
            case HINGE -> BackendJointType.HINGE;
            case SLIDER -> BackendJointType.SLIDER;
            case SPRING -> BackendJointType.SPRING;
        };
    }

    private static final class SpaceState {

        @Nonnull
        private final PhysicsSpace space;
        private final Map<Long, PhysicsBody> bodiesById = new HashMap<>();
        private final Map<PhysicsBody, Long> bodyIdsByBody = new IdentityHashMap<>();
        private final Map<Long, PhysicsJoint> jointsById = new HashMap<>();
        private final Map<PhysicsJoint, Long> jointIdsByJoint = new IdentityHashMap<>();

        private SpaceState(@Nonnull PhysicsSpace space) {
            this.space = Objects.requireNonNull(space, "space");
        }
    }
}
