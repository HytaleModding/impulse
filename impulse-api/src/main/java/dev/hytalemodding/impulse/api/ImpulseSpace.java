package dev.hytalemodding.impulse.api;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.objects.PhysicsRigidBody;
import lombok.Getter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Wraps a Bullet {@link PhysicsSpace} and manages the bodies within it.
 */
public final class ImpulseSpace {

    @Getter(onMethod = @__(@Nonnull))
    private final PhysicsSpace space;

    ImpulseSpace(@Nonnull PhysicsSpace space) {
        this.space = space;
    }

    /**
     * Advance the simulation by the given time step (seconds).
     */
    public void step(float dt) {
        space.update(dt, 0);
    }

    public void addBody(@Nonnull ImpulseBody body) {
        space.addCollisionObject(body.getRigidBody());
    }

    public void removeBody(@Nonnull ImpulseBody body) {
        space.removeCollisionObject(body.getRigidBody());
    }

    /**
     * Get all rigid bodies in the space (raw Bullet objects).
     */
    @Nonnull
    public List<PhysicsRigidBody> getRigidBodies() {
        Collection<PhysicsRigidBody> col = space.getRigidBodyList();
        return new ArrayList<>(col);
    }

    /**
     * Get all bodies wrapped as {@link ImpulseBody}.
     */
    @Nonnull
    public List<ImpulseBody> getBodies() {
        Collection<PhysicsRigidBody> col = space.getRigidBodyList();
        List<ImpulseBody> result = new ArrayList<>(col.size());
        for (PhysicsRigidBody rb : col) {
            result.add(new ImpulseBody(rb));
        }
        return result;
    }

    /**
     * Set the gravity vector (default is Y = -9.81).
     */
    public void setGravity(float x, float y, float z) {
        space.setGravity(new com.jme3.math.Vector3f(x, y, z));
    }
}
