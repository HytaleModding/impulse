package dev.hytalemodding.impulse.core.internal.services;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rebuilds a physics space with another backend while preserving its logical id.
 * <p>
 *     This method shouldn't be overused. It is by nature unstable.
 * </p>
 * <p>
 * The migration clones bodies and joints into a fresh target space, remaps the body-id
 * registry to the cloned backend handles, then swaps the resource entry.
 */
@Deprecated
public final class PhysicsSpaceMigrationService {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private PhysicsSpaceMigrationService() {
    }

    @Nonnull
    public static MigrationResult migrateSpace(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId sourceSpaceId,
        @Nonnull BackendId targetBackendId,
        @Nonnull String worldName) {
        return PhysicsWorkerAccess.call(store, "migrate physics space",
            () -> migrateSpaceOnWorker(resource, sourceSpaceId, targetBackendId, worldName));
    }

    @Nonnull
    private static MigrationResult migrateSpaceOnWorker(@Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId sourceSpaceId,
        @Nonnull BackendId targetBackendId,
        @Nonnull String worldName) {
        PhysicsSpace sourceSpace = resource.getSpace(sourceSpaceId);
        if (sourceSpace == null) {
            throw new IllegalStateException("Physics space " + sourceSpaceId + " does not exist");
        }

        BackendId sourceBackendId = sourceSpace.getBackendId();
        if (sourceBackendId.equals(targetBackendId)) {
            return new MigrationResult(sourceSpaceId, sourceBackendId, targetBackendId,
                sourceSpace.getBodies().size(), sourceSpace.getJoints().size(), false);
        }

        /*
         * Generated terrain collision belongs to the world-collision cache, not to ECS bodies.
         * Drop it before migration so the target backend can rebuild it with its own best shape.
         */
        resource.getWorldVoxelCollisionCache().clear(sourceSpaceId, sourceSpace);

        List<PhysicsBody> sourceBodies = sourceSpace.getBodies();
        List<PhysicsJoint> sourceJoints = sourceSpace.getJoints();
        PhysicsSpace targetSpace = Impulse.createSpace(targetBackendId, sourceSpaceId);
        boolean migrated = false;
        try {
            targetSpace.setGravity(sourceSpace.getGravity().x,
                sourceSpace.getGravity().y,
                sourceSpace.getGravity().z);

            Map<PhysicsBody, PhysicsBody> bodyMap = new Reference2ObjectOpenHashMap<>();
            for (PhysicsBody sourceBody : sourceBodies) {
                PhysicsBody targetBody = cloneBody(sourceSpaceId, sourceBody, targetSpace);
                boolean sleeping = sourceBody.isSleeping();
                targetSpace.addBody(targetBody);

                if (!targetBody.isStatic()) {
                    if (sleeping) {
                        targetBody.sleep();
                    } else {
                        targetBody.activate();
                    }
                }

                bodyMap.put(sourceBody, targetBody);
            }

            for (PhysicsJoint sourceJoint : sourceJoints) {
                cloneJoint(sourceSpaceId, sourceJoint, targetSpace, bodyMap);
            }

            boolean registryRemapped = false;
            PhysicsSpace replaced;
            try {
                resource.remapMigratedBodies(bodyMap);
                registryRemapped = true;
                replaced = resource.replaceSpace(sourceSpaceId, targetSpace, worldName);
            } catch (Exception exception) {
                if (registryRemapped) {
                    resource.remapMigratedBodies(reverseBodyMap(bodyMap));
                }
                throw exception;
            }

            /*
             * The migration is complete once the new space is active and body ids point
             * at the cloned backend handles.
             * Cleanup failures on the old space should not invalidate the migrated state.
             */
            migrated = true;

            try {
                replaced.close();
            } catch (Exception exception) {
                LOGGER.at(Level.WARNING).log(
                    "World %s migrated physics space id=%s but failed to close previous backend=%s: %s",
                    worldName,
                    sourceSpaceId,
                    sourceBackendId,
                    exception.getMessage());
            }

            LOGGER.at(Level.INFO).log(
                "World %s migrated physics space id=%s backend=%s -> backend=%s (%s bodies, %s joints)",
                worldName,
                sourceSpaceId,
                sourceBackendId,
                targetBackendId,
                sourceBodies.size(),
                sourceJoints.size());
            return new MigrationResult(sourceSpaceId, sourceBackendId, targetBackendId,
                sourceBodies.size(), sourceJoints.size(), true);
        } finally {
            if (!migrated) {
                targetSpace.close();
            }
        }
    }

