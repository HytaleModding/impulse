package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable copy of body state captured on the physics owner.
 *
 * <p>Snapshots deliberately contain shape metadata instead of a live {@link PhysicsBody} handle so
 * they can be published to world-thread readers and debug systems without escaping backend
 * ownership.</p>
 */
public record PhysicsBodySnapshot(@Nonnull Vector3f position,
                                  @Nonnull Quaternionf rotation,
                                  @Nonnull Vector3f linearVelocity,
                                  @Nonnull Vector3f angularVelocity,
                                  @Nonnull PhysicsBodyType bodyType,
                                  boolean sleeping,
                                  boolean sensor,
                                  float centerOfMassOffsetY,
                                  @Nonnull ShapeType shapeType,
                                  @Nullable Vector3f boxHalfExtents,
                                  float sphereRadius,
                                  float halfHeight,
                                  @Nonnull PhysicsAxis shapeAxis,
                                  float planeGroundY) {

    public PhysicsBodySnapshot {
        position = new Vector3f(position);
        rotation = new Quaternionf(rotation);
        linearVelocity = new Vector3f(linearVelocity);
        angularVelocity = new Vector3f(angularVelocity);
        boxHalfExtents = boxHalfExtents != null ? new Vector3f(boxHalfExtents) : null;
    }

    @Nonnull
    public static PhysicsBodySnapshot from(@Nonnull PhysicsBody body) {
        return from(body, null);
    }

    @Nonnull
    public static PhysicsBodySnapshot from(@Nonnull PhysicsBody body,
        @Nullable PhysicsBodySnapshot previous) {
        boolean sleeping = body.isSleeping();
        if (sleeping && previous != null && previous.sleeping()) {
            return previous;
        }

        Vector3f position = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        Vector3f linearVelocity = new Vector3f();
        Vector3f angularVelocity = new Vector3f();
        body.getPosition(position);
        body.getRotation(rotation);
        PhysicsBodyType bodyType = body.getBodyType();
        if (!sleeping && bodyType != PhysicsBodyType.STATIC) {
            body.getLinearVelocity(linearVelocity);
            body.getAngularVelocity(angularVelocity);
        }
        return new PhysicsBodySnapshot(position,
            rotation,
            linearVelocity,
            angularVelocity,
            bodyType,
            sleeping,
            body.isSensor(),
            body.getCenterOfMassOffsetY(),
            body.getShapeType(),
            body.getBoxHalfExtents(),
            body.getSphereRadius(),
            body.getHalfHeight(),
            body.getShapeAxis(),
            body.getPlaneGroundY());
    }

    @Nonnull
    @Override
    public Vector3f position() {
        return new Vector3f(position);
    }

    @Nonnull
    @Override
    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    @Nonnull
    @Override
    public Vector3f linearVelocity() {
        return new Vector3f(linearVelocity);
    }

    @Nonnull
    @Override
    public Vector3f angularVelocity() {
        return new Vector3f(angularVelocity);
    }

    @Nullable
    @Override
    public Vector3f boxHalfExtents() {
        return boxHalfExtents != null ? new Vector3f(boxHalfExtents) : null;
    }

    public boolean isStatic() {
        return bodyType == PhysicsBodyType.STATIC;
    }

    public boolean isDynamic() {
        return bodyType == PhysicsBodyType.DYNAMIC;
    }

    public boolean isKinematic() {
        return bodyType == PhysicsBodyType.KINEMATIC;
    }
}
