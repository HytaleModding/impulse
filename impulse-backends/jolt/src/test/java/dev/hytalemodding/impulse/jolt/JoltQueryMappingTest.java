package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendContactSink;
import dev.hytalemodding.impulse.api.runtime.BackendRayHitSink;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JoltQueryMappingTest {

    @Test
    void raycastsMapNativeBodyHandlesBackToRuntimeBodyIds() {
        JoltTestNativeLibrary nativeLibrary = new JoltTestNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        int spaceId = runtime.createSpace(new SpaceId(61));
        long bodyAId = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);
        long bodyBId = JoltBodyLifecycleTest.createBox(runtime, spaceId, 2.0f);
        long spaceHandle = nativeLibrary.firstSpaceHandle();
        long bodyAHandle = nativeLibrary.nativeBodyHandle(spaceHandle, 0);
        long bodyBHandle = nativeLibrary.nativeBodyHandle(spaceHandle, 1);
        nativeLibrary.addRayHit(spaceHandle, bodyAHandle, 0.0f, 1.0f, -1.0f, 0.25f, 4.0f);
        nativeLibrary.addRayHit(spaceHandle, bodyBHandle, 0.0f, 1.0f, 2.0f, 0.55f, 7.0f);
        RayHitRecorder closest = new RayHitRecorder();
        RayHitRecorder all = new RayHitRecorder();

        boolean hit = runtime.raycastClosest(spaceId,
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

        assertTrue(hit);
        assertEquals(bodyAId, closest.firstBodyId());
        assertEquals(0.25f, closest.firstFraction());
        assertEquals(2, hitCount);
        assertEquals(List.of(bodyAId, bodyBId), all.bodyIds());
    }

    @Test
    void contactsMapNativeBodyHandlesAndRespectLimit() {
        JoltTestNativeLibrary nativeLibrary = new JoltTestNativeLibrary();
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), nativeLibrary);
        int spaceId = runtime.createSpace(new SpaceId(62));
        long bodyAId = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);
        long bodyBId = JoltBodyLifecycleTest.createBox(runtime, spaceId, 2.0f);
        long spaceHandle = nativeLibrary.firstSpaceHandle();
        long bodyAHandle = nativeLibrary.nativeBodyHandle(spaceHandle, 0);
        long bodyBHandle = nativeLibrary.nativeBodyHandle(spaceHandle, 1);
        nativeLibrary.addContact(spaceHandle, bodyAHandle, bodyBHandle, 0.5f, -0.1f);
        nativeLibrary.addContact(spaceHandle, bodyBHandle, bodyAHandle, 0.6f, -0.2f);
        ContactRecorder contacts = new ContactRecorder();

        int emitted = runtime.contacts(spaceId, 1, contacts);

        assertEquals(2, runtime.contactCount(spaceId));
        assertEquals(1, emitted);
        assertEquals(1, contacts.count());
        assertEquals(bodyAId, contacts.firstBodyAId());
        assertEquals(bodyBId, contacts.firstBodyBId());
        assertEquals(-0.1f, contacts.firstDistance());
    }

    private static final class RayHitRecorder implements BackendRayHitSink {

        private final List<Long> bodyIds = new ArrayList<>();
        private final List<Float> fractions = new ArrayList<>();

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
            fractions.add(fraction);
        }

        private long firstBodyId() {
            return bodyIds.getFirst();
        }

        private float firstFraction() {
            return fractions.getFirst();
        }

        private List<Long> bodyIds() {
            return bodyIds;
        }
    }

    private static final class ContactRecorder implements BackendContactSink {

        private long firstBodyAId;
        private long firstBodyBId;
        private float firstDistance;
        private int count;

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
            if (count == 0) {
                firstBodyAId = bodyAId;
                firstBodyBId = bodyBId;
                firstDistance = distance;
            }
            count++;
        }

        private long firstBodyAId() {
            return firstBodyAId;
        }

        private long firstBodyBId() {
            return firstBodyBId;
        }

        private float firstDistance() {
            return firstDistance;
        }

        private int count() {
            return count;
        }
    }
}
