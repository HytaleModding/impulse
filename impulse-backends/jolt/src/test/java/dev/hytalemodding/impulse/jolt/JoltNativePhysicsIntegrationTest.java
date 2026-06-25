package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import org.junit.jupiter.api.Test;

class JoltNativePhysicsIntegrationTest {

    @Test
    void dynamicBoxRestsOnStaticBoxFloor() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend());
        int spaceId = runtime.createSpace(new SpaceId(51));
        runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
        runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_BOX,
            20.0f,
            0.5f,
            20.0f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            0.0f,
            BackendRuntimeCodes.BODY_STATIC,
            0.0f,
            -0.5f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        long boxId = runtime.createBody(spaceId,
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
            4.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);

        for (int step = 0; step < 300; step++) {
            runtime.step(spaceId, 1.0f / 60.0f);
        }

        JoltTestNativeLibrary.CapturedBodySnapshot snapshot =
            new JoltTestNativeLibrary.CapturedBodySnapshot();
        assertTrue(runtime.bodySnapshot(spaceId, boxId, snapshot));
        assertTrue(snapshot.positionY > 0.45f,
            "dynamic box should rest on the static floor, y=" + snapshot.positionY);
        assertTrue(snapshot.positionY < 0.75f,
            "dynamic box should stay close to its resting height, y=" + snapshot.positionY);

        runtime.destroySpace(spaceId);
    }
}