    @Nonnull
    private static PhysicsBody cloneBody(@Nonnull SpaceId sourceSpaceId,
        @Nonnull PhysicsBody sourceBody,
        @Nonnull PhysicsSpace targetSpace) {
        PhysicsBody targetBody;
        ShapeType shapeType = sourceBody.getShapeType();
        switch (shapeType) {
            case BOX -> {
                Vector3f halfExtents = sourceBody.getBoxHalfExtents();
                if (halfExtents == null) {
                    throw new IllegalStateException("Space " + sourceSpaceId
                        + " contains BOX body with null half extents");
                }
                targetBody = targetSpace.createBox(halfExtents, sourceBody.getMass());
            }
            case SPHERE -> targetBody = targetSpace.createSphere(sourceBody.getSphereRadius(),
                sourceBody.getMass());
            case CAPSULE -> targetBody = targetSpace.createCapsule(sourceBody.getSphereRadius(),
                sourceBody.getHalfHeight(), sourceBody.getShapeAxis(), sourceBody.getMass());
            case CYLINDER -> targetBody = targetSpace.createCylinder(sourceBody.getSphereRadius(),
                sourceBody.getHalfHeight(), sourceBody.getShapeAxis(), sourceBody.getMass());
            case CONE -> targetBody = targetSpace.createCone(sourceBody.getSphereRadius(),
                sourceBody.getHalfHeight(), sourceBody.getShapeAxis(), sourceBody.getMass());
            case PLANE -> {
                float planeGroundY = sourceBody.getPlaneGroundY();
                if (Float.isNaN(planeGroundY)) {
                    throw new IllegalStateException("Space " + sourceSpaceId
                        + " contains PLANE body without recoverable groundY");
                }
                targetBody = targetSpace.createStaticPlane(planeGroundY);
            }
            case UNKNOWN -> throw new IllegalStateException("Space " + sourceSpaceId
                + " contains unsupported UNKNOWN shape body");
            default -> throw new IllegalStateException("Space " + sourceSpaceId
                + " contains unsupported shape type " + shapeType);
        }

        /*
         * Plane height is stored in the plane shape, not in every backend body transform.
         * Copying transform state can move the migrated plane away from its recovered ground Y.
         */
        boolean copyTransformState = shapeType != ShapeType.PLANE;
        copyBodyState(sourceBody, targetBody, copyTransformState);
        return targetBody;
    }

    private static void copyBodyState(@Nonnull PhysicsBody sourceBody,
        @Nonnull PhysicsBody targetBody,
        boolean copyTransformState) {
        if (copyTransformState) {
            Vector3f position = sourceBody.getPosition();
            targetBody.setPosition(position.x, position.y, position.z);

            Quaternionf rotation = sourceBody.getRotation();
            targetBody.setRotation(rotation.x, rotation.y, rotation.z, rotation.w);
        }

        PhysicsBodyType bodyType = sourceBody.getBodyType();
        targetBody.setBodyType(bodyType);
        targetBody.setMass(sourceBody.getMass());

        Vector3f linearVelocity = sourceBody.getLinearVelocity();
        targetBody.setLinearVelocity(linearVelocity.x, linearVelocity.y, linearVelocity.z);

        Vector3f angularVelocity = sourceBody.getAngularVelocity();
        targetBody.setAngularVelocity(angularVelocity.x, angularVelocity.y, angularVelocity.z);

        targetBody.setFriction(sourceBody.getFriction());
        targetBody.setRestitution(sourceBody.getRestitution());
        targetBody.setDamping(sourceBody.getLinearDamping(), sourceBody.getAngularDamping());
        targetBody.setSensor(sourceBody.isSensor());
        targetBody.setCollisionFilter(sourceBody.getCollisionGroup(), sourceBody.getCollisionMask());
        targetBody.setContinuousCollisionEnabled(sourceBody.isContinuousCollisionEnabled());
    }

