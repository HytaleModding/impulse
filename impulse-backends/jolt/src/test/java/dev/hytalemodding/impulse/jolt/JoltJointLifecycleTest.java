package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import org.junit.jupiter.api.Test;

class JoltJointLifecycleTest {

    @Test
    void createJointReturnsStableJavaJointIdAndStoresNativeHandle() {
        JoltTestNativeLibrary nativeLibrary = new JoltTestNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        int spaceId = runtime.createSpace(new SpaceId(21));
        long bodyA = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);
        long bodyB = JoltBodyLifecycleTest.createBox(runtime, spaceId, 2.0f);

        long jointId = createJoint(runtime, spaceId, BackendRuntimeCodes.JOINT_POINT, bodyA, bodyB);
        long spaceHandle = nativeLibrary.firstSpaceHandle();
        long nativeJointHandle = nativeLibrary.nativeJointHandle(spaceHandle, 0);

        assertEquals(1L, jointId);
        assertNotEquals(nativeJointHandle, jointId);
        assertEquals(1, runtime.jointCount(spaceId));
        assertEquals(BackendRuntimeCodes.JOINT_POINT, runtime.jointType(spaceId, jointId));
        assertEquals(bodyA, runtime.jointBodyA(spaceId, jointId));
        assertEquals(bodyB, runtime.jointBodyB(spaceId, jointId));
    }

    @Test
    void createJointSupportsEveryBackendJointCode() {
        JoltTestNativeLibrary nativeLibrary = new JoltTestNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        int spaceId = runtime.createSpace(new SpaceId(22));
        long bodyA = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);
        long bodyB = JoltBodyLifecycleTest.createBox(runtime, spaceId, 2.0f);

        int[] jointTypes = {
            BackendRuntimeCodes.JOINT_FIXED,
            BackendRuntimeCodes.JOINT_POINT,
            BackendRuntimeCodes.JOINT_HINGE,
            BackendRuntimeCodes.JOINT_SLIDER,
            BackendRuntimeCodes.JOINT_SPRING
        };

        for (int index = 0; index < jointTypes.length; index++) {
            long jointId = createJoint(runtime, spaceId, jointTypes[index], bodyA, bodyB);

            assertEquals(index + 1L, jointId);
            assertEquals(jointTypes[index], runtime.jointType(spaceId, jointId));
        }
        assertEquals(jointTypes.length, runtime.jointCount(spaceId));
    }

    @Test
    void removeJointDestroysNativeHandleAndMakesJavaIdStale() {
        JoltTestNativeLibrary nativeLibrary = new JoltTestNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        int spaceId = runtime.createSpace(new SpaceId(23));
        long bodyA = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);
        long bodyB = JoltBodyLifecycleTest.createBox(runtime, spaceId, 2.0f);
        long jointId = createJoint(runtime, spaceId, BackendRuntimeCodes.JOINT_FIXED, bodyA, bodyB);
        long nativeJointHandle = nativeLibrary.nativeJointHandle(nativeLibrary.firstSpaceHandle(), 0);

        runtime.removeJoint(spaceId, jointId);

        assertEquals(nativeJointHandle, nativeLibrary.lastRemovedJointHandle());
        assertEquals(0, runtime.jointCount(spaceId));
        assertThrows(IllegalArgumentException.class, () -> runtime.jointType(spaceId, jointId));
        assertThrows(IllegalArgumentException.class, () -> runtime.jointBodyA(spaceId, jointId));
        assertThrows(IllegalArgumentException.class, () -> runtime.jointBodyB(spaceId, jointId));
    }

    @Test
    void removingBodyRemovesAttachedJointState() {
        JoltTestNativeLibrary nativeLibrary = new JoltTestNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        int spaceId = runtime.createSpace(new SpaceId(25));
        long bodyA = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);
        long bodyB = JoltBodyLifecycleTest.createBox(runtime, spaceId, 2.0f);
        long jointId = createJoint(runtime, spaceId, BackendRuntimeCodes.JOINT_FIXED, bodyA, bodyB);

        runtime.removeBody(spaceId, bodyA);

        assertEquals(0, runtime.jointCount(spaceId));
        assertEquals(0, nativeLibrary.jointCount(nativeLibrary.firstSpaceHandle()));
        assertThrows(IllegalArgumentException.class, () -> runtime.jointType(spaceId, jointId));
    }

    @Test
    void sameBodyJointEndpointsFailBeforeNativeCall() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), new JoltTestNativeLibrary());
        int spaceId = runtime.createSpace(new SpaceId(26));
        long body = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);

        assertThrows(IllegalArgumentException.class,
            () -> createJoint(runtime, spaceId, BackendRuntimeCodes.JOINT_POINT, body, body));
    }

    @Test
    void hingeAndSliderSupportDefaultZeroLimits() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), new JoltTestNativeLibrary());
        int spaceId = runtime.createSpace(new SpaceId(27));
        long bodyA = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);
        long bodyB = JoltBodyLifecycleTest.createBox(runtime, spaceId, 2.0f);

        long hinge = createJoint(runtime,
            spaceId,
            BackendRuntimeCodes.JOINT_HINGE,
            bodyA,
            bodyB,
            0.0f,
            0.0f);
        long slider = createJoint(runtime,
            spaceId,
            BackendRuntimeCodes.JOINT_SLIDER,
            bodyA,
            bodyB,
            0.0f,
            0.0f);

        assertEquals(1L, hinge);
        assertEquals(2L, slider);
        assertEquals(2, runtime.jointCount(spaceId));
    }

    @Test
    void unknownJointMutationIsANoopAndLookupFails() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), new JoltTestNativeLibrary());
        int spaceId = runtime.createSpace(new SpaceId(24));

        runtime.removeJoint(spaceId, 404L);

        assertEquals(0, runtime.jointCount(spaceId));
        assertThrows(IllegalArgumentException.class, () -> runtime.jointType(spaceId, 404L));
    }

    private static long createJoint(JoltBackendRuntime runtime,
        int spaceId,
        int jointType,
        long bodyA,
        long bodyB) {
        return createJoint(runtime, spaceId, jointType, bodyA, bodyB, -1.0f, 1.0f);
    }

    private static long createJoint(JoltBackendRuntime runtime,
        int spaceId,
        int jointType,
        long bodyA,
        long bodyB,
        float lowerLimit,
        float upperLimit) {
        return runtime.createJoint(spaceId,
            jointType,
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
            1.0f,
            10.0f,
            0.5f,
            lowerLimit,
            upperLimit,
            true,
            0.5f,
            2.0f);
    }
}
