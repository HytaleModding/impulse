package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRayHitSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshots;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistration;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RigidBodySpawnBatch;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RigidBodySpawnTemplateBatch;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.query.CcdSupportQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandCompletion;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import dev.hytalemodding.impulse.core.plugin.simulation.query.PhysicsQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RaycastAllQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RaycastClosestBatchQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastClosestBatchResult;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RaycastClosestQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastSegment;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RuntimeJointCountQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyPose;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RigidBodyStateQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RigidBodyStateView;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SolverCapabilityQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.SolverCapabilitySummary;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SpaceBodyCountQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SpaceSummaryQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.UnsupportedCcdSpacesQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Owner-lane translator from public simulation commands to live backend calls.
 */
public final class PhysicsSimulationExecutor implements PhysicsCommandDispatcher {

    @Nonnull
    private final PhysicsWorldRuntimeResource runtime;

    public PhysicsSimulationExecutor(@Nonnull PhysicsWorldRuntimeResource runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Nonnull
    public PhysicsCommandCompletion execute(@Nonnull RecordedPhysicsCommandBatch batch) {
        Objects.requireNonNull(batch, "batch");
        long currentWorldEpoch = runtime.commandWorldEpoch();
        if (batch.commandWorldEpoch() != currentWorldEpoch) {
            return rejectStaleBatch(batch, currentWorldEpoch);
        }
        PhysicsCommandOperations operations = batch.operations();
        List<PhysicsCommandResult> results = null;
        for (int index = 0; index < operations.size(); index++) {
            long commandSequence = index + 1L;
            try {
                dispatch(index, operations);
                if (results != null) {
                    results.add(PhysicsCommandResult.applied(batch.metadata(), commandSequence));
                }
            } catch (RuntimeException exception) {
                if (results == null) {
                    results = new ArrayList<>(operations.size());
                    for (int appliedIndex = 0; appliedIndex < index; appliedIndex++) {
                        results.add(PhysicsCommandResult.applied(batch.metadata(), appliedIndex + 1L));
                    }
                }
                results.add(PhysicsCommandResult.rejected(batch.metadata(),
                    commandSequence,
                    exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName()));
            }
        }
        return results != null
            ? PhysicsCommandCompletion.of(results)
            : PhysicsCommandCompletion.allApplied(batch.metadata(), operations.size());
    }

    @Nonnull
    private static PhysicsCommandCompletion rejectStaleBatch(@Nonnull RecordedPhysicsCommandBatch batch,
        long currentWorldEpoch) {
        String message = "stale physics command batch worldEpoch="
            + batch.commandWorldEpoch()
            + " currentWorldEpoch="
            + currentWorldEpoch;
        return PhysicsCommandCompletion.allRejected(batch.metadata(), batch.commandCount(), message);
    }

    private void dispatch(int index,
        @Nonnull PhysicsCommandOperations operations) {
        switch (operations.opcode(index)) {
            case PhysicsCommandOperations.SPAWN_RIGID_BODY -> dispatchSpawn(index, operations);
            case PhysicsCommandOperations.SPAWN_RIGID_BODY_BATCH ->
                dispatchSpawnBatch(operations.requiredObjectAt(index,
                    PhysicsCommandOperations.SPAWN_BATCH_OBJECT_SLOT,
                    RigidBodySpawnBatch.class));
            case PhysicsCommandOperations.SPAWN_RIGID_BODY_TEMPLATE_BATCH ->
                dispatchSpawnTemplateBatch(operations.requiredObjectAt(index,
                    PhysicsCommandOperations.SPAWN_TEMPLATE_BATCH_OBJECT_SLOT,
                    RigidBodySpawnTemplateBatch.class));
            case PhysicsCommandOperations.DESTROY_RIGID_BODY -> destroyRigidBody(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                    RigidBodyKey.class));
            case PhysicsCommandOperations.SET_SPACE_GRAVITY -> setSpaceGravity(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.SET_SPACE_GRAVITY_SPACE_ID_OBJECT_SLOT,
                    SpaceId.class),
                operations.floatAt(index, PhysicsCommandOperations.SET_SPACE_GRAVITY_X_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_SPACE_GRAVITY_Y_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_SPACE_GRAVITY_Z_FLOAT_SLOT));
            case PhysicsCommandOperations.SET_RIGID_BODY_TRANSFORM -> setRigidBodyTransform(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                    RigidBodyKey.class),
                operations.floatAt(index, PhysicsCommandOperations.SET_TRANSFORM_POSITION_X_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_TRANSFORM_POSITION_Y_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_TRANSFORM_POSITION_Z_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_TRANSFORM_ROTATION_X_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_TRANSFORM_ROTATION_Y_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_TRANSFORM_ROTATION_Z_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_TRANSFORM_ROTATION_W_FLOAT_SLOT),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_ACTIVATE) != 0);
            case PhysicsCommandOperations.SET_RIGID_BODY_POSITION -> setRigidBodyPosition(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                    RigidBodyKey.class),
                operations.floatAt(index, PhysicsCommandOperations.SET_POSITION_X_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_POSITION_Y_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_POSITION_Z_FLOAT_SLOT),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_ACTIVATE) != 0);
            case PhysicsCommandOperations.SET_RIGID_BODY_VELOCITY -> setRigidBodyVelocity(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                    RigidBodyKey.class),
                operations.floatAt(index, PhysicsCommandOperations.SET_VELOCITY_LINEAR_X_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_VELOCITY_LINEAR_Y_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_VELOCITY_LINEAR_Z_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_VELOCITY_ANGULAR_X_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_VELOCITY_ANGULAR_Y_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.SET_VELOCITY_ANGULAR_Z_FLOAT_SLOT),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_ACTIVATE) != 0);
            case PhysicsCommandOperations.SET_RIGID_BODY_TYPE -> setRigidBodyType(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.SET_TYPE_BODY_KEY_OBJECT_SLOT,
                    RigidBodyKey.class),
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.SET_TYPE_BODY_TYPE_OBJECT_SLOT,
                    PhysicsBodyType.class),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_ACTIVATE) != 0);
            case PhysicsCommandOperations.ACTIVATE_RIGID_BODY -> activateRigidBody(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                    RigidBodyKey.class));
            case PhysicsCommandOperations.APPLY_RIGID_BODY_IMPULSE -> applyRigidBodyImpulse(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                    RigidBodyKey.class),
                operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_X_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_Y_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_Z_FLOAT_SLOT),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_OFFSET_X_FLOAT_SLOT)
                    : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_OFFSET_Y_FLOAT_SLOT)
                    : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_OFFSET_Z_FLOAT_SLOT)
                    : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_TORQUE) != 0);
            case PhysicsCommandOperations.APPLY_RIGID_BODY_FORCE -> applyRigidBodyForce(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.BODY_COMMAND_BODY_KEY_OBJECT_SLOT,
                    RigidBodyKey.class),
                operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_X_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_Y_FLOAT_SLOT),
                operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_Z_FLOAT_SLOT),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_OFFSET_X_FLOAT_SLOT)
                    : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_OFFSET_Y_FLOAT_SLOT)
                    : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, PhysicsCommandOperations.VECTOR_COMMAND_OFFSET_Z_FLOAT_SLOT)
                    : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_TORQUE) != 0);
            case PhysicsCommandOperations.CREATE_JOINT -> dispatchJoint(index, operations);
            case PhysicsCommandOperations.DESTROY_JOINT -> destroyJoint(
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.DESTROY_JOINT_KEY_OBJECT_SLOT,
                    JointKey.class));
            case PhysicsCommandOperations.DESTROY_JOINT_BETWEEN_BODIES -> destroyJointBetween(
                (JointKey) operations.objectAt(index,
                    PhysicsCommandOperations.DESTROY_JOINT_BETWEEN_PREFERRED_KEY_OBJECT_SLOT),
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.DESTROY_JOINT_BETWEEN_SPACE_ID_OBJECT_SLOT,
                    SpaceId.class),
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.DESTROY_JOINT_BETWEEN_BODY_A_OBJECT_SLOT,
                    RigidBodyKey.class),
                operations.requiredObjectAt(index,
                    PhysicsCommandOperations.DESTROY_JOINT_BETWEEN_BODY_B_OBJECT_SLOT,
                    RigidBodyKey.class));
            default -> throw new IllegalArgumentException("Unsupported physics command opcode "
                + operations.opcode(index));
        }
    }

    private void dispatchSpawn(int index,
        @Nonnull PhysicsCommandOperations operations) {
        spawnRigidBody(operations.requiredObjectAt(index,
                PhysicsCommandOperations.SPAWN_BODY_KEY_OBJECT_SLOT,
                RigidBodyKey.class),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.SPAWN_SPACE_ID_OBJECT_SLOT,
                SpaceId.class),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.SPAWN_SHAPE_OBJECT_SLOT,
                PhysicsShapeSpec.class),
            operations.floatAt(index, PhysicsCommandOperations.SPAWN_MASS_FLOAT_SLOT),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.SPAWN_BODY_TYPE_OBJECT_SLOT,
                PhysicsBodyType.class),
            operations.floatAt(index, PhysicsCommandOperations.SPAWN_POSITION_X_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.SPAWN_POSITION_Y_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.SPAWN_POSITION_Z_FLOAT_SLOT),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.SPAWN_SETTINGS_OBJECT_SLOT,
                RigidBodySpawnSettings.class),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.SPAWN_KIND_OBJECT_SLOT,
                PhysicsBodyKind.class),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.SPAWN_PERSISTENCE_MODE_OBJECT_SLOT,
                PhysicsBodyPersistenceMode.class));
    }

    private void dispatchSpawnBatch(@Nonnull RigidBodySpawnBatch batch) {
        for (int index = 0; index < batch.size(); index++) {
            spawnRigidBody(batch.bodyKey(index),
                batch.spaceId(index),
                batch.shape(index),
                batch.mass(index),
                batch.bodyType(index),
                batch.positionX(index),
                batch.positionY(index),
                batch.positionZ(index),
                batch.settings(index),
                batch.kind(index),
                batch.persistenceMode(index));
        }
    }

    private void dispatchSpawnTemplateBatch(@Nonnull RigidBodySpawnTemplateBatch batch) {
        spawnRigidBodies(batch.size(),
            batch.bodyKeyMostSignificantBits(),
            batch.bodyKeyLeastSignificantBits(),
            batch.spaceId(),
            batch.shape(),
            batch.mass(),
            batch.bodyType(),
            batch.positions(),
            batch.settings(),
            batch.kind(),
            batch.persistenceMode());
    }

    private void dispatchJoint(int index,
        @Nonnull PhysicsCommandOperations operations) {
        createJoint(operations.requiredObjectAt(index,
                PhysicsCommandOperations.CREATE_JOINT_JOINT_KEY_OBJECT_SLOT,
                JointKey.class),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.CREATE_JOINT_SPACE_ID_OBJECT_SLOT,
                SpaceId.class),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.CREATE_JOINT_BODY_A_OBJECT_SLOT,
                RigidBodyKey.class),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.CREATE_JOINT_BODY_B_OBJECT_SLOT,
                RigidBodyKey.class),
            operations.requiredObjectAt(index,
                PhysicsCommandOperations.CREATE_JOINT_TYPE_OBJECT_SLOT,
                JointType.class),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_ANCHOR_A_X_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_ANCHOR_A_Y_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_ANCHOR_A_Z_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_ANCHOR_B_X_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_ANCHOR_B_Y_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_ANCHOR_B_Z_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_AXIS_X_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_AXIS_Y_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_AXIS_Z_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_REST_LENGTH_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_STIFFNESS_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_DAMPING_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_LOWER_LIMIT_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_UPPER_LIMIT_FLOAT_SLOT),
            (operations.flags(index) & PhysicsCommandOperations.FLAG_MOTOR_ENABLED) != 0,
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_MOTOR_TARGET_VELOCITY_FLOAT_SLOT),
            operations.floatAt(index, PhysicsCommandOperations.CREATE_JOINT_MOTOR_MAX_FORCE_FLOAT_SLOT));
    }

    @Nonnull
    public <R> R query(@Nonnull PhysicsQuery<R> query) {
        Objects.requireNonNull(query, "query");
        Object result = switch (query) {
            case RaycastClosestQuery raycast -> raycastClosest(raycast);
            case RaycastClosestBatchQuery raycasts -> raycastClosestBatch(raycasts);
            case RaycastAllQuery raycast -> raycastAll(raycast);
            case SpaceBodyCountQuery count -> spaceBodyCount(count);
            case SpaceSummaryQuery summary -> spaceSummary(summary);
            case CcdSupportQuery ignored -> ccdSupported();
            case UnsupportedCcdSpacesQuery ignored -> unsupportedCcdSpaces();
            case SolverCapabilityQuery solver -> solverCapability(solver);
            case RigidBodyStateQuery state -> rigidBodyState(state);
            case RuntimeJointCountQuery ignored -> runtimeJointCount();
        };
        @SuppressWarnings("unchecked")
        R typed = (R) result;
        return typed;
    }

    @Override
    public void spawnRigidBody(@Nonnull RigidBodyKey bodyKey,
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
        if (runtime.getRegistration(bodyKey) != null) {
            throw new IllegalArgumentException("Rigid body key=" + bodyKey
                + " is already registered");
        }
        PhysicsSpaceBinding space = requireSpace(spaceId);
        spawnRigidBody(space,
            bodyKey,
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

    @Override
    public void spawnRigidBodies(int bodyCount,
        @Nonnull long[] bodyKeyMostSignificantBits,
        @Nonnull long[] bodyKeyLeastSignificantBits,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull float[] positions,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsSpaceBinding space = requireSpace(spaceId);
        for (int index = 0; index < bodyCount; index++) {
            RigidBodyKey bodyKey = RigidBodyKey.of(bodyKeyMostSignificantBits[index],
                bodyKeyLeastSignificantBits[index]);
            if (runtime.getRegistration(bodyKey) != null) {
                throw new IllegalArgumentException("Rigid body key=" + bodyKey
                    + " is already registered");
            }
            int positionOffset = index * 3;
            spawnRigidBody(space,
                bodyKey,
                spaceId,
                shape,
                mass,
                bodyType,
                positions[positionOffset],
                positions[positionOffset + 1],
                positions[positionOffset + 2],
                settings,
                kind,
                persistenceMode);
        }
    }

    private void spawnRigidBody(@Nonnull PhysicsSpaceBinding space,
        @Nonnull RigidBodyKey bodyKey,
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
        BackendBodyHandle backendBodyHandle = createRuntimeBody(space,
            shape,
            mass,
            bodyType,
            positionX,
            positionY,
            positionZ);
        applySpawnSettings(space, backendBodyHandle, settings);
        runtime.addBodyOnOwner(bodyKey,
            spaceId,
            backendBodyHandle,
            kind,
            persistenceMode);
    }

    private void applySpawnSettings(@Nonnull PhysicsSpaceBinding space,
        @Nonnull BackendBodyHandle backendBodyHandle,
        @Nonnull RigidBodySpawnSettings settings) {
        long backendBodyId = backendBodyHandle.value();
        if (settings.hasFriction()) {
            space.runtime().setBodyFriction(space.backendSpaceHandle().value(), backendBodyId, settings.friction());
        }
        if (settings.hasRestitution()) {
            space.runtime().setBodyRestitution(space.backendSpaceHandle().value(), backendBodyId, settings.restitution());
        }
        if (settings.hasLinearDamping()) {
            space.runtime().setBodyDamping(space.backendSpaceHandle().value(),
                backendBodyId,
                settings.linearDamping(),
                settings.hasAngularDamping() ? settings.angularDamping() : 0.0f);
        } else if (settings.hasAngularDamping()) {
            space.runtime().setBodyDamping(space.backendSpaceHandle().value(), backendBodyId, 0.0f, settings.angularDamping());
        }
        if (settings.hasCollisionFilter()) {
            space.runtime()
                .setBodyCollisionFilter(space.backendSpaceHandle().value(),
                    backendBodyId,
                    settings.collisionGroup(),
                    settings.collisionMask());
        }
        if (settings.hasSensor()) {
            space.runtime().setBodySensor(space.backendSpaceHandle().value(), backendBodyId, settings.sensor());
        }
    }

    @Nonnull
    private BackendBodyHandle createRuntimeBody(@Nonnull PhysicsSpaceBinding space,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ) {
        long backendBodyId = space.runtime().createBody(space.backendSpaceHandle().value(),
            BackendRuntimeCodes.shapeTypeCode(shape.type()),
            shape.halfExtentX(),
            shape.halfExtentY(),
            shape.halfExtentZ(),
            shape.radius(),
            shape.halfHeight(),
            BackendRuntimeCodes.axisCode(shape.axis()),
            shape.groundY(),
            mass,
            BackendRuntimeCodes.bodyTypeCode(bodyType),
            positionX,
            positionY,
            positionZ,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        return new BackendBodyHandle(backendBodyId);
    }

    @Override
    public void destroyRigidBody(@Nonnull RigidBodyKey bodyKey) {
        runtime.destroyBody(bodyKey);
    }

    @Override
    public void setSpaceGravity(@Nonnull SpaceId spaceId,
        float x,
        float y,
        float z) {
        PhysicsSpaceBinding space = requireSpace(spaceId);
        space.runtime().setGravity(space.backendSpaceHandle().value(), x, y, z);
    }

    @Override
    public void setRigidBodyTransform(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        boolean activate) {
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        PhysicsSpaceBinding space = requireSpace(registration.spaceId());
        space.runtime().setBodyTransform(space.backendSpaceHandle().value(),
            registration.backendBodyHandle().value(),
            positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW);
        if (activate) {
            space.runtime().activateBody(space.backendSpaceHandle().value(), registration.backendBodyHandle().value());
        }
    }

    @Override
    public void setRigidBodyPosition(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        boolean activate) {
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        PhysicsSpaceBinding space = requireSpace(registration.spaceId());
        space.runtime().setBodyPosition(space.backendSpaceHandle().value(),
            registration.backendBodyHandle().value(),
            positionX,
            positionY,
            positionZ);
        if (activate) {
            space.runtime().activateBody(space.backendSpaceHandle().value(), registration.backendBodyHandle().value());
        }
    }

    @Override
    public void setRigidBodyVelocity(@Nonnull RigidBodyKey bodyKey,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ,
        boolean activate) {
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        PhysicsSpaceBinding space = requireSpace(registration.spaceId());
        space.runtime().setBodyVelocity(space.backendSpaceHandle().value(),
            registration.backendBodyHandle().value(),
            linearX,
            linearY,
            linearZ,
            angularX,
            angularY,
            angularZ);
        if (activate) {
            space.runtime().activateBody(space.backendSpaceHandle().value(), registration.backendBodyHandle().value());
        }
    }

    @Override
    public void setRigidBodyType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        PhysicsSpaceBinding space = requireSpace(registration.spaceId());
        space.runtime().setBodyType(space.backendSpaceHandle().value(),
            registration.backendBodyHandle().value(),
            BackendRuntimeCodes.bodyTypeCode(bodyType));
        if (activate) {
            space.runtime().activateBody(space.backendSpaceHandle().value(), registration.backendBodyHandle().value());
        }
    }

    @Override
    public void activateRigidBody(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        PhysicsSpaceBinding space = requireSpace(registration.spaceId());
        space.runtime().activateBody(space.backendSpaceHandle().value(), registration.backendBodyHandle().value());
    }

    @Override
    public void applyRigidBodyImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        PhysicsSpaceBinding space = requireSpace(registration.spaceId());
        space.runtime().applyBodyImpulse(space.backendSpaceHandle().value(),
            registration.backendBodyHandle().value(),
            x,
            y,
            z,
            hasOffset,
            offsetX,
            offsetY,
            offsetZ,
            torque);
        space.runtime().activateBody(space.backendSpaceHandle().value(), registration.backendBodyHandle().value());
    }

    @Override
    public void applyRigidBodyForce(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque) {
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        PhysicsSpaceBinding space = requireSpace(registration.spaceId());
        space.runtime().applyBodyForce(space.backendSpaceHandle().value(),
            registration.backendBodyHandle().value(),
            x,
            y,
            z,
            hasOffset,
            offsetX,
            offsetY,
            offsetZ,
            torque);
        space.runtime().activateBody(space.backendSpaceHandle().value(), registration.backendBodyHandle().value());
    }

    @Override
    public void createJoint(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyAKey,
        @Nonnull RigidBodyKey bodyBKey,
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
        PhysicsSpaceBinding space = requireSpace(spaceId);
        PhysicsBodyRegistration bodyA = requireBodyRegistration(bodyAKey);
        PhysicsBodyRegistration bodyB = requireBodyRegistration(bodyBKey);
        if (!bodyA.spaceId().equals(spaceId) || !bodyB.spaceId().equals(spaceId)) {
            throw new IllegalArgumentException("Joint bodies must both be registered in space " + spaceId);
        }
        BackendJointHandle backendJointHandle = new BackendJointHandle(space.runtime().createJoint(
            space.backendSpaceHandle().value(),
            toRuntimeJointTypeCode(type),
            bodyA.backendBodyHandle().value(),
            bodyB.backendBodyHandle().value(),
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
            motorMaxForce));
        runtime.addJointOnOwner(jointKey,
            spaceId,
            backendJointHandle,
            bodyAKey,
            bodyBKey,
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

    @Override
    public void destroyJoint(@Nonnull JointKey jointKey) {
        runtime.removeJoint(jointKey);
    }

    @Override
    public void destroyJointBetween(@Nullable JointKey preferredJointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        if (preferredJointKey != null && runtime.removeJoint(preferredJointKey)) {
            return;
        }
        PhysicsJointRegistration registration = runtime.findJointBetween(spaceId, bodyA, bodyB);
        if (registration != null) {
            runtime.removeJoint(registration.jointKey());
        }
    }

    @Nonnull
    private Optional<RaycastHitView> raycastClosest(@Nonnull RaycastClosestQuery query) {
        PhysicsSpaceBinding space = requireSpace(query.spaceId());
        RayHitCapture hit = new RayHitCapture();
        Vector3f from = query.from();
        Vector3f to = query.to();
        boolean hitFound = space.runtime().raycastClosest(space.backendSpaceHandle().value(),
            from.x,
            from.y,
            from.z,
            to.x,
            to.y,
            to.z,
            hit);
        return hitFound && hit.captured ? Optional.of(toView(hit)) : Optional.empty();
    }

    @Nonnull
    private RaycastClosestBatchResult raycastClosestBatch(@Nonnull RaycastClosestBatchQuery query) {
        PhysicsSpaceBinding space = requireSpace(query.spaceId());
        int rayCount = query.rayCount();
        RaycastHitView[] hits = new RaycastHitView[rayCount];
        Vector3f from = new Vector3f();
        Vector3f to = new Vector3f();
        for (int index = 0; index < rayCount; index++) {
            RaycastSegment ray = query.ray(index);
            ray.copyFrom(from);
            ray.copyTo(to);
            int rayIndex = index;
            space.runtime().raycastClosest(space.backendSpaceHandle().value(),
                from.x,
                from.y,
                from.z,
                to.x,
                to.y,
                to.z,
                (bodyId,
                    pointX,
                    pointY,
                    pointZ,
                    normalX,
                    normalY,
                    normalZ,
                    fraction,
                    distance) -> hits[rayIndex] = toView(bodyId,
                    pointX,
                    pointY,
                    pointZ,
                    normalX,
                    normalY,
                    normalZ,
                    fraction,
                    distance));
        }
        return new RaycastClosestBatchResult(hits);
    }

    @Nonnull
    private List<RaycastHitView> raycastAll(@Nonnull RaycastAllQuery query) {
        PhysicsSpaceBinding space = requireSpace(query.spaceId());
        Vector3f from = query.from();
        Vector3f to = query.to();
        List<RaycastHitView> views = new ArrayList<>();
        space.runtime().raycastAll(space.backendSpaceHandle().value(),
            from.x,
            from.y,
            from.z,
            to.x,
            to.y,
            to.z,
            (bodyId,
                pointX,
                pointY,
                pointZ,
                normalX,
                normalY,
                normalZ,
                fraction,
                distance) -> views.add(toView(bodyId,
                pointX,
                pointY,
                pointZ,
                normalX,
                normalY,
                normalZ,
                fraction,
                distance)));
        if (views.isEmpty()) {
            return List.of();
        }
        return List.copyOf(views);
    }

    private int spaceBodyCount(@Nonnull SpaceBodyCountQuery query) {
        PhysicsSpaceBinding space = runtime.getSpaceBinding(query.spaceId());
        return space != null ? space.runtime().bodyCount(space.backendSpaceHandle().value()) : 0;
    }

    @Nonnull
    private List<SpaceSummary> spaceSummary(@Nonnull SpaceSummaryQuery query) {
        List<SpaceSummary> summaries = new ArrayList<>();
        if (query.spaceId() != null) {
            PhysicsSpaceBinding space = runtime.getSpaceBinding(query.spaceId());
            if (space != null) {
                summaries.add(summary(space));
            }
            return List.copyOf(summaries);
        }
        for (PhysicsSpaceBinding space : runtime.getSpaceBindings()) {
            summaries.add(summary(space));
        }
        return List.copyOf(summaries);
    }

    private boolean ccdSupported() {
        for (PhysicsSpaceBinding space : runtime.getSpaceBindings()) {
            if (space.runtime().supportsContinuousCollision(space.backendSpaceHandle().value())) {
                return true;
            }
        }
        return false;
    }

    private int runtimeJointCount() {
        int count = 0;
        for (PhysicsSpaceBinding space : runtime.getSpaceBindings()) {
            count += space.runtime().jointCount(space.backendSpaceHandle().value());
        }
        return count;
    }

    @Nonnull
    private Optional<RigidBodyStateView> rigidBodyState(@Nonnull RigidBodyStateQuery query) {
        PhysicsBodyRegistration registration = runtime.getRegistration(query.bodyKey());
        if (registration == null) {
            return Optional.empty();
        }
        PhysicsSpaceBinding space = requireSpace(registration.spaceId());
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshots.read(space, registration.backendBodyHandle().value());
        if (snapshot == null) {
            return Optional.empty();
        }
        return Optional.of(new RigidBodyStateView(query.bodyKey(),
            snapshot.bodyType(),
            RigidBodyPose.of(snapshot.position(), snapshot.rotation())));
    }

    @Nonnull
    private SolverCapabilitySummary solverCapability(@Nonnull SolverCapabilityQuery query) {
        PhysicsSpaceBinding space = requireSpace(query.spaceId());
        return new SolverCapabilitySummary(space.spaceId(),
            space.backendId().value(),
            space.runtime().supportsSolverTuning(space.backendSpaceHandle().value()),
            space.runtime().supportsActivationTuning(space.backendSpaceHandle().value()));
    }

    @Nonnull
    private List<SpaceSummary> unsupportedCcdSpaces() {
        List<SpaceSummary> spaces = new ArrayList<>();
        for (PhysicsSpaceBinding space : runtime.getSpaceBindings()) {
            if (space.runtime().supportsContinuousCollision(space.backendSpaceHandle().value())) {
                continue;
            }
            spaces.add(summary(space));
        }
        return List.copyOf(spaces);
    }

    @Nonnull
    private SpaceSummary summary(@Nonnull PhysicsSpaceBinding space) {
        return new SpaceSummary(space.spaceId(),
            space.backendId(),
            space.runtime().bodyCount(space.backendSpaceHandle().value()),
            space.runtime().jointCount(space.backendSpaceHandle().value()));
    }

    @Nonnull
    private RaycastHitView toView(@Nonnull RayHitCapture hit) {
        return toView(hit.bodyId,
            hit.pointX,
            hit.pointY,
            hit.pointZ,
            hit.normalX,
            hit.normalY,
            hit.normalZ,
            hit.fraction,
            hit.distance);
    }

    @Nonnull
    private RaycastHitView toView(long bodyId,
        float pointX,
        float pointY,
        float pointZ,
        float normalX,
        float normalY,
        float normalZ,
        float fraction,
        float distance) {
        PhysicsBodyRegistration registration = findBodyRegistration(bodyId);
        RigidBodyKey bodyKey = registration != null ? registration.bodyKey() : null;
        PhysicsBodySnapshot snapshot = registration != null
            ? runtime.getBodySnapshot(registration.bodyKey())
            : null;
        return new RaycastHitView(bodyKey,
            snapshot != null ? snapshot.bodyType() : PhysicsBodyType.STATIC,
            new Vector3f(pointX, pointY, pointZ),
            new Vector3f(normalX, normalY, normalZ),
            snapshot != null ? snapshot.shapeType() : ShapeType.BOX,
            fraction,
            distance);
    }

    private static final class RayHitCapture implements BackendRayHitSink {

        private long bodyId;
        private float pointX;
        private float pointY;
        private float pointZ;
        private float normalX;
        private float normalY;
        private float normalZ;
        private float fraction;
        private float distance;
        private boolean captured;

        @Override
        public void accept(long bodyId,
            float pointX,
            float pointY,
            float pointZ,
            float normalX,
            float normalY,
            float normalZ,
            float fraction,
            float distance) {
            this.bodyId = bodyId;
            this.pointX = pointX;
            this.pointY = pointY;
            this.pointZ = pointZ;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
            this.fraction = fraction;
            this.distance = distance;
            captured = true;
        }
    }

    @Nonnull
    private PhysicsSpaceBinding requireSpace(@Nonnull SpaceId spaceId) {
        PhysicsSpaceBinding space = runtime.getSpaceBinding(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        return space;
    }

    @Nonnull
    private PhysicsBodyRegistration requireBodyRegistration(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodyRegistration registration = runtime.getRegistration(bodyKey);
        if (registration == null) {
            throw new IllegalArgumentException("Rigid body key=" + bodyKey + " is not registered");
        }
        return registration;
    }

    @Nullable
    private PhysicsBodyRegistration findBodyRegistration(long backendBodyId) {
        for (PhysicsBodyRegistration registration : runtime.getBodyRegistrations()) {
            if (registration.backendBodyHandle().value() == backendBodyId) {
                return registration;
            }
        }
        return null;
    }

    private static int toRuntimeJointTypeCode(@Nonnull JointType type) {
        return switch (type) {
            case FIXED -> BackendRuntimeCodes.JOINT_FIXED;
            case POINT -> BackendRuntimeCodes.JOINT_POINT;
            case HINGE -> BackendRuntimeCodes.JOINT_HINGE;
            case SLIDER -> BackendRuntimeCodes.JOINT_SLIDER;
            case SPRING -> BackendRuntimeCodes.JOINT_SPRING;
        };
    }
}
