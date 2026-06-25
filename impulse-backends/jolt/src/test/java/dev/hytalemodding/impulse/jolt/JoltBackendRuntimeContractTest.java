package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import org.junit.jupiter.api.Test;

class JoltBackendRuntimeContractTest {

    @Test
    void validCreateBodyCodesReachDeferredBodyImplementation() {
        JoltBackendRuntime runtime = runtimeWithSpace(1);

        int[] shapes = {
            BackendRuntimeCodes.SHAPE_BOX,
            BackendRuntimeCodes.SHAPE_SPHERE,
            BackendRuntimeCodes.SHAPE_CAPSULE,
            BackendRuntimeCodes.SHAPE_CYLINDER,
            BackendRuntimeCodes.SHAPE_CONE,
            BackendRuntimeCodes.SHAPE_PLANE
        };
        int[] axes = {
            BackendRuntimeCodes.AXIS_X,
            BackendRuntimeCodes.AXIS_Y,
            BackendRuntimeCodes.AXIS_Z
        };
        int[] bodyTypes = {
            BackendRuntimeCodes.BODY_STATIC,
            BackendRuntimeCodes.BODY_DYNAMIC,
            BackendRuntimeCodes.BODY_KINEMATIC
        };

        for (int shape : shapes) {
            for (int axis : axes) {
                for (int bodyType : bodyTypes) {
                    assertThrows(UnsupportedOperationException.class,
                        () -> createBody(runtime, 1, shape, axis, bodyType));
                }
            }
        }
    }

    @Test
    void invalidCreateBodyCodesFailBeforeDeferredBodyImplementation() {
        JoltBackendRuntime runtime = runtimeWithSpace(2);

        assertThrows(IllegalArgumentException.class,
            () -> createBody(runtime, 2, 999, BackendRuntimeCodes.AXIS_Y, BackendRuntimeCodes.BODY_DYNAMIC));
        assertThrows(IllegalArgumentException.class,
            () -> createBody(runtime, 2, BackendRuntimeCodes.SHAPE_BOX, 999, BackendRuntimeCodes.BODY_DYNAMIC));
        assertThrows(IllegalArgumentException.class,
            () -> createBody(runtime, 2, BackendRuntimeCodes.SHAPE_BOX, BackendRuntimeCodes.AXIS_Y, 999));
    }

    @Test
    void unsupportedCreateBodyShapesFailAsContractInputErrors() {
        JoltBackendRuntime runtime = runtimeWithSpace(3);

        assertThrows(IllegalArgumentException.class,
            () -> createBody(runtime,
                3,
                BackendRuntimeCodes.SHAPE_UNKNOWN,
                BackendRuntimeCodes.AXIS_Y,
                BackendRuntimeCodes.BODY_DYNAMIC));
        assertThrows(IllegalArgumentException.class,
            () -> createBody(runtime,
                3,
                BackendRuntimeCodes.SHAPE_VOXELS,
                BackendRuntimeCodes.AXIS_Y,
                BackendRuntimeCodes.BODY_STATIC));
    }

    @Test
    void validCreateJointCodesReachEndpointValidation() {
        JoltBackendRuntime runtime = runtimeWithSpace(4);

        int[] jointTypes = {
            BackendRuntimeCodes.JOINT_FIXED,
            BackendRuntimeCodes.JOINT_POINT,
            BackendRuntimeCodes.JOINT_HINGE,
            BackendRuntimeCodes.JOINT_SLIDER,
            BackendRuntimeCodes.JOINT_SPRING
        };

        for (int jointType : jointTypes) {
            assertThrows(IllegalArgumentException.class,
                () -> createJoint(runtime, 4, jointType));
        }
    }

    @Test
    void invalidCreateJointCodeFailsBeforeDeferredJointImplementation() {
        JoltBackendRuntime runtime = runtimeWithSpace(5);

        assertThrows(IllegalArgumentException.class, () -> createJoint(runtime, 5, 999));
    }

