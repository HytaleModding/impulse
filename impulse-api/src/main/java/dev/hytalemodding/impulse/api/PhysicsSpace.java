package dev.hytalemodding.impulse.api;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Live simulation container for a physics backend.
 * <p>
 * Backends are expected to agree on the meaning of these operations, but not on identical
 * numerical output. Contact ordering, solver settling, and ray hit details can differ slightly
 * between implementations.
 * <ul>
 *     <li>This type must not be assumed to be thread-safe.</li>
 *     <li>All mutations and stepping must happen from a single owner thread.</li>
 *     <li>If other threads need to interact, queue commands onto the owner thread.</li>
 * </ul>
 */
public interface PhysicsSpace {

    @Nonnull
    SpaceId getId();

    @Nonnull
    BackendId getBackendId();

    void step(float dt);

    void setGravity(float x, float y, float z);

    @Nonnull
    Vector3f getGravity();

    void addBody(@Nonnull PhysicsBody body);

    void removeBody(@Nonnull PhysicsBody body);

    @Nonnull
    List<PhysicsBody> getBodies();

    @Nonnull
    PhysicsBody createStaticPlane(float groundY);

    @Nonnull
    PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass);

    @Nonnull
    PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass);

    @Nonnull
    PhysicsBody createSphere(float radius, float mass);

    @Nonnull
    PhysicsBody createCapsule(float radius, float halfHeight, @Nonnull PhysicsAxis axis,
        float mass);

    @Nonnull
    PhysicsBody createCylinder(float radius, float halfHeight, @Nonnull PhysicsAxis axis,
        float mass);

    @Nonnull
    PhysicsBody createCone(float radius, float halfHeight, @Nonnull PhysicsAxis axis, float mass);

    @Nonnull
    Optional<PhysicsRayHit> raycastClosest(@Nonnull Vector3f from, @Nonnull Vector3f to);

    @Nonnull
    List<PhysicsRayHit> raycastAll(@Nonnull Vector3f from, @Nonnull Vector3f to);

    @Nonnull
    List<PhysicsContact> getContacts();

    /**
     * Create a fixed joint.
     * Anchors are local to each body.
     * The joint locks the bodies together with no relative translation or rotation.
     */
    @Nonnull
    PhysicsJoint createFixedJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB);

    /**
     * Create a point joint.
     * Anchors are local to each body.
     * The joint keeps the two anchors together but allows free rotation.
     */
    @Nonnull
    PhysicsJoint createPointJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB);

    /**
     * Create a hinge joint.
     * Anchors are local to each body.
     * Axis describes the hinge axis in joint local space.
     * The joint allows rotation around that axis.
     */
    @Nonnull
    PhysicsJoint createHingeJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis);

    /**
     * Create a slider joint.
     * Anchors are local to each body.
     * Axis describes the slide axis in joint local space.
     * The joint allows translation along that axis.
     */
    @Nonnull
    PhysicsJoint createSliderJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis);

    /**
     * Create a spring joint.
     * Anchors are local to each body.
     * Rest length, stiffness, and damping define the spring behavior.
     */
    @Nonnull
    PhysicsJoint createSpringJoint(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        float restLength,
        float stiffness,
        float damping);

    void removeJoint(@Nonnull PhysicsJoint joint);

    @Nonnull
    List<PhysicsJoint> getJoints();
}
