package dev.hytalemodding.impulse.rapier;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class RapierBody implements PhysicsBody {

    private static final float DEFAULT_DYNAMIC_MASS = 1.0f;

    private final ShapeType shapeType;
    private final Vector3f boxHalfExtents;
    private final float sphereRadius;
    private final float halfHeight;
    private final PhysicsAxis axis;
    private final float centerOfMassOffsetY;
    private final Vector3f voxelSize;
    private final int[] voxelCoordinates;

    private final Vector3f position = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f linearVelocity = new Vector3f();
    private final Vector3f angularVelocity = new Vector3f();
    private final float[] vec3Scratch = new float[3];
    private final float[] quatScratch = new float[4];
    private final float[] dampingScratch = new float[2];
    private final int[] collisionFilterScratch = new int[2];

    private float mass;
    private float friction = 0.5f;
    private float restitution;
    private float linearDamping;
    private float angularDamping;
    private PhysicsBodyType bodyType;
    private boolean sensor;
    private int collisionGroup = 1;
    private int collisionMask = 1;
    private boolean continuousCollisionEnabled;

    private RapierSpace space;
    private long bodyHandle;

    private RapierBody(@Nonnull ShapeType shapeType,
        @Nullable Vector3f boxHalfExtents,
        float sphereRadius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float centerOfMassOffsetY,
        @Nullable Vector3f voxelSize,
        @Nullable int[] voxelCoordinates,
        float mass) {
        this.shapeType = shapeType;
        this.boxHalfExtents = boxHalfExtents != null ? new Vector3f(boxHalfExtents) : null;
        this.sphereRadius = sphereRadius;
        this.halfHeight = halfHeight;
        this.axis = axis;
        this.centerOfMassOffsetY = centerOfMassOffsetY;
        this.voxelSize = voxelSize != null ? new Vector3f(voxelSize) : null;
        this.voxelCoordinates = voxelCoordinates != null ? voxelCoordinates.clone() : null;
        this.mass = mass;
        this.bodyType = mass <= 0f ? PhysicsBodyType.STATIC : PhysicsBodyType.DYNAMIC;
    }

    @Nonnull
    static RapierBody box(float halfX, float halfY, float halfZ, float mass) {
        return new RapierBody(ShapeType.BOX, new Vector3f(halfX, halfY, halfZ), -1f,
            -1f, PhysicsAxis.Y, halfY, null, null, mass);
    }

    @Nonnull
    static RapierBody sphere(float radius, float mass) {
        return new RapierBody(ShapeType.SPHERE, null, radius, -1f, PhysicsAxis.Y,
            radius, null, null, mass);
    }

    @Nonnull
    static RapierBody capsule(float radius, float halfHeight, @Nonnull PhysicsAxis axis, float mass) {
        float offsetY = axis == PhysicsAxis.Y ? halfHeight + radius : radius;
        return new RapierBody(ShapeType.CAPSULE, null, radius, halfHeight, axis, offsetY,
            null, null, mass);
    }

    @Nonnull
    static RapierBody cylinder(float radius, float halfHeight, @Nonnull PhysicsAxis axis, float mass) {
        float offsetY = axis == PhysicsAxis.Y ? halfHeight : radius;
        return new RapierBody(ShapeType.CYLINDER, null, radius, halfHeight, axis, offsetY,
            null, null, mass);
    }

    @Nonnull
    static RapierBody cone(float radius, float halfHeight, @Nonnull PhysicsAxis axis, float mass) {
        float offsetY = axis == PhysicsAxis.Y ? halfHeight : radius;
        return new RapierBody(ShapeType.CONE, null, radius, halfHeight, axis, offsetY,
            null, null, mass);
    }

    @Nonnull
    static RapierBody staticPlane(float groundY) {
        RapierBody body = new RapierBody(ShapeType.PLANE, null, -1f, -1f, PhysicsAxis.Y,
            0f, null, null, 0f);
        body.position.set(0f, groundY, 0f);
        return body;
    }

    @Nonnull
    static RapierBody voxelTerrain(float voxelSizeX,
        float voxelSizeY,
        float voxelSizeZ,
        @Nonnull int[] voxelCoordinates) {
        return new RapierBody(ShapeType.VOXELS, null, -1f, -1f, PhysicsAxis.Y,
            0f, new Vector3f(voxelSizeX, voxelSizeY, voxelSizeZ), voxelCoordinates, 0f);
    }

    @Override
    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        if (isAttached()) {
            RapierNative.setBodyPositionNative(getSpaceHandle(), bodyHandle, x, y, z);
        }
    }

    @Override
    public void setPosition(@Nonnull Vector3f pos) {
        setPosition(pos.x, pos.y, pos.z);
    }

    @Nonnull
    @Override
    public Vector3f getPosition() {
        Vector3f out = new Vector3f();
        getPosition(out);
        return out;
    }

    @Override
    public void getPosition(@Nonnull Vector3f out) {
        if (isAttached()) {
            RapierNative.getBodyPositionNative(getSpaceHandle(), bodyHandle, vec3Scratch);
            position.set(vec3Scratch[0], vec3Scratch[1], vec3Scratch[2]);
        }
        out.set(position);
    }

    @Override
    public void setRotation(float x, float y, float z, float w) {
        setStoredRotation(x, y, z, w);
        if (isAttached()) {
            RapierNative.setBodyRotationNative(getSpaceHandle(), bodyHandle,
                rotation.x, rotation.y, rotation.z, rotation.w);
        }
    }

    @Override
    public void setRotation(@Nonnull Quaternionf rot) {
        setRotation(rot.x, rot.y, rot.z, rot.w);
    }

    @Nonnull
    @Override
    public Quaternionf getRotation() {
        Quaternionf out = new Quaternionf();
        getRotation(out);
        return out;
    }

    @Override
    public void getRotation(@Nonnull Quaternionf out) {
        if (isAttached()) {
            RapierNative.getBodyRotationNative(getSpaceHandle(), bodyHandle, quatScratch);
            setStoredRotation(quatScratch[0], quatScratch[1], quatScratch[2], quatScratch[3]);
        }
        out.set(rotation);
    }

    @Override
    public void setRestitution(float restitution) {
        this.restitution = restitution;
        if (isAttached()) {
            RapierNative.setBodyRestitutionNative(getSpaceHandle(), bodyHandle, restitution);
        }
    }

    @Override
    public float getRestitution() {
        return restitution;
    }

    @Override
    public void setFriction(float friction) {
        this.friction = friction;
        if (isAttached()) {
            RapierNative.setBodyFrictionNative(getSpaceHandle(), bodyHandle, friction);
        }
    }

    @Override
    public float getFriction() {
        return friction;
    }

    @Nonnull
    @Override
    public PhysicsBodyType getBodyType() {
        if (isAttached()) {
            bodyType = PhysicsBodyType.values()[RapierNative.getBodyTypeNative(getSpaceHandle(), bodyHandle)];
        }
        return bodyType;
    }

    @Override
    public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
        if (bodyType == PhysicsBodyType.STATIC) {
            if (mass != 0f) {
                setMass(0f);
            }
        } else if (mass <= 0f) {
            setMass(DEFAULT_DYNAMIC_MASS);
        }
        this.bodyType = bodyType;
        if (isAttached()) {
            RapierNative.setBodyTypeNative(getSpaceHandle(), bodyHandle, bodyType.ordinal());
        }
    }

    @Override
    public boolean isStatic() {
        return getBodyType() == PhysicsBodyType.STATIC;
    }

    @Override
    public boolean isKinematic() {
        return getBodyType() == PhysicsBodyType.KINEMATIC;
    }

    @Override
    public void setKinematic(boolean kinematic) {
        if (kinematic) {
            setBodyType(PhysicsBodyType.KINEMATIC);
        } else if (!isStatic()) {
            setBodyType(PhysicsBodyType.DYNAMIC);
        }
    }

    @Override
    public void activate() {
        if (isAttached()) {
            RapierNative.activateBodyNative(getSpaceHandle(), bodyHandle);
        }
    }

    @Override
    public boolean isActive() {
        return !isSleeping();
    }

    @Override
    public boolean isSleeping() {
        return isAttached() && RapierNative.isBodySleepingNative(getSpaceHandle(), bodyHandle);
    }

    @Override
    public void sleep() {
        if (isAttached()) {
            RapierNative.sleepBodyNative(getSpaceHandle(), bodyHandle);
        }
    }

    @Override
    public float getMass() {
        if (isAttached()) {
            mass = RapierNative.getBodyMassNative(getSpaceHandle(), bodyHandle);
        }
        return mass;
    }

    @Override
    public void setMass(float mass) {
        this.mass = mass;
        if (mass <= 0f) {
            bodyType = PhysicsBodyType.STATIC;
        }
        if (isAttached()) {
            RapierNative.setBodyMassNative(getSpaceHandle(), bodyHandle, mass);
        }
    }

    @Nonnull
    @Override
    public Vector3f getLinearVelocity() {
        Vector3f out = new Vector3f();
        getLinearVelocity(out);
        return out;
    }

    @Override
    public void getLinearVelocity(@Nonnull Vector3f out) {
        if (isAttached()) {
            RapierNative.getBodyLinearVelocityNative(getSpaceHandle(), bodyHandle, vec3Scratch);
            linearVelocity.set(vec3Scratch[0], vec3Scratch[1], vec3Scratch[2]);
        }
        out.set(linearVelocity);
    }

    @Override
    public void setLinearVelocity(@Nonnull Vector3f vel) {
        setLinearVelocity(vel.x, vel.y, vel.z);
    }

    @Override
    public void setLinearVelocity(float x, float y, float z) {
        linearVelocity.set(x, y, z);
        if (isAttached()) {
            RapierNative.setBodyLinearVelocityNative(getSpaceHandle(), bodyHandle, x, y, z);
        }
    }

    @Nonnull
    @Override
    public Vector3f getAngularVelocity() {
        Vector3f out = new Vector3f();
        getAngularVelocity(out);
        return out;
    }

    @Override
    public void getAngularVelocity(@Nonnull Vector3f out) {
        if (isAttached()) {
            RapierNative.getBodyAngularVelocityNative(getSpaceHandle(), bodyHandle, vec3Scratch);
            angularVelocity.set(vec3Scratch[0], vec3Scratch[1], vec3Scratch[2]);
        }
        out.set(angularVelocity);
    }

    @Override
    public void setAngularVelocity(@Nonnull Vector3f vel) {
        setAngularVelocity(vel.x, vel.y, vel.z);
    }

    @Override
    public void setAngularVelocity(float x, float y, float z) {
        angularVelocity.set(x, y, z);
        if (isAttached()) {
            RapierNative.setBodyAngularVelocityNative(getSpaceHandle(), bodyHandle, x, y, z);
        }
    }

    @Override
    public float getLinearDamping() {
        refreshDamping();
        return linearDamping;
    }

    @Override
    public void setLinearDamping(float damping) {
        linearDamping = damping;
        pushDamping();
    }

    @Override
    public float getAngularDamping() {
        refreshDamping();
        return angularDamping;
    }

    @Override
    public void setAngularDamping(float damping) {
        angularDamping = damping;
        pushDamping();
    }

    @Override
    public void applyCentralForce(@Nonnull Vector3f force) {
        applyCentralForce(force.x, force.y, force.z);
    }

    @Override
    public void applyCentralForce(float x, float y, float z) {
        if (isAttached()) {
            RapierNative.applyBodyCentralForceNative(getSpaceHandle(), bodyHandle, x, y, z);
        }
    }

    @Override
    public void applyForce(@Nonnull Vector3f force, @Nonnull Vector3f offset) {
        applyForce(force.x, force.y, force.z, offset.x, offset.y, offset.z);
    }

    @Override
    public void applyForce(float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        if (isAttached()) {
            RapierNative.applyBodyForceNative(getSpaceHandle(), bodyHandle,
                x, y, z, offsetX, offsetY, offsetZ);
        }
    }

    @Override
    public void applyCentralImpulse(@Nonnull Vector3f impulse) {
        applyCentralImpulse(impulse.x, impulse.y, impulse.z);
    }

    @Override
    public void applyCentralImpulse(float x, float y, float z) {
        if (isAttached()) {
            RapierNative.applyBodyCentralImpulseNative(getSpaceHandle(), bodyHandle, x, y, z);
        }
    }

    @Override
    public void applyImpulse(@Nonnull Vector3f impulse, @Nonnull Vector3f offset) {
        applyImpulse(impulse.x, impulse.y, impulse.z, offset.x, offset.y, offset.z);
    }

    @Override
    public void applyImpulse(float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        if (isAttached()) {
            RapierNative.applyBodyImpulseNative(getSpaceHandle(), bodyHandle,
                x, y, z, offsetX, offsetY, offsetZ);
        }
    }

    @Override
    public void applyTorque(@Nonnull Vector3f torque) {
        applyTorque(torque.x, torque.y, torque.z);
    }

    @Override
    public void applyTorque(float x, float y, float z) {
        if (isAttached()) {
            RapierNative.applyBodyTorqueNative(getSpaceHandle(), bodyHandle,
                x, y, z);
        }
    }

    @Override
    public void applyTorqueImpulse(@Nonnull Vector3f torqueImpulse) {
        applyTorqueImpulse(torqueImpulse.x, torqueImpulse.y, torqueImpulse.z);
    }

    @Override
    public void applyTorqueImpulse(float x, float y, float z) {
        if (isAttached()) {
            RapierNative.applyBodyTorqueImpulseNative(getSpaceHandle(), bodyHandle,
                x, y, z);
        }
    }

    @Override
    public void clearForces() {
        if (isAttached()) {
            RapierNative.clearBodyForcesNative(getSpaceHandle(), bodyHandle);
        }
    }

    @Override
    public boolean isSensor() {
        if (isAttached()) {
            sensor = RapierNative.isBodySensorNative(getSpaceHandle(), bodyHandle);
        }
        return sensor;
    }

    @Override
    public void setSensor(boolean sensor) {
        this.sensor = sensor;
        if (isAttached()) {
            RapierNative.setBodySensorNative(getSpaceHandle(), bodyHandle, sensor);
        }
    }

    @Override
    public int getCollisionGroup() {
        refreshCollisionFilter();
        return collisionGroup;
    }

    @Override
    public int getCollisionMask() {
        refreshCollisionFilter();
        return collisionMask;
    }

    @Override
    public void setCollisionFilter(int group, int mask) {
        collisionGroup = group;
        collisionMask = mask;
        if (isAttached()) {
            RapierNative.setBodyCollisionFilterNative(getSpaceHandle(), bodyHandle, group, mask);
        }
    }

    @Override
    public boolean isContinuousCollisionEnabled() {
        if (isAttached()) {
            continuousCollisionEnabled = RapierNative.isBodyCcdNative(getSpaceHandle(), bodyHandle);
        }
        return continuousCollisionEnabled;
    }

    @Override
    public void setContinuousCollisionEnabled(boolean enabled) {
        continuousCollisionEnabled = enabled;
        if (isAttached()) {
            RapierNative.setBodyCcdNative(getSpaceHandle(), bodyHandle, enabled);
        }
    }

    @Nonnull
    @Override
    public ShapeType getShapeType() {
        return shapeType;
    }

    @Nullable
    @Override
    public Vector3f getBoxHalfExtents() {
        return boxHalfExtents != null ? new Vector3f(boxHalfExtents) : null;
    }

    @Override
    public float getSphereRadius() {
        return sphereRadius;
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
        return centerOfMassOffsetY;
    }

    boolean isAttached() {
        return space != null && bodyHandle != 0L;
    }

    boolean isAttachedTo(@Nonnull RapierSpace owner) {
        return space == owner && bodyHandle != 0L;
    }

    void attach(@Nonnull RapierSpace space, long bodyHandle) {
        if (isAttached()) {
            throw new IllegalStateException("Rapier body is already attached to a space");
        }
        this.space = space;
        this.bodyHandle = bodyHandle;
    }

    void detach(@Nonnull RapierSpace owner) {
        if (space != owner) {
            return;
        }
        space = null;
        bodyHandle = 0L;
    }

    long getBodyHandle() {
        return bodyHandle;
    }

    float getStoredMass() {
        return mass;
    }

    float getStoredFriction() {
        return friction;
    }

    float getStoredRestitution() {
        return restitution;
    }

    float getStoredLinearDamping() {
        return linearDamping;
    }

    float getStoredAngularDamping() {
        return angularDamping;
    }

    boolean getStoredSensor() {
        return sensor;
    }

    int getStoredCollisionGroup() {
        return collisionGroup;
    }

    int getStoredCollisionMask() {
        return collisionMask;
    }

    boolean getStoredContinuousCollisionEnabled() {
        return continuousCollisionEnabled;
    }

    @Nonnull
    PhysicsBodyType getStoredBodyType() {
        return bodyType;
    }

    @Nonnull
    PhysicsBodySnapshot snapshotFromNative(@Nonnull float[] values,
        int offset,
        @Nullable PhysicsBodySnapshot previous) {
        boolean nativeSleeping = values[offset + 14] != 0.0f;
        if (nativeSleeping && previous != null && previous.sleeping()) {
            return previous;
        }

        position.set(values[offset], values[offset + 1], values[offset + 2]);
        setStoredRotation(values[offset + 3], values[offset + 4], values[offset + 5],
            values[offset + 6]);
        linearVelocity.set(values[offset + 7], values[offset + 8], values[offset + 9]);
        angularVelocity.set(values[offset + 10], values[offset + 11], values[offset + 12]);

        int bodyTypeOrdinal = Math.round(values[offset + 13]);
        PhysicsBodyType[] bodyTypes = PhysicsBodyType.values();
        if (bodyTypeOrdinal >= 0 && bodyTypeOrdinal < bodyTypes.length) {
            bodyType = bodyTypes[bodyTypeOrdinal];
        }
        sensor = values[offset + 15] != 0.0f;

        return new PhysicsBodySnapshot(position,
            rotation,
            linearVelocity,
            angularVelocity,
            bodyType,
            nativeSleeping,
            sensor,
            centerOfMassOffsetY,
            shapeType,
            boxHalfExtents,
            sphereRadius,
            halfHeight,
            axis);
    }

    @Nonnull
    Vector3f getStoredPosition() {
        return new Vector3f(position);
    }

    @Nonnull
    Quaternionf getStoredRotation() {
        return new Quaternionf(rotation);
    }

    @Nonnull
    Vector3f getStoredLinearVelocity() {
        return new Vector3f(linearVelocity);
    }

    @Nonnull
    Vector3f getStoredAngularVelocity() {
        return new Vector3f(angularVelocity);
    }

    @Nonnull
    Vector3f getVoxelSize() {
        if (voxelSize == null) {
            throw new IllegalStateException("Body is not a voxel terrain body");
        }
        return new Vector3f(voxelSize);
    }

    @Nonnull
    int[] getVoxelCoordinates() {
        if (voxelCoordinates == null) {
            throw new IllegalStateException("Body is not a voxel terrain body");
        }
        return voxelCoordinates.clone();
    }

    private long getSpaceHandle() {
        return space.getNativeSpaceHandle();
    }

    private void refreshDamping() {
        if (isAttached()) {
            RapierNative.getBodyDampingNative(getSpaceHandle(), bodyHandle, dampingScratch);
            linearDamping = dampingScratch[0];
            angularDamping = dampingScratch[1];
        }
    }

    private void pushDamping() {
        if (isAttached()) {
            RapierNative.setBodyDampingNative(getSpaceHandle(), bodyHandle,
                linearDamping, angularDamping);
        }
    }

    private void refreshCollisionFilter() {
        if (isAttached()) {
            RapierNative.getBodyCollisionFilterNative(getSpaceHandle(), bodyHandle, collisionFilterScratch);
            collisionGroup = collisionFilterScratch[0];
            collisionMask = collisionFilterScratch[1];
        }
    }

    private void setStoredRotation(float x, float y, float z, float w) {
        rotation.set(x, y, z, w);
        if (rotation.lengthSquared() == 0f) {
            rotation.identity();
            return;
        }
        rotation.normalize();
    }
}
