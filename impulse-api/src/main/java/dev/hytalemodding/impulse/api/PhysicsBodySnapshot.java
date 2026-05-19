package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Owner-thread copy of body state for downstream systems.
 */
public record PhysicsBodySnapshot(@Nonnull PhysicsBody body,
                                  @Nonnull Vector3f position,
                                  @Nonnull Quaternionf rotation,
                                  @Nonnull Vector3f linearVelocity,
                                  @Nonnull Vector3f angularVelocity,
                                  @Nonnull PhysicsBodyType bodyType,
                                  boolean sleeping,
                                  boolean sensor,
                                  float centerOfMassOffsetY) {

    public PhysicsBodySnapshot {
        position = new Vector3f(position);
        rotation = new Quaternionf(rotation);
        linearVelocity = new Vector3f(linearVelocity);
        angularVelocity = new Vector3f(angularVelocity);
    }

    @Nonnull
    public static PhysicsBodySnapshot from(@Nonnull PhysicsBody body) {
        return from(body, null);
    }

    @Nonnull
    public static PhysicsBodySnapshot from(@Nonnull PhysicsBody body,
        @Nullable PhysicsBodySnapshot previous) {
        boolean sleeping = body.isSleeping();
        if (sleeping && previous != null && previous.body() == body && previous.sleeping()) {
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
        return new PhysicsBodySnapshot(body,
            position,
            rotation,
            linearVelocity,
            angularVelocity,
            bodyType,
            sleeping,
            body.isSensor(),
            body.getCenterOfMassOffsetY());
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
