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
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsExtensionSettingsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuningCapability;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshot;
import dev.hytalemodding.impulse.api.runtime.BackendBodySpec;
import dev.hytalemodding.impulse.api.runtime.BackendContact;
import dev.hytalemodding.impulse.api.runtime.BackendJointSpec;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRayHit;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeStats;
import dev.hytalemodding.impulse.api.runtime.BackendStepPhaseStats;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Id-only runtime facade over the legacy live-object backend API.
 */
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

    @Nonnull
    @Override
    public Vector3f getGravity(int spaceId) {
        return requireSpace(spaceId).space.getGravity();
    }

    @Override
    public long createBody(int spaceId, @Nonnull BackendBodySpec spec) {
        SpaceState state = requireSpace(spaceId);
        PhysicsBody body = createLiveBody(state.space, spec);
        body.setBodyType(spec.bodyType());
        body.setPosition(spec.positionX(), spec.positionY(), spec.positionZ());
        body.setRotation(spec.rotationX(), spec.rotationY(), spec.rotationZ(), spec.rotationW());
        long bodyId = nextBodyId++;
        state.space.addBody(body);
        state.bodiesById.put(bodyId, body);
        state.bodyIdsByBody.put(body, bodyId);
        return bodyId;
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

    @Nonnull
    @Override
    public Optional<BackendBodySnapshot> bodySnapshot(int spaceId, long bodyId) {
        SpaceState state = requireSpace(spaceId);
        PhysicsBody body = state.bodiesById.get(bodyId);
        return body != null && state.space.getBodies().contains(body)
            ? Optional.of(new BackendBodySnapshot(bodyId, PhysicsBodySnapshot.from(body)))
            : Optional.empty();
    }

    @Override
    public void snapshotBodies(int spaceId,
        @Nonnull Iterable<Long> bodyIds,
        @Nonnull BiConsumer<Long, BackendBodySnapshot> consumer) {
        SpaceState state = requireSpace(spaceId);
        for (Long bodyId : bodyIds) {
            if (bodyId == null) {
                continue;
            }
            PhysicsBody body = state.bodiesById.get(bodyId);
            if (body != null && state.space.getBodies().contains(body)) {
                consumer.accept(bodyId, new BackendBodySnapshot(bodyId, PhysicsBodySnapshot.from(body)));
            }
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
    public void setBodyType(int spaceId, long bodyId, @Nonnull PhysicsBodyType bodyType) {
        requireBody(spaceId, bodyId).setBodyType(bodyType);
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
    public long createJoint(int spaceId, @Nonnull BackendJointSpec spec) {
        SpaceState state = requireSpace(spaceId);
        PhysicsBody bodyA = requireBody(spaceId, spec.bodyAId());
        PhysicsBody bodyB = requireBody(spaceId, spec.bodyBId());
        PhysicsJoint joint = createLiveJoint(state.space, bodyA, bodyB, spec);
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

    @Nonnull
    @Override
    public BackendJointType jointType(int spaceId, long jointId) {
        return toBackendJointType(requireJoint(spaceId, jointId).getType());
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

    @Nonnull
    @Override
    public Optional<BackendRayHit> raycastClosest(int spaceId,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        SpaceState state = requireSpace(spaceId);
        return state.space.raycastClosest(from, to)
            .flatMap(hit -> toBackendRayHit(state, hit));
    }

    @Nonnull
    @Override
    public List<BackendRayHit> raycastAll(int spaceId,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        SpaceState state = requireSpace(spaceId);
        List<BackendRayHit> hits = new ArrayList<>();
        for (PhysicsRayHit hit : state.space.raycastAll(from, to)) {
            toBackendRayHit(state, hit).ifPresent(hits::add);
        }
        return List.copyOf(hits);
    }

    @Nonnull
    @Override
    public List<BackendContact> contacts(int spaceId) {
        SpaceState state = requireSpace(spaceId);
        List<BackendContact> contacts = new ArrayList<>();
        for (PhysicsContact contact : state.space.getContacts()) {
            Long bodyAId = state.bodyIdsByBody.get(contact.bodyA());
            Long bodyBId = state.bodyIdsByBody.get(contact.bodyB());
            if (bodyAId != null && bodyBId != null) {
                contacts.add(new BackendContact(bodyAId,
                    bodyBId,
                    contact.pointOnA(),
                    contact.pointOnB(),
                    contact.normalOnB(),
                    contact.distance(),
                    contact.impulse()));
            }
        }
        return List.copyOf(contacts);
    }

    @Override
    public int contactCount(int spaceId) {
        return requireSpace(spaceId).space.contactCount();
    }

    @Nonnull
    @Override
    public BackendRuntimeStats runtimeStats(int spaceId) {
        PhysicsRuntimeStats stats = requireSpace(spaceId).space.getRuntimeStats();
        return new BackendRuntimeStats(stats.bodyCount(),
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

    @Nonnull
    @Override
    public BackendStepPhaseStats stepPhaseStats(int spaceId) {
        PhysicsStepPhaseStats stats = requireSpace(spaceId).space.getStepPhaseStats();
        return new BackendStepPhaseStats(stats.stepNanos(),
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
        @Nonnull Map<String, String> settings) {
        requireSpace(spaceId).space.getCapability(PhysicsExtensionSettingsCapability.class)
            .ifPresent(capability -> capability.applyExtensionSettings(capabilityId, settings));
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
        @Nonnull BackendBodySpec spec) {
        return switch (spec.shapeType()) {
            case BOX -> space.createBox(spec.halfExtentX(), spec.halfExtentY(), spec.halfExtentZ(), spec.mass());
            case SPHERE -> space.createSphere(spec.radius(), spec.mass());
            case CAPSULE -> space.createCapsule(spec.radius(), spec.halfHeight(), spec.axis(), spec.mass());
            case CYLINDER -> space.createCylinder(spec.radius(), spec.halfHeight(), spec.axis(), spec.mass());
            case CONE -> space.createCone(spec.radius(), spec.halfHeight(), spec.axis(), spec.mass());
            case PLANE -> space.createStaticPlane(spec.groundY());
            default -> throw new IllegalArgumentException("Unsupported shape " + spec.shapeType());
        };
    }

    @Nonnull
    private static PhysicsJoint createLiveJoint(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull BackendJointSpec spec) {
        PhysicsJoint joint = switch (spec.type()) {
            case FIXED -> space.createFixedJoint(bodyA,
                bodyB,
                spec.anchorAX(),
                spec.anchorAY(),
                spec.anchorAZ(),
                spec.anchorBX(),
                spec.anchorBY(),
                spec.anchorBZ());
            case POINT -> space.createPointJoint(bodyA,
                bodyB,
                spec.anchorAX(),
                spec.anchorAY(),
                spec.anchorAZ(),
                spec.anchorBX(),
                spec.anchorBY(),
                spec.anchorBZ());
            case HINGE -> space.createHingeJoint(bodyA,
                bodyB,
                spec.anchorAX(),
                spec.anchorAY(),
                spec.anchorAZ(),
                spec.anchorBX(),
                spec.anchorBY(),
                spec.anchorBZ(),
                spec.axisX(),
                spec.axisY(),
                spec.axisZ());
            case SLIDER -> space.createSliderJoint(bodyA,
                bodyB,
                spec.anchorAX(),
                spec.anchorAY(),
                spec.anchorAZ(),
                spec.anchorBX(),
                spec.anchorBY(),
                spec.anchorBZ(),
                spec.axisX(),
                spec.axisY(),
                spec.axisZ());
            case SPRING -> space.createSpringJoint(bodyA,
                bodyB,
                spec.anchorAX(),
                spec.anchorAY(),
                spec.anchorAZ(),
                spec.anchorBX(),
                spec.anchorBY(),
                spec.anchorBZ(),
                spec.restLength(),
                spec.stiffness(),
                spec.damping());
        };
        if (spec.type() == BackendJointType.HINGE || spec.type() == BackendJointType.SLIDER) {
            joint.setLimits(spec.lowerLimit(), spec.upperLimit());
            joint.setMotor(spec.motorTargetVelocity(), spec.motorMaxForce());
            joint.setMotorEnabled(spec.motorEnabled());
        }
        return joint;
    }

    @Nonnull
    private static Optional<BackendRayHit> toBackendRayHit(@Nonnull SpaceState state,
        @Nonnull PhysicsRayHit hit) {
        Long bodyId = state.bodyIdsByBody.get(hit.body());
        if (bodyId == null) {
            return Optional.empty();
        }
        return Optional.of(new BackendRayHit(bodyId,
            hit.point(),
            hit.normal(),
            hit.fraction(),
            hit.distance()));
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
