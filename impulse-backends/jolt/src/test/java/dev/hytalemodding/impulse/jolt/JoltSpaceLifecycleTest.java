package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JoltSpaceLifecycleTest {

    @Test
    void createSpaceUsesRequestedIdAndNativeHandle() {
        InMemoryNativeLibrary nativeLibrary = new InMemoryNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);

        int spaceId = runtime.createSpace(new SpaceId(42));

        assertEquals(42, spaceId);
        assertEquals(1, nativeLibrary.createCalls);
        assertEquals(1L, nativeLibrary.handleFor(42));
        assertEquals(0, runtime.bodyCount(42));
        assertEquals(0, runtime.jointCount(42));
    }

    @Test
    void gravityRoundTripUsesNativeSpaceHandle() {
        InMemoryNativeLibrary nativeLibrary = new InMemoryNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        runtime.createSpace(new SpaceId(7));

        runtime.setGravity(7, 0.0f, -4.0f, 1.0f);
        float[] gravity = new float[3];
        runtime.getGravity(7, (x, y, z) -> {
            gravity[0] = x;
            gravity[1] = y;
            gravity[2] = z;
        });

        assertArrayEquals(new float[] {0.0f, -4.0f, 1.0f}, gravity);
    }

    @Test
    void nonPositiveStepDoesNotCallNativeStep() {
        InMemoryNativeLibrary nativeLibrary = new InMemoryNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        runtime.createSpace(new SpaceId(3));

        runtime.step(3, 0.0f);
        runtime.step(3, -1.0f);

        assertEquals(0, nativeLibrary.stepCalls);
    }

    @Test
    void nonPositiveStepStillRequiresKnownSpace() {
        InMemoryNativeLibrary nativeLibrary = new InMemoryNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);

        assertThrows(IllegalArgumentException.class, () -> runtime.step(404, 0.0f));
        assertEquals(0, nativeLibrary.stepCalls);
    }

    @Test
    void positiveStepCallsNativeStepWithHandle() {
        InMemoryNativeLibrary nativeLibrary = new InMemoryNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        runtime.createSpace(new SpaceId(4));

        runtime.step(4, 1.0f / 20.0f);

        assertEquals(1, nativeLibrary.stepCalls);
        assertEquals(1L, nativeLibrary.lastStepHandle);
        assertEquals(1.0f / 20.0f, nativeLibrary.lastStepDt);
    }

    @Test
    void destroySpaceReleasesNativeHandleAndRejectsFurtherAccess() {
        InMemoryNativeLibrary nativeLibrary = new InMemoryNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        runtime.createSpace(new SpaceId(9));

        runtime.destroySpace(9);

        assertEquals(1, nativeLibrary.destroyCalls);
        assertEquals(1L, nativeLibrary.destroyedHandle);
        assertThrows(IllegalArgumentException.class, () -> runtime.bodyCount(9));
    }

    @Test
    void closeReleasesEveryRegisteredNativeSpace() {
        InMemoryNativeLibrary nativeLibrary = new InMemoryNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        runtime.createSpace(new SpaceId(10));
        runtime.createSpace(new SpaceId(11));

        runtime.close();

        assertEquals(2, nativeLibrary.destroyCalls);
        assertThrows(IllegalArgumentException.class, () -> runtime.bodyCount(10));
        assertThrows(IllegalArgumentException.class, () -> runtime.bodyCount(11));
    }

    @Test
    void runtimeStatsReportsNativeCounts() {
        InMemoryNativeLibrary nativeLibrary = new InMemoryNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        runtime.createSpace(new SpaceId(6));
        nativeLibrary.space(1L).bodyCount = 12;
        nativeLibrary.space(1L).jointCount = 5;

        int[] counts = new int[2];
        boolean[] available = new boolean[1];
        runtime.runtimeStats(6, (bodyCount,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            jointCount,
            statsAvailable) -> {
            counts[0] = bodyCount;
            counts[1] = jointCount;
            available[0] = statsAvailable;
        });

        assertArrayEquals(new int[] {12, 5}, counts);
        assertEquals(true, available[0]);
    }

    private static final class InMemoryNativeLibrary implements JoltNativeLibrary {

        private final Map<Long, SpaceState> spaces = new HashMap<>();
        private long nextHandle = 1L;
        private int createCalls;
        private int destroyCalls;
        private long destroyedHandle;
        private int stepCalls;
        private long lastStepHandle;
        private float lastStepDt;

        @Override
        public long createSpace() {
            long handle = nextHandle++;
            spaces.put(handle, new SpaceState());
            createCalls++;
            return handle;
        }

        @Override
        public void destroySpace(long spaceHandle) {
            spaces.remove(spaceHandle);
            destroyedHandle = spaceHandle;
            destroyCalls++;
        }

        @Override
        public void step(long spaceHandle, float dt) {
            requireSpace(spaceHandle);
            lastStepHandle = spaceHandle;
            lastStepDt = dt;
            stepCalls++;
        }

        @Override
        public void setGravity(long spaceHandle, float x, float y, float z) {
            SpaceState space = requireSpace(spaceHandle);
            space.gravity[0] = x;
            space.gravity[1] = y;
            space.gravity[2] = z;
        }

        @Override
        public void getGravity(long spaceHandle, float[] out) {
            SpaceState space = requireSpace(spaceHandle);
            out[0] = space.gravity[0];
            out[1] = space.gravity[1];
            out[2] = space.gravity[2];
        }

        @Override
        public int bodyCount(long spaceHandle) {
            return requireSpace(spaceHandle).bodyCount;
        }

        @Override
        public int jointCount(long spaceHandle) {
            return requireSpace(spaceHandle).jointCount;
        }

        private long handleFor(int spaceId) {
            assertEquals(42, spaceId);
            return spaces.keySet().iterator().next();
        }

        private SpaceState space(long handle) {
            return requireSpace(handle);
        }

        private SpaceState requireSpace(long spaceHandle) {
            SpaceState space = spaces.get(spaceHandle);
            if (space == null) {
                throw new IllegalArgumentException("Unknown test native space handle: " + spaceHandle);
            }
            return space;
        }
    }

    private static final class SpaceState {

        private final float[] gravity = new float[] {0.0f, -9.81f, 0.0f};
        private int bodyCount;
        private int jointCount;
    }
}
