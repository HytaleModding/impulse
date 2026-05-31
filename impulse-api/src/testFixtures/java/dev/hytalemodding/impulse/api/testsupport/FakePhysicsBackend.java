package dev.hytalemodding.impulse.api.testsupport;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityDescriptor;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuningCapability;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Small in-memory backend for resource lifecycle tests that need real API objects
 * without loading a native physics implementation.
 */
public final class FakePhysicsBackend implements PhysicsBackend {

    private final BackendId id;
    private final List<InMemoryPhysicsSpace> createdSpaces = new ArrayList<>();

    public FakePhysicsBackend(@Nonnull String id) {
        this(new BackendId(id));
    }

    public FakePhysicsBackend(@Nonnull BackendId id) {
        this.id = id;
    }

    @Nonnull
    @Override
    public BackendId getId() {
        return id;
    }

    @Override
    public void init() {
    }

    @Nonnull
    @Override
    public PhysicsSpace createSpace() {
        return createSpace(SpaceId.next());
    }

    @Nonnull
    @Override
    public PhysicsSpace createSpace(@Nonnull SpaceId spaceId) {
        InMemoryPhysicsSpace space = new InMemoryPhysicsSpace(spaceId, id);
        createdSpaces.add(space);
        return space;
    }

    @Nonnull
    public List<InMemoryPhysicsSpace> createdSpaces() {
        return List.copyOf(createdSpaces);
    }

    public static final class InMemoryPhysicsSpace implements PhysicsSpace,
        PhysicsSolverTuningCapability,
        PhysicsActivationTuningCapability {

        private final SpaceId id;
        private final BackendId backendId;
        private final List<PhysicsBody> bodies = new ArrayList<>();
        private final List<PhysicsJoint> joints = new ArrayList<>();
        private final List<PhysicsContact> contacts = new ArrayList<>();
        private final Vector3f gravity = new Vector3f();
        private boolean closed;
        private int solverIterations;
        private int stabilizationIterations;
        private float sleepLinearThreshold;
        private float sleepAngularThreshold;
        private float sleepTimeUntilSleep;

        private InMemoryPhysicsSpace(@Nonnull SpaceId id, @Nonnull BackendId backendId) {
            this.id = id;
            this.backendId = backendId;
        }

        @Nonnull
        @Override
        public SpaceId id() {
            return id;
        }

        @Nonnull
        @Override
        public BackendId backendId() {
            return backendId;
        }

        @Override
        public void step(float dt) {
        }

        @Override
        public void setGravity(float x, float y, float z) {
            gravity.set(x, y, z);
        }

        @Nonnull
        @Override
        public Vector3f getGravity() {
            return new Vector3f(gravity);
        }

        @Override
        public void addBody(@Nonnull PhysicsBody body) {
            bodies.add(body);
        }

        @Override
        public void removeBody(@Nonnull PhysicsBody body) {
            bodies.remove(body);
        }

        @Nonnull
        @Override
        public List<PhysicsBody> getBodies() {
            return new ArrayList<>(bodies);
        }

        @Override
        public int bodyCount() {
            return bodies.size();
        }

        @Nonnull
        @Override
        public <T extends PhysicsCapability> Optional<T> getCapability(@Nonnull Class<T> type) {
            Objects.requireNonNull(type, "type");
            if (type == PhysicsSolverTuningCapability.class || type == PhysicsActivationTuningCapability.class) {
                return Optional.of(type.cast(this));
            }
            return Optional.empty();
        }

        @Nonnull
        @Override
        public List<PhysicsCapabilityDescriptor> getCapabilityDescriptors() {
            return List.of(PhysicsSolverTuningCapability.DESCRIPTOR, PhysicsActivationTuningCapability.DESCRIPTOR);
        }

        @Nonnull
        @Override
        public PhysicsBody createStaticPlane(float groundY) {
            InMemoryPhysicsBody body = new InMemoryPhysicsBody(ShapeType.PLANE, PhysicsBodyType.STATIC);
            body.position.y = groundY;
            return body;
        }

        @Nonnull
        @Override
        public PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass) {
            InMemoryPhysicsBody body = new InMemoryPhysicsBody(ShapeType.BOX, PhysicsBodyType.DYNAMIC);
            body.halfExtents.set(halfX, halfY, halfZ);
            body.mass = mass;
            return body;
        }

