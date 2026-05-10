package dev.hytalemodding.impulse.bullet;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.PlaneCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.ShapeType;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BulletBody implements PhysicsBody {

    // TODO: the interpretation of bullet's PhysicsRigidBody -> impulse's PhysicsBody it's not clear
    private final PhysicsRigidBody body;

    BulletBody(@Nonnull PhysicsRigidBody body) {
        this.body = body;
    }

    @Override
    public void setPosition(float x, float y, float z) {
        body.setPhysicsLocation(new com.jme3.math.Vector3f(x, y, z));
    }

    @Override
    public void setPosition(@Nonnull Vector3f pos) {
        body.setPhysicsLocation(new com.jme3.math.Vector3f(pos.x, pos.y, pos.z));
    }

    @Nonnull
    @Override
    public Vector3f getPosition() {
        com.jme3.math.Vector3f pos = body.getPhysicsLocation(new com.jme3.math.Vector3f());
        return new Vector3f(pos.x, pos.y, pos.z);
    }

    @Override
    public void setRotation(float x, float y, float z, float w) {
        body.setPhysicsRotation(new com.jme3.math.Quaternion(x, y, z, w));
    }

    @Override
    public void setRotation(@Nonnull Quaternionf rot) {
        body.setPhysicsRotation(new com.jme3.math.Quaternion(rot.x, rot.y, rot.z, rot.w));
    }

    @Nonnull
    @Override
    public Quaternionf getRotation() {
        com.jme3.math.Quaternion rot = body.getPhysicsRotation(new com.jme3.math.Quaternion());
        return new Quaternionf(rot.getX(), rot.getY(), rot.getZ(), rot.getW());
    }

    @Override
    public void setRestitution(float restitution) {
        body.setRestitution(restitution);
    }

    @Override
    public void setFriction(float friction) {
        body.setFriction(friction);
    }

    @Override
    public boolean isStatic() {
        return body.isStatic();
    }

    @Override
    public boolean isKinematic() {
        return body.isKinematic();
    }

    @Override
    public void setKinematic(boolean kinematic) {
        body.setKinematic(kinematic);
    }

    @Override
    public void activate() {
        body.activate();
    }

    @Nonnull
    @Override
    public Vector3f getLinearVelocity() {
        com.jme3.math.Vector3f vel = body.getLinearVelocity(new com.jme3.math.Vector3f());
        return new Vector3f(vel.x, vel.y, vel.z);
    }

    @Override
    public void setLinearVelocity(@Nonnull Vector3f vel) {
        body.setLinearVelocity(new com.jme3.math.Vector3f(vel.x, vel.y, vel.z));
    }

    @Override
    public void setLinearVelocity(float x, float y, float z) {
        body.setLinearVelocity(new com.jme3.math.Vector3f(x, y, z));
    }

    @Nonnull
    @Override
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

    @Nullable
    @Override
    public Vector3f getBoxHalfExtents() {
        CollisionShape shape = body.getCollisionShape();
        if (shape instanceof BoxCollisionShape box) {
            com.jme3.math.Vector3f halfExtents = box.getHalfExtents(new com.jme3.math.Vector3f());
            return new Vector3f(halfExtents.x, halfExtents.y, halfExtents.z);
        }
        return null;
    }

    @Override
    public float getSphereRadius() {
        CollisionShape shape = body.getCollisionShape();
        if (shape instanceof SphereCollisionShape sphere) {
            return sphere.getRadius();
        }
        return -1f;
    }

    @Override
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
}
