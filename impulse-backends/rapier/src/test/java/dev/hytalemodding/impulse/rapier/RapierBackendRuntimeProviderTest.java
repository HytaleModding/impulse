package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import java.lang.reflect.Field;
import java.util.Map;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class RapierBackendRuntimeProviderTest {

    @Test
    void providerCreatesIdOnlyRuntime() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();

        PhysicsBackendRuntime runtime = provider.createRuntime();

        assertInstanceOf(RapierBackendRuntime.class, runtime);
    }

    @Test
    void runtimeSupportsPrimitiveBodySnapshotAndJointLifecycle() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        PhysicsBackendRuntime runtime = provider.createRuntime();
        int spaceId = runtime.createSpace(new SpaceId(70));
        try {
            runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
            float[] gravity = new float[3];
            runtime.getGravity(spaceId, (x, y, z) -> {
                gravity[0] = x;
                gravity[1] = y;
                gravity[2] = z;
            });

            long firstBodyId = runtime.createBody(spaceId,
                BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
                0.5f,
                0.5f,
                0.5f,
                -1.0f,
                -1.0f,
                BackendRuntimeCodes.AXIS_Y,
                0.0f,
                1.0f,
                BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
                1.0f,
                2.0f,
                3.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f);
            long secondBodyId = runtime.createBody(spaceId,
                BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
                0.5f,
                0.5f,
                0.5f,
                -1.0f,
                -1.0f,
                BackendRuntimeCodes.AXIS_Y,
                0.0f,
                1.0f,
                BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
                2.0f,
                2.0f,
                3.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f);
            CapturedSnapshot snapshot = new CapturedSnapshot();
            long jointId = runtime.createJoint(spaceId,
                BackendRuntimeCodes.jointTypeCode(BackendJointType.FIXED),
                firstBodyId,
                secondBodyId,
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

            assertEquals(-9.81f, gravity[1]);
            assertEquals(2, runtime.bodyCount(spaceId));
            assertTrue(runtime.containsBody(spaceId, firstBodyId));
            assertTrue(runtime.bodySnapshot(spaceId, firstBodyId, snapshot));
            assertEquals(firstBodyId, snapshot.bodyId);
            assertEquals(BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX), snapshot.shapeTypeCode);
            assertEquals(BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
                snapshot.bodyTypeCode);
            assertEquals(1.0f, snapshot.positionX);
            assertEquals(2.0f, snapshot.positionY);
            assertEquals(3.0f, snapshot.positionZ);
            assertEquals(1, runtime.jointCount(spaceId));
            assertEquals(BackendRuntimeCodes.jointTypeCode(BackendJointType.FIXED),
                runtime.jointType(spaceId, jointId));
            assertEquals(firstBodyId, runtime.jointBodyA(spaceId, jointId));
            assertEquals(secondBodyId, runtime.jointBodyB(spaceId, jointId));

            runtime.removeJoint(spaceId, jointId);
            runtime.removeBody(spaceId, firstBodyId);

            assertEquals(0, runtime.jointCount(spaceId));
            assertFalse(runtime.containsBody(spaceId, firstBodyId));
            assertEquals(1, runtime.bodyCount(spaceId));
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void gravityRoundTripsEveryAxisDirection() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        PhysicsBackendRuntime runtime = provider.createRuntime();
        float[][] gravities = {
            {0.0f, -9.81f, 0.0f},
            {0.0f, 9.81f, 0.0f},
            {9.81f, 0.0f, 0.0f},
            {-9.81f, 0.0f, 0.0f}
        };
        for (int index = 0; index < gravities.length; index++) {
            int spaceId = runtime.createSpace(new SpaceId(700 + index));
            try {
                float[] expected = gravities[index];
                runtime.setGravity(spaceId, expected[0], expected[1], expected[2]);

                assertGravityEquals(expected, runtime, spaceId);
                runtime.step(spaceId, 1.0f / 60.0f);
                assertGravityEquals(expected, runtime, spaceId);
            } finally {
                runtime.destroySpace(spaceId);
            }
        }
    }

    @Test
    void configuredBodyCreationSeedsInitialBodyState() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        PhysicsBackendRuntime runtime = provider.createRuntime();
        int spaceId = runtime.createSpace(new SpaceId(76));
        try {
            long bodyId = runtime.createBodyWithInitialState(spaceId,
                BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
                0.5f,
                0.5f,
                0.5f,
                -1.0f,
                -1.0f,
                BackendRuntimeCodes.AXIS_Y,
                0.0f,
                1.0f,
                BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
                1.0f,
                2.0f,
                3.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f,
                0.2f,
                0.3f,
                0.65f,
                0.15f,
                2,
                3,
                true,
                true);
            CapturedSnapshot snapshot = new CapturedSnapshot();

            assertTrue(runtime.bodySnapshot(spaceId, bodyId, snapshot));

            assertEquals(0.2f, snapshot.linearDamping);
            assertEquals(0.3f, snapshot.angularDamping);
            assertEquals(0.65f, snapshot.friction);
            assertEquals(0.15f, snapshot.restitution);
            assertEquals(2, snapshot.collisionGroup);
            assertEquals(3, snapshot.collisionMask);
            assertTrue(snapshot.sensor);
            assertTrue(snapshot.continuousCollisionEnabled);
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static void assertGravityEquals(float[] expected,
        PhysicsBackendRuntime runtime,
        int spaceId) {
        float[] actual = new float[3];
        runtime.getGravity(spaceId, (x, y, z) -> {
            actual[0] = x;
            actual[1] = y;
            actual[2] = z;
        });
        assertEquals(expected[0], actual[0], 0.0001f);
        assertEquals(expected[1], actual[1], 0.0001f);
        assertEquals(expected[2], actual[2], 0.0001f);
    }

    @Test
    void failedNativeBodyRemovalKeepsJavaBodyStateForRetry() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        PhysicsBackendRuntime runtime = provider.createRuntime();
        int spaceId = runtime.createSpace(new SpaceId(71));
        long bodyId = createBox(runtime, spaceId, 1.0f, 2.0f, 3.0f);
        long nativeSpaceHandle = nativeSpaceHandle(runtime, spaceId);
        RapierNative.destroySpaceNative(nativeSpaceHandle);
        try {
            assertThrows(IllegalStateException.class, () -> runtime.removeBody(spaceId, bodyId));

            assertTrue(runtime.containsBody(spaceId, bodyId));
            assertEquals(1, runtime.bodyCount(spaceId));
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void failedNativeJointRemovalKeepsJavaJointStateForRetry() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        PhysicsBackendRuntime runtime = provider.createRuntime();
        int spaceId = runtime.createSpace(new SpaceId(72));
        long firstBodyId = createBox(runtime, spaceId, 1.0f, 2.0f, 3.0f);
        long secondBodyId = createBox(runtime, spaceId, 2.0f, 2.0f, 3.0f);
        long jointId = runtime.createJoint(spaceId,
            BackendRuntimeCodes.jointTypeCode(BackendJointType.FIXED),
            firstBodyId,
            secondBodyId,
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
        long nativeSpaceHandle = nativeSpaceHandle(runtime, spaceId);
        RapierNative.destroySpaceNative(nativeSpaceHandle);
        try {
            assertThrows(IllegalStateException.class, () -> runtime.removeJoint(spaceId, jointId));

            assertEquals(1, runtime.jointCount(spaceId));
            assertEquals(BackendRuntimeCodes.jointTypeCode(BackendJointType.FIXED),
                runtime.jointType(spaceId, jointId));
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void failedNativeBodyMutationDoesNotAdvanceCachedState() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        PhysicsBackendRuntime runtime = provider.createRuntime();
        int spaceId = runtime.createSpace(new SpaceId(73));
        long bodyId = createBox(runtime, spaceId, 1.0f, 2.0f, 3.0f);
        long nativeSpaceHandle = nativeSpaceHandle(runtime, spaceId);
        RapierNative.destroySpaceNative(nativeSpaceHandle);
        try {
            assertThrows(IllegalStateException.class,
                () -> runtime.setBodyPosition(spaceId, bodyId, 9.0f, 9.0f, 9.0f));

            assertEquals(1.0f, cachedPositionX(runtime, spaceId, bodyId));
            assertEquals(2.0f, cachedPositionY(runtime, spaceId, bodyId));
            assertEquals(3.0f, cachedPositionZ(runtime, spaceId, bodyId));
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Test
    void closeDestroysAllCachedSpaces() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        PhysicsBackendRuntime runtime = provider.createRuntime();
        int firstSpaceId = runtime.createSpace(new SpaceId(74));
        int secondSpaceId = runtime.createSpace(new SpaceId(75));
        createBox(runtime, firstSpaceId, 1.0f, 2.0f, 3.0f);
        createBox(runtime, secondSpaceId, 2.0f, 2.0f, 3.0f);

        runtime.close();

        assertThrows(IllegalArgumentException.class, () -> runtime.bodyCount(firstSpaceId));
        assertThrows(IllegalArgumentException.class, () -> runtime.bodyCount(secondSpaceId));
    }

    private static long createBox(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        float positionX,
        float positionY,
        float positionZ) {
        return runtime.createBody(spaceId,
            BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
            0.5f,
            0.5f,
            0.5f,
            -1.0f,
            -1.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            positionX,
            positionY,
            positionZ,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static long nativeSpaceHandle(@Nonnull PhysicsBackendRuntime runtime, int spaceId) {
        Object state = spaceState(runtime, spaceId);
        try {
            Field handle = state.getClass().getDeclaredField("nativeSpaceHandle");
            handle.setAccessible(true);
            return handle.getLong(state);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Rapier native space handle", exception);
        }
    }

    private static float cachedPositionX(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId) {
        return cachedFloat(runtime, spaceId, bodyId, "positionX");
    }

    private static float cachedPositionY(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId) {
        return cachedFloat(runtime, spaceId, bodyId, "positionY");
    }

    private static float cachedPositionZ(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId) {
        return cachedFloat(runtime, spaceId, bodyId, "positionZ");
    }

    private static float cachedFloat(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId,
        @Nonnull String fieldName) {
        Object body = bodyState(runtime, spaceId, bodyId);
        try {
            Field field = body.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getFloat(body);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Rapier body cache", exception);
        }
    }

    private static Object bodyState(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId) {
        Object state = spaceState(runtime, spaceId);
        try {
            Field bodies = state.getClass().getDeclaredField("bodiesById");
            bodies.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, ?> bodiesById = (Map<Long, ?>) bodies.get(state);
            Object body = bodiesById.get(bodyId);
            if (body == null) {
                throw new AssertionError("No cached body " + bodyId);
            }
            return body;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Rapier body cache", exception);
        }
    }

    private static Object spaceState(@Nonnull PhysicsBackendRuntime runtime, int spaceId) {
        try {
            Field spaces = runtime.getClass().getDeclaredField("spaces");
            spaces.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Integer, ?> spacesById = (Map<Integer, ?>) spaces.get(runtime);
            Object state = spacesById.get(spaceId);
            if (state == null) {
                throw new AssertionError("No cached space " + spaceId);
            }
            return state;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Rapier runtime cache", exception);
        }
    }

    private static final class CapturedSnapshot implements BackendBodySnapshotSink {

        private long bodyId;
        private int shapeTypeCode;
        private int bodyTypeCode;
        private float positionX;
        private float positionY;
        private float positionZ;
        private boolean sensor;
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
            this.bodyId = bodyId;
            this.shapeTypeCode = shapeTypeCode;
            this.bodyTypeCode = bodyTypeCode;
            this.positionX = positionX;
            this.positionY = positionY;
            this.positionZ = positionZ;
            this.sensor = sensor;
            this.friction = friction;
            this.restitution = restitution;
            this.linearDamping = linearDamping;
            this.angularDamping = angularDamping;
            this.collisionGroup = collisionGroup;
            this.collisionMask = collisionMask;
            this.continuousCollisionEnabled = continuousCollisionEnabled;
        }
    }
}