        @Nonnull
        @Override
        public PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass) {
            return createBox(halfExtents.x, halfExtents.y, halfExtents.z, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createSphere(float radius, float mass) {
            InMemoryPhysicsBody body = new InMemoryPhysicsBody(ShapeType.SPHERE, PhysicsBodyType.DYNAMIC);
            body.radius = radius;
            body.mass = mass;
            return body;
        }

        @Nonnull
        @Override
        public PhysicsBody createCapsule(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            return createRoundHeightBody(ShapeType.CAPSULE, radius, halfHeight, axis, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createCylinder(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            return createRoundHeightBody(ShapeType.CYLINDER, radius, halfHeight, axis, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createCone(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            return createRoundHeightBody(ShapeType.CONE, radius, halfHeight, axis, mass);
        }

        @Nonnull
        @Override
        public Optional<PhysicsRayHit> raycastClosest(@Nonnull Vector3f from, @Nonnull Vector3f to) {
            return Optional.empty();
        }

        @Nonnull
        @Override
        public List<PhysicsRayHit> raycastAll(@Nonnull Vector3f from, @Nonnull Vector3f to) {
            return List.of();
        }

        @Nonnull
        @Override
        public List<PhysicsContact> getContacts() {
            return new ArrayList<>(contacts);
        }

        public void addContact(@Nonnull PhysicsContact contact) {
            contacts.add(contact);
        }

        @Nonnull
        @Override
        public PhysicsJoint createFixedJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB) {
            return addJoint(PhysicsJointType.FIXED, bodyA, bodyB, anchorA, anchorB, null);
        }

        @Nonnull
        @Override
        public PhysicsJoint createPointJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB) {
            return addJoint(PhysicsJointType.POINT, bodyA, bodyB, anchorA, anchorB, null);
        }

        @Nonnull
        @Override
        public PhysicsJoint createHingeJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            @Nonnull Vector3f axis) {
            return addJoint(PhysicsJointType.HINGE, bodyA, bodyB, anchorA, anchorB, axis);
        }

        @Nonnull
        @Override
        public PhysicsJoint createSliderJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            @Nonnull Vector3f axis) {
            return addJoint(PhysicsJointType.SLIDER, bodyA, bodyB, anchorA, anchorB, axis);
        }

        @Nonnull
        @Override
        public PhysicsJoint createSpringJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            float restLength,
            float stiffness,
            float damping) {
            return addJoint(PhysicsJointType.SPRING, bodyA, bodyB, anchorA, anchorB, null);
        }

        @Override
        public void removeJoint(@Nonnull PhysicsJoint joint) {
            joints.remove(joint);
        }

        @Nonnull
        @Override
        public List<PhysicsJoint> getJoints() {
            return new ArrayList<>(joints);
        }

        @Override
        public int jointCount() {
            return joints.size();
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public void setSolverTuning(@Nonnull PhysicsSolverTuning tuning) {
            Objects.requireNonNull(tuning, "tuning");
            solverIterations = tuning.solverIterations();
            stabilizationIterations = tuning.stabilizationIterations();
        }

        @Override
        public void setActivationTuning(@Nonnull PhysicsActivationTuning tuning) {
            Objects.requireNonNull(tuning, "tuning");
            sleepLinearThreshold = tuning.linearSleepThreshold();
            sleepAngularThreshold = tuning.angularSleepThreshold();
            sleepTimeUntilSleep = tuning.timeUntilSleep();
        }

        public boolean isClosed() {
            return closed;
        }

        public int getSolverIterations() {
            return solverIterations;
        }

        public int getStabilizationIterations() {
            return stabilizationIterations;
        }

        public float getSleepLinearThreshold() {
            return sleepLinearThreshold;
        }

        public float getSleepAngularThreshold() {
            return sleepAngularThreshold;
        }

        public float getSleepTimeUntilSleep() {
            return sleepTimeUntilSleep;
        }

        @Nonnull
        private PhysicsBody createRoundHeightBody(@Nonnull ShapeType shapeType,
            float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            InMemoryPhysicsBody body = new InMemoryPhysicsBody(shapeType, PhysicsBodyType.DYNAMIC);
            body.radius = radius;
            body.halfHeight = halfHeight;
            body.axis = axis;
            body.mass = mass;
            return body;
        }

        @Nonnull
        private PhysicsJoint addJoint(@Nonnull PhysicsJointType type,
            @Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            @Nullable Vector3f axis) {
            PhysicsJoint joint = new InMemoryPhysicsJoint(type, bodyA, bodyB, anchorA, anchorB, axis);
            joints.add(joint);
            return joint;
        }
    }

    private static final class InMemoryPhysicsBody implements PhysicsBody {

        private final ShapeType shapeType;
        private final Vector3f position = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f linearVelocity = new Vector3f();
        private final Vector3f angularVelocity = new Vector3f();
        private final Vector3f halfExtents = new Vector3f();
        private PhysicsBodyType bodyType;
        private PhysicsAxis axis = PhysicsAxis.Y;
        private float mass = 1.0f;
        private float radius;
        private float halfHeight;
        private float restitution;
        private float friction;
        private float linearDamping;
        private float angularDamping;
        private boolean sensor;
        private boolean continuousCollision;
        private int collisionGroup;
        private int collisionMask;

        private InMemoryPhysicsBody(@Nonnull ShapeType shapeType, @Nonnull PhysicsBodyType bodyType) {
            this.shapeType = shapeType;
            this.bodyType = bodyType;
        }

        @Override
        public void setPosition(float x, float y, float z) {
            position.set(x, y, z);
        }

        @Override
        public void setPosition(@Nonnull Vector3f pos) {
            position.set(pos);
        }

        @Nonnull
        @Override
        public Vector3f getPosition() {
            return new Vector3f(position);
        }

        @Override
        public void setRotation(float x, float y, float z, float w) {
            rotation.set(x, y, z, w);
        }

        @Override
        public void setRotation(@Nonnull Quaternionf rot) {
            rotation.set(rot);
        }

        @Nonnull
        @Override
        public Quaternionf getRotation() {
            return new Quaternionf(rotation);
        }

        @Override
        public void setRestitution(float restitution) {
            this.restitution = restitution;
        }

        @Override
        public float getRestitution() {
            return restitution;
        }

        @Override
        public void setFriction(float friction) {
            this.friction = friction;
        }

        @Override
        public float getFriction() {
            return friction;
        }

        @Nonnull
        @Override
        public PhysicsBodyType getBodyType() {
            return bodyType;
        }

        @Override
        public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
            this.bodyType = bodyType;
        }

        @Override
        public boolean isStatic() {
            return bodyType == PhysicsBodyType.STATIC;
        }

        @Override
        public boolean isKinematic() {
            return bodyType == PhysicsBodyType.KINEMATIC;
        }

        @Override
        public void setKinematic(boolean kinematic) {
            bodyType = kinematic ? PhysicsBodyType.KINEMATIC : PhysicsBodyType.DYNAMIC;
        }

        @Override
        public void activate() {
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public boolean isSleeping() {
            return false;
        }

        @Override
        public void sleep() {
        }

        @Override
        public float getMass() {
            return mass;
        }

        @Override
        public void setMass(float mass) {
            this.mass = mass;
        }

        @Nonnull
        @Override
        public Vector3f getLinearVelocity() {
            return new Vector3f(linearVelocity);
        }

        @Override
        public void setLinearVelocity(@Nonnull Vector3f vel) {
            linearVelocity.set(vel);
        }

        @Override
        public void setLinearVelocity(float x, float y, float z) {
            linearVelocity.set(x, y, z);
        }

        @Nonnull
        @Override
        public Vector3f getAngularVelocity() {
            return new Vector3f(angularVelocity);
        }

        @Override
        public void setAngularVelocity(@Nonnull Vector3f vel) {
            angularVelocity.set(vel);
        }

        @Override
        public void setAngularVelocity(float x, float y, float z) {
            angularVelocity.set(x, y, z);
        }

        @Override
        public float getLinearDamping() {
            return linearDamping;
        }

        @Override
        public void setLinearDamping(float damping) {
            linearDamping = damping;
        }

        @Override
        public float getAngularDamping() {
            return angularDamping;
        }

        @Override
        public void setAngularDamping(float damping) {
            angularDamping = damping;
        }

        @Override
        public void applyCentralForce(@Nonnull Vector3f force) {
        }

        @Override
        public void applyCentralForce(float x, float y, float z) {
        }

        @Override
        public void applyForce(@Nonnull Vector3f force, @Nonnull Vector3f offset) {
        }

        @Override
        public void applyCentralImpulse(@Nonnull Vector3f impulse) {
        }

        @Override
        public void applyCentralImpulse(float x, float y, float z) {
        }

        @Override
        public void applyImpulse(@Nonnull Vector3f impulse, @Nonnull Vector3f offset) {
        }

        @Override
        public void applyTorque(@Nonnull Vector3f torque) {
        }

        @Override
        public void applyTorqueImpulse(@Nonnull Vector3f torqueImpulse) {
        }

        @Override
        public void clearForces() {
        }

        @Override
        public boolean isSensor() {
            return sensor;
        }

        @Override
        public void setSensor(boolean sensor) {
            this.sensor = sensor;
        }

        @Override
        public int getCollisionGroup() {
            return collisionGroup;
        }

        @Override
        public int getCollisionMask() {
            return collisionMask;
        }

        @Override
        public void setCollisionFilter(int group, int mask) {
            collisionGroup = group;
            collisionMask = mask;
        }

        @Override
        public boolean isContinuousCollisionEnabled() {
            return continuousCollision;
        }

        @Override
        public void setContinuousCollisionEnabled(boolean enabled) {
            continuousCollision = enabled;
        }

        @Nonnull
        @Override
        public ShapeType getShapeType() {
            return shapeType;
        }

        @Nonnull
        @Override
        public Vector3f getBoxHalfExtents() {
            return new Vector3f(halfExtents);
        }

        @Override
        public float getSphereRadius() {
            return radius;
        }

        @Override
        public float getHalfHeight() {
            return halfHeight;
        }

        @Nonnull
        @Override
        public PhysicsAxis getShapeAxis() {
            return axis;
        }

        @Override
        public float getCenterOfMassOffsetY() {
            return 0.0f;
        }
    }

    private static final class InMemoryPhysicsJoint implements PhysicsJoint {

        private final PhysicsJointType type;
        private final PhysicsBody bodyA;
        private final PhysicsBody bodyB;
        private final Vector3f anchorA;
        private final Vector3f anchorB;
        @Nullable
        private final Vector3f axis;
        private boolean enabled = true;
        private float lowerLimit;
        private float upperLimit;
        private boolean motorEnabled;
        private float motorTargetVelocity;
        private float motorMaxForce;

        private InMemoryPhysicsJoint(@Nonnull PhysicsJointType type,
            @Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            @Nullable Vector3f axis) {
            this.type = type;
            this.bodyA = bodyA;
            this.bodyB = bodyB;
            this.anchorA = new Vector3f(anchorA);
            this.anchorB = new Vector3f(anchorB);
            this.axis = axis != null ? new Vector3f(axis) : null;
        }

        @Nonnull
        @Override
        public PhysicsJointType getType() {
            return type;
        }

        @Nonnull
        @Override
        public PhysicsBody getBodyA() {
            return bodyA;
        }

        @Nonnull
        @Override
        public PhysicsBody getBodyB() {
            return bodyB;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Nonnull
        @Override
        public Vector3f getAnchorA() {
            return new Vector3f(anchorA);
        }

        @Nonnull
        @Override
        public Vector3f getAnchorB() {
            return new Vector3f(anchorB);
        }

        @Nullable
        @Override
        public Vector3f getAxis() {
            return axis != null ? new Vector3f(axis) : null;
        }

        @Override
        public float getLowerLimit() {
            return lowerLimit;
        }

        @Override
        public float getUpperLimit() {
            return upperLimit;
        }

        @Override
        public void setLimits(float lowerLimit, float upperLimit) {
            this.lowerLimit = lowerLimit;
            this.upperLimit = upperLimit;
        }

        @Override
        public boolean isMotorEnabled() {
            return motorEnabled;
        }

        @Override
        public void setMotorEnabled(boolean enabled) {
            motorEnabled = enabled;
        }

        @Override
        public float getMotorTargetVelocity() {
            return motorTargetVelocity;
        }

        @Override
        public float getMotorMaxForce() {
            return motorMaxForce;
        }

        @Override
        public void setMotor(float targetVelocity, float maxForce) {
            motorTargetVelocity = targetVelocity;
            motorMaxForce = maxForce;
        }
    }
}
