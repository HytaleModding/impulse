package dev.hytalemodding.impulse.api.testsupport;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.runtime.BackendBodyIdSource;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendContactSink;
import dev.hytalemodding.impulse.api.runtime.BackendExtensionSettingsSource;
import dev.hytalemodding.impulse.api.runtime.BackendRayHitSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeStatsSink;
import dev.hytalemodding.impulse.api.runtime.BackendStepPhaseStatsSink;
import dev.hytalemodding.impulse.api.runtime.BackendVec3Sink;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Small id-only backend runtime provider for core tests.
 */
public final class FakePhysicsBackendRuntimeProvider implements PhysicsBackendRuntimeProvider {

    @Nonnull
    private final BackendId id;
    private final boolean continuousCollision;
    private final boolean voxelTerrain;
    private final List<FakePhysicsBackendRuntime> runtimes = new ArrayList<>();

    public FakePhysicsBackendRuntimeProvider(@Nonnull String id) {
        this(new BackendId(id), false, false);
    }

    public FakePhysicsBackendRuntimeProvider(@Nonnull String id, boolean continuousCollision) {
        this(new BackendId(id), continuousCollision, false);
    }

    public FakePhysicsBackendRuntimeProvider(@Nonnull String id,
        boolean continuousCollision,
        boolean voxelTerrain) {
        this(new BackendId(id), continuousCollision, voxelTerrain);
    }

    public FakePhysicsBackendRuntimeProvider(@Nonnull BackendId id,
        boolean continuousCollision,
        boolean voxelTerrain) {
        this.id = Objects.requireNonNull(id, "id");
        this.continuousCollision = continuousCollision;
        this.voxelTerrain = voxelTerrain;
    }

    @Nonnull
    @Override
    public BackendId getId() {
        return id;
    }

    @Nonnull
    @Override
    public PhysicsBackendRuntime createRuntime() {
        FakePhysicsBackendRuntime runtime =
            new FakePhysicsBackendRuntime(continuousCollision, voxelTerrain);
        runtimes.add(runtime);
        return runtime;
    }

    @Nonnull
    public List<FakePhysicsBackendRuntime> createdRuntimes() {
        return List.copyOf(runtimes);
    }

    public static final class FakePhysicsBackendRuntime implements PhysicsBackendRuntime {

        private final Map<Integer, SpaceState> spaces = new HashMap<>();
        private final boolean continuousCollision;
        private final boolean voxelTerrain;
        private long nextBodyId = 1L;
        private long nextJointId = 1L;

        private FakePhysicsBackendRuntime(boolean continuousCollision, boolean voxelTerrain) {
            this.continuousCollision = continuousCollision;
            this.voxelTerrain = voxelTerrain;
        }

        @Override
        public int createSpace(@Nonnull SpaceId requestedId) {
            if (spaces.putIfAbsent(requestedId.value(), new SpaceState()) != null) {
                throw new IllegalArgumentException("Space already exists: " + requestedId);
            }
            return requestedId.value();
        }

        @Override
        public void destroySpace(int spaceId) {
            spaces.remove(spaceId);
        }

        @Override
        public void step(int spaceId, float dt) {
            requireSpace(spaceId);
        }

        @Override
        public void setGravity(int spaceId, float x, float y, float z) {
            SpaceState space = requireSpace(spaceId);
            space.gravityX = x;
            space.gravityY = y;
            space.gravityZ = z;
        }

        @Override
        public void getGravity(int spaceId, @Nonnull BackendVec3Sink sink) {
            SpaceState space = requireSpace(spaceId);
            sink.accept(space.gravityX, space.gravityY, space.gravityZ);
        }

        @Override
        public long createBody(int spaceId,
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
            float rotationW) {
            SpaceState space = requireSpace(spaceId);
            long bodyId = nextBodyId++;
            space.bodies.put(bodyId,
                new BodyState(shapeTypeCode,
                    bodyTypeCode,
                    positionX,
                    positionY,
                    positionZ,
                    rotationX,
                    rotationY,
                    rotationZ,
                    rotationW,
                    halfExtentX,
                    halfExtentY,
                    halfExtentZ,
                    radius,
                    halfHeight,
                    axisCode,
                    false));
            return bodyId;
        }

        @Override
        public boolean supportsVoxelTerrain(int spaceId) {
            requireSpace(spaceId);
            return voxelTerrain;
        }

