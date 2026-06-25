package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import org.junit.jupiter.api.Test;

class JoltBodyLifecycleTest {

    @Test
    void createBodyReturnsStableJavaBodyIdAndStoresNativeHandle() {
        JoltTestNativeLibrary nativeLibrary = new JoltTestNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        int spaceId = runtime.createSpace(new SpaceId(11));

        long bodyA = createBox(runtime, spaceId, 1.0f);
        long bodyB = createBox(runtime, spaceId, 2.0f);
        long spaceHandle = nativeLibrary.firstSpaceHandle();
        long nativeBodyA = nativeLibrary.nativeBodyHandle(spaceHandle, 0);
        long nativeBodyB = nativeLibrary.nativeBodyHandle(spaceHandle, 1);

        assertEquals(1L, bodyA);
        assertEquals(2L, bodyB);
        assertNotEquals(nativeBodyA, bodyA);
        assertNotEquals(nativeBodyB, bodyB);
        assertEquals(2, runtime.bodyCount(spaceId));
        assertTrue(runtime.containsBody(spaceId, bodyA));
        assertTrue(runtime.containsBody(spaceId, bodyB));
    }

    @Test
    void removeBodyDestroysNativeHandleAndMakesJavaIdStale() {
        JoltTestNativeLibrary nativeLibrary = new JoltTestNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        int spaceId = runtime.createSpace(new SpaceId(12));
        long bodyId = createBox(runtime, spaceId, 1.0f);
        long nativeBodyHandle = nativeLibrary.nativeBodyHandle(nativeLibrary.firstSpaceHandle(), 0);

        runtime.removeBody(spaceId, bodyId);

        assertEquals(nativeBodyHandle, nativeLibrary.lastRemovedBodyHandle());
        assertFalse(runtime.containsBody(spaceId, bodyId));
        assertEquals(0, runtime.bodyCount(spaceId));
        assertFalse(runtime.bodySnapshot(spaceId,
            bodyId,
            new JoltTestNativeLibrary.CapturedBodySnapshot()));
    }

    @Test
    void unknownBodyMutationFailsBeforeNativeCall() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), new JoltTestNativeLibrary());
        int spaceId = runtime.createSpace(new SpaceId(13));

        assertThrows(IllegalArgumentException.class,
            () -> runtime.setBodyPosition(spaceId, 404L, 1.0f, 2.0f, 3.0f));
    }

    static long createBox(JoltBackendRuntime runtime, int spaceId, float positionY) {
        return runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.75f,
            1.25f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            2.0f,
            BackendRuntimeCodes.BODY_DYNAMIC,
            1.0f,
            positionY,
            3.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }
}
