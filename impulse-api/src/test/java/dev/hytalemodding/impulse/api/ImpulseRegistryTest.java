package dev.hytalemodding.impulse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ImpulseRegistryTest {

    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    private static final int CONCURRENT_SPACE_CREATIONS = 4;

    @Test
    void throwsWhenRequestingUnknownBackend() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> Impulse.getBackend(new BackendId(uniqueId())));

        assertTrue(exception.getMessage().startsWith("No backend registered with id:"));
    }

    @Test
    void registersBackendsInitializesOnceAndPreservesRequestedSpaceId() {
        CountingBackend backend = new CountingBackend(new BackendId(uniqueId()), false);
        Impulse.registerBackend(backend);

        PhysicsSpace firstSpace = Impulse.createSpace(backend.getId());
        PhysicsSpace secondSpace = Impulse.createSpace(backend.getId(), new SpaceId(12345));

        assertSame(backend, Impulse.getBackend(backend.getId()));
        assertEquals(1, backend.initCount);
        assertEquals(2, backend.createSpaceCount);
        assertEquals(new SpaceId(12345), secondSpace.id());
        assertEquals(backend.getId(), firstSpace.backendId());
        assertTrue(Impulse.getBackends().contains(backend));
    }

    @Test
    void replacingABackendResetsItsInitializationState() {
        BackendId backendId = new BackendId(uniqueId());
        CountingBackend first = new CountingBackend(backendId, false);
        CountingBackend second = new CountingBackend(backendId, false);

        Impulse.registerBackend(first);
        Impulse.createSpace(backendId);
        Impulse.registerBackend(second);
        Impulse.createSpace(backendId);

        assertEquals(1, first.initCount);
        assertEquals(1, second.initCount);
        assertEquals(1, second.createSpaceCount);
        assertSame(second, Impulse.getBackend(backendId));
    }

    @Test
    void createsSpacesConcurrentlyThroughRegistry() throws Exception {
        CountingBackend backend = new CountingBackend(new BackendId(uniqueId()), false);
        Impulse.registerBackend(backend);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_SPACE_CREATIONS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_SPACE_CREATIONS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PhysicsSpace>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENT_SPACE_CREATIONS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return Impulse.createSpace(backend.getId());
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            Set<SpaceId> returnedIds = new HashSet<>();
            for (Future<PhysicsSpace> future : futures) {
                PhysicsSpace space = future.get(5, TimeUnit.SECONDS);
                assertEquals(backend.getId(), space.backendId());
                assertTrue(returnedIds.add(space.id()));
            }

            assertEquals(CONCURRENT_SPACE_CREATIONS, returnedIds.size());
            assertEquals(1, backend.initCount);
            assertEquals(CONCURRENT_SPACE_CREATIONS, backend.createSpaceCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void throwsWhenBackendReturnsDifferentLogicalSpaceId() {
        CountingBackend backend = new CountingBackend(new BackendId(uniqueId()), true);
        Impulse.registerBackend(backend);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> Impulse.createSpace(backend.getId(), new SpaceId(77)));

        assertTrue(exception.getMessage().contains("created space id"));
        assertTrue(exception.getMessage().contains("expected"));
    }

    @Test
    void defaultCapabilityLookupReportsUnsupported() {
        PhysicsSpace space = new FakePhysicsSpace(new SpaceId(1), new BackendId(uniqueId()));

        assertTrue(space.getCapability(PhysicsContinuousCollisionCapability.class).isEmpty());
        assertEquals(List.of(), space.getCapabilityDescriptors());
    }

    private static String uniqueId() {
        return "test:backend-" + ID_COUNTER.incrementAndGet();
    }

    private static final class CountingBackend implements PhysicsBackend {

        private final BackendId id;
        private final boolean returnWrongSpaceId;
        private int initCount;
        private int createSpaceCount;

        private CountingBackend(@Nonnull BackendId id, boolean returnWrongSpaceId) {
            this.id = id;
            this.returnWrongSpaceId = returnWrongSpaceId;
        }

        @Nonnull
        @Override
        public BackendId getId() {
            return id;
        }

        @Override
        public synchronized void init() {
            initCount++;
        }

        @Nonnull
        @Override
        public PhysicsSpace createSpace() {
            return createSpace(SpaceId.next());
        }

        @Nonnull
        @Override
        public synchronized PhysicsSpace createSpace(@Nonnull SpaceId spaceId) {
            createSpaceCount++;
            return new FakePhysicsSpace(returnWrongSpaceId ? new SpaceId(spaceId.value() + 1) : spaceId,
                id);
        }
    }

    private record FakePhysicsSpace(SpaceId id, BackendId backendId) implements PhysicsSpace {

            private FakePhysicsSpace(@Nonnull SpaceId id, @Nonnull BackendId backendId) {
                this.id = id;
                this.backendId = backendId;
            }

            @Nonnull
            @Override
            public SpaceId id() {
                return id;
            }

            @Nonnull
            @Override
            public BackendId backendId() {
                return backendId;
            }

            @Override
            public void step(float dt) {
            }

            @Override
            public void setGravity(float x, float y, float z) {
            }

            @Nonnull
            @Override
            public Vector3f getGravity() {
                return new Vector3f();
            }

            @Override
            public void addBody(@Nonnull PhysicsBody body) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void removeBody(@Nonnull PhysicsBody body) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public List<PhysicsBody> getBodies() {
                return List.of();
            }

            @Nonnull
            @Override
            public PhysicsBody createStaticPlane(float groundY) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsBody createSphere(float radius, float mass) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsBody createCapsule(float radius,
                float halfHeight,
                @Nonnull PhysicsAxis axis,
                float mass) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsBody createCylinder(float radius,
                float halfHeight,
                @Nonnull PhysicsAxis axis,
                float mass) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsBody createCone(float radius,
                float halfHeight,
                @Nonnull PhysicsAxis axis,
                float mass) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public Optional<PhysicsRayHit> raycastClosest(@Nonnull Vector3f from,
                @Nonnull Vector3f to) {
                return Optional.empty();
            }

            @Nonnull
            @Override
            public List<PhysicsRayHit> raycastAll(@Nonnull Vector3f from, @Nonnull Vector3f to) {
                return List.of();
            }

            @Nonnull
            @Override
            public List<PhysicsContact> getContacts() {
                return List.of();
            }

            @Nonnull
            @Override
            public PhysicsJoint createFixedJoint(@Nonnull PhysicsBody bodyA,
                @Nonnull PhysicsBody bodyB,
                @Nonnull Vector3f anchorA,
                @Nonnull Vector3f anchorB) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsJoint createPointJoint(@Nonnull PhysicsBody bodyA,
                @Nonnull PhysicsBody bodyB,
                @Nonnull Vector3f anchorA,
                @Nonnull Vector3f anchorB) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsJoint createHingeJoint(@Nonnull PhysicsBody bodyA,
                @Nonnull PhysicsBody bodyB,
                @Nonnull Vector3f anchorA,
                @Nonnull Vector3f anchorB,
                @Nonnull Vector3f axis) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsJoint createSliderJoint(@Nonnull PhysicsBody bodyA,
                @Nonnull PhysicsBody bodyB,
                @Nonnull Vector3f anchorA,
                @Nonnull Vector3f anchorB,
                @Nonnull Vector3f axis) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public PhysicsJoint createSpringJoint(@Nonnull PhysicsBody bodyA,
                @Nonnull PhysicsBody bodyB,
                @Nonnull Vector3f anchorA,
                @Nonnull Vector3f anchorB,
                float restLength,
                float stiffness,
                float damping) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void removeJoint(@Nonnull PhysicsJoint joint) {
                throw new UnsupportedOperationException();
            }

            @Nonnull
            @Override
            public List<PhysicsJoint> getJoints() {
                return List.of();
            }

            @Override
            public void forEachBody(@Nonnull Consumer<PhysicsBody> consumer) {
            }

            @Override
            public void forEachJoint(@Nonnull Consumer<PhysicsJoint> consumer) {
            }

        }
}
