package dev.hytalemodding.impulse.api;

import dev.hytalemodding.impulse.api.capability.PhysicsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityDescriptor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Live simulation container for a physics backend.
 * <p>
 * One world can have different physics spaces of different space size.
 * </p>
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

    /**
     * Returns the number of bodies in this space.
     *
     * <p>Default implementation delegates to {@link #getBodies()}.
     * Backends should override to avoid list allocation.</p>
     */
    default int bodyCount() {
        return getBodies().size();
    }

    /**
     * Iterates all bodies without allocating a copied list.
     *
     * <p>Default implementation delegates to {@link #getBodies()}.
     * Backends should override to iterate internal lists directly.</p>
     */
    default void forEachBody(@Nonnull Consumer<PhysicsBody> consumer) {
        for (PhysicsBody body : getBodies()) {
            consumer.accept(body);
        }
    }

    /**
     * Returns whether the given body is currently attached to this space.
     *
     * <p>Default implementation delegates to {@link #getBodies()}.
     * Backends should override to avoid list allocation.</p>
     */
    default boolean containsBody(@Nonnull PhysicsBody body) {
        return getBodies().contains(body);
    }

    /**
     * Publishes owner-thread body snapshots for systems that must not repeatedly
     * read mutable backend bodies.
     */
    default void snapshotBodies(@Nonnull Consumer<PhysicsBodySnapshot> consumer) {
        snapshotBodies(body -> null, consumer);
    }

    /**
     * Publishes owner-thread body snapshots, allowing callers to provide the last
     * published snapshot so backends can avoid stable sleeping-body refreshes.
     */
    default void snapshotBodies(@Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull Consumer<PhysicsBodySnapshot> consumer) {
        snapshotBodies(previousSnapshots, (_, snapshot) -> consumer.accept(snapshot));
    }

    /**
     * Publishes owner-thread body snapshots with the live body available only during the callback.
     */
    default void snapshotBodies(@Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull BiConsumer<PhysicsBody, PhysicsBodySnapshot> consumer) {
        forEachBody(body -> consumer.accept(body, PhysicsBodySnapshot.from(body, previousSnapshots.apply(body))));
    }

    /**
     * Publishes owner-thread snapshots for a caller-selected subset of bodies.
     *
     * <p>This lets backends batch-read only bodies that higher-level systems actually
     * need to publish. Callers should pass bodies that currently belong to this space.</p>
     */
    default void snapshotBodies(@Nonnull Iterable<? extends PhysicsBody> selectedBodies,
        @Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull Consumer<PhysicsBodySnapshot> consumer) {
        snapshotBodies(selectedBodies, previousSnapshots, (_, snapshot) -> consumer.accept(snapshot));
    }

    /**
     * Publishes owner-thread snapshots for a caller-selected subset of bodies with the live body
     * available only during the callback.
     */
    default void snapshotBodies(@Nonnull Iterable<? extends PhysicsBody> selectedBodies,
        @Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
        @Nonnull BiConsumer<PhysicsBody, PhysicsBodySnapshot> consumer) {
        for (PhysicsBody body : selectedBodies) {
            consumer.accept(body, PhysicsBodySnapshot.from(body, previousSnapshots.apply(body)));
        }
    }

    /**
     * Returns optional backend runtime counters for diagnostics.
     *
     * <p>The default implementation reports no backend-specific runtime counters so
     * existing backends do not need to synthesize opaque internal state.</p>
     */
    @Nonnull
    default PhysicsRuntimeStats getRuntimeStats() {
        return PhysicsRuntimeStats.unavailable();
    }

    /**
     * Resets backend-native phase counters collected by {@link #getStepPhaseStats()}.
     *
     * <p>Backends that do not expose phase timings may keep the default no-op behavior.</p>
     */
    default void resetStepPhaseStats() {
    }

    /**
     * Returns backend-native phase timings collected since the last reset.
     *
     * <p>These counters are intended for profiling only. Unsupported backends should return
     * {@link PhysicsStepPhaseStats#unavailable()}.</p>
     */
    @Nonnull
    default PhysicsStepPhaseStats getStepPhaseStats() {
        return PhysicsStepPhaseStats.unavailable();
    }

    /**
     * Returns an optional backend-specific capability for this space.
     *
     * <p>The default implementation supports spaces that directly implement a capability
     * interface. Backends that expose separate capability objects should override this method.</p>
     */
    @Nonnull
    default <T extends PhysicsCapability> Optional<T> getCapability(@Nonnull Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    /**
     * Returns metadata for backend-specific capabilities exposed by this space.
     */
    @Nonnull
    default List<PhysicsCapabilityDescriptor> getCapabilityDescriptors() {
        return List.of();
    }

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

    /**
     * Returns the number of joints in this space.
     *
     * <p>Default implementation delegates to {@link #getJoints()}.
     * Backends should override to avoid list allocation.</p>
     */
    default int jointCount() {
        return getJoints().size();
    }

    /**
     * Iterates all joints without allocating a copied list.
     *
     * <p>Default implementation delegates to {@link #getJoints()}.
     * Backends should override to iterate internal lists directly.</p>
     */
    default void forEachJoint(@Nonnull Consumer<PhysicsJoint> consumer) {
        for (PhysicsJoint joint : getJoints()) {
            consumer.accept(joint);
        }
    }

    /**
     * Release backend resources for this space.
     * <p>
     * Default implementation is a no-op.
     */
    default void close() {
    }
}
