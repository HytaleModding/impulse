package dev.hytalemodding.impulse.bullet;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.PlaneCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Plane;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

public final class BulletSpace implements PhysicsSpace {

    private final SpaceId id;
    private final BulletBackend backend;
    private final com.jme3.bullet.PhysicsSpace space;

    BulletSpace(@Nonnull BulletBackend backend, @Nonnull com.jme3.bullet.PhysicsSpace space) {
        this.id = SpaceId.next();
        this.backend = backend;
        this.space = space;
    }

    @Nonnull
    @Override
    public SpaceId getId() {
        return id;
    }

    @Nonnull
    @Override
    public BackendId getBackendId() {
        return backend.getId();
    }

    @Override
    public void step(float dt) {
        space.update(dt, 0);
    }

    @Override
    public void setGravity(float x, float y, float z) {
        space.setGravity(new com.jme3.math.Vector3f(x, y, z));
    }

    @Override
    public void addBody(@Nonnull PhysicsBody body) {
        if (!(body instanceof BulletBody bulletBody)) {
            throw new IllegalArgumentException("Body does not belong to bullet backend");
        }
        space.addCollisionObject(bulletBody.getRigidBody());
    }

    @Override
    public void removeBody(@Nonnull PhysicsBody body) {
        if (!(body instanceof BulletBody bulletBody)) {
            return;
        }
        space.removeCollisionObject(bulletBody.getRigidBody());
    }

    @Nonnull
    @Override
    public List<PhysicsBody> getBodies() {
        Collection<PhysicsRigidBody> col = space.getRigidBodyList();
        List<PhysicsBody> result = new ArrayList<>(col.size());
        for (PhysicsRigidBody rb : col) {
            result.add(new BulletBody(rb));
        }
        return result;
    }

    @Nonnull
    @Override
    public PhysicsBody createStaticPlane(float groundY) {
        Plane plane = new Plane(com.jme3.math.Vector3f.UNIT_Y, groundY);
        CollisionShape shape = new PlaneCollisionShape(plane);
        PhysicsRigidBody body = new PhysicsRigidBody(shape,
            com.jme3.bullet.objects.PhysicsBody.massForStatic);
        return new BulletBody(body);
    }

    @Nonnull
    @Override
    public PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass) {
        CollisionShape shape = new BoxCollisionShape(new com.jme3.math.Vector3f(halfX, halfY, halfZ));
        PhysicsRigidBody body = new PhysicsRigidBody(shape, mass);
        return new BulletBody(body);
    }

    @Nonnull
    @Override
    public PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass) {
        CollisionShape shape = new BoxCollisionShape(
            new com.jme3.math.Vector3f(halfExtents.x, halfExtents.y, halfExtents.z));
        PhysicsRigidBody body = new PhysicsRigidBody(shape, mass);
        return new BulletBody(body);
    }
}
