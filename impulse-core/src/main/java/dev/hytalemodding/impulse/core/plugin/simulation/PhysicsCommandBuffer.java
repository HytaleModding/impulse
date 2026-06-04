package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.simulation.recorder.MutablePhysicsCommandContext;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.query.PhysicsQueryHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RaycastAllQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RaycastClosestQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.JointCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.PhysicsCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * Tick-scoped facade over the existing packed physics command context.
 */
public final class PhysicsCommandBuffer {

    @Nullable
    private final PhysicsWorldRuntimeResource runtime;
    @Nullable
    private final PhysicsWorldResource resource;
    @Nonnull
    private final MutablePhysicsCommandContext context;
    private boolean submitted;

    private PhysicsCommandBuffer(@Nullable PhysicsWorldRuntimeResource runtime,
        @Nullable PhysicsWorldResource resource,
        @Nonnull MutablePhysicsCommandContext context) {
        this.runtime = runtime;
        this.resource = resource;
        this.context = Objects.requireNonNull(context, "context");
    }

    @Nonnull
    public static PhysicsCommandBuffer begin(@Nonnull PhysicsWorldResource resource,
        long submittedServerTick) {
        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(resource);
        return new PhysicsCommandBuffer(runtime,
            resource,
            runtime.createMutableCommandContext(submittedServerTick));
    }

    @Nonnull
    public static PhysicsCommandBuffer begin(@Nonnull PhysicsWorldResource resource,
        long submittedServerTick,
        int expectedOperations) {
        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(resource);
        return new PhysicsCommandBuffer(runtime,
            resource,
            runtime.createMutableCommandContext(submittedServerTick, expectedOperations));
    }

    @Nonnull
    static PhysicsCommandBuffer recording(@Nonnull MutablePhysicsCommandContext context) {
        return new PhysicsCommandBuffer(null, null, context);
    }

    @Nonnull
    public PhysicsCommandBuffer record(@Nonnull Consumer<PhysicsCommandRecorder> recipe) {
        ensureOpen();
        Objects.requireNonNull(recipe, "recipe").accept(context);
        return this;
    }

    @Nonnull
    public PhysicsCommandBuffer applyImpulse(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3dc impulse) {
        ensureOpen();
        context.applyBodyImpulse(bodyKey, toFloat(impulse.x()), toFloat(impulse.y()), toFloat(impulse.z()));
        return this;
    }

    @Nonnull
    public PhysicsCommandBuffer applyForce(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3dc force) {
        ensureOpen();
        context.applyBodyForce(bodyKey, toFloat(force.x()), toFloat(force.y()), toFloat(force.z()));
        return this;
    }

    @Nonnull
    public PhysicsCommandBuffer setVelocity(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3dc linearVelocity,
        @Nonnull Vector3dc angularVelocity) {
        ensureOpen();
        context.setBodyVelocity(bodyKey,
            toFloat(linearVelocity.x()),
            toFloat(linearVelocity.y()),
            toFloat(linearVelocity.z()),
            toFloat(angularVelocity.x()),
            toFloat(angularVelocity.y()),
            toFloat(angularVelocity.z()),
            true);
        return this;
    }

    @Nonnull
    public PhysicsCommandBuffer setLinearVelocity(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3dc linearVelocity) {
        PhysicsBodySnapshot snapshot = requireResource().getBodySnapshot(bodyKey);
        Vector3f angularVelocity = snapshot.copyAngularVelocityTo(new Vector3f());
        return setVelocity(bodyKey, linearVelocity, new Vector3d(angularVelocity));
    }

    @Nonnull
    public PhysicsCommandBuffer setAngularVelocity(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3dc angularVelocity) {
        PhysicsBodySnapshot snapshot = requireResource().getBodySnapshot(bodyKey);
        Vector3f linearVelocity = snapshot.copyLinearVelocityTo(new Vector3f());
        return setVelocity(bodyKey, new Vector3d(linearVelocity), angularVelocity);
    }

    @Nonnull
    public PhysicsCommandBuffer setType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType) {
        ensureOpen();
        context.setBodyType(bodyKey, bodyType, true);
        return this;
    }

    @Nonnull
    public PhysicsCommandBuffer destroyBody(@Nonnull RigidBodyKey bodyKey) {
        ensureOpen();
        context.destroyBody(bodyKey);
        return this;
    }

    @Nonnull
    public PhysicsCommandBuffer createJoint(@Nonnull JointKey jointKey,
        @Nonnull Consumer<JointCommandRecorder> recipe) {
        ensureOpen();
        context.joint(jointKey, recipe);
        return this;
    }

    @Nonnull
    public PhysicsCommandBuffer destroyJoint(@Nonnull JointKey jointKey) {
        ensureOpen();
        context.destroyJoint(jointKey);
        return this;
    }

    @Nonnull
    public PhysicsCommandBuffer destroyJointBetween(@Nullable JointKey preferredJointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        ensureOpen();
        context.destroyJointBetween(preferredJointKey, spaceId, bodyA, bodyB);
        return this;
    }

    @Nonnull
    public PhysicsQueryHandle<Optional<RaycastHitView>> raycastClosest(@Nonnull SpaceId spaceId,
        @Nonnull Vector3dc from,
        @Nonnull Vector3dc to) {
        return requireResource().query(new RaycastClosestQuery(spaceId, vector(from), vector(to)));
    }

    @Nonnull
    public PhysicsQueryHandle<List<RaycastHitView>> raycastAll(@Nonnull SpaceId spaceId,
        @Nonnull Vector3dc from,
        @Nonnull Vector3dc to) {
        return requireResource().query(new RaycastAllQuery(spaceId, vector(from), vector(to)));
    }

    @Nonnull
    public PhysicsCommandHandle submit() {
        ensureOpen();
        if (runtime == null) {
            throw new IllegalStateException("PhysicsCommandBuffer was not created from a resource");
        }
        submitted = true;
        return runtime.submitRecordedCommands(context);
    }

    @Nonnull
    PhysicsCommandBatch freezeForTesting(long commandBatchSequence) {
        ensureOpen();
        submitted = true;
        return context.freezeInternal(commandBatchSequence).publicBatch();
    }

    private void ensureOpen() {
        if (submitted) {
            throw new IllegalStateException("Physics command buffer is already submitted");
        }
    }

    @Nonnull
    private PhysicsWorldResource requireResource() {
        ensureOpen();
        if (resource == null) {
            throw new IllegalStateException("PhysicsCommandBuffer was not created from a resource");
        }
        return resource;
    }

    private static float toFloat(double value) {
        return (float) value;
    }

    @Nonnull
    private static Vector3f vector(@Nonnull Vector3dc value) {
        return new Vector3f(toFloat(value.x()), toFloat(value.y()), toFloat(value.z()));
    }
}
