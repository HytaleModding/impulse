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

    /**
     * Returns true when the backend can create one static voxel collider from local voxel cells.
     *
     * <p>This is intended for section-sized terrain batches. Backends that do not support a
     * native voxel shape can return false and let higher-level code fall back to merged boxes.</p>
     */
    default boolean supportsVoxelTerrain() {
        return false;
    }

    /**
     * Creates a static terrain body made from occupied voxel cells.
     *
     * <p>The {@code voxelCoordinates} array stores triples of local integer grid coordinates:
     * {@code x0, y0, z0, x1, y1, z1, ...}. The returned body can then be positioned at the
     * section/world origin like any other static body.</p>
     */
    @Nonnull
    default PhysicsBody createVoxelTerrain(float voxelSizeX,
        float voxelSizeY,
        float voxelSizeZ,
        @Nonnull int[] voxelCoordinates) {
        throw new UnsupportedOperationException("This physics backend does not support voxel terrain");
    }

    /**
     * Couples two adjacent voxel terrain bodies so the backend can treat their shared boundary
     * as continuous terrain instead of two unrelated voxel sets.
     *
     * <p>The shift is expressed in voxel units from {@code bodyA}'s local voxel origin to
     * {@code bodyB}'s local voxel origin. Backends that do not use native voxel terrain can keep
     * the default no-op behavior.</p>
     */
    default void combineVoxelTerrains(@Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB,
        int shiftX,
        int shiftY,
        int shiftZ) {
    }

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

    /**
     * Release backend resources for this space.
     * <p>
     * Default implementation is a no-op.
     */
    default void close() {
    }
}
