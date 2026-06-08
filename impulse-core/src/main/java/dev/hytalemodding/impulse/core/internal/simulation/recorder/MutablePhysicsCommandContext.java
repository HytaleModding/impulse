package dev.hytalemodding.impulse.core.internal.simulation.recorder;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsCommandOperations;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RigidBodySpawnBatch;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RigidBodySpawnTemplateBatch;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.JointCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandBatch;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandContext;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandMetadata;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandRecipe;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodyCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodySpawnBatchRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodySpawnRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.RigidBodySpawnTemplateRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Mutable recorder for physics simulation commands.
 *
 * <p>Mutable contexts are short-lived. Record commands during an ECS/server tick, then
 * submit the context to freeze it into an immutable {@link PhysicsCommandBatch}.</p>
 */
public final class MutablePhysicsCommandContext implements PhysicsCommandContext {

    private static final int DEFAULT_EXPECTED_OPERATIONS = 8;

    private final long submittedServerTick;
    private final long worldEpoch;
    private final PhysicsCommandOperations operations;
    private final List<MutableRigidBodySpawnRecorder> pendingSpawns = new ArrayList<>();
    private final List<MutableJointCommandRecorder> pendingJoints = new ArrayList<>();
    private boolean frozen;

    public MutablePhysicsCommandContext(long submittedServerTick, long worldEpoch) {
        this(submittedServerTick, worldEpoch, DEFAULT_EXPECTED_OPERATIONS);
    }

    public MutablePhysicsCommandContext(long submittedServerTick,
        long worldEpoch,
        int expectedOperations) {
        this.submittedServerTick = Math.max(0L, submittedServerTick);
        this.worldEpoch = Math.max(0L, worldEpoch);
        this.operations = new PhysicsCommandOperations(expectedOperations);
    }

    @Nonnull
    @Override
    public PhysicsCommandContext compose(@Nonnull PhysicsCommandRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe").record(this);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder body(@Nonnull RigidBodyKey bodyKey) {
        return new MutableRigidBodyCommandRecorder(this, bodyKey);
    }

