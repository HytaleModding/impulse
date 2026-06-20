package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendContactSink;
import dev.hytalemodding.impulse.api.runtime.BackendRayHitSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JoltNativeQueryIntegrationTest {

    @Test
    void raycastClosestAndAllReturnBodyIdsInDistanceOrder() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend());
        int spaceId = runtime.createSpace(new SpaceId(63));
        long nearBody = createStaticBox(runtime, spaceId, 0.0f, 1.0f, -1.0f);
        long farBody = createStaticBox(runtime, spaceId, 0.0f, 1.0f, 2.0f);
        RayHitRecorder closest = new RayHitRecorder();
        RayHitRecorder all = new RayHitRecorder();

        boolean hasHit = runtime.raycastClosest(spaceId,
            0.0f,
            1.0f,
            -5.0f,
            0.0f,
            1.0f,
            5.0f,
            closest);
        int hitCount = runtime.raycastAll(spaceId,
            0.0f,
            1.0f,
            -5.0f,
            0.0f,
            1.0f,
            5.0f,
            all);

        assertTrue(hasHit);
        assertEquals(nearBody, closest.firstBodyId());
        assertTrue(closest.firstDistance() > 0.0f);
        assertEquals(2, hitCount);
        assertEquals(List.of(nearBody, farBody), all.bodyIds());
        assertTrue(all.distances().get(0) < all.distances().get(1));

        runtime.destroySpace(spaceId);
    }

    @Test
    void contactsExposeCurrentBodyPairAndRespectLimit() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend());
        int spaceId = runtime.createSpace(new SpaceId(64));
        runtime.setGravity(spaceId, 0.0f, -9.81f, 0.0f);
        long floor = createStaticBox(runtime, spaceId, 0.0f, -0.5f, 0.0f, 20.0f, 0.5f, 20.0f);
        long box = createDynamicBox(runtime, spaceId, 0.0f, 3.0f, 0.0f);

        for (int step = 0; step < 240 && runtime.contactCount(spaceId) == 0; step++) {
            runtime.step(spaceId, 1.0f / 60.0f);
        }

        ContactRecorder all = new ContactRecorder();
        int contactCount = runtime.contactCount(spaceId);
        int emitted = runtime.contacts(spaceId, all);
        ContactRecorder bounded = new ContactRecorder();
        int boundedEmitted = runtime.contacts(spaceId, 1, bounded);

        assertTrue(contactCount > 0);
        assertEquals(contactCount, emitted);
        assertTrue(all.containsPair(floor, box));
        assertEquals(1, boundedEmitted);
        assertEquals(1, bounded.count());

        runtime.destroySpace(spaceId);
    }

    private static long createStaticBox(JoltBackendRuntime runtime,
        int spaceId,
        float positionX,
        float positionY,
        float positionZ) {
        return createStaticBox(runtime,
            spaceId,
            positionX,
            positionY,
            positionZ,
            0.5f,
            0.5f,
            0.5f);
    }

    private static long createStaticBox(JoltBackendRuntime runtime,
        int spaceId,
        float positionX,
        float positionY,
        float positionZ,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ) {
        return runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_BOX,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            0.0f,
            BackendRuntimeCodes.BODY_STATIC,
            positionX,
            positionY,
            positionZ,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static long createDynamicBox(JoltBackendRuntime runtime,
        int spaceId,
        float positionX,
        float positionY,
        float positionZ) {
        return runtime.createBody(spaceId,
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
            positionX,
            positionY,
            positionZ,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static final class RayHitRecorder implements BackendRayHitSink {

        private final List<Long> bodyIds = new ArrayList<>();
        private final List<Float> distances = new ArrayList<>();

        @Override
        public void accept(long bodyId,
            float pointX,
            float pointY,
            float pointZ,
            float normalX,
            float normalY,
            float normalZ,
            float fraction,
            float distance) {
            bodyIds.add(bodyId);
            distances.add(distance);
        }

        private long firstBodyId() {
            return bodyIds.getFirst();
        }

        private float firstDistance() {
            return distances.getFirst();
        }

        private List<Long> bodyIds() {
            return bodyIds;
        }

        private List<Float> distances() {
            return distances;
        }
    }

    private static final class ContactRecorder implements BackendContactSink {

        private final List<Long> bodyAIds = new ArrayList<>();
        private final List<Long> bodyBIds = new ArrayList<>();

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
            bodyAIds.add(bodyAId);
            bodyBIds.add(bodyBId);
        }

        private boolean containsPair(long bodyAId, long bodyBId) {
            for (int index = 0; index < bodyAIds.size(); index++) {
                long actualA = bodyAIds.get(index);
                long actualB = bodyBIds.get(index);
                if ((actualA == bodyAId && actualB == bodyBId)
                    || (actualA == bodyBId && actualB == bodyAId)) {
                    return true;
                }
            }
            return false;
        }

        private int count() {
            return bodyAIds.size();
        }
    }
}
