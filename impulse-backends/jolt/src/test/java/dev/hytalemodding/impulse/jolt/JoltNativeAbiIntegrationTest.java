package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import org.junit.jupiter.api.Test;

class JoltNativeAbiIntegrationTest {

    @Test
    void nativeLibraryRunsSpaceAndBodyAbi() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend());
        int spaceId = runtime.createSpace(new SpaceId(41));
        runtime.setGravity(spaceId, 0.0f, -10.0f, 0.0f);

        long bodyId = runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_SPHERE,
            0.0f,
            0.0f,
            0.0f,
            0.5f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            2.0f,
            BackendRuntimeCodes.BODY_DYNAMIC,
            0.0f,
            10.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);

        runtime.setBodyFriction(spaceId, bodyId, 0.4f);
        runtime.setBodyRestitution(spaceId, bodyId, 0.2f);
        runtime.setBodyCollisionFilter(spaceId, bodyId, 3, 7);
        runtime.setBodySensor(spaceId, bodyId, true);
        runtime.setBodyVelocity(spaceId, bodyId, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        runtime.step(spaceId, 0.1f);

        JoltTestNativeLibrary.CapturedBodySnapshot snapshot =
            new JoltTestNativeLibrary.CapturedBodySnapshot();
        assertTrue(runtime.bodySnapshot(spaceId, bodyId, snapshot));
        assertEquals(bodyId, snapshot.bodyId);
        assertEquals(BackendRuntimeCodes.SHAPE_SPHERE, snapshot.shapeTypeCode);
        assertEquals(BackendRuntimeCodes.BODY_DYNAMIC, snapshot.bodyTypeCode);
        assertEquals(0.5f, snapshot.radius);
        assertEquals(0.4f, snapshot.friction);
        assertEquals(0.2f, snapshot.restitution);
        assertEquals(3, snapshot.collisionGroup);
        assertEquals(7, snapshot.collisionMask);
        assertTrue(snapshot.sensor);
        assertTrue(snapshot.positionY < 10.0f);
        assertTrue(snapshot.linearVelocityY < 0.0f);
        assertEquals(1, runtime.bodyCount(spaceId));

        runtime.removeBody(spaceId, bodyId);

        assertFalse(runtime.containsBody(spaceId, bodyId));
        assertEquals(0, runtime.bodyCount(spaceId));
        assertFalse(runtime.bodySnapshot(spaceId,
            bodyId,
            new JoltTestNativeLibrary.CapturedBodySnapshot()));

        runtime.destroySpace(spaceId);

        assertThrows(IllegalArgumentException.class, () -> runtime.bodyCount(spaceId));
    }

    @Test
    void nativeLibraryCreatesRestoredShapeAndBodyTypeCombinations() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend());
        int spaceId = runtime.createSpace(new SpaceId(43));
        int[] shapes = {
            BackendRuntimeCodes.SHAPE_BOX,
            BackendRuntimeCodes.SHAPE_SPHERE,
            BackendRuntimeCodes.SHAPE_CAPSULE,
            BackendRuntimeCodes.SHAPE_CYLINDER,
            BackendRuntimeCodes.SHAPE_CONE,
            BackendRuntimeCodes.SHAPE_PLANE
        };
        int[] bodyTypes = {
            BackendRuntimeCodes.BODY_STATIC,
            BackendRuntimeCodes.BODY_DYNAMIC,
            BackendRuntimeCodes.BODY_KINEMATIC
        };

        for (int shape : shapes) {
            for (int bodyType : bodyTypes) {
                long bodyId = assertDoesNotThrow(() -> runtime.createBody(spaceId,
                    shape,
                    0.5f,
                    0.5f,
                    0.5f,
                    0.5f,
                    0.5f,
                    BackendRuntimeCodes.AXIS_Y,
                    0.0f,
                    bodyType == BackendRuntimeCodes.BODY_DYNAMIC ? 1.0f : 0.0f,
                    bodyType,
                    0.0f,
                    2.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f),
                    "shape=" + shape + " bodyType=" + bodyType);
                assertTrue(runtime.containsBody(spaceId, bodyId));
                runtime.removeBody(spaceId, bodyId);
            }
        }

        runtime.destroySpace(spaceId);
    }

    @Test
    void nativeLibraryCreatesBodiesPastPreviousRestoreScaleLimit() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend());
        int spaceId = runtime.createSpace(new SpaceId(44));
        int bodyCount = 65_537;

        for (int index = 0; index < bodyCount; index++) {
            long bodyId = assertDoesNotThrow(() -> runtime.createBody(spaceId,
                BackendRuntimeCodes.SHAPE_BOX,
                0.5f,
                0.5f,
                0.5f,
                0.0f,
                0.0f,
                BackendRuntimeCodes.AXIS_Y,
                0.0f,
                0.0f,
                BackendRuntimeCodes.BODY_STATIC,
                0.0f,
                2.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f),
                "body index=" + index);
            assertTrue(runtime.containsBody(spaceId, bodyId));
        }

        assertEquals(bodyCount, runtime.bodyCount(spaceId));
        runtime.destroySpace(spaceId);
    }

    @Test
    void nativeLibraryRunsJointAbiForEveryJointType() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend());
        int spaceId = runtime.createSpace(new SpaceId(42));
        long bodyA = runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            0.0f,
            BackendRuntimeCodes.BODY_STATIC,
            0.0f,
            2.0f,
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
            BackendRuntimeCodes.BODY_DYNAMIC,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);

        int[] jointTypes = {
            BackendRuntimeCodes.JOINT_FIXED,
            BackendRuntimeCodes.JOINT_POINT,
            BackendRuntimeCodes.JOINT_HINGE,
            BackendRuntimeCodes.JOINT_SLIDER,
            BackendRuntimeCodes.JOINT_SPRING
        };
        for (int jointType : jointTypes) {
            long jointId = runtime.createJoint(spaceId,
                jointType,
                bodyA,
                bodyB,
                0.0f,
                -0.5f,
                0.0f,
                0.0f,
                0.5f,
                0.0f,
                0.0f,
                1.0f,
                0.0f,
                1.0f,
                10.0f,
                0.5f,
                -0.75f,
                0.75f,
                true,
                0.25f,
                2.0f);

            assertEquals(1, runtime.jointCount(spaceId));
            assertEquals(1, nativeJointCount(runtime, spaceId));
            assertEquals(jointType, runtime.jointType(spaceId, jointId));
            assertEquals(bodyA, runtime.jointBodyA(spaceId, jointId));
            assertEquals(bodyB, runtime.jointBodyB(spaceId, jointId));

            runtime.removeJoint(spaceId, jointId);

            assertEquals(0, runtime.jointCount(spaceId));
            assertEquals(0, nativeJointCount(runtime, spaceId));
        }

        runtime.destroySpace(spaceId);
    }

    @Test
    void nativeLibraryCreatesHingeAndSliderWithDefaultZeroLimits() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend());
        int spaceId = runtime.createSpace(new SpaceId(45));
        long bodyA = runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            0.0f,
            BackendRuntimeCodes.BODY_STATIC,
            0.0f,
            2.0f,
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
            BackendRuntimeCodes.BODY_DYNAMIC,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);

        for (int jointType : new int[] {
            BackendRuntimeCodes.JOINT_HINGE,
            BackendRuntimeCodes.JOINT_SLIDER
        }) {
            long jointId = assertDoesNotThrow(() -> runtime.createJoint(spaceId,
                jointType,
                bodyA,
                bodyB,
                0.0f,
                -0.5f,
                0.0f,
                0.0f,
                0.5f,
                0.0f,
                0.0f,
                1.0f,
                0.0f,
                1.0f,
                10.0f,
                0.5f,
                0.0f,
                0.0f,
                true,
                0.25f,
                2.0f));

            assertEquals(jointType, runtime.jointType(spaceId, jointId));
            runtime.removeJoint(spaceId, jointId);
        }

        runtime.destroySpace(spaceId);
    }

    private static int nativeJointCount(JoltBackendRuntime runtime, int spaceId) {
        int[] jointCount = new int[1];
        runtime.runtimeStats(spaceId, (_,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            joints,
            _) -> jointCount[0] = joints);
        return jointCount[0];
    }
}