        @Override
        public long createVoxelTerrain(int spaceId,
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
            int collisionMask) {
            if (!supportsVoxelTerrain(spaceId)) {
                throw new UnsupportedOperationException("Voxel terrain is not supported");
            }
            SpaceState space = requireSpace(spaceId);
            long bodyId = nextBodyId++;
            space.bodies.put(bodyId,
                new BodyState(BackendRuntimeCodes.SHAPE_UNKNOWN,
                    BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.STATIC),
                    positionX,
                    positionY,
                    positionZ,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    BackendRuntimeCodes.AXIS_Y,
                    true));
            space.voxelTerrainCalls.add(new VoxelTerrainCall(bodyId,
                voxelSizeX,
                voxelSizeY,
                voxelSizeZ,
                voxelCoordinates.clone(),
                positionX,
                positionY,
                positionZ,
                friction,
                restitution,
                collisionGroup,
                collisionMask));
            return bodyId;
        }

        @Override
        public void combineVoxelTerrains(int spaceId,
            long bodyAId,
            long bodyBId,
            int shiftX,
            int shiftY,
            int shiftZ) {
            SpaceState space = requireSpace(spaceId);
            if (!voxelTerrain) {
                throw new UnsupportedOperationException("Voxel terrain is not supported");
            }
            requireBody(space, bodyAId);
            requireBody(space, bodyBId);
            space.combineCalls.add(new CombineCall(bodyAId, bodyBId, shiftX, shiftY, shiftZ));
        }

        @Override
        public void removeBody(int spaceId, long bodyId) {
            requireSpace(spaceId).bodies.remove(bodyId);
        }

        @Override
        public int bodyCount(int spaceId) {
            return requireSpace(spaceId).bodies.size();
        }

        @Override
        public boolean containsBody(int spaceId, long bodyId) {
            return requireSpace(spaceId).bodies.containsKey(bodyId);
        }

        @Override
        public boolean bodySnapshot(int spaceId, long bodyId, @Nonnull BackendBodySnapshotSink sink) {
            BodyState body = requireSpace(spaceId).bodies.get(bodyId);
            if (body == null) {
                return false;
            }
            emitSnapshot(bodyId, body, sink);
            return true;
        }

        @Override
        public void snapshotBodies(int spaceId,
            @Nonnull BackendBodyIdSource bodyIds,
            @Nonnull BackendBodySnapshotSink sink) {
            SpaceState space = requireSpace(spaceId);
            bodyIds.forEachBodyId(bodyId -> {
                BodyState body = space.bodies.get(bodyId);
                if (body != null) {
                    emitSnapshot(bodyId, body, sink);
                }
            });
        }

        @Override
        public void setBodyTransform(int spaceId,
            long bodyId,
            float positionX,
            float positionY,
            float positionZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW) {
            requireBody(requireSpace(spaceId), bodyId)
                .setTransform(positionX, positionY, positionZ, rotationX, rotationY, rotationZ, rotationW);
        }

        @Override
        public void setBodyPosition(int spaceId, long bodyId, float x, float y, float z) {
            requireBody(requireSpace(spaceId), bodyId).setPosition(x, y, z);
        }

        @Override
        public void setBodyVelocity(int spaceId,
            long bodyId,
            float linearX,
            float linearY,
            float linearZ,
            float angularX,
            float angularY,
            float angularZ) {
            requireBody(requireSpace(spaceId), bodyId);
        }

        @Override
        public void setBodyType(int spaceId, long bodyId, int bodyTypeCode) {
            requireBody(requireSpace(spaceId), bodyId).bodyTypeCode = bodyTypeCode;
        }

        @Override
        public void setBodyDamping(int spaceId, long bodyId, float linearDamping, float angularDamping) {
            requireBody(requireSpace(spaceId), bodyId);
        }

        @Override
        public void setBodyFriction(int spaceId, long bodyId, float friction) {
            requireBody(requireSpace(spaceId), bodyId).friction = friction;
        }

        @Override
        public void setBodyRestitution(int spaceId, long bodyId, float restitution) {
            requireBody(requireSpace(spaceId), bodyId).restitution = restitution;
        }

        @Override
        public void setBodyCollisionFilter(int spaceId, long bodyId, int group, int mask) {
            BodyState body = requireBody(requireSpace(spaceId), bodyId);
            body.collisionGroup = group;
            body.collisionMask = mask;
        }

        @Override
        public void setBodySensor(int spaceId, long bodyId, boolean sensor) {
            requireBody(requireSpace(spaceId), bodyId).sensor = sensor;
        }

        @Override
        public void setBodyContinuousCollision(int spaceId, long bodyId, boolean enabled) {
            requireBody(requireSpace(spaceId), bodyId).continuousCollision = enabled;
        }

