package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendContactSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import org.junit.jupiter.api.Test;

class RapierBoundedContactsTest {

    @Test
    void getContactsWithLimitReturnsAtMostRequestedContacts() {
        RapierBackendRuntimeProvider provider = new RapierBackendRuntimeProvider();
        provider.init();
        PhysicsBackendRuntime runtime = provider.createRuntime();
        int spaceId = runtime.createSpace(new SpaceId(1));
        try {
            runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
            addStaticFloor(runtime, spaceId, 8, 8);
            addRestingBoxes(runtime, spaceId, 8, 8);
            for (int i = 0; i < 180; i++) {
                runtime.step(spaceId, 1.0f / 30.0f);
            }

            int contacts = runtime.contacts(spaceId, 5, new CountingContactSink());

            assertFalse(contacts == 0);
            assertTrue(contacts <= 5);
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static void addStaticFloor(PhysicsBackendRuntime runtime,
        int spaceId,
        int width,
        int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                createBox(runtime,
                    spaceId,
                    x,
                    0.0f,
                    z,
                    0.5f,
                    BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.STATIC));
            }
        }
    }

    private static void addRestingBoxes(PhysicsBackendRuntime runtime,
        int spaceId,
        int width,
        int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                createBox(runtime,
                    spaceId,
                    x,
                    1.05f,
                    z,
                    1.0f,
                    BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC));
            }
        }
    }

    private static void createBox(PhysicsBackendRuntime runtime,
        int spaceId,
        float x,
        float y,
        float z,
        float mass,
        int bodyTypeCode) {
        runtime.createBody(spaceId,
            BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
            0.5f,
            0.5f,
            0.5f,
            -1.0f,
            -1.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            mass,
            bodyTypeCode,
            x,
            y,
            z,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static final class CountingContactSink implements BackendContactSink {

        @Override
        public void accept(long bodyAId,
            long bodyBId,
            float pointAX,
            float pointAY,
            float pointAZ,
            float pointBX,
            float pointBY,
            float pointBZ,
            float normalBX,
            float normalBY,
            float normalBZ,
            float distance,
            float impulse) {
        }
    }
}
