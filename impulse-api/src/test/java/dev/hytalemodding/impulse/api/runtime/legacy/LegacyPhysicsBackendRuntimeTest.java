package dev.hytalemodding.impulse.api.runtime.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsVoxelTerrainCapability;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class LegacyPhysicsBackendRuntimeTest {

    @Test
    void wrapsLegacyBackendWithNumericBodyAndJointIds() {
        LegacyPhysicsBackendRuntime runtime =
            new LegacyPhysicsBackendRuntime(new FakePhysicsBackend("impulse:test"));

        int spaceId = runtime.createSpace(new SpaceId(9001));
        long bodyA = runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_SPHERE,
            0.0f,
            0.0f,
            0.0f,
            0.5f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            0.0f,
            3.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        long bodyB = runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            1.0f,
            3.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        long joint = runtime.createJoint(spaceId,
            BackendRuntimeCodes.JOINT_POINT,
            bodyA,
            bodyB,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            false,
            0.0f,
            0.0f);

        assertEquals(9001, spaceId);
        assertNotEquals(bodyA, bodyB);
        assertTrue(bodyA > 0L);
        assertTrue(bodyB > 0L);
        assertTrue(joint > 0L);
        assertEquals(2, runtime.bodyCount(spaceId));
        assertEquals(1, runtime.jointCount(spaceId));

        runtime.setBodyFriction(spaceId, bodyA, 0.65f);
        runtime.setBodyRestitution(spaceId, bodyA, 0.15f);
        runtime.setBodyDamping(spaceId, bodyA, 0.2f, 0.3f);
        runtime.setBodyCollisionFilter(spaceId, bodyA, 2, 3);
        runtime.setBodyContinuousCollision(spaceId, bodyA, true);

        CapturedBodySnapshot snapshot = new CapturedBodySnapshot();
        boolean snapshotPresent = runtime.bodySnapshot(spaceId,
            bodyA,
            snapshot);

        assertTrue(snapshotPresent);
        assertEquals(ShapeType.SPHERE, BackendRuntimeCodes.shapeType(snapshot.shapeTypeCode));
        assertEquals(BackendRuntimeCodes.AXIS_Y, snapshot.axisCode);
        assertEquals(1.0f, snapshot.mass, 0.0001f);
        assertEquals(0.65f, snapshot.friction, 0.0001f);
        assertEquals(0.15f, snapshot.restitution, 0.0001f);
        assertEquals(0.2f, snapshot.linearDamping, 0.0001f);
        assertEquals(0.3f, snapshot.angularDamping, 0.0001f);
        assertEquals(2, snapshot.collisionGroup);
        assertEquals(3, snapshot.collisionMask);
        assertTrue(snapshot.continuousCollisionEnabled);
        assertEquals(BackendRuntimeCodes.jointTypeCode(BackendJointType.POINT), runtime.jointType(spaceId, joint));
        assertEquals(bodyA, runtime.jointBodyA(spaceId, joint));
        assertEquals(bodyB, runtime.jointBodyB(spaceId, joint));
    }

    @Test
    void legacyRuntimeRejectsVoxelTerrainWhenCapabilityMissing() {
        LegacyPhysicsBackendRuntime runtime =
            new LegacyPhysicsBackendRuntime(new FakePhysicsBackend("impulse:test-voxel"));
        int spaceId = runtime.createSpace(new SpaceId(9002));

        assertFalse(runtime.supportsVoxelTerrain(spaceId));
        assertThrows(UnsupportedOperationException.class,
            () -> runtime.createVoxelTerrain(spaceId,
                1.0f,
                1.0f,
                1.0f,
                new int[] { 0, 0, 0 },
                0.0f,
                0.0f,
                0.0f,
                0.75f,
                0.0f,
                1,
                -1));
        assertThrows(UnsupportedOperationException.class,
            () -> runtime.combineVoxelTerrains(spaceId, 1L, 2L, 16, 0, 0));
    }

    @Test
    void legacyRuntimePassesThroughVoxelTerrainCapability() {
        RecordingVoxelBackend backend = new RecordingVoxelBackend("impulse:test-voxel-passthrough");
        LegacyPhysicsBackendRuntime runtime = new LegacyPhysicsBackendRuntime(backend);
        int spaceId = runtime.createSpace(new SpaceId(9003));

        assertTrue(runtime.supportsVoxelTerrain(spaceId));
        long first = runtime.createVoxelTerrain(spaceId,
            1.0f,
            1.0f,
            1.0f,
            new int[] { 0, 0, 0 },
            8.0f,
            16.0f,
            24.0f,
            0.75f,
            0.0f,
            2,
            4);
        long second = runtime.createVoxelTerrain(spaceId,
            1.0f,
            1.0f,
            1.0f,
            new int[] { 1, 0, 0 },
            24.0f,
            16.0f,
            24.0f,
            0.5f,
            0.1f,
            8,
            16);

        assertEquals(2, runtime.bodyCount(spaceId));
        assertTrue(runtime.containsBody(spaceId, first));
        PhysicsBody firstBody = backend.voxelBodies().getFirst();
        assertEquals(8.0f, firstBody.getPosition().x);
        assertEquals(16.0f, firstBody.getPosition().y);
        assertEquals(24.0f, firstBody.getPosition().z);
        assertEquals(0.75f, firstBody.getFriction());
        assertEquals(0.0f, firstBody.getRestitution());
        assertEquals(2, firstBody.getCollisionGroup());
        assertEquals(4, firstBody.getCollisionMask());

        runtime.combineVoxelTerrains(spaceId, first, second, 16, 0, 0);

        assertEquals(List.of(new CombineCall(firstBody, backend.voxelBodies().get(1), 16, 0, 0)),
            backend.combineCalls());
    }

    private record CombineCall(@Nonnull PhysicsBody bodyA,
                               @Nonnull PhysicsBody bodyB,
                               int shiftX,
                               int shiftY,
                               int shiftZ) {
    }

    private static final class CapturedBodySnapshot implements BackendBodySnapshotSink {

        private int shapeTypeCode = BackendRuntimeCodes.SHAPE_UNKNOWN;
        private int axisCode = -1;
        private float mass;
        private float friction;
        private float restitution;
        private float linearDamping;
        private float angularDamping;
        private int collisionGroup;
        private int collisionMask;
        private boolean continuousCollisionEnabled;

        @Override
        public void accept(long bodyId,
            int shapeTypeCode,
            int bodyTypeCode,
            float positionX,
            float positionY,
            float positionZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW,
            float linearVelocityX,
            float linearVelocityY,
            float linearVelocityZ,
            float angularVelocityX,
            float angularVelocityY,
            float angularVelocityZ,
            boolean sleeping,
            boolean sensor,
            float mass,
            float friction,
            float restitution,
            float linearDamping,
            float angularDamping,
            int collisionGroup,
            int collisionMask,
            boolean continuousCollisionEnabled,
            float centerOfMassOffsetY,
            boolean hasBoxHalfExtents,
            float halfExtentX,
            float halfExtentY,
            float halfExtentZ,
            float radius,
            float halfHeight,
            int axisCode) {
            this.shapeTypeCode = shapeTypeCode;
            this.axisCode = axisCode;
            this.mass = mass;
            this.friction = friction;
            this.restitution = restitution;
            this.linearDamping = linearDamping;
            this.angularDamping = angularDamping;
            this.collisionGroup = collisionGroup;
            this.collisionMask = collisionMask;
            this.continuousCollisionEnabled = continuousCollisionEnabled;
        }
    }

    private static final class RecordingVoxelBackend implements PhysicsBackend {

        @Nonnull
        private final BackendId id;
        @Nonnull
        private final FakePhysicsBackend delegate;
        @Nonnull
        private final RecordingVoxelCapability voxelCapability = new RecordingVoxelCapability();

        private RecordingVoxelBackend(@Nonnull String id) {
            this.id = new BackendId(id);
            this.delegate = new FakePhysicsBackend(this.id);
        }

        @Nonnull
        @Override
        public BackendId getId() {
            return id;
        }

        @Override
        public void init() {
        }

        @Nonnull
        @Override
        public PhysicsSpace createSpace() {
            return createSpace(SpaceId.next());
        }

        @Nonnull
        @Override
        public PhysicsSpace createSpace(@Nonnull SpaceId spaceId) {
            PhysicsSpace space = delegate.createSpace(spaceId);
            return (PhysicsSpace) Proxy.newProxyInstance(PhysicsSpace.class.getClassLoader(),
                new Class<?>[] { PhysicsSpace.class },
                new RecordingVoxelSpace(space, voxelCapability));
        }

        @Nonnull
        private List<PhysicsBody> voxelBodies() {
            return List.copyOf(voxelCapability.voxelBodies);
        }

        @Nonnull
        private List<CombineCall> combineCalls() {
            return List.copyOf(voxelCapability.combineCalls);
        }
    }

    private record RecordingVoxelSpace(@Nonnull PhysicsSpace delegate,
                                       @Nonnull RecordingVoxelCapability voxelCapability)
        implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getCapability".equals(method.getName()) && args != null && args.length == 1
                && args[0] == PhysicsVoxelTerrainCapability.class) {
                return Optional.of(voxelCapability);
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }

    private static final class RecordingVoxelCapability implements PhysicsVoxelTerrainCapability {

        private final List<PhysicsBody> voxelBodies = new ArrayList<>();
        private final List<CombineCall> combineCalls = new ArrayList<>();

        @Nonnull
        @Override
        public PhysicsBody createVoxelTerrain(float voxelSizeX,
            float voxelSizeY,
            float voxelSizeZ,
            @Nonnull int[] voxelCoordinates) {
            PhysicsBody body = new FakePhysicsBackend("impulse:test-voxel-body")
                .createSpace()
                .createBox(0.5f, 0.5f, 0.5f, 0.0f);
            voxelBodies.add(body);
            return body;
        }

        @Override
        public void combineVoxelTerrains(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            int shiftX,
            int shiftY,
            int shiftZ) {
            combineCalls.add(new CombineCall(bodyA, bodyB, shiftX, shiftY, shiftZ));
        }
    }
}
