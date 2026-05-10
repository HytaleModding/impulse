package dev.hytalemodding.impulse.api;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.PlaneCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Wraps a Bullet {@link PhysicsRigidBody} and provides position/rotation readback.
 */
public final class ImpulseBody {

    private final PhysicsRigidBody body;

    ImpulseBody(@Nonnull PhysicsRigidBody body) {
        this.body = body;
    }

    /**
     * Set the world-space position of this body.
     */
    public void setPosition(float x, float y, float z) {
        body.setPhysicsLocation(new com.jme3.math.Vector3f(x, y, z));
    }

    /**
     * Set the world-space rotation of this body (quaternion: x, y, z, w).
     */
    public void setRotation(float x, float y, float z, float w) {
        body.setPhysicsRotation(new com.jme3.math.Quaternion(x, y, z, w));
    }

    /**
     * Get the world-space position.
     */
    @Nonnull
    public Vector3f getPosition() {
        com.jme3.math.Vector3f pos = body.getPhysicsLocation(new com.jme3.math.Vector3f());
        return new Vector3f(pos.x, pos.y, pos.z);
    }

    /**
     * Set the world-space position of this body.
     */
    public void setPosition(@Nonnull Vector3f pos) {
        body.setPhysicsLocation(new com.jme3.math.Vector3f(pos.x, pos.y, pos.z));
    }

    /**
     * Get the world-space rotation as a quaternion.
     */
    @Nonnull
    public Quaternionf getRotation() {
        com.jme3.math.Quaternion rot = body.getPhysicsRotation(new com.jme3.math.Quaternion());
        return new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
    }

    /**
     * Set the world-space rotation of this body.
     */
    public void setRotation(@Nonnull Quaternionf rot) {
        body.setPhysicsRotation(new com.jme3.math.Quaternion(rot.x, rot.y, rot.z, rot.w));
    }

    /**
     * Set the restitution (bounciness) of this body.
     */
    public void setRestitution(float restitution) {
        body.setRestitution(restitution);
    }

    /**
     * Set the friction of this body.
     */
    public void setFriction(float friction) {
        body.setFriction(friction);
    }

    /**
     * Check if this body is static (mass = 0).
     */
    public boolean isStatic() {
        return body.isStatic();
    }

    /**
     * Check if this body is kinematic (driven by code, not by physics).
     */
    public boolean isKinematic() {
        return body.isKinematic();
    }

    /**
     * Set whether this body is kinematic (driven by code, not by physics).
     */
    public void setKinematic(boolean kinematic) {
        body.setKinematic(kinematic);
    }

    /**
     * Activate this body so Bullet simulates it.
     */
    public void activate() {
        body.activate();
    }

    @Nonnull
    public Vector3f getLinearVelocity() {
        com.jme3.math.Vector3f vel = body.getLinearVelocity(new com.jme3.math.Vector3f());
        return new Vector3f(vel.x, vel.y, vel.z);
    }

    /**
     * Set the linear velocity.
     */
    public void setLinearVelocity(@Nonnull Vector3f vel) {
        body.setLinearVelocity(new com.jme3.math.Vector3f(vel.x, vel.y, vel.z));
    }

    /**
     * Set the linear velocity.
     */
    public void setLinearVelocity(float x, float y, float z) {
        body.setLinearVelocity(new com.jme3.math.Vector3f(x, y, z));
    }

    /**
     * Get the collision shape type.
     */
    @Nonnull
    public ShapeType getShapeType() {
        CollisionShape shape = body.getCollisionShape();
        if (shape instanceof BoxCollisionShape) {
            return ShapeType.BOX;
        }
        if (shape instanceof SphereCollisionShape) {
            return ShapeType.SPHERE;
        }
        if (shape instanceof PlaneCollisionShape) {
            return ShapeType.PLANE;
        }
        return ShapeType.UNKNOWN;
    }

    /**
     * Get the half-extents of a box shape, or {@code null} if not a box.
     */
    @Nullable
    public Vector3f getBoxHalfExtents() {
        CollisionShape shape = body.getCollisionShape();
        if (shape instanceof BoxCollisionShape box) {
            com.jme3.math.Vector3f he = box.getHalfExtents(new com.jme3.math.Vector3f());
            return new Vector3f(he.x, he.y, he.z);
        }
        return null;
    }

    /**
     * Get the radius of a sphere shape, or {@code -1} if not a sphere.
     */
    public float getSphereRadius() {
        CollisionShape shape = body.getCollisionShape();
        if (shape instanceof SphereCollisionShape sphere) {
            return sphere.getRadius();
        }
        return -1f;
    }

    /**
     * Get the Y offset from the bottom-center of the shape to its center of mass. This is the Y
     * half-extent for boxes, the radius for spheres, and 0 for planes.
     */
    public float getCenterOfMassOffsetY() {
        CollisionShape shape = body.getCollisionShape();
        if (shape instanceof BoxCollisionShape box) {
            return box.getHalfExtents(new com.jme3.math.Vector3f()).y;
        }
        if (shape instanceof SphereCollisionShape sphere) {
            return sphere.getRadius();
        }
        return 0f;
    }

    @Nonnull
    PhysicsRigidBody getRigidBody() {
        return body;
    }

    /**
     * Collision shape types.
     */
    public enum ShapeType {
        BOX,
        SPHERE,
        PLANE,
        UNKNOWN
    }
}