    @Test
    void unsupportedCapabilityProbesReturnFalseAndSettingsMutationsNoop() {
        JoltBackendRuntime runtime = runtimeWithSpace(6);

        assertFalse(runtime.supportsVoxelTerrain(6));
        assertTrue(runtime.supportsContinuousCollision(6));
        assertFalse(runtime.supportsSolverTuning(6));
        assertFalse(runtime.supportsActivationTuning(6));

        assertDoesNotThrow(() -> runtime.applySolverTuning(6, new PhysicsSolverTuning(8, 2)));
        assertDoesNotThrow(() -> runtime.applyActivationTuning(6,
            new PhysicsActivationTuning(0.05f, 0.1f, 0.5f)));
        assertDoesNotThrow(() -> runtime.applyExtensionSettings(6,
            new PhysicsCapabilityId("impulse:test"),
            consumer -> {
                consumer.accept("key", "value");
            }));
    }

    @Test
    void unimplementedEmptyQueriesReturnEmptyResults() {
        JoltBackendRuntime runtime = runtimeWithSpace(7);

        assertFalse(runtime.raycastClosest(7,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            -1.0f,
            0.0f,
            (_, _, _, _, _, _, _, _, _) -> {
            }));
        assertEquals(0,
            runtime.raycastAll(7,
                0.0f,
                1.0f,
                0.0f,
                0.0f,
                -1.0f,
                0.0f,
                (_, _, _, _, _, _, _, _, _) -> {
                }));
        assertEquals(0, runtime.contacts(7, (_, _, _, _, _, _, _, _, _, _, _, _, _) -> {
        }));
        assertEquals(0, runtime.contactCount(7));
    }

    private static JoltBackendRuntime runtimeWithSpace(int spaceId) {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), new NoopNativeLibrary());
        runtime.createSpace(new SpaceId(spaceId));
        return runtime;
    }

    private static long createBody(JoltBackendRuntime runtime,
        int spaceId,
        int shape,
        int axis,
        int bodyType) {
        return runtime.createBody(spaceId,
            shape,
            0.5f,
            0.5f,
            0.5f,
            0.25f,
            0.5f,
            axis,
            0.0f,
            1.0f,
            bodyType,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static long createJoint(JoltBackendRuntime runtime, int spaceId, int jointType) {
        return runtime.createJoint(spaceId,
            jointType,
            1L,
            2L,
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
            -1.0f,
            1.0f,
            false,
            0.0f,
            0.0f);
    }

    private static final class NoopNativeLibrary implements JoltNativeLibrary {

        private long nextHandle = 1L;

        @Override
        public long createSpace() {
            return nextHandle++;
        }

        @Override
        public void destroySpace(long spaceHandle) {
        }

        @Override
        public void step(long spaceHandle, float dt) {
        }

        @Override
        public void setGravity(long spaceHandle, float x, float y, float z) {
        }

        @Override
        public void getGravity(long spaceHandle, float[] out) {
        }

        @Override
        public int bodyCount(long spaceHandle) {
            return 0;
        }

        @Override
        public int raycastClosest(long spaceHandle,
            float fromX,
            float fromY,
            float fromZ,
            float toX,
            float toY,
            float toZ,
            long[] bodyHandleOut,
            float[] hitOut) {
            return 0;
        }

        @Override
        public int raycastAll(long spaceHandle,
            float fromX,
            float fromY,
            float fromZ,
            float toX,
            float toY,
            float toZ,
            int maxHits,
            long[] bodyHandles,
            float[] hits) {
            return 0;
        }

        @Override
        public int contacts(long spaceHandle, int maxContacts, long[] bodyHandles, float[] contacts) {
            return 0;
        }

        @Override
        public int contactCount(long spaceHandle) {
            return 0;
        }

        @Override
        public int jointCount(long spaceHandle) {
            return 0;
        }
    }
}
