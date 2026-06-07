package dev.hytalemodding.impulse.api.runtime;

/**
 * Primitive body snapshot callback keyed by backend-local body id.
 */
@FunctionalInterface
public interface BackendBodySnapshotSink {

    void accept(long bodyId,
        int shapeTypeCode,
        int bodyTypeCode,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        float linearVelocityX,
        float linearVelocityY,
        float linearVelocityZ,
        float angularVelocityX,
        float angularVelocityY,
        float angularVelocityZ,
        boolean sleeping,
        boolean sensor,
        float mass,
        float friction,
        float restitution,
        float linearDamping,
        float angularDamping,
        int collisionGroup,
        int collisionMask,
        boolean continuousCollisionEnabled,
        float centerOfMassOffsetY,
        boolean hasBoxHalfExtents,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        int axisCode);
}
