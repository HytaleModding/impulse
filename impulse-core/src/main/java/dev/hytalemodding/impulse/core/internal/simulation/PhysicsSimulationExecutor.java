package dev.hytalemodding.impulse.core.internal.simulation;

import com.hypixel.hytale.math.util.ChunkUtil;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsRuntimeStats;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache.BuildStats;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsOwnerTransaction;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.CcdSupportQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandCompletion;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastAllQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastClosestBatchQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastClosestBatchResult;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastClosestQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastHitView;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastSegment;
import dev.hytalemodding.impulse.core.plugin.simulation.RuntimeJointCountQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyPose;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyStateQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyStateView;
import dev.hytalemodding.impulse.core.plugin.simulation.SolverCapabilityQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.SolverCapabilitySummary;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceBodyCountQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummaryQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.UnsupportedCcdSpacesQuery;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuningCapability;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Owner-thread translator from public simulation commands to live backend calls.
 */
public final class PhysicsSimulationExecutor implements PhysicsCommandDispatcher {

    private static final float CONTACT_NORMAL_SCALE = 0.75f;
    private static final float JOINT_AXIS_SCALE = 0.9f;

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
                dispatchSpawnBatch(operations.objectAt(index, 0, RigidBodySpawnBatch.class));
            case PhysicsCommandOperations.SPAWN_RIGID_BODY_TEMPLATE_BATCH ->
                dispatchSpawnTemplateBatch(operations.objectAt(index, 0, RigidBodySpawnTemplateBatch.class));
            case PhysicsCommandOperations.DESTROY_RIGID_BODY -> destroyRigidBody(
                operations.objectAt(index, 0, RigidBodyKey.class));
            case PhysicsCommandOperations.SET_SPACE_GRAVITY -> setSpaceGravity(
                operations.objectAt(index, 0, SpaceId.class),
                operations.floatAt(index, 0),
                operations.floatAt(index, 1),
                operations.floatAt(index, 2));
            case PhysicsCommandOperations.SET_RIGID_BODY_TRANSFORM -> setRigidBodyTransform(
                operations.objectAt(index, 0, RigidBodyKey.class),
                operations.floatAt(index, 0),
                operations.floatAt(index, 1),
                operations.floatAt(index, 2),
                operations.floatAt(index, 3),
                operations.floatAt(index, 4),
                operations.floatAt(index, 5),
                operations.floatAt(index, 6),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_ACTIVATE) != 0);
            case PhysicsCommandOperations.SET_RIGID_BODY_POSITION -> setRigidBodyPosition(
                operations.objectAt(index, 0, RigidBodyKey.class),
                operations.floatAt(index, 0),
                operations.floatAt(index, 1),
                operations.floatAt(index, 2),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_ACTIVATE) != 0);
            case PhysicsCommandOperations.SET_RIGID_BODY_VELOCITY -> setRigidBodyVelocity(
                operations.objectAt(index, 0, RigidBodyKey.class),
                operations.floatAt(index, 0),
                operations.floatAt(index, 1),
                operations.floatAt(index, 2),
                operations.floatAt(index, 3),
                operations.floatAt(index, 4),
                operations.floatAt(index, 5),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_ACTIVATE) != 0);
            case PhysicsCommandOperations.SET_RIGID_BODY_TYPE -> setRigidBodyType(
                operations.objectAt(index, 0, RigidBodyKey.class),
                operations.objectAt(index, 1, PhysicsBodyType.class),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_ACTIVATE) != 0);
            case PhysicsCommandOperations.ACTIVATE_RIGID_BODY -> activateRigidBody(
                operations.objectAt(index, 0, RigidBodyKey.class));
            case PhysicsCommandOperations.APPLY_RIGID_BODY_IMPULSE -> applyRigidBodyImpulse(
                operations.objectAt(index, 0, RigidBodyKey.class),
                operations.floatAt(index, 0),
                operations.floatAt(index, 1),
                operations.floatAt(index, 2),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, 3) : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, 4) : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, 5) : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_TORQUE) != 0);
            case PhysicsCommandOperations.APPLY_RIGID_BODY_FORCE -> applyRigidBodyForce(
                operations.objectAt(index, 0, RigidBodyKey.class),
                operations.floatAt(index, 0),
                operations.floatAt(index, 1),
                operations.floatAt(index, 2),
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, 3) : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, 4) : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_OFFSET) != 0
                    ? operations.floatAt(index, 5) : 0.0f,
                (operations.flags(index) & PhysicsCommandOperations.FLAG_TORQUE) != 0);
            case PhysicsCommandOperations.CREATE_JOINT -> dispatchJoint(index, operations);
            case PhysicsCommandOperations.DESTROY_JOINT -> destroyJoint(
                operations.objectAt(index, 0, JointKey.class));
            case PhysicsCommandOperations.DESTROY_JOINT_BETWEEN_BODIES -> destroyJointBetween(
                (JointKey) operations.objectAt(index, 0),
                operations.objectAt(index, 1, SpaceId.class),
                operations.objectAt(index, 2, RigidBodyKey.class),
                operations.objectAt(index, 3, RigidBodyKey.class));
            case PhysicsCommandOperations.LIVE_OWNER_TRANSACTION -> liveOwnerTransaction(
                operations.objectAt(index, 0, String.class),
                operations.objectAt(index, 1, PhysicsOwnerTransaction.class));
            default -> throw new IllegalArgumentException("Unsupported physics command opcode "
                + operations.opcode(index));
        }
    }

    private void dispatchSpawn(int index,
        @Nonnull PhysicsCommandOperations operations) {
        spawnRigidBody(operations.objectAt(index, 0, RigidBodyKey.class),
            operations.objectAt(index, 1, SpaceId.class),
            operations.objectAt(index, 2, PhysicsShapeSpec.class),
            operations.floatAt(index, 0),
            operations.objectAt(index, 3, PhysicsBodyType.class),
            operations.floatAt(index, 1),
            operations.floatAt(index, 2),
            operations.floatAt(index, 3),
            operations.objectAt(index, 4, RigidBodySpawnSettings.class),
            operations.objectAt(index, 5, PhysicsBodyKind.class),
            operations.objectAt(index, 6, PhysicsBodyPersistenceMode.class));
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
        createJoint(operations.objectAt(index, 0, JointKey.class),
            operations.objectAt(index, 1, SpaceId.class),
            operations.objectAt(index, 2, RigidBodyKey.class),
            operations.objectAt(index, 3, RigidBodyKey.class),
            operations.objectAt(index, 4, JointType.class),
            operations.floatAt(index, 0),
            operations.floatAt(index, 1),
            operations.floatAt(index, 2),
            operations.floatAt(index, 3),
            operations.floatAt(index, 4),
            operations.floatAt(index, 5),
            operations.floatAt(index, 6),
            operations.floatAt(index, 7),
            operations.floatAt(index, 8),
            operations.floatAt(index, 9),
            operations.floatAt(index, 10),
            operations.floatAt(index, 11),
            operations.floatAt(index, 12),
            operations.floatAt(index, 13),
            (operations.flags(index) & PhysicsCommandOperations.FLAG_MOTOR_ENABLED) != 0,
            operations.floatAt(index, 14),
            operations.floatAt(index, 15));
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

    @Nonnull
    public <R> R queryInternal(@Nonnull PhysicsInternalQuery<R> query) {
        Objects.requireNonNull(query, "query");
        Object result = switch (query) {
            case BenchmarkSpaceStatsQuery stats -> benchmarkSpaceStats(stats);
            case PhysicsDebugContactsQuery contacts -> debugContacts(contacts);
            case PhysicsDebugJointsQuery joints -> debugJoints(joints);
            case PhysicsSpaceRuntimeStatsQuery stats -> physicsSpaceRuntimeStats(stats);
            case WorldCollisionPrewarmEnvelopeQuery prewarm -> prewarmWorldCollisionEnvelope(prewarm);
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
        PhysicsSpace space = requireSpace(spaceId);
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
        PhysicsSpace space = requireSpace(spaceId);
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

    private void spawnRigidBody(@Nonnull PhysicsSpace space,
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
        PhysicsBody body = createBody(space, shape, mass);
        body.setBodyType(bodyType);
        body.setPosition(positionX, positionY, positionZ);
        applySpawnSettings(body, settings);
        runtime.addBodyOnOwner(bodyKey,
            spaceId,
            body,
            kind,
            persistenceMode);
    }

    private void applySpawnSettings(@Nonnull PhysicsBody body,
        @Nonnull RigidBodySpawnSettings settings) {
        if (settings.hasFriction()) {
            body.setFriction(settings.friction());
        }
        if (settings.hasRestitution()) {
            body.setRestitution(settings.restitution());
        }
        if (settings.hasLinearDamping()) {
            body.setLinearDamping(settings.linearDamping());
        }
        if (settings.hasAngularDamping()) {
            body.setAngularDamping(settings.angularDamping());
        }
        if (settings.hasCollisionFilter()) {
            body.setCollisionFilter(settings.collisionGroup(), settings.collisionMask());
        }
        if (settings.hasSensor()) {
            body.setSensor(settings.sensor());
        }
    }

    @Nonnull
    private PhysicsBody createBody(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsShapeSpec shape,
        float mass) {
        return switch (shape.type()) {
            case BOX -> {
                yield space.createBox(shape.halfExtentX(), shape.halfExtentY(), shape.halfExtentZ(), mass);
            }
            case SPHERE -> space.createSphere(shape.radius(), mass);
            case CAPSULE -> space.createCapsule(shape.radius(), shape.halfHeight(), shape.axis(), mass);
            case CYLINDER -> space.createCylinder(shape.radius(), shape.halfHeight(), shape.axis(), mass);
            case CONE -> space.createCone(shape.radius(), shape.halfHeight(), shape.axis(), mass);
            case PLANE -> space.createStaticPlane(shape.groundY());
            default -> throw new IllegalArgumentException("Unsupported rigid body shape " + shape.type());
        };
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
        requireSpace(spaceId).setGravity(x, y, z);
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
        PhysicsBody body = requireBody(bodyKey);
        body.setPosition(positionX, positionY, positionZ);
        body.setRotation(rotationX, rotationY, rotationZ, rotationW);
        if (activate) {
            body.activate();
        }
    }

    @Override
    public void setRigidBodyPosition(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        boolean activate) {
        PhysicsBody body = requireBody(bodyKey);
        body.setPosition(positionX, positionY, positionZ);
        if (activate) {
            body.activate();
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
        PhysicsBody body = requireBody(bodyKey);
        body.setLinearVelocity(linearX, linearY, linearZ);
        body.setAngularVelocity(angularX, angularY, angularZ);
        if (activate) {
            body.activate();
        }
    }

    @Override
    public void setRigidBodyType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        PhysicsBody body = requireBody(bodyKey);
        body.setBodyType(bodyType);
        if (activate) {
            body.activate();
        }
    }

    @Override
    public void activateRigidBody(@Nonnull RigidBodyKey bodyKey) {
        requireBody(bodyKey).activate();
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
        PhysicsBody body = requireBody(bodyKey);
        if (torque) {
            body.applyTorqueImpulse(x, y, z);
        } else if (hasOffset) {
            body.applyImpulse(x, y, z, offsetX, offsetY, offsetZ);
        } else {
            body.applyCentralImpulse(x, y, z);
        }
        body.activate();
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
        PhysicsBody body = requireBody(bodyKey);
        if (torque) {
            body.applyTorque(x, y, z);
        } else if (hasOffset) {
            body.applyForce(x, y, z, offsetX, offsetY, offsetZ);
        } else {
            body.applyCentralForce(x, y, z);
        }
        body.activate();
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
        PhysicsSpace space = requireSpace(spaceId);
        PhysicsBody bodyA = requireBody(bodyAKey);
        PhysicsBody bodyB = requireBody(bodyBKey);
        PhysicsJoint joint = switch (type) {
            case FIXED -> space.createFixedJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ);
            case POINT -> space.createPointJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ);
            case HINGE -> space.createHingeJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ,
                axisX,
                axisY,
                axisZ);
            case SLIDER -> space.createSliderJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ,
                axisX,
                axisY,
                axisZ);
            case SPRING -> space.createSpringJoint(bodyA,
                bodyB,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ,
                restLength,
                stiffness,
                damping);
        };
        if (type == JointType.HINGE || type == JointType.SLIDER) {
            joint.setLimits(lowerLimit, upperLimit);
            joint.setMotor(motorTargetVelocity, motorMaxForce);
            joint.setMotorEnabled(motorEnabled);
        }
        runtime.addJoint(jointKey, spaceId, joint);
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
        PhysicsSpace space = requireSpace(spaceId);
        for (PhysicsJoint joint : new ArrayList<>(space.getJoints())) {
            RigidBodyKey bodyAKey = runtime.getBodyKey(joint.getBodyA());
            RigidBodyKey bodyBKey = runtime.getBodyKey(joint.getBodyB());
            if (connects(bodyA, bodyB, bodyAKey, bodyBKey)) {
                space.removeJoint(joint);
                return;
            }
        }
    }

    @Override
    public void liveOwnerTransaction(@Nonnull String operation,
        @Nonnull PhysicsOwnerTransaction transaction) {
        try {
            runtime.runOwnerTransactionDirect(transaction);
        } catch (Exception exception) {
            throw new IllegalStateException("Live owner transaction failed: " + operation, exception);
        }
    }

    private static boolean connects(@Nonnull RigidBodyKey expectedA,
        @Nonnull RigidBodyKey expectedB,
        @Nullable RigidBodyKey actualA,
        @Nullable RigidBodyKey actualB) {
        return (expectedA.equals(actualA) && expectedB.equals(actualB))
            || (expectedA.equals(actualB) && expectedB.equals(actualA));
    }

    @Nonnull
    private Optional<RaycastHitView> raycastClosest(@Nonnull RaycastClosestQuery query) {
        Optional<PhysicsRayHit> hit = requireSpace(query.spaceId()).raycastClosest(query.from(), query.to());
        return hit.isPresent() ? Optional.of(toView(hit.get())) : Optional.empty();
    }

    @Nonnull
    private RaycastClosestBatchResult raycastClosestBatch(@Nonnull RaycastClosestBatchQuery query) {
        PhysicsSpace space = requireSpace(query.spaceId());
        RaycastHitView[] hits = new RaycastHitView[query.rays().size()];
        Vector3f from = new Vector3f();
        Vector3f to = new Vector3f();
        for (int index = 0; index < query.rays().size(); index++) {
            RaycastSegment ray = query.rays().get(index);
            Optional<PhysicsRayHit> hit = space.raycastClosest(ray.copyFrom(from), ray.copyTo(to));
            hits[index] = hit.isPresent() ? toView(hit.get()) : null;
        }
        return new RaycastClosestBatchResult(hits);
    }

    @Nonnull
    private List<RaycastHitView> raycastAll(@Nonnull RaycastAllQuery query) {
        List<PhysicsRayHit> hits = requireSpace(query.spaceId()).raycastAll(query.from(), query.to());
        if (hits.isEmpty()) {
            return List.of();
        }
        List<RaycastHitView> views = new ArrayList<>(hits.size());
        for (PhysicsRayHit hit : hits) {
            views.add(toView(hit));
        }
        return List.copyOf(views);
    }

    private int spaceBodyCount(@Nonnull SpaceBodyCountQuery query) {
        PhysicsSpace space = runtime.getSpace(query.spaceId());
        return space != null ? space.bodyCount() : 0;
    }

    @Nonnull
    private List<SpaceSummary> spaceSummary(@Nonnull SpaceSummaryQuery query) {
        List<SpaceSummary> summaries = new ArrayList<>();
        if (query.spaceId() != null) {
            PhysicsSpace space = runtime.getSpace(query.spaceId());
            if (space != null) {
                summaries.add(summary(space));
            }
            return List.copyOf(summaries);
        }
        for (PhysicsSpace space : runtime.getSpaces()) {
            summaries.add(summary(space));
        }
        return List.copyOf(summaries);
    }

    private boolean ccdSupported() {
        for (PhysicsSpace space : runtime.getSpaces()) {
            if (space.getCapability(PhysicsContinuousCollisionCapability.class).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private int runtimeJointCount() {
        int count = 0;
        for (PhysicsSpace space : runtime.getSpaces()) {
            count += space.jointCount();
        }
        return count;
    }

    @Nonnull
    private PhysicsSpaceRuntimeStatsView physicsSpaceRuntimeStats(
        @Nonnull PhysicsSpaceRuntimeStatsQuery query) {
        PhysicsSpace space = requireSpace(query.spaceId());
        WorldVoxelCollisionCache cache = runtime.worldCollisionCache();
        PhysicsSpaceRuntimeStatsAccumulator stats = new PhysicsSpaceRuntimeStatsAccumulator();
        space.forEachBody(body -> classifyRuntimeBody(stats, cache, space, body));
        stats.joints = space.jointCount();
        stats.contacts = space.contactCount();
        PhysicsRuntimeStats runtimeStats = space.getRuntimeStats();
        if (runtimeStats.available()) {
            stats.runtimeStatsAvailable = true;
            stats.runtimeBodyCount = runtimeStats.bodyCount();
            stats.runtimeColliderCount = runtimeStats.colliderCount();
            stats.runtimeActiveBodyCount = runtimeStats.activeBodyCount();
            stats.runtimeContactPairCount = runtimeStats.contactPairCount();
            stats.runtimeContactManifoldCount = runtimeStats.contactManifoldCount();
            stats.runtimeContactPointCount = runtimeStats.contactPointCount();
            stats.runtimeDynamicDynamicContactPairCount = runtimeStats.dynamicDynamicContactPairCount();
            stats.runtimeTerrainContactPairCount = runtimeStats.terrainContactPairCount();
            stats.runtimeActiveIslandCount = runtimeStats.activeIslandCount();
            stats.runtimeJointCount = runtimeStats.jointCount();
        }
        return stats.toView();
    }

    private void classifyRuntimeBody(@Nonnull PhysicsSpaceRuntimeStatsAccumulator stats,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body) {
        stats.bodies++;
        if (body.isDynamic()) {
            stats.dynamicBodies++;
            if (body.isSleeping()) {
                stats.sleepingDynamicBodies++;
            } else {
                stats.awakeDynamicBodies++;
            }
        } else if (body.isKinematic()) {
            stats.kinematicBodies++;
        } else {
            stats.staticBodies++;
        }

        PhysicsBodyRegistration registration = runtime.getBodyRegistration(body);
        if (registration != null && registration.kind() == PhysicsBodyKind.BODY) {
            if (runtime.hasBodyAttachments(registration.id())) {
                stats.entityOwnedBodies++;
            } else {
                stats.detachedBodies++;
            }
            return;
        }
        if (registration != null && registration.kind() == PhysicsBodyKind.WORLD_COLLISION) {
            stats.worldCollisionBodies++;
            return;
        }
        if (body.getShapeType() == ShapeType.PLANE) {
            stats.planeBodies++;
            return;
        }
        if (cache.containsBody(space.getId(), body)) {
            stats.worldCollisionBodies++;
            return;
        }
        stats.rawBodies++;
    }

    @Nonnull
    private BenchmarkSpaceStatsView benchmarkSpaceStats(@Nonnull BenchmarkSpaceStatsQuery query) {
        PhysicsSpace space = requireSpace(query.spaceId());
        WorldVoxelCollisionCache cache = runtime.worldCollisionCache();
        BenchmarkSpaceStatsAccumulator stats = new BenchmarkSpaceStatsAccumulator();
        space.forEachBody(body -> classifyBenchmarkBody(stats, cache, space, body, query));
        return stats.toView();
    }

    private void classifyBenchmarkBody(@Nonnull BenchmarkSpaceStatsAccumulator stats,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull BenchmarkSpaceStatsQuery query) {
        stats.bodies++;
        if (body.isDynamic()) {
            stats.dynamicBodies++;
            Vector3f position = body.getPosition();
            stats.minDynamicBodyY = Math.min(stats.minDynamicBodyY, position.y);
            stats.maxDynamicBodyY = Math.max(stats.maxDynamicBodyY, position.y);
            if (position.y < query.groundY() - query.belowPlaneTolerance()) {
                stats.belowPlaneBodies++;
            }
            if (query.includeTerrainProbe()) {
                WorldVoxelCollisionCache.GroundProbe ground = cache.probeGround(space.getId(),
                    position.x,
                    position.z,
                    horizontalHalfExtent(body));
                if (ground.found()) {
                    stats.terrainBaselineBodies++;
                    double bottomClearance = position.y - verticalHalfExtent(body) - ground.topY();
                    stats.minTerrainBottomClearance =
                        Math.min(stats.minTerrainBottomClearance, bottomClearance);
                    if (bottomClearance < -query.belowPlaneTolerance()) {
                        stats.belowTerrainBodies++;
                    }
                } else {
                    stats.missingTerrainBaselineBodies++;
                }
            }
            if (position.y < query.bodyWorldMinY()) {
                stats.belowWorldMinBodies++;
            }
            if (position.y < query.bodyVoidY()) {
                stats.belowVoidBodies++;
            }
            if (body.isSleeping()) {
                stats.sleepingDynamicBodies++;
            } else {
                stats.awakeDynamicBodies++;
            }
        }

        PhysicsBodyRegistration registration = runtime.getBodyRegistration(body);
        if (registration != null && registration.kind() == PhysicsBodyKind.BODY) {
            if (!runtime.hasBodyAttachments(registration.id())) {
                stats.detachedBodies++;
            }
            return;
        }
        if (body.getShapeType() == ShapeType.PLANE) {
            return;
        }
        if (cache.containsBody(space.getId(), body)) {
            stats.worldCollisionBodies++;
            return;
        }
        stats.rawBodies++;
    }

    @Nonnull
    private WorldCollisionPrewarmStats prewarmWorldCollisionEnvelope(
        @Nonnull WorldCollisionPrewarmEnvelopeQuery query) {
        PhysicsSpace space = requireSpace(query.spaceId());
        WorldVoxelCollisionCache cache = runtime.worldCollisionCache();
        LongSet visitedSections = new LongOpenHashSet();
        Set<StreamingPrewarmTarget> visitedTargets = new ObjectOpenHashSet<>();
        BuildStats total = BuildStats.empty();
        for (int index = 0; index < Math.max(0, query.count()); index++) {
            double positionX = query.originX() + (index % query.side()) * query.spacing();
            double positionY = query.originY();
            double positionZ = query.originZ() + (index / query.side()) * query.spacing();
            total = total.plus(prewarmStreamingCollisionEnvelope(cache,
                space,
                query,
                positionX,
                positionY,
                positionZ,
                visitedSections,
                visitedTargets));
        }
        return new WorldCollisionPrewarmStats(visitedSections.size(), worldCollisionStats(total));
    }

    @Nonnull
    private BuildStats prewarmStreamingCollisionEnvelope(
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull PhysicsSpace space,
        @Nonnull WorldCollisionPrewarmEnvelopeQuery query,
        double positionX,
        double positionY,
        double positionZ,
        @Nonnull LongSet visitedSections,
        @Nonnull Set<StreamingPrewarmTarget> visitedTargets) {
        BuildStats total = BuildStats.empty();
        double halo = query.horizontalDriftHaloBlocks();
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                total = total.plus(prewarmStreamingCollisionEnvelopeAt(cache,
                    space,
                    query,
                    positionX + offsetX * halo,
                    positionY,
                    positionZ + offsetZ * halo,
                    visitedSections,
                    visitedTargets));
            }
        }
        return total;
    }

    @Nonnull
    private BuildStats prewarmStreamingCollisionEnvelopeAt(
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull PhysicsSpace space,
        @Nonnull WorldCollisionPrewarmEnvelopeQuery query,
        double positionX,
        double positionY,
        double positionZ,
        @Nonnull LongSet visitedSections,
        @Nonnull Set<StreamingPrewarmTarget> visitedTargets) {
        double step = Math.max(1.0, query.radius() * 2.0);
        double minCenterY = Math.min(positionY, query.fallEnvelopeMinY() + query.radius());
        BuildStats total = BuildStats.empty();
        double lastY = Double.NaN;
        for (double y = positionY; y >= minCenterY; y -= step) {
            total = total.plus(prewarmStreamingCollisionTarget(cache,
                space,
                query,
                positionX,
                y,
                positionZ,
                visitedSections,
                visitedTargets));
            lastY = y;
        }
        if (Double.isNaN(lastY) || lastY > minCenterY) {
            total = total.plus(prewarmStreamingCollisionTarget(cache,
                space,
                query,
                positionX,
                minCenterY,
                positionZ,
                visitedSections,
                visitedTargets));
        }
        return total;
    }

    @Nonnull
    private BuildStats prewarmStreamingCollisionTarget(
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull PhysicsSpace space,
        @Nonnull WorldCollisionPrewarmEnvelopeQuery query,
        double centerX,
        double centerY,
        double centerZ,
        @Nonnull LongSet visitedSections,
        @Nonnull Set<StreamingPrewarmTarget> visitedTargets) {
        StreamingPrewarmTarget target = streamingPrewarmTarget(centerX,
            centerY,
            centerZ,
            query.radius());
        if (!visitedTargets.add(target)) {
            return BuildStats.empty();
        }
        return cache.ensureAround(query.world(),
            space,
            new org.joml.Vector3d(centerX, centerY, centerZ),
            query.radius(),
            query.tick(),
            null,
            visitedSections);
    }

    @Nonnull
    private static StreamingPrewarmTarget streamingPrewarmTarget(double centerX,
        double centerY,
        double centerZ,
        int radius) {
        int minX = (int) Math.floor(centerX) - radius;
        int maxX = (int) Math.floor(centerX) + radius;
        int minY = Math.max(0, (int) Math.floor(centerY) - radius);
        int maxY = Math.min(ChunkUtil.HEIGHT_MINUS_1, (int) Math.floor(centerY) + radius);
        int minZ = (int) Math.floor(centerZ) - radius;
        int maxZ = (int) Math.floor(centerZ) + radius;
        return new StreamingPrewarmTarget(ChunkUtil.chunkCoordinate(minX),
            ChunkUtil.chunkCoordinate(maxX),
            ChunkUtil.indexSection(minY),
            ChunkUtil.indexSection(maxY),
            ChunkUtil.chunkCoordinate(minZ),
            ChunkUtil.chunkCoordinate(maxZ));
    }

    @Nonnull
    private static WorldCollisionBuildStats worldCollisionStats(@Nonnull BuildStats stats) {
        return new WorldCollisionBuildStats(stats.scannedBlocks(),
            stats.solidBlocks(),
            stats.culledInteriorBlocks(),
            stats.fullCubeRuns(),
            stats.detailBoxes(),
            stats.colliderBodies(),
            stats.removedBodies(),
            stats.sectionsBuilt(),
            stats.sectionsRebuilt(),
            stats.voxelBodies());
    }

    private static double horizontalHalfExtent(@Nonnull PhysicsBody body) {
        if (body.getShapeType() == ShapeType.BOX) {
            Vector3f halfExtents = body.getBoxHalfExtents();
            if (halfExtents != null) {
                return Math.max(finitePositive(halfExtents.x), finitePositive(halfExtents.z));
            }
        }
        return Math.max(finitePositive(body.getSphereRadius()), finitePositive(body.getHalfHeight()));
    }

    private static double verticalHalfExtent(@Nonnull PhysicsBody body) {
        if (body.getShapeType() == ShapeType.BOX) {
            Vector3f halfExtents = body.getBoxHalfExtents();
            if (halfExtents != null) {
                return finitePositive(halfExtents.y);
            }
        }
        if (body.getShapeType() == ShapeType.SPHERE) {
            return finitePositive(body.getSphereRadius());
        }
        return finitePositive(body.getHalfHeight()) + finitePositive(body.getSphereRadius());
    }

    private static double finitePositive(float value) {
        return Float.isFinite(value) && value > 0.0f ? value : 0.0;
    }

    @Nonnull
    private List<PhysicsDebugContactView> debugContacts(@Nonnull PhysicsDebugContactsQuery query) {
        int limit = Math.max(0, query.maxContacts());
        if (limit == 0) {
            return List.of();
        }

        PhysicsSpace space = requireSpace(query.spaceId());
        double maxDistanceSquared = query.viewRadius() * query.viewRadius();
        List<PhysicsDebugContactView> visible = new ArrayList<>(Math.min(limit, 64));
        for (PhysicsContact contact : space.getContacts()) {
            if (visible.size() >= limit) {
                break;
            }

            Vector3f point = contact.pointOnB();
            if (distanceSquared(point.x,
                point.y,
                point.z,
                query.viewerX(),
                query.viewerY(),
                query.viewerZ()) > maxDistanceSquared) {
                continue;
            }

            visible.add(toDebugContactView(contact, point));
        }
        return List.copyOf(visible);
    }

    @Nonnull
    private List<PhysicsDebugJointView> debugJoints(@Nonnull PhysicsDebugJointsQuery query) {
        int limit = Math.max(0, query.maxJoints());
        if (limit == 0) {
            return List.of();
        }

        PhysicsSpace space = requireSpace(query.spaceId());
        double maxDistanceSquared = query.viewRadius() * query.viewRadius();
        List<PhysicsDebugJointView> visible = new ArrayList<>(Math.min(limit, 64));
        space.forEachJoint(joint -> {
            if (visible.size() >= limit) {
                return;
            }

            PhysicsDebugJointView view = toDebugJointView(joint);
            double midpointX = (view.anchorAX() + view.anchorBX()) * 0.5;
            double midpointY = (view.anchorAY() + view.anchorBY()) * 0.5;
            double midpointZ = (view.anchorAZ() + view.anchorBZ()) * 0.5;
            if (distanceSquared(midpointX,
                midpointY,
                midpointZ,
                query.viewerX(),
                query.viewerY(),
                query.viewerZ()) > maxDistanceSquared) {
                return;
            }

            visible.add(view);
        });
        return List.copyOf(visible);
    }

    @Nonnull
    private static PhysicsDebugContactView toDebugContactView(@Nonnull PhysicsContact contact,
        @Nonnull Vector3f point) {
        Vector3f normal = contact.normalOnB();
        if (normal.lengthSquared() <= 0.0f) {
            return new PhysicsDebugContactView(point.x,
                point.y,
                point.z,
                false,
                0.0f,
                0.0f,
                0.0f);
        }

        float magnitude = Math.max(CONTACT_NORMAL_SCALE, Math.abs(contact.impulse()) * 0.05f);
        normal.normalize().mul(magnitude);
        return new PhysicsDebugContactView(point.x,
            point.y,
            point.z,
            true,
            normal.x,
            normal.y,
            normal.z);
    }

    @Nonnull
    private static PhysicsDebugJointView toDebugJointView(@Nonnull PhysicsJoint joint) {
        Vector3f anchorA = worldAnchor(joint.getBodyA(), joint.getAnchorA());
        Vector3f anchorB = worldAnchor(joint.getBodyB(), joint.getAnchorB());
        Vector3f axis = joint.getAxis();
        if (axis == null || axis.lengthSquared() <= 0.0f) {
            return new PhysicsDebugJointView(anchorA.x,
                anchorA.y,
                anchorA.z,
                anchorB.x,
                anchorB.y,
                anchorB.z,
                false,
                0.0f,
                0.0f,
                0.0f);
        }

        Vector3f worldAxis = new Vector3f(axis).normalize().mul(JOINT_AXIS_SCALE);
        joint.getBodyA().getRotation().transform(worldAxis);
        return new PhysicsDebugJointView(anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            true,
            worldAxis.x,
            worldAxis.y,
            worldAxis.z);
    }

    @Nonnull
    private static Vector3f worldAnchor(@Nonnull PhysicsBody body,
        @Nonnull Vector3f localAnchor) {
        Vector3f anchor = new Vector3f(localAnchor);
        Quaternionf rotation = body.getRotation();
        rotation.transform(anchor);
        Vector3f position = body.getPosition();
        return anchor.add(position);
    }

    private static double distanceSquared(double x,
        double y,
        double z,
        double targetX,
        double targetY,
        double targetZ) {
        double dx = x - targetX;
        double dy = y - targetY;
        double dz = z - targetZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class BenchmarkSpaceStatsAccumulator {

        private int bodies;
        private int dynamicBodies;
        private int awakeDynamicBodies;
        private int sleepingDynamicBodies;
        private int detachedBodies;
        private int rawBodies;
        private int worldCollisionBodies;
        private int belowPlaneBodies;
        private int belowTerrainBodies;
        private int belowWorldMinBodies;
        private int belowVoidBodies;
        private int terrainBaselineBodies;
        private int missingTerrainBaselineBodies;
        private double minTerrainBottomClearance = Double.POSITIVE_INFINITY;
        private double minDynamicBodyY = Double.POSITIVE_INFINITY;
        private double maxDynamicBodyY = Double.NEGATIVE_INFINITY;

        @Nonnull
        private BenchmarkSpaceStatsView toView() {
            return new BenchmarkSpaceStatsView(bodies,
                dynamicBodies,
                awakeDynamicBodies,
                sleepingDynamicBodies,
                detachedBodies,
                rawBodies,
                worldCollisionBodies,
                belowPlaneBodies,
                belowTerrainBodies,
                belowWorldMinBodies,
                belowVoidBodies,
                terrainBaselineBodies,
                missingTerrainBaselineBodies,
                Double.isFinite(minTerrainBottomClearance) ? (float) minTerrainBottomClearance : Float.NaN,
                Double.isFinite(minDynamicBodyY) ? (float) minDynamicBodyY : Float.NaN,
                Double.isFinite(maxDynamicBodyY) ? (float) maxDynamicBodyY : Float.NaN);
        }
    }

    private static final class PhysicsSpaceRuntimeStatsAccumulator {

        private int bodies;
        private int dynamicBodies;
        private int awakeDynamicBodies;
        private int sleepingDynamicBodies;
        private int staticBodies;
        private int kinematicBodies;
        private int entityOwnedBodies;
        private int detachedBodies;
        private int worldCollisionBodies;
        private int planeBodies;
        private int rawBodies;
        private int joints;
        private int contacts;
        private boolean runtimeStatsAvailable;
        private int runtimeBodyCount;
        private int runtimeColliderCount;
        private int runtimeActiveBodyCount;
        private int runtimeContactPairCount;
        private int runtimeContactManifoldCount;
        private int runtimeContactPointCount;
        private int runtimeDynamicDynamicContactPairCount;
        private int runtimeTerrainContactPairCount;
        private int runtimeActiveIslandCount;
        private int runtimeJointCount;

        @Nonnull
        private PhysicsSpaceRuntimeStatsView toView() {
            return new PhysicsSpaceRuntimeStatsView(bodies,
                dynamicBodies,
                awakeDynamicBodies,
                sleepingDynamicBodies,
                staticBodies,
                kinematicBodies,
                entityOwnedBodies,
                detachedBodies,
                worldCollisionBodies,
                planeBodies,
                rawBodies,
                joints,
                contacts,
                runtimeStatsAvailable,
                runtimeBodyCount,
                runtimeColliderCount,
                runtimeActiveBodyCount,
                runtimeContactPairCount,
                runtimeContactManifoldCount,
                runtimeContactPointCount,
                runtimeDynamicDynamicContactPairCount,
                runtimeTerrainContactPairCount,
                runtimeActiveIslandCount,
                runtimeJointCount);
        }
    }

    private record StreamingPrewarmTarget(int minChunkX,
                                          int maxChunkX,
                                          int minSectionY,
                                          int maxSectionY,
                                          int minChunkZ,
                                          int maxChunkZ) {
    }

    @Nonnull
    private Optional<RigidBodyStateView> rigidBodyState(@Nonnull RigidBodyStateQuery query) {
        PhysicsBody body = runtime.getBody(query.bodyKey());
        if (body == null) {
            return Optional.empty();
        }
        return Optional.of(new RigidBodyStateView(query.bodyKey(),
            body.getBodyType(),
            RigidBodyPose.of(body.getPosition(), body.getRotation())));
    }

    @Nonnull
    private SolverCapabilitySummary solverCapability(@Nonnull SolverCapabilityQuery query) {
        PhysicsSpace space = requireSpace(query.spaceId());
        return new SolverCapabilitySummary(space.getId(),
            space.getBackendId().value(),
            space.getCapability(PhysicsSolverTuningCapability.class).isPresent(),
            space.getCapability(PhysicsActivationTuningCapability.class).isPresent());
    }

    @Nonnull
    private List<SpaceSummary> unsupportedCcdSpaces() {
        List<SpaceSummary> spaces = new ArrayList<>();
        for (PhysicsSpace space : runtime.getSpaces()) {
            if (space.getCapability(PhysicsContinuousCollisionCapability.class).isPresent()) {
                continue;
            }
            spaces.add(summary(space));
        }
        return List.copyOf(spaces);
    }

    @Nonnull
    private SpaceSummary summary(@Nonnull PhysicsSpace space) {
        return new SpaceSummary(space.getId(), space.getBackendId(), space.bodyCount(), space.jointCount());
    }

    @Nonnull
    private RaycastHitView toView(@Nonnull PhysicsRayHit hit) {
        RigidBodyKey bodyKey = runtime.getBodyKey(hit.body());
        ShapeType shapeType = hit.body().getShapeType();
        return new RaycastHitView(bodyKey,
            hit.body().getBodyType(),
            hit.point(),
            hit.normal(),
            shapeType,
            hit.fraction(),
            hit.distance());
    }

    @Nonnull
    private PhysicsSpace requireSpace(@Nonnull dev.hytalemodding.impulse.api.SpaceId spaceId) {
        PhysicsSpace space = runtime.getSpace(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        return space;
    }

    @Nonnull
    private PhysicsBody requireBody(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBody body = runtime.getBody(bodyKey);
        if (body == null) {
            throw new IllegalArgumentException("Rigid body key=" + bodyKey + " is not registered");
        }
        return body;
    }
}
