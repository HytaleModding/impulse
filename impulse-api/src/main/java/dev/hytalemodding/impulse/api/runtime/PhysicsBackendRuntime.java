package dev.hytalemodding.impulse.api.runtime;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import javax.annotation.Nonnull;

/**
 * Owner-lane backend runtime port using backend-local numeric ids and primitive payloads.
 */
public interface PhysicsBackendRuntime {

    int createSpace(@Nonnull SpaceId requestedId);

    void destroySpace(int spaceId);

    void step(int spaceId, float dt);

    void setGravity(int spaceId, float x, float y, float z);

    void getGravity(int spaceId, @Nonnull BackendVec3Sink sink);

    long createBody(int spaceId,
        int shapeTypeCode,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        int axisCode,
        float groundY,
        float mass,
        int bodyTypeCode,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW);

    boolean supportsVoxelTerrain(int spaceId);

    long createVoxelTerrain(int spaceId,
        float voxelSizeX,
        float voxelSizeY,
        float voxelSizeZ,
        @Nonnull int[] voxelCoordinates,
        float positionX,
        float positionY,
        float positionZ,
        float friction,
        float restitution,
        int collisionGroup,
        int collisionMask);

    void combineVoxelTerrains(int spaceId,
        long bodyAId,
        long bodyBId,
        int shiftX,
        int shiftY,
        int shiftZ);

    void removeBody(int spaceId, long bodyId);

    int bodyCount(int spaceId);

    boolean containsBody(int spaceId, long bodyId);

    boolean bodySnapshot(int spaceId, long bodyId, @Nonnull BackendBodySnapshotSink sink);

    void snapshotBodies(int spaceId,
        @Nonnull BackendBodyIdSource bodyIds,
        @Nonnull BackendBodySnapshotSink sink);

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

    void setBodyType(int spaceId, long bodyId, int bodyTypeCode);

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

    long createJoint(int spaceId,
        int jointTypeCode,
        long bodyAId,
        long bodyBId,
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
        float motorMaxForce);

    void removeJoint(int spaceId, long jointId);

    int jointCount(int spaceId);

    int jointType(int spaceId, long jointId);

    long jointBodyA(int spaceId, long jointId);

    long jointBodyB(int spaceId, long jointId);

    boolean raycastClosest(int spaceId,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        @Nonnull BackendRayHitSink sink);

    int raycastAll(int spaceId,
        float fromX,
        float fromY,
        float fromZ,
        float toX,
        float toY,
        float toZ,
        @Nonnull BackendRayHitSink sink);

    int contacts(int spaceId, @Nonnull BackendContactSink sink);

    int contactCount(int spaceId);

    void runtimeStats(int spaceId, @Nonnull BackendRuntimeStatsSink sink);

    void resetStepPhaseStats(int spaceId);

    void stepPhaseStats(int spaceId, @Nonnull BackendStepPhaseStatsSink sink);

    boolean supportsContinuousCollision(int spaceId);

    boolean supportsSolverTuning(int spaceId);

    boolean supportsActivationTuning(int spaceId);

    void applySolverTuning(int spaceId, @Nonnull PhysicsSolverTuning tuning);

    void applyActivationTuning(int spaceId, @Nonnull PhysicsActivationTuning tuning);

    void applyExtensionSettings(int spaceId,
        @Nonnull PhysicsCapabilityId capabilityId,
        @Nonnull BackendExtensionSettingsSource settings);
}
