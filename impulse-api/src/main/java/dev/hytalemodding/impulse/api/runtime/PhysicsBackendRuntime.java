package dev.hytalemodding.impulse.api.runtime;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Owner-lane backend runtime port using backend-local numeric ids.
 */
public interface PhysicsBackendRuntime {

    int createSpace(@Nonnull SpaceId requestedId);

    void destroySpace(int spaceId);

    void step(int spaceId, float dt);

    void setGravity(int spaceId, float x, float y, float z);

    @Nonnull
    Vector3f getGravity(int spaceId);

    long createBody(int spaceId, @Nonnull BackendBodySpec spec);

    void removeBody(int spaceId, long bodyId);

    int bodyCount(int spaceId);

    boolean containsBody(int spaceId, long bodyId);

    @Nonnull
    Optional<BackendBodySnapshot> bodySnapshot(int spaceId, long bodyId);

    void snapshotBodies(int spaceId,
        @Nonnull Iterable<Long> bodyIds,
        @Nonnull BiConsumer<Long, BackendBodySnapshot> consumer);

    void setBodyTransform(int spaceId,
        long bodyId,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW);

    void setBodyPosition(int spaceId, long bodyId, float x, float y, float z);

    void setBodyVelocity(int spaceId,
        long bodyId,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ);

    void setBodyType(int spaceId, long bodyId, @Nonnull PhysicsBodyType bodyType);

    void setBodyDamping(int spaceId, long bodyId, float linearDamping, float angularDamping);

    void setBodyFriction(int spaceId, long bodyId, float friction);

    void setBodyRestitution(int spaceId, long bodyId, float restitution);

    void setBodyCollisionFilter(int spaceId, long bodyId, int group, int mask);

    void setBodySensor(int spaceId, long bodyId, boolean sensor);

    void setBodyContinuousCollision(int spaceId, long bodyId, boolean enabled);

    boolean isBodyContinuousCollisionEnabled(int spaceId, long bodyId);

    void activateBody(int spaceId, long bodyId);

    void sleepBody(int spaceId, long bodyId);

    void applyBodyImpulse(int spaceId,
        long bodyId,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque);

    void applyBodyForce(int spaceId,
        long bodyId,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque);

    long createJoint(int spaceId, @Nonnull BackendJointSpec spec);

    void removeJoint(int spaceId, long jointId);

    int jointCount(int spaceId);

    @Nonnull
    BackendJointType jointType(int spaceId, long jointId);

    long jointBodyA(int spaceId, long jointId);

    long jointBodyB(int spaceId, long jointId);

    @Nonnull
    Optional<BackendRayHit> raycastClosest(int spaceId, @Nonnull Vector3f from, @Nonnull Vector3f to);

    @Nonnull
    List<BackendRayHit> raycastAll(int spaceId, @Nonnull Vector3f from, @Nonnull Vector3f to);

    @Nonnull
    List<BackendContact> contacts(int spaceId);

    int contactCount(int spaceId);

    @Nonnull
    BackendRuntimeStats runtimeStats(int spaceId);

    void resetStepPhaseStats(int spaceId);

    @Nonnull
    BackendStepPhaseStats stepPhaseStats(int spaceId);

    boolean supportsContinuousCollision(int spaceId);

    boolean supportsSolverTuning(int spaceId);

    boolean supportsActivationTuning(int spaceId);

    void applySolverTuning(int spaceId, @Nonnull PhysicsSolverTuning tuning);

    void applyActivationTuning(int spaceId, @Nonnull PhysicsActivationTuning tuning);

    void applyExtensionSettings(int spaceId,
        @Nonnull PhysicsCapabilityId capabilityId,
        @Nonnull Map<String, String> settings);
}
