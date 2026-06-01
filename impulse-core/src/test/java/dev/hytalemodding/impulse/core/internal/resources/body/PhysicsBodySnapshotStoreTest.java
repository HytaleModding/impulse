package dev.hytalemodding.impulse.core.internal.resources.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSpaceFrame;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsBodySnapshotStoreTest {

    @Test
    void refreshPassesLazySelectedBodiesToBackend() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:snapshot-store-lazy-refresh");
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace delegate = resource.createLiveSpace(backend.getId(), "test-world");
        PhysicsBody body = delegate.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey bodyId = RigidBodyKey.of(0L, 1L);
        resource.addBody(bodyId,
            delegate.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsSpaceBinding binding = resource.requireSpaceBinding(delegate.id());
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();
        registry.registerBody(bodyId,
            resource.requireBodyRegistration(bodyId).backendBodyHandle(),
            delegate.id(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();

        assertEquals(1, store.refresh(List.of(binding), registry));

        assertEquals(1, store.bodyCount());
    }

    @Test
    void appliesPublishedFramesIncrementallyWithoutReinsertingUnchangedBodies() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:snapshot-store-incremental");
        var space = backend.createSpace(new SpaceId(1));
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey bodyId = RigidBodyKey.of(0L, 1L);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();
        registry.registerBody(bodyId,
            handle(1L),
            space.id(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();

        PhysicsBodySnapshotStore.ApplyStats firstApply = store.applyPublishedFrame(
            frame(space.id(), bodyId, 1L, new Vector3f(1.0f, 2.0f, 3.0f)),
            registry);
        PhysicsBodySnapshotStore.ApplyStats secondApply = store.applyPublishedFrame(
            frame(space.id(), bodyId, 2L, new Vector3f(2.0f, 2.0f, 3.0f)),
            registry);

        assertEquals(1, firstApply.applied());
        assertEquals(1, firstApply.inserted());
        assertEquals(0, firstApply.removed());
        assertEquals(1, secondApply.applied());
        assertEquals(0, secondApply.inserted());
        assertEquals(0, secondApply.removed());
        assertEquals(1, store.bodyCount());
        assertEquals(1, store.bodyCount(space.id()));
        assertEquals(1, store.cellCount());
    }

    @Test
    void applyPublishedFrameUsesFrameMetadataWithoutLiveRegistry() {
        SpaceId spaceId = new SpaceId(1);
        RigidBodyKey bodyId = RigidBodyKey.of(0L, 12L);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();

        PhysicsBodySnapshotStore.ApplyStats apply = store.applyPublishedFrame(
            frame(spaceId, bodyId, 1L, new Vector3f(1.0f, 2.0f, 3.0f)),
            new PhysicsBodyRegistry());

        assertEquals(1, apply.applied());
        assertEquals(1, apply.inserted());
        assertEquals(1, store.bodyCount());
        assertEquals(1, store.bodyCount(spaceId));
    }

    @Test
    void applyPublishedFrameReusesSnapshotWhenBodyStateIsUnchanged() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:snapshot-store-unchanged-apply");
        var space = backend.createSpace(new SpaceId(1));
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey bodyId = RigidBodyKey.of(0L, 2L);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();
        registry.registerBody(bodyId,
            handle(1L),
            space.id(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();

        store.applyPublishedFrame(frame(space.id(), bodyId, 1L, new Vector3f(1.0f, 2.0f, 3.0f)),
            registry);
        var firstSnapshot = store.get(bodyId);
        store.applyPublishedFrame(frame(space.id(), bodyId, 2L, new Vector3f(1.0f, 2.0f, 3.0f)),
            registry);

        assertSame(firstSnapshot, store.get(bodyId));
    }

    @Test
    void internalNearVisitorExposesSnapshotMetadataWithoutEntryDto() {
        SpaceId spaceId = new SpaceId(1);
        RigidBodyKey nearBodyId = RigidBodyKey.of(0L, 10L);
        RigidBodyKey farBodyId = RigidBodyKey.of(0L, 11L);
        PhysicsBodySnapshot nearSnapshot = snapshotAt(1.0f, 2.0f, 3.0f);
        PhysicsBodySnapshot farSnapshot = snapshotAt(100.0f, 2.0f, 3.0f);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();
        store.put(nearBodyId,
            nearSnapshot,
            spaceId,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        store.put(farBodyId,
            farSnapshot,
            spaceId,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        List<RigidBodyKey> visited = new java.util.ArrayList<>();
        int candidates = store.forEachIndexedNear(spaceId,
            new Vector3f(0.0f, 2.0f, 3.0f),
            4.0f,
            (bodyId, snapshot, bodySpaceId, kind, persistenceMode) -> {
                visited.add(bodyId);
                assertSame(nearSnapshot, snapshot);
                assertEquals(spaceId, bodySpaceId);
                assertEquals(PhysicsBodyKind.BODY, kind);
                assertEquals(PhysicsBodyPersistenceMode.RUNTIME_ONLY, persistenceMode);
            });

        assertEquals(1, candidates);
        assertEquals(List.of(nearBodyId), visited);
    }

    private static PublishedPhysicsSnapshotFrame frame(SpaceId spaceId,
        RigidBodyKey bodyId,
        long frameEpoch,
        Vector3f position) {
        PublishedPhysicsBodySnapshot body = new PublishedPhysicsBodySnapshot(bodyId,
            spaceId,
            frameEpoch,
            0L,
            0L,
            0L,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            position,
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.0f,
            ShapeType.BOX,
            new Vector3f(0.5f, 0.5f, 0.5f),
            0.0f,
            0.0f,
            PhysicsAxis.Y);
        return new PublishedPhysicsSnapshotFrame(frameEpoch,
            0L,
            frameEpoch,
            frameEpoch,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            1,
            0L,
            0L,
            List.of(new PublishedPhysicsSpaceFrame(spaceId, frameEpoch, 0L, 0L, List.of(body))));
    }

    private static PhysicsBodySnapshot snapshotAt(float x, float y, float z) {
        return new PhysicsBodySnapshot(new Vector3f(x, y, z),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.0f,
            ShapeType.BOX,
            new Vector3f(0.5f, 0.5f, 0.5f),
            0.0f,
            0.0f,
            PhysicsAxis.Y);
    }

    @Nonnull
    private static BackendBodyHandle handle(long value) {
        return new BackendBodyHandle(value);
    }

    private static final class RecordingSnapshotSpace implements PhysicsSpace {

        private final PhysicsSpace delegate;
        private boolean selectedBodiesWasCollection;
        private int selectedBodyCount;

        private RecordingSnapshotSpace(@Nonnull PhysicsSpace delegate) {
            this.delegate = delegate;
        }

        @Nonnull
        @Override
        public SpaceId id() {
            return delegate.id();
        }

        @Nonnull
        @Override
        public BackendId backendId() {
            return delegate.backendId();
        }

        @Override
        public void step(float dt) {
            delegate.step(dt);
        }

        @Override
        public void setGravity(float x, float y, float z) {
            delegate.setGravity(x, y, z);
        }

        @Nonnull
        @Override
        public Vector3f getGravity() {
            return delegate.getGravity();
        }

        @Override
        public void addBody(@Nonnull PhysicsBody body) {
            delegate.addBody(body);
        }

        @Override
        public void removeBody(@Nonnull PhysicsBody body) {
            delegate.removeBody(body);
        }

        @Nonnull
        @Override
        public List<PhysicsBody> getBodies() {
            return delegate.getBodies();
        }

        @Override
        public boolean containsBody(@Nonnull PhysicsBody body) {
            return delegate.containsBody(body);
        }

        @Override
        public void snapshotBodies(@Nonnull Iterable<? extends PhysicsBody> selectedBodies,
            @Nonnull Function<PhysicsBody, PhysicsBodySnapshot> previousSnapshots,
            @Nonnull BiConsumer<PhysicsBody, PhysicsBodySnapshot> consumer) {
            selectedBodiesWasCollection = selectedBodies instanceof Collection<?>;
            for (PhysicsBody body : selectedBodies) {
                selectedBodyCount++;
                consumer.accept(body, PhysicsBodySnapshot.from(body, previousSnapshots.apply(body)));
            }
        }

        @Nonnull
        @Override
        public PhysicsBody createStaticPlane(float groundY) {
            return delegate.createStaticPlane(groundY);
        }

        @Nonnull
        @Override
        public PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass) {
            return delegate.createBox(halfX, halfY, halfZ, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass) {
            return delegate.createBox(halfExtents, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createSphere(float radius, float mass) {
            return delegate.createSphere(radius, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createCapsule(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            return delegate.createCapsule(radius, halfHeight, axis, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createCylinder(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            return delegate.createCylinder(radius, halfHeight, axis, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createCone(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            return delegate.createCone(radius, halfHeight, axis, mass);
        }

        @Nonnull
        @Override
        public Optional<PhysicsRayHit> raycastClosest(@Nonnull Vector3f from, @Nonnull Vector3f to) {
            return delegate.raycastClosest(from, to);
        }

        @Nonnull
        @Override
        public List<PhysicsRayHit> raycastAll(@Nonnull Vector3f from, @Nonnull Vector3f to) {
            return delegate.raycastAll(from, to);
        }

        @Nonnull
        @Override
        public List<PhysicsContact> getContacts() {
            return delegate.getContacts();
        }

        @Nonnull
        @Override
        public PhysicsJoint createFixedJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB) {
            return delegate.createFixedJoint(bodyA, bodyB, anchorA, anchorB);
        }

        @Nonnull
        @Override
        public PhysicsJoint createPointJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB) {
            return delegate.createPointJoint(bodyA, bodyB, anchorA, anchorB);
        }

        @Nonnull
        @Override
        public PhysicsJoint createHingeJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            @Nonnull Vector3f axis) {
            return delegate.createHingeJoint(bodyA, bodyB, anchorA, anchorB, axis);
        }

        @Nonnull
        @Override
        public PhysicsJoint createSliderJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            @Nonnull Vector3f axis) {
            return delegate.createSliderJoint(bodyA, bodyB, anchorA, anchorB, axis);
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
            return delegate.createSpringJoint(bodyA, bodyB, anchorA, anchorB, restLength, stiffness, damping);
        }

        @Override
        public void removeJoint(@Nonnull PhysicsJoint joint) {
            delegate.removeJoint(joint);
        }

        @Nonnull
        @Override
        public List<PhysicsJoint> getJoints() {
            return delegate.getJoints();
        }
    }
}