        @Override
        public boolean isBodyContinuousCollisionEnabled(int spaceId, long bodyId) {
            return requireBody(requireSpace(spaceId), bodyId).continuousCollision;
        }

        @Override
        public void activateBody(int spaceId, long bodyId) {
            requireBody(requireSpace(spaceId), bodyId).sleeping = false;
        }

        @Override
        public void sleepBody(int spaceId, long bodyId) {
            requireBody(requireSpace(spaceId), bodyId).sleeping = true;
        }

        @Override
        public void applyBodyImpulse(int spaceId,
            long bodyId,
            float x,
            float y,
            float z,
            boolean hasOffset,
            float offsetX,
            float offsetY,
            float offsetZ,
            boolean torque) {
            requireBody(requireSpace(spaceId), bodyId);
        }

        @Override
        public void applyBodyForce(int spaceId,
            long bodyId,
            float x,
            float y,
            float z,
            boolean hasOffset,
            float offsetX,
            float offsetY,
            float offsetZ,
            boolean torque) {
            requireBody(requireSpace(spaceId), bodyId);
        }

        @Override
        public long createJoint(int spaceId,
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
            float motorMaxForce) {
            SpaceState space = requireSpace(spaceId);
            requireBody(space, bodyAId);
            requireBody(space, bodyBId);
            long jointId = nextJointId++;
            space.joints.put(jointId, new JointState(jointTypeCode, bodyAId, bodyBId));
            return jointId;
        }

        @Override
        public void removeJoint(int spaceId, long jointId) {
            requireSpace(spaceId).joints.remove(jointId);
        }

        @Override
        public int jointCount(int spaceId) {
            return requireSpace(spaceId).joints.size();
        }

        @Override
        public int jointType(int spaceId, long jointId) {
            return requireJoint(requireSpace(spaceId), jointId).typeCode;
        }

        @Override
        public long jointBodyA(int spaceId, long jointId) {
            return requireJoint(requireSpace(spaceId), jointId).bodyAId;
        }

        @Override
        public long jointBodyB(int spaceId, long jointId) {
            return requireJoint(requireSpace(spaceId), jointId).bodyBId;
        }

        @Override
        public boolean raycastClosest(int spaceId,
            float fromX,
            float fromY,
            float fromZ,
            float toX,
            float toY,
            float toZ,
            @Nonnull BackendRayHitSink sink) {
            requireSpace(spaceId);
            return false;
        }

        @Override
        public int raycastAll(int spaceId,
            float fromX,
            float fromY,
            float fromZ,
            float toX,
            float toY,
            float toZ,
            @Nonnull BackendRayHitSink sink) {
            requireSpace(spaceId);
            return 0;
        }

        @Override
        public int contacts(int spaceId, @Nonnull BackendContactSink sink) {
            requireSpace(spaceId);
            return 0;
        }

        @Override
        public int contactCount(int spaceId) {
            requireSpace(spaceId);
            return 0;
        }

        @Override
        public void runtimeStats(int spaceId, @Nonnull BackendRuntimeStatsSink sink) {
            SpaceState space = requireSpace(spaceId);
            sink.accept(space.bodies.size(), 0, space.bodies.size(), 0, 0, 0, 0, 0, 0, space.joints.size(), true);
        }

        @Override
        public void resetStepPhaseStats(int spaceId) {
            requireSpace(spaceId);
        }

        @Override
        public void stepPhaseStats(int spaceId, @Nonnull BackendStepPhaseStatsSink sink) {
            requireSpace(spaceId);
            sink.accept(0L, 0L, 0L, 0L, 0L, 0L, false);
        }

        @Override
        public boolean supportsContinuousCollision(int spaceId) {
            requireSpace(spaceId);
            return continuousCollision;
        }

        @Override
        public boolean supportsSolverTuning(int spaceId) {
            requireSpace(spaceId);
            return false;
        }

        @Override
        public boolean supportsActivationTuning(int spaceId) {
            requireSpace(spaceId);
            return false;
        }

        @Override
        public void applySolverTuning(int spaceId, @Nonnull PhysicsSolverTuning tuning) {
            requireSpace(spaceId);
        }

        @Override
        public void applyActivationTuning(int spaceId, @Nonnull PhysicsActivationTuning tuning) {
            requireSpace(spaceId);
        }

        @Override
        public void applyExtensionSettings(int spaceId,
            @Nonnull PhysicsCapabilityId capabilityId,
            @Nonnull BackendExtensionSettingsSource settings) {
            requireSpace(spaceId);
        }

