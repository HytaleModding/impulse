package dev.hytalemodding.impulse.core.plugin.simulation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Optional copied properties applied immediately after a rigid body is created.
 */
public final class RigidBodySpawnSettings {

    private static final int FRICTION = 1;
    private static final int RESTITUTION = 1 << 1;
    private static final int LINEAR_DAMPING = 1 << 2;
    private static final int ANGULAR_DAMPING = 1 << 3;
    private static final int COLLISION_FILTER = 1 << 4;
    private static final int SENSOR = 1 << 5;

    private static final RigidBodySpawnSettings DEFAULTS =
        new RigidBodySpawnSettings(0, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0, false);

    private final int flags;
    private final float friction;
    private final float restitution;
    private final float linearDamping;
    private final float angularDamping;
    private final int collisionGroup;
    private final int collisionMask;
    private final boolean sensor;

    private RigidBodySpawnSettings(int flags,
        float friction,
        float restitution,
        float linearDamping,
        float angularDamping,
        int collisionGroup,
        int collisionMask,
        boolean sensor) {
        this.flags = flags;
        this.friction = friction;
        this.restitution = restitution;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
        this.collisionGroup = collisionGroup;
        this.collisionMask = collisionMask;
        this.sensor = sensor;
    }

    @Nonnull
    public static RigidBodySpawnSettings defaults() {
        return DEFAULTS;
    }

    @Nonnull
    public static RigidBodySpawnSettings material(float friction,
        float restitution) {
        validateNonNegativeFinite(friction, "friction");
        validateNonNegativeFinite(restitution, "restitution");
        return new RigidBodySpawnSettings(FRICTION | RESTITUTION,
            friction,
            restitution,
            0.0f,
            0.0f,
            0,
            0,
            false);
    }

    @Nonnull
    public static RigidBodySpawnSettings of(float friction,
        float restitution,
        float linearDamping,
        float angularDamping,
        int collisionGroup,
        int collisionMask) {
        return primitive(friction,
            restitution,
            linearDamping,
            angularDamping,
            collisionGroup,
            collisionMask,
            false,
            false);
    }

    @Nonnull
    public static RigidBodySpawnSettings of(float friction,
        float restitution,
        float linearDamping,
        float angularDamping,
        int collisionGroup,
        int collisionMask,
        boolean sensor) {
        return primitive(friction,
            restitution,
            linearDamping,
            angularDamping,
            collisionGroup,
            collisionMask,
            true,
            sensor);
    }

    @Nonnull
    public static RigidBodySpawnSettings fromOptionalValues(@Nullable Float friction,
        @Nullable Float restitution,
        @Nullable Float linearDamping,
        @Nullable Float angularDamping,
        @Nullable Integer collisionGroup,
        @Nullable Integer collisionMask) {
        return fromOptionalValues(friction,
            restitution,
            linearDamping,
            angularDamping,
            collisionGroup,
            collisionMask,
            null);
    }

    @Nonnull
    public static RigidBodySpawnSettings fromOptionalValues(@Nullable Float friction,
        @Nullable Float restitution,
        @Nullable Float linearDamping,
        @Nullable Float angularDamping,
        @Nullable Integer collisionGroup,
        @Nullable Integer collisionMask,
        @Nullable Boolean sensor) {
        int flags = 0;
        float frictionValue = 0.0f;
        float restitutionValue = 0.0f;
        float linearDampingValue = 0.0f;
        float angularDampingValue = 0.0f;
        int collisionGroupValue = 0;
        int collisionMaskValue = 0;
        boolean sensorValue = false;

        if (friction != null) {
            validateNonNegativeFinite(friction, "friction");
            flags |= FRICTION;
            frictionValue = friction;
        }
        if (restitution != null) {
            validateNonNegativeFinite(restitution, "restitution");
            flags |= RESTITUTION;
            restitutionValue = restitution;
        }
        if (linearDamping != null) {
            validateNonNegativeFinite(linearDamping, "linearDamping");
            flags |= LINEAR_DAMPING;
            linearDampingValue = linearDamping;
        }
        if (angularDamping != null) {
            validateNonNegativeFinite(angularDamping, "angularDamping");
            flags |= ANGULAR_DAMPING;
            angularDampingValue = angularDamping;
        }
        if ((collisionGroup == null) != (collisionMask == null)) {
            throw new IllegalArgumentException("collisionGroup and collisionMask must be set together");
        }
        if (collisionGroup != null && collisionMask != null) {
            flags |= COLLISION_FILTER;
            collisionGroupValue = collisionGroup;
            collisionMaskValue = collisionMask;
        }
        if (sensor != null) {
            flags |= SENSOR;
            sensorValue = sensor;
        }
        return new RigidBodySpawnSettings(flags,
            frictionValue,
            restitutionValue,
            linearDampingValue,
            angularDampingValue,
            collisionGroupValue,
            collisionMaskValue,
            sensorValue);
    }

    @Nonnull
    public RigidBodySpawnSettings withSensor(boolean sensor) {
        return new RigidBodySpawnSettings(flags | SENSOR,
            friction,
            restitution,
            linearDamping,
            angularDamping,
            collisionGroup,
            collisionMask,
            sensor);
    }

    @Nonnull
    public RigidBodySpawnSettings withCollisionFilter(int group,
        int mask) {
        return new RigidBodySpawnSettings(flags | COLLISION_FILTER,
            friction,
            restitution,
            linearDamping,
            angularDamping,
            group,
            mask,
            sensor);
    }

    public boolean hasFriction() {
        return has(FRICTION);
    }

    public float friction() {
        return friction;
    }

    public boolean hasRestitution() {
        return has(RESTITUTION);
    }

    public float restitution() {
        return restitution;
    }

    public boolean hasLinearDamping() {
        return has(LINEAR_DAMPING);
    }

    public float linearDamping() {
        return linearDamping;
    }

    public boolean hasAngularDamping() {
        return has(ANGULAR_DAMPING);
    }

    public float angularDamping() {
        return angularDamping;
    }

    public boolean hasCollisionFilter() {
        return has(COLLISION_FILTER);
    }

    public int collisionGroup() {
        return collisionGroup;
    }

    public int collisionMask() {
        return collisionMask;
    }

    public boolean hasSensor() {
        return has(SENSOR);
    }

    public boolean sensor() {
        return sensor;
    }

    private boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Nonnull
    private static RigidBodySpawnSettings primitive(float friction,
        float restitution,
        float linearDamping,
        float angularDamping,
        int collisionGroup,
        int collisionMask,
        boolean hasSensor,
        boolean sensor) {
        validateNonNegativeFinite(friction, "friction");
        validateNonNegativeFinite(restitution, "restitution");
        validateNonNegativeFinite(linearDamping, "linearDamping");
        validateNonNegativeFinite(angularDamping, "angularDamping");
        int flags = FRICTION | RESTITUTION | LINEAR_DAMPING | ANGULAR_DAMPING | COLLISION_FILTER;
        if (hasSensor) {
            flags |= SENSOR;
        }
        return new RigidBodySpawnSettings(flags,
            friction,
            restitution,
            linearDamping,
            angularDamping,
            collisionGroup,
            collisionMask,
            sensor);
    }

    private static void validateNonNegativeFinite(float value,
        @Nonnull String fieldName) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(fieldName + " must be finite and >= 0");
        }
    }
}