    private static void cloneJoint(@Nonnull SpaceId sourceSpaceId,
        @Nonnull PhysicsJoint sourceJoint,
        @Nonnull PhysicsSpace targetSpace,
        @Nonnull Map<PhysicsBody, PhysicsBody> bodyMap) {
        PhysicsBody targetBodyA = bodyMap.get(sourceJoint.getBodyA());
        PhysicsBody targetBodyB = bodyMap.get(sourceJoint.getBodyB());
        if (targetBodyA == null || targetBodyB == null) {
            throw new IllegalStateException("Space " + sourceSpaceId
                + " contains joint with body that was not migrated");
        }

        PhysicsJoint targetJoint;
        PhysicsJointType type = sourceJoint.getType();
        switch (type) {
            case FIXED -> targetJoint = targetSpace.createFixedJoint(targetBodyA, targetBodyB,
                sourceJoint.getAnchorA(), sourceJoint.getAnchorB());
            case POINT -> targetJoint = targetSpace.createPointJoint(targetBodyA, targetBodyB,
                sourceJoint.getAnchorA(), sourceJoint.getAnchorB());
            case HINGE -> {
                Vector3f axis = sourceJoint.getAxis();
                if (axis == null) {
                    throw new IllegalStateException("Space " + sourceSpaceId
                        + " contains HINGE joint without axis");
                }
                targetJoint = targetSpace.createHingeJoint(targetBodyA, targetBodyB,
                    sourceJoint.getAnchorA(), sourceJoint.getAnchorB(), axis);
            }
            case SLIDER -> {
                Vector3f axis = sourceJoint.getAxis();
                if (axis == null) {
                    throw new IllegalStateException("Space " + sourceSpaceId
                        + " contains SLIDER joint without axis");
                }
                targetJoint = targetSpace.createSliderJoint(targetBodyA, targetBodyB,
                    sourceJoint.getAnchorA(), sourceJoint.getAnchorB(), axis);
            }
            case SPRING -> {
                float restLength = sourceJoint.getSpringRestLength();
                float stiffness = sourceJoint.getSpringStiffness();
                float damping = sourceJoint.getSpringDamping();
                if (Float.isNaN(restLength) || Float.isNaN(stiffness) || Float.isNaN(damping)) {
                    throw new IllegalStateException("Space " + sourceSpaceId
                        + " contains SPRING joint without recoverable spring parameters");
                }
                targetJoint = targetSpace.createSpringJoint(targetBodyA, targetBodyB,
                    sourceJoint.getAnchorA(), sourceJoint.getAnchorB(),
                    restLength, stiffness, damping);
            }
            default -> throw new IllegalStateException("Space " + sourceSpaceId
                + " contains unsupported joint type " + type);
        }

        targetJoint.setLimits(sourceJoint.getLowerLimit(), sourceJoint.getUpperLimit());
        targetJoint.setMotor(sourceJoint.getMotorTargetVelocity(), sourceJoint.getMotorMaxForce());
        targetJoint.setMotorEnabled(sourceJoint.isMotorEnabled());
        targetJoint.setEnabled(sourceJoint.isEnabled());
    }

    @Nonnull
    private static Map<PhysicsBody, PhysicsBody> reverseBodyMap(@Nonnull Map<PhysicsBody, PhysicsBody> bodyMap) {
        Map<PhysicsBody, PhysicsBody> reverseMap = new Reference2ObjectOpenHashMap<>();
        for (Map.Entry<PhysicsBody, PhysicsBody> entry : bodyMap.entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }
        return reverseMap;
    }

    public record MigrationResult(@Nonnull SpaceId spaceId,
                                  @Nonnull BackendId sourceBackendId,
                                  @Nonnull BackendId targetBackendId,
                                  int migratedBodies,
                                  int migratedJoints,
                                  boolean changed) {
    }
}
