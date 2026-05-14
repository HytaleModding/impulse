package dev.hytalemodding.impulse.bullet;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.ConeCollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.PlaneCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BulletBody implements PhysicsBody {

    private final PhysicsRigidBody body;

    BulletBody(@Nonnull PhysicsRigidBody body) {
        this.body = body;
    }

    @Override
    public void setPosition(float x, float y, float z) {
        body.setPhysicsLocation(toJme(x, y, z));
    }

    @Override
    public void setPosition(@Nonnull Vector3f pos) {
        setPosition(pos.x, pos.y, pos.z);
    }

    @Nonnull
    @Override
    public Vector3f getPosition() {
        return fromJme(body.getPhysicsLocation(new com.jme3.math.Vector3f()));
    }

    @Override
    public void setRotation(float x, float y, float z, float w) {
        body.setPhysicsRotation(new com.jme3.math.Quaternion(x, y, z, w));
    }

    @Override
    public void setRotation(@Nonnull Quaternionf rot) {
        setRotation(rot.x, rot.y, rot.z, rot.w);
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
    public float getRestitution() {
        return body.getRestitution();
    }

    @Override
    public void setFriction(float friction) {
        body.setFriction(friction);
    }

    @Override
    public float getFriction() {
        return body.getFriction();
    }

    @Nonnull
    @Override
    public PhysicsBodyType getBodyType() {
        if (body.isStatic()) {
            return PhysicsBodyType.STATIC;
        }
        if (body.isKinematic()) {
            return PhysicsBodyType.KINEMATIC;
        }
        return PhysicsBodyType.DYNAMIC;
    }

    @Override
    public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
        switch (bodyType) {
            case STATIC -> {
                body.setKinematic(false);
                body.setMass(com.jme3.bullet.objects.PhysicsBody.massForStatic);
            }
            case DYNAMIC -> {
                if (body.getMass() <= 0f) {
                    body.setMass(1f);
                }
                body.setKinematic(false);
            }
            case KINEMATIC -> {
                if (body.getMass() <= 0f) {
                    body.setMass(1f);
                }
                body.setKinematic(true);
            }
        }
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
        if (kinematic) {
            setBodyType(PhysicsBodyType.KINEMATIC);
        } else if (!body.isStatic()) {
            body.setKinematic(false);
        }
    }

    @Override
    public void activate() {
        body.activate();
    }

    @Override
    public boolean isActive() {
        return body.isActive();
    }

    @Override
    public boolean isSleeping() {
        return !body.isActive();
    }

    @Override
    public void sleep() {
        body.setLinearVelocity(toJme(0f, 0f, 0f));
        body.setAngularVelocity(toJme(0f, 0f, 0f));
        body.setEnableSleep(true);
    }

    @Override
    public float getMass() {
        return body.getMass();
    }

    @Override
    public void setMass(float mass) {
        body.setMass(mass);
    }

    @Nonnull
    @Override
    public Vector3f getLinearVelocity() {
        return fromJme(body.getLinearVelocity(new com.jme3.math.Vector3f()));
    }

    @Override
    public void setLinearVelocity(@Nonnull Vector3f vel) {
        setLinearVelocity(vel.x, vel.y, vel.z);
    }

    @Override
    public void setLinearVelocity(float x, float y, float z) {
        body.setLinearVelocity(toJme(x, y, z));
    }

    @Nonnull
    @Override
    public Vector3f getAngularVelocity() {
        return fromJme(body.getAngularVelocity(new com.jme3.math.Vector3f()));
    }

    @Override
    public void setAngularVelocity(@Nonnull Vector3f vel) {
        setAngularVelocity(vel.x, vel.y, vel.z);
    }

    @Override
    public void setAngularVelocity(float x, float y, float z) {
        body.setAngularVelocity(toJme(x, y, z));
    }

    @Override
    public float getLinearDamping() {
        return body.getLinearDamping();
    }

    @Override
    public void setLinearDamping(float damping) {
        body.setLinearDamping(damping);
    }

    @Override
    public float getAngularDamping() {
        return body.getAngularDamping();
    }

    @Override
    public void setAngularDamping(float damping) {
        body.setAngularDamping(damping);
    }

    @Override
    public void applyCentralForce(@Nonnull Vector3f force) {
        applyCentralForce(force.x, force.y, force.z);
    }

    @Override
    public void applyCentralForce(float x, float y, float z) {
        body.applyCentralForce(toJme(x, y, z));
    }

    @Override
    public void applyForce(@Nonnull Vector3f force, @Nonnull Vector3f offset) {
        body.applyForce(toJme(force), toJme(offset));
    }

    @Override
    public void applyCentralImpulse(@Nonnull Vector3f impulse) {
        applyCentralImpulse(impulse.x, impulse.y, impulse.z);
    }

    @Override
    public void applyCentralImpulse(float x, float y, float z) {
        body.applyCentralImpulse(toJme(x, y, z));
    }

    @Override
    public void applyImpulse(@Nonnull Vector3f impulse, @Nonnull Vector3f offset) {
        body.applyImpulse(toJme(impulse), toJme(offset));
    }

    @Override
    public void applyTorque(@Nonnull Vector3f torque) {
        body.applyTorque(toJme(torque));
    }

    @Override
    public void applyTorqueImpulse(@Nonnull Vector3f torqueImpulse) {
        body.applyTorqueImpulse(toJme(torqueImpulse));
    }

    @Override
    public void clearForces() {
        body.clearForces();
    }

    @Override
    public boolean isSensor() {
        return !body.isContactResponse();
    }

    @Override
    public void setSensor(boolean sensor) {
        body.setContactResponse(!sensor);
    }

    @Override
    public int getCollisionGroup() {
        return body.getCollisionGroup();
    }

    @Override
    public int getCollisionMask() {
        return body.getCollideWithGroups();
    }

    @Override
    public void setCollisionFilter(int group, int mask) {
        body.setCollisionGroup(group);
        body.setCollideWithGroups(mask);
    }

    @Override
    public boolean isContinuousCollisionEnabled() {
        return body.getCcdMotionThreshold() > 0f;
    }

    @Override
    public void setContinuousCollisionEnabled(boolean enabled) {
        if (enabled) {
            body.setCcdMotionThreshold(0.0001f);
            body.setCcdSweptSphereRadius(Math.max(0.001f, estimateCcdRadius()));
        } else {
            body.setCcdMotionThreshold(0f);
            body.setCcdSweptSphereRadius(0f);
        }
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
        if (shape instanceof CapsuleCollisionShape) {
            return ShapeType.CAPSULE;
        }
        if (shape instanceof CylinderCollisionShape) {
            return ShapeType.CYLINDER;
        }
        if (shape instanceof ConeCollisionShape) {
            return ShapeType.CONE;
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
            return fromJme(box.getHalfExtents(new com.jme3.math.Vector3f()));
        }
        if (shape instanceof CylinderCollisionShape cylinder) {
            return fromJme(cylinder.getHalfExtents(new com.jme3.math.Vector3f()));
        }
        return null;
    }

    @Override
    public float getSphereRadius() {
        CollisionShape shape = body.getCollisionShape();
        if (shape instanceof SphereCollisionShape sphere) {
            return sphere.getRadius();
        }
        if (shape instanceof CapsuleCollisionShape capsule) {
            return capsule.getRadius();
        }
        if (shape instanceof ConeCollisionShape cone) {
            return cone.getRadius();
        }
        return -1f;
    }

    @Override
    public float getHalfHeight() {
        CollisionShape shape = body.getCollisionShape();
        if (shape instanceof CapsuleCollisionShape capsule) {
            return capsule.getHeight() * 0.5f;
        }
        if (shape instanceof CylinderCollisionShape cylinder) {
            return cylinder.getHeight() * 0.5f;
        }
        if (shape instanceof ConeCollisionShape cone) {
            return cone.getHeight() * 0.5f;
        }
        return -1f;
    }

    @Nonnull
    @Override
    public PhysicsAxis getShapeAxis() {
        CollisionShape shape = body.getCollisionShape();
        if (shape instanceof CapsuleCollisionShape capsule) {
            return PhysicsAxis.fromIndex(capsule.getAxis());
        }
        if (shape instanceof CylinderCollisionShape cylinder) {
            return PhysicsAxis.fromIndex(cylinder.getAxis());
        }
        if (shape instanceof ConeCollisionShape cone) {
            return PhysicsAxis.fromIndex(cone.getAxis());
        }
        return PhysicsAxis.Y;
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
        if (shape instanceof CapsuleCollisionShape capsule) {
            return capsule.getAxis() == PhysicsAxis.Y.index()
                ? capsule.getHeight() * 0.5f + capsule.getRadius()
                : capsule.getRadius();
        }
        if (shape instanceof CylinderCollisionShape cylinder) {
            return cylinder.getAxis() == PhysicsAxis.Y.index()
                ? cylinder.getHeight() * 0.5f
                : cylinder.maxRadius();
        }
        if (shape instanceof ConeCollisionShape cone) {
            return cone.getAxis() == PhysicsAxis.Y.index()
                ? cone.getHeight() * 0.5f
                : cone.maxRadius();
        }
        return 0f;
    }

    @Nonnull
    PhysicsRigidBody getRigidBody() {
        return body;
    }

    long getNativeId() {
        return body.nativeId();
    }

    private float estimateCcdRadius() {
        return switch (getShapeType()) {
            case BOX, CYLINDER -> {
                Vector3f half = getBoxHalfExtents();
                yield half != null ? Math.min(half.x, Math.min(half.y, half.z)) : 0.05f;
            }
            case SPHERE, CAPSULE, CONE -> Math.max(0.001f, getSphereRadius());
            default -> 0.05f;
        };
    }

    private static com.jme3.math.Vector3f toJme(@Nonnull Vector3f vector) {
        return toJme(vector.x, vector.y, vector.z);
    }

    private static com.jme3.math.Vector3f toJme(float x, float y, float z) {
        return new com.jme3.math.Vector3f(x, y, z);
    }

    private static Vector3f fromJme(@Nonnull com.jme3.math.Vector3f vector) {
        return new Vector3f(vector.x, vector.y, vector.z);
    }
}