    @Nonnull
    @Override
    public PhysicsCommandContext body(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Consumer<RigidBodyCommandRecorder> recipe) {
        MutableRigidBodyCommandRecorder body = new MutableRigidBodyCommandRecorder(this, bodyKey);
        try {
            Objects.requireNonNull(recipe, "recipe").accept(body);
        } finally {
            body.seal();
        }
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext setSpaceGravity(@Nonnull SpaceId spaceId,
        float x,
        float y,
        float z) {
        recordSetSpaceGravity(spaceId, x, y, z);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext setBodyTransform(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        boolean activate) {
        recordSetTransform(bodyKey,
            positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW,
            activate);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext setBodyVelocity(@Nonnull RigidBodyKey bodyKey,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ,
        boolean activate) {
        recordSetVelocity(bodyKey, linearX, linearY, linearZ, angularX, angularY, angularZ, activate);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext setBodyPosition(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        boolean activate) {
        recordSetPosition(bodyKey, positionX, positionY, positionZ, activate);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext setBodyType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        recordSetType(bodyKey, bodyType, activate);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext activateBody(@Nonnull RigidBodyKey bodyKey) {
        recordActivate(bodyKey);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext applyBodyImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z) {
        recordImpulse(bodyKey, x, y, z, false, 0.0f, 0.0f, 0.0f, false);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext applyBodyImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        recordImpulse(bodyKey, x, y, z, true, offsetX, offsetY, offsetZ, false);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext applyBodyTorqueImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z) {
        recordImpulse(bodyKey, x, y, z, false, 0.0f, 0.0f, 0.0f, true);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext applyBodyForce(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z) {
        recordForce(bodyKey, x, y, z, false, 0.0f, 0.0f, 0.0f, false);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext applyBodyForce(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        recordForce(bodyKey, x, y, z, true, offsetX, offsetY, offsetZ, false);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext applyBodyTorque(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z) {
        recordForce(bodyKey, x, y, z, false, 0.0f, 0.0f, 0.0f, true);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext destroyBody(@Nonnull RigidBodyKey bodyKey) {
        recordDestroyBody(bodyKey);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodySpawnRecorder spawnBody(@Nonnull RigidBodyKey bodyKey) {
        assertMutable();
        MutableRigidBodySpawnRecorder spawn = new MutableRigidBodySpawnRecorder(this::recordSpawn, bodyKey);
        pendingSpawns.add(spawn);
        return spawn;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext spawnBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Consumer<RigidBodySpawnRecorder> recipe) {
        MutableRigidBodySpawnRecorder spawn =
            new MutableRigidBodySpawnRecorder(this::recordSpawn, bodyKey);
        try {
            Objects.requireNonNull(recipe, "recipe").accept(spawn);
            spawn.record();
        } finally {
            spawn.seal();
        }
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext spawnBodies(
        @Nonnull Consumer<RigidBodySpawnBatchRecorder> recipe) {
        return spawnBodies(DEFAULT_EXPECTED_OPERATIONS, recipe);
    }

    @Nonnull
    @Override
    public PhysicsCommandContext spawnBodies(int expectedBodies,
        @Nonnull Consumer<RigidBodySpawnBatchRecorder> recipe) {
        MutableRigidBodySpawnBatchRecorder spawns =
            new MutableRigidBodySpawnBatchRecorder(expectedBodies);
        try {
            Objects.requireNonNull(recipe, "recipe").accept(spawns);
        } finally {
            spawns.seal();
        }
        if (!spawns.isEmpty()) {
            recordSpawnBatch(spawns.spawns());
        }
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext spawnBodies(int expectedBodies,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull Consumer<RigidBodySpawnTemplateRecorder> recipe) {
        MutableRigidBodySpawnTemplateRecorder spawns = new MutableRigidBodySpawnTemplateRecorder(expectedBodies,
            spaceId,
            shape,
            mass,
            bodyType,
            settings,
            kind,
            persistenceMode);
        try {
            Objects.requireNonNull(recipe, "recipe").accept(spawns);
        } finally {
            spawns.seal();
        }
        if (!spawns.isEmpty()) {
            recordSpawnTemplateBatch(spawns.spawns());
        }
        return this;
    }

    @Nonnull
    @Override
    public JointCommandRecorder joint(@Nonnull JointKey jointKey) {
        assertMutable();
        MutableJointCommandRecorder joint = new MutableJointCommandRecorder(this, jointKey);
        pendingJoints.add(joint);
        return joint;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext joint(@Nonnull JointKey jointKey,
        @Nonnull Consumer<JointCommandRecorder> recipe) {
        MutableJointCommandRecorder joint = new MutableJointCommandRecorder(this, jointKey);
        try {
            Objects.requireNonNull(recipe, "recipe").accept(joint);
            joint.record();
        } finally {
            joint.seal();
        }
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext destroyJoint(@Nonnull JointKey jointKey) {
        recordDestroyJoint(jointKey);
        return this;
    }

    @Nonnull
    @Override
    public PhysicsCommandContext destroyJointBetween(@Nullable JointKey preferredJointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        recordDestroyJointBetween(preferredJointKey, spaceId, bodyA, bodyB);
        return this;
    }

    @Nonnull
    public RecordedPhysicsCommandBatch freezeInternal(long commandBatchSequence) {
        if (frozen) {
            throw new IllegalStateException("Physics command context is already frozen");
        }
        recordPendingRecorders();
        frozen = true;
        return new RecordedPhysicsCommandBatch(
            new PhysicsCommandMetadata(submittedServerTick, commandBatchSequence),
            worldEpoch,
            operations.freeze());
    }

    public boolean isEmpty() {
        return operations.size() == 0 && pendingSpawns.isEmpty() && pendingJoints.isEmpty();
    }

    private void recordPendingRecorders() {
        try {
            for (MutableRigidBodySpawnRecorder spawn : pendingSpawns) {
                spawn.validate();
            }
            for (MutableJointCommandRecorder joint : pendingJoints) {
                joint.validate();
            }
            for (MutableRigidBodySpawnRecorder spawn : pendingSpawns) {
                spawn.record();
            }
            for (MutableJointCommandRecorder joint : pendingJoints) {
                joint.record();
            }
        } finally {
            for (MutableRigidBodySpawnRecorder spawn : pendingSpawns) {
                spawn.seal();
            }
            for (MutableJointCommandRecorder joint : pendingJoints) {
                joint.seal();
            }
            pendingSpawns.clear();
            pendingJoints.clear();
        }
    }

    void recordSpawn(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        assertMutable();
        operations.addSpawn(bodyKey,
            spaceId,
            shape,
            mass,
            bodyType,
            positionX,
            positionY,
            positionZ,
            settings,
            kind,
            persistenceMode);
    }

    void recordSpawnBatch(@Nonnull RigidBodySpawnBatch spawns) {
        assertMutable();
        operations.addSpawnBatch(spawns);
    }

    void recordSpawnTemplateBatch(@Nonnull RigidBodySpawnTemplateBatch spawns) {
        assertMutable();
        operations.addSpawnTemplateBatch(spawns);
    }

    void recordDestroyBody(@Nonnull RigidBodyKey bodyKey) {
        assertMutable();
        operations.addDestroyBody(bodyKey);
    }

    void recordSetSpaceGravity(@Nonnull SpaceId spaceId,
        float x,
        float y,
        float z) {
        assertMutable();
        operations.addSetSpaceGravity(spaceId, x, y, z);
    }

    void recordSetTransform(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        boolean activate) {
        assertMutable();
        operations.addSetTransform(bodyKey,
            positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW,
            activate);
    }

    void recordSetVelocity(@Nonnull RigidBodyKey bodyKey,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ,
        boolean activate) {
        assertMutable();
        operations.addSetVelocity(bodyKey, linearX, linearY, linearZ, angularX, angularY, angularZ, activate);
    }

    void recordSetPosition(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        boolean activate) {
        assertMutable();
        operations.addSetPosition(bodyKey, positionX, positionY, positionZ, activate);
    }

    void recordSetType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        assertMutable();
        operations.addSetType(bodyKey, bodyType, activate);
    }

    void recordActivate(@Nonnull RigidBodyKey bodyKey) {
        assertMutable();
        operations.addActivate(bodyKey);
    }

    void recordImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        assertMutable();
        operations.addImpulse(bodyKey, x, y, z, hasOffset, offsetX, offsetY, offsetZ, torque);
    }

    void recordForce(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        assertMutable();
        operations.addForce(bodyKey, x, y, z, hasOffset, offsetX, offsetY, offsetZ, torque);
    }

    void recordJoint(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB,
        @Nonnull JointType type,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ,
        float restLength,
        float stiffness,
        float damping,
        float lowerLimit,
        float upperLimit,
        boolean motorEnabled,
        float motorTargetVelocity,
        float motorMaxForce) {
        assertMutable();
        operations.addJoint(jointKey,
            spaceId,
            bodyA,
            bodyB,
            type,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            restLength,
            stiffness,
            damping,
            lowerLimit,
            upperLimit,
            motorEnabled,
            motorTargetVelocity,
            motorMaxForce);
    }

    void recordDestroyJoint(@Nonnull JointKey jointKey) {
        assertMutable();
        operations.addDestroyJoint(jointKey);
    }

    void recordDestroyJointBetween(@Nullable JointKey preferredJointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        assertMutable();
        operations.addDestroyJointBetween(preferredJointKey, spaceId, bodyA, bodyB);
    }

    private void assertMutable() {
        if (frozen) {
            throw new IllegalStateException("Physics command context is already frozen");
        }
    }
}
