package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public interface PhysicsBody {

    void setPosition(float x, float y, float z);

    void setPosition(@Nonnull Vector3f pos);

    @Nonnull
    Vector3f getPosition();

    void setRotation(float x, float y, float z, float w);

    void setRotation(@Nonnull Quaternionf rot);

    @Nonnull
    Quaternionf getRotation();

    void setRestitution(float restitution);

    void setFriction(float friction);

    boolean isStatic();

    boolean isKinematic();

    void setKinematic(boolean kinematic);

    void activate();

    @Nonnull
    Vector3f getLinearVelocity();

    void setLinearVelocity(@Nonnull Vector3f vel);

    void setLinearVelocity(float x, float y, float z);

    @Nonnull
    ShapeType getShapeType();

    @Nullable
    Vector3f getBoxHalfExtents();

    float getSphereRadius();

    float getCenterOfMassOffsetY();
}