        @Nonnull
        public List<VoxelTerrainCall> voxelTerrainCalls(int spaceId) {
            return List.copyOf(requireSpace(spaceId).voxelTerrainCalls);
        }

        @Nonnull
        public List<CombineCall> combineCalls(int spaceId) {
            return List.copyOf(requireSpace(spaceId).combineCalls);
        }

        private static void emitSnapshot(long bodyId,
            @Nonnull BodyState body,
            @Nonnull BackendBodySnapshotSink sink) {
            sink.accept(bodyId,
                body.shapeTypeCode,
                body.bodyTypeCode,
                body.positionX,
                body.positionY,
                body.positionZ,
                body.rotationX,
                body.rotationY,
                body.rotationZ,
                body.rotationW,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                body.sleeping,
                body.sensor,
                0.0f,
                body.halfExtentX > 0.0f && body.halfExtentY > 0.0f && body.halfExtentZ > 0.0f,
                body.halfExtentX,
                body.halfExtentY,
                body.halfExtentZ,
                body.radius,
                body.halfHeight,
                body.axisCode);
        }

        @Nonnull
        private SpaceState requireSpace(int spaceId) {
            SpaceState space = spaces.get(spaceId);
            if (space == null) {
                throw new IllegalArgumentException("Unknown space id=" + spaceId);
            }
            return space;
        }

        @Nonnull
        private static BodyState requireBody(@Nonnull SpaceState space, long bodyId) {
            BodyState body = space.bodies.get(bodyId);
            if (body == null) {
                throw new IllegalArgumentException("Unknown body id=" + bodyId);
            }
            return body;
        }

        @Nonnull
        private static JointState requireJoint(@Nonnull SpaceState space, long jointId) {
            JointState joint = space.joints.get(jointId);
            if (joint == null) {
                throw new IllegalArgumentException("Unknown joint id=" + jointId);
            }
            return joint;
        }
    }

    private static final class SpaceState {

        private final Map<Long, BodyState> bodies = new HashMap<>();
        private final Map<Long, JointState> joints = new HashMap<>();
        private final List<VoxelTerrainCall> voxelTerrainCalls = new ArrayList<>();
        private final List<CombineCall> combineCalls = new ArrayList<>();
        private float gravityX;
        private float gravityY;
        private float gravityZ;
    }

    private static final class BodyState {

        private final int shapeTypeCode;
        private final float halfExtentX;
        private final float halfExtentY;
        private final float halfExtentZ;
        private final float radius;
        private final float halfHeight;
        private final int axisCode;
        private final boolean voxelTerrain;
        private int bodyTypeCode;
        private float positionX;
        private float positionY;
        private float positionZ;
        private float rotationX;
        private float rotationY;
        private float rotationZ;
        private float rotationW;
        private float friction;
        private float restitution;
        private int collisionGroup;
        private int collisionMask;
        private boolean sensor;
        private boolean continuousCollision;
        private boolean sleeping;

        private BodyState(int shapeTypeCode,
            int bodyTypeCode,
            float positionX,
            float positionY,
            float positionZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW,
            float halfExtentX,
            float halfExtentY,
            float halfExtentZ,
            float radius,
            float halfHeight,
            int axisCode,
            boolean voxelTerrain) {
            this.shapeTypeCode = shapeTypeCode;
            this.bodyTypeCode = bodyTypeCode;
            this.positionX = positionX;
            this.positionY = positionY;
            this.positionZ = positionZ;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.rotationW = rotationW;
            this.halfExtentX = halfExtentX;
            this.halfExtentY = halfExtentY;
            this.halfExtentZ = halfExtentZ;
            this.radius = radius;
            this.halfHeight = halfHeight;
            this.axisCode = axisCode;
            this.voxelTerrain = voxelTerrain;
        }

        private void setTransform(float positionX,
            float positionY,
            float positionZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW) {
            setPosition(positionX, positionY, positionZ);
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.rotationW = rotationW;
        }

        private void setPosition(float positionX, float positionY, float positionZ) {
            this.positionX = positionX;
            this.positionY = positionY;
            this.positionZ = positionZ;
        }
    }

    private record JointState(int typeCode, long bodyAId, long bodyBId) {
    }

    public record VoxelTerrainCall(long bodyId,
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
                                   int collisionMask) {

        public VoxelTerrainCall {
            voxelCoordinates = voxelCoordinates.clone();
        }

        @Nonnull
        @Override
        public int[] voxelCoordinates() {
            return voxelCoordinates.clone();
        }
    }

    public record CombineCall(long bodyAId, long bodyBId, int shiftX, int shiftY, int shiftZ) {
    }
}
