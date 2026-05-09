package dev.hytalemodding.impulse.api;

import com.jme3.bullet.objects.PhysicsRigidBody;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Wraps a Bullet {@link PhysicsRigidBody} and provides position/rotation readback.
 */
public final class ImpulseBody
{
    private final PhysicsRigidBody body;

    ImpulseBody(@Nonnull PhysicsRigidBody body)
    {
        this.body = body;
    }

    /**
     * Set the world-space position of this body.
     */
    public void setPosition(float x, float y, float z)
    {
        body.setPhysicsLocation(new com.jme3.math.Vector3f(x, y, z));
    }

    /**
     * Set the world-space position of this body.
     */
    public void setPosition(@Nonnull Vector3f pos)
    {
        body.setPhysicsLocation(new com.jme3.math.Vector3f(pos.x, pos.y, pos.z));
    }

    /**
     * Set the world-space rotation of this body (quaternion: x, y, z, w).
     */
    public void setRotation(float x, float y, float z, float w)
    {
        body.setPhysicsRotation(new com.jme3.math.Quaternion(x, y, z, w));
    }

    /**
     * Set the world-space rotation of this body.
     */
    public void setRotation(@Nonnull Quaternionf rot)
    {
        body.setPhysicsRotation(new com.jme3.math.Quaternion(rot.x, rot.y, rot.z, rot.w));
    }

    /**
     * Get the world-space position.
     */
    @Nonnull
    public Vector3f getPosition()
    {
        com.jme3.math.Vector3f pos = body.getPhysicsLocation(new com.jme3.math.Vector3f());
        return new Vector3f(pos.x, pos.y, pos.z);
    }

    /**
     * Get the world-space rotation as a quaternion.
     */
    @Nonnull
    public Quaternionf getRotation()
    {
        com.jme3.math.Quaternion rot = body.getPhysicsRotation(new com.jme3.math.Quaternion());
        return new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
    }

    /**
     * Set the restitution (bounciness) of this body.
     */
    public void setRestitution(float restitution)
    {
        body.setRestitution(restitution);
    }

    /**
     * Set the friction of this body.
     */
    public void setFriction(float friction)
    {
        body.setFriction(friction);
    }

    /**
     * Set whether this body is kinematic (driven by code, not by physics).
     */
    public void setKinematic(boolean kinematic)
    {
        body.setKinematic(kinematic);
    }

    /**
     * Check if this body is static (mass = 0).
     */
    public boolean isStatic()
    {
        return body.isStatic();
    }

    @Nonnull
    PhysicsRigidBody getRigidBody()
    {
        return body;
    }
}
