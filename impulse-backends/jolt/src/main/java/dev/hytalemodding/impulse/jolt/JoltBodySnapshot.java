package dev.hytalemodding.impulse.jolt;

import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import javax.annotation.Nonnull;

final class JoltBodySnapshot {

    static final int FLOAT_FIELD_COUNT = 24;
    static final int INT_FIELD_COUNT = 9;

    int shapeTypeCode;
    int bodyTypeCode;
    float positionX;
    float positionY;
    float positionZ;
    float rotationX;
    float rotationY;
    float rotationZ;
    float rotationW;
    float linearVelocityX;
    float linearVelocityY;
    float linearVelocityZ;
    float angularVelocityX;
    float angularVelocityY;
    float angularVelocityZ;
    boolean sleeping;
    boolean sensor;
    float mass;
    float friction;
    float restitution;
    float linearDamping;
    float angularDamping;
    int collisionGroup;
    int collisionMask;
    boolean continuousCollisionEnabled;
    float centerOfMassOffsetY;
    boolean hasBoxHalfExtents;
    float halfExtentX;
    float halfExtentY;
    float halfExtentZ;
    float radius;
    float halfHeight;
    int axisCode;

    void clear() {
        shapeTypeCode = 0;
        bodyTypeCode = 0;
        positionX = 0.0f;
        positionY = 0.0f;
        positionZ = 0.0f;
        rotationX = 0.0f;
        rotationY = 0.0f;
        rotationZ = 0.0f;
        rotationW = 0.0f;
        linearVelocityX = 0.0f;
        linearVelocityY = 0.0f;
        linearVelocityZ = 0.0f;
        angularVelocityX = 0.0f;
        angularVelocityY = 0.0f;
        angularVelocityZ = 0.0f;
        sleeping = false;
        sensor = false;
        mass = 0.0f;
        friction = 0.0f;
        restitution = 0.0f;
        linearDamping = 0.0f;
        angularDamping = 0.0f;
        collisionGroup = 0;
        collisionMask = 0;
        continuousCollisionEnabled = false;
        centerOfMassOffsetY = 0.0f;
        hasBoxHalfExtents = false;
        halfExtentX = 0.0f;
        halfExtentY = 0.0f;
        halfExtentZ = 0.0f;
        radius = 0.0f;
        halfHeight = 0.0f;
        axisCode = 0;
    }

    void copyFrom(@Nonnull JoltBodySnapshot other) {
        shapeTypeCode = other.shapeTypeCode;
        bodyTypeCode = other.bodyTypeCode;
        positionX = other.positionX;
        positionY = other.positionY;
        positionZ = other.positionZ;
        rotationX = other.rotationX;
        rotationY = other.rotationY;
        rotationZ = other.rotationZ;
        rotationW = other.rotationW;
        linearVelocityX = other.linearVelocityX;
        linearVelocityY = other.linearVelocityY;
        linearVelocityZ = other.linearVelocityZ;
        angularVelocityX = other.angularVelocityX;
        angularVelocityY = other.angularVelocityY;
        angularVelocityZ = other.angularVelocityZ;
        sleeping = other.sleeping;
        sensor = other.sensor;
        mass = other.mass;
        friction = other.friction;
        restitution = other.restitution;
        linearDamping = other.linearDamping;
        angularDamping = other.angularDamping;
        collisionGroup = other.collisionGroup;
        collisionMask = other.collisionMask;
        continuousCollisionEnabled = other.continuousCollisionEnabled;
        centerOfMassOffsetY = other.centerOfMassOffsetY;
        hasBoxHalfExtents = other.hasBoxHalfExtents;
        halfExtentX = other.halfExtentX;
        halfExtentY = other.halfExtentY;
        halfExtentZ = other.halfExtentZ;
        radius = other.radius;
        halfHeight = other.halfHeight;
        axisCode = other.axisCode;
    }

    void emit(long bodyId, @Nonnull BackendBodySnapshotSink sink) {
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
            hasBoxHalfExtents,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axisCode);
    }
}
