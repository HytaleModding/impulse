package dev.hytalemodding.impulse.core.internal.resources.owner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBackendEventSink;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PhysicsOwnerStepCommandTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void runsStepOnOwnerLaneAndPublishesProfiledSnapshot() throws Exception {
        CountingBackend backend = registerBackend(true);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        CountingSpace space = (CountingSpace) resource.createLiveSpace(backend.getId(),
            "owner-test",
            PhysicsSpaceSettings.defaults());
        configureWorldSettings(resource, settings -> {
            settings.setStepMode(PhysicsStepMode.FIXED);
            settings.setSimulationSteps(2);
        });

        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        second.setPosition(32.0f, 0.0f, 0.0f);
        resource.addBody(space.id(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.addBody(space.id(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(2, Duration.ofSeconds(2L))) {
            owner.start("step-command-test");
            PhysicsOwnerStepCommand command = new PhysicsOwnerStepCommand(resource,
                0.05f,
                true,
                12L,
                34L);
            PhysicsOwnerResult result = owner.submitAndDrain(command);

            assertEquals(1, result.snapshot().spaces());
            assertEquals(2, result.snapshot().substeps());
            assertEquals(2, result.snapshot().bodySnapshots());
            assertEquals(2, result.snapshot().spatialIndexCells());
            assertTrue(result.snapshot().stepNanos() > 0L);
            assertTrue(result.snapshot().snapshotNanos() > 0L);
            PublishedPhysicsSnapshotFrame frame = command.publishedFrame();
            Assertions.assertNotNull(frame);
            assertEquals(PublishedPhysicsSnapshotFrame.Status.COMPLETE, frame.status());
            assertEquals(12L, frame.stepSequence());
            assertEquals(34L, frame.serverTick());
            assertEquals(2, frame.bodyCount());
            assertEquals(2, space.stepThreadNames.size());
            assertTrue(space.stepThreadNames.stream()
                .allMatch(name -> name.startsWith("Impulse physics owner lane executor ")));
            assertEquals(List.of(0.025f, 0.025f), space.stepDts);
        }
    }

    @Test
    void progressiveRefinementUsesMaxStepDtBudget() {
        CountingBackend backend = registerBackend(false);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        CountingSpace space = (CountingSpace) resource.createLiveSpace(backend.getId(),
            "owner-test",
            PhysicsSpaceSettings.defaults());
        configureWorldSettings(resource, settings -> {
            settings.setStepMode(PhysicsStepMode.PROGRESSIVE_REFINEMENT);
            settings.setSimulationSteps(2);
            settings.setMaxStepDt(0.1f);
        });

        PhysicsOwnerSnapshot snapshot = PhysicsOwnerStepCommand.runStep(resource, 0.35f, false);

        assertEquals(1, snapshot.spaces());
        assertEquals(4, snapshot.substeps());
        assertEquals(List.of(0.0875f, 0.0875f, 0.0875f, 0.0875f), space.stepDts);
        assertEquals(0L, snapshot.stepNanos());
        assertEquals(0L, snapshot.snapshotNanos());
    }

    @Test
    void nonFiniteDtDoesNotReachBackendStep() {
        CountingBackend backend = registerBackend(false);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        CountingSpace space = (CountingSpace) resource.createLiveSpace(backend.getId(),
            "owner-test",
            PhysicsSpaceSettings.defaults());
        configureWorldSettings(resource, settings -> settings.setStepMode(PhysicsStepMode.FIXED));

        PhysicsOwnerStepCommand.runStep(resource, Float.NaN, false);
        PhysicsOwnerStepCommand.runStep(resource, Float.POSITIVE_INFINITY, false);
        PhysicsOwnerStepCommand.runStep(resource, -1.0f, false);

        assertEquals(List.of(0.0f, 0.0f, 0.0f), space.stepDts);
    }

    @Test
    void adaptiveRefinementRaisesStepsForFastBodies() {
        CountingBackend backend = registerBackend(false);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        CountingSpace space = (CountingSpace) resource.createLiveSpace(backend.getId(),
            "owner-test",
            PhysicsSpaceSettings.defaults());
        configureWorldSettings(resource, settings -> {
            settings.setStepMode(PhysicsStepMode.ADAPTIVE);
            settings.setSimulationSteps(1);
            settings.setMaxStepDt(1.0f);
        });
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setLinearVelocity(2.0f, 0.0f, 0.0f);
        resource.addBody(space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        PhysicsOwnerSnapshot snapshot = PhysicsOwnerStepCommand.runStep(resource, 0.5f, false);

        assertEquals(3, snapshot.substeps());
        assertEquals(3, space.stepDts.size());
        assertEquals(1, snapshot.bodySnapshots());
    }

    @Test
    void disabledEventCollectionStepsWithoutBackendEventSink() {
        CountingBackend backend = registerBackend(false);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        CountingSpace space = (CountingSpace) resource.createLiveSpace(backend.getId(),
            "owner-test",
            PhysicsSpaceSettings.defaults());
        configureWorldSettings(resource, settings -> {
            settings.setStepMode(PhysicsStepMode.FIXED);
            settings.setSimulationSteps(1);
        });
        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        resource.addBody(space.id(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.addBody(space.id(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        space.contactToEmit = new PhysicsContact(first,
            second,
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Vector3f(4.0f, 5.0f, 6.0f),
            new Vector3f(0.0f, 1.0f, 0.0f),
            -0.125f,
            2.5f);

        PhysicsOwnerStepCommand.runStep(resource, 0.05f, false);

        PhysicsEventFrame frame = resource.getLatestEventFrame();
        assertEquals(0, space.eventStepCount);
        assertEquals(0, frame.physicsEventCount());
    }

    @Test
    void translatesBackendContactEventsToStableBodyKeysInStepFrame() {
        CountingBackend backend = registerBackend(false);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        CountingSpace space = (CountingSpace) resource.createLiveSpace(backend.getId(),
            "owner-test",
            PhysicsSpaceSettings.defaults());
        configureWorldSettings(resource, settings -> {
            settings.setStepMode(PhysicsStepMode.FIXED);
            settings.setSimulationSteps(1);
            settings.setEventCollectionMode(PhysicsEventCollectionMode.CONTACTS);
        });
        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey firstKey = resource.addBody(space.id(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        RigidBodyKey secondKey = resource.addBody(space.id(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        space.contactToEmit = new PhysicsContact(first,
            second,
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Vector3f(4.0f, 5.0f, 6.0f),
            new Vector3f(0.0f, 1.0f, 0.0f),
            -0.125f,
            2.5f);

        PhysicsOwnerStepCommand.runStep(resource, 0.05f, false);

        PhysicsEventFrame frame = resource.getLatestEventFrame();
        assertEquals(1, frame.physicsEventCount());
        PhysicsContactEvent event =
            assertInstanceOf(PhysicsContactEvent.class, frame.physicsEvents().getFirst());
        assertEquals(space.id(), event.spaceId());
        assertEquals(PhysicsContactPhase.OBSERVED, event.phase());
        assertEquals(firstKey, event.bodyAKey());
        assertEquals(secondKey, event.bodyBKey());
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), event.pointOnA());
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), event.pointOnB());
        assertEquals(new Vector3f(0.0f, 1.0f, 0.0f), event.normalOnB());
        assertEquals(-0.125f, event.distance());
        assertEquals(2.5f, event.impulse());
    }

    @Test
    void ccdModeForcesAndRestoresOnlyOwnerAppliedOverrides() {
        CountingBackend backend = registerBackend(true);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        CountingSpace space = (CountingSpace) resource.createLiveSpace(backend.getId(),
            "owner-test",
            PhysicsSpaceSettings.defaults());
        PhysicsBody forced = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody alreadyEnabled = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody unregistered = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        space.addBody(unregistered);
        alreadyEnabled.setContinuousCollisionEnabled(true);
        resource.addBody(space.id(),
            forced,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.addBody(space.id(),
            alreadyEnabled,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        configureWorldSettings(resource, settings -> settings.setStepMode(PhysicsStepMode.CCD));
        PhysicsOwnerStepCommand.runStep(resource, 0.05f, false);

        assertTrue(forced.isContinuousCollisionEnabled());
        assertTrue(alreadyEnabled.isContinuousCollisionEnabled());
        assertFalse(unregistered.isContinuousCollisionEnabled());

        configureWorldSettings(resource, settings -> settings.setStepMode(PhysicsStepMode.FIXED));
        PhysicsOwnerStepCommand.runStep(resource, 0.05f, false);

        assertFalse(forced.isContinuousCollisionEnabled());
        assertTrue(alreadyEnabled.isContinuousCollisionEnabled());
        assertFalse(unregistered.isContinuousCollisionEnabled());
    }

    @Test
    void stepFailuresPublishSnapshotsAndRemainInspectable() throws Exception {
        CountingBackend backend = registerBackend(false);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        CountingSpace space = (CountingSpace) resource.createLiveSpace(backend.getId(),
            "owner-test",
            PhysicsSpaceSettings.defaults());
        RuntimeException failure = new RuntimeException("step failed");
        space.stepFailure = failure;

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(1,
            1,
            Duration.ofSeconds(2L))) {
            owner.start("failing-step-command-test");
            PhysicsOwnerStepCommand command = new PhysicsOwnerStepCommand(resource,
                0.05f,
                false);
            PhysicsOwnerResult result = owner.submitAndDrain(command);

            assertSame(failure, command.failure());
            Assertions.assertNotNull(command.publishedFrame());
            assertEquals(PublishedPhysicsSnapshotFrame.Status.PARTIAL,
                command.publishedFrame().status());
            assertEquals(1, result.snapshot().spaces());
            assertEquals(0, result.snapshot().substeps());
            assertEquals(0, owner.pendingCommands());
        }
    }

    @Test
    void snapshotFailureIsSuppressedWhenStepAlreadyFailed() {
        CountingBackend backend = registerBackend(false);
        FailingSnapshotWorldResource resource = new FailingSnapshotWorldResource();
        CountingSpace space = (CountingSpace) resource.createLiveSpace(backend.getId(),
            "owner-test",
            PhysicsSpaceSettings.defaults());
        RuntimeException stepFailure = new RuntimeException("step failed");
        RuntimeException snapshotFailure = new RuntimeException("snapshot failed");
        space.stepFailure = stepFailure;
        resource.snapshotFailure = snapshotFailure;

        RuntimeException thrown = assertThrows(RuntimeException.class,
            () -> PhysicsOwnerStepCommand.runStep(resource, 0.05f, false));

        assertSame(stepFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(snapshotFailure, thrown.getSuppressed()[0]);
    }

    @Nonnull
    private static CountingBackend registerBackend(boolean supportsContinuousCollision) {
        CountingBackend backend = new CountingBackend("test:owner-step-"
            + BACKEND_COUNTER.incrementAndGet(), supportsContinuousCollision);
        Impulse.registerBackend(backend);
        return backend;
    }

    private static void configureWorldSettings(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull Consumer<PhysicsWorldSettings> configurator) {
        PhysicsWorldSettings settings = resource.getWorldSettings();
        configurator.accept(settings);
        resource.setWorldSettings(settings);
    }

    private static final class CountingBackend implements PhysicsBackend {

        @Nonnull
        private final BackendId id;
        private final boolean supportsContinuousCollision;

        private CountingBackend(@Nonnull String id, boolean supportsContinuousCollision) {
            this.id = new BackendId(id);
            this.supportsContinuousCollision = supportsContinuousCollision;
        }

        @Nonnull
        @Override
        public BackendId getId() {
            return id;
        }

        @Override
        public void init() {
        }

        @Nonnull
        @Override
        public PhysicsSpace createSpace() {
            return createSpace(SpaceId.next());
        }

        @Nonnull
        @Override
        public PhysicsSpace createSpace(@Nonnull SpaceId spaceId) {
            return new CountingSpace(spaceId, id, supportsContinuousCollision);
        }
    }

    private static final class FailingSnapshotWorldResource extends PhysicsWorldRuntimeResource {

        @Nullable
        private RuntimeException snapshotFailure;

        @NonNullDecl
        @Override
        public PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrame(long stepSequence,
            long serverTick,
            @NonNullDecl PublishedPhysicsSnapshotFrame.Status status,
            long stepNanos,
            boolean profilingEnabled,
            @NonNullDecl List<PhysicsFrameEvent> physicsEvents,
            int droppedBackendEventCount) {
            if (snapshotFailure != null) {
                throw snapshotFailure;
            }
            return super.capturePublishedSnapshotFrame(stepSequence,
                serverTick,
                status,
                stepNanos,
                profilingEnabled,
                physicsEvents,
                droppedBackendEventCount);
        }
    }

    private static final class CountingSpace implements PhysicsSpace {

        @Nonnull
        private final SpaceId id;
        @Nonnull
        private final BackendId backendId;
        private final boolean supportsContinuousCollision;
        @Nonnull
        private final List<PhysicsBody> bodies = new ArrayList<>();
        @Nonnull
        private final Vector3f gravity = new Vector3f();
        @Nonnull
        private final List<Float> stepDts = new ArrayList<>();
        @Nonnull
        private final List<String> stepThreadNames = new ArrayList<>();
        @Nullable
        private RuntimeException stepFailure;
        @Nullable
        private PhysicsContact contactToEmit;
        private int eventStepCount;

        private CountingSpace(@Nonnull SpaceId id,
            @Nonnull BackendId backendId,
            boolean supportsContinuousCollision) {
            this.id = id;
            this.backendId = backendId;
            this.supportsContinuousCollision = supportsContinuousCollision;
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
            if (stepFailure != null) {
                throw stepFailure;
            }
            stepDts.add(dt);
            stepThreadNames.add(Thread.currentThread().getName());
        }

        @Override
        public void step(float dt, @Nonnull PhysicsBackendEventSink events) {
            eventStepCount++;
            step(dt);
            if (contactToEmit != null) {
                events.contact(PhysicsContactPhase.OBSERVED,
                    contactToEmit.bodyA(),
                    contactToEmit.bodyB(),
                    contactToEmit.pointOnA(),
                    contactToEmit.pointOnB(),
                    contactToEmit.normalOnB(),
                    contactToEmit.distance(),
                    contactToEmit.impulse());
            }
        }

        @Override
        public void setGravity(float x, float y, float z) {
            gravity.set(x, y, z);
        }

        @Nonnull
        @Override
        public Vector3f getGravity() {
            return new Vector3f(gravity);
        }

        @Override
        public void addBody(@Nonnull PhysicsBody body) {
            bodies.add(body);
        }

        @Override
        public void removeBody(@Nonnull PhysicsBody body) {
            bodies.remove(body);
        }

        @Nonnull
        @Override
        public List<PhysicsBody> getBodies() {
            return new ArrayList<>(bodies);
        }

        @Override
        public int bodyCount() {
            return bodies.size();
        }

        @Override
        public boolean containsBody(@Nonnull PhysicsBody body) {
            return bodies.contains(body);
        }

        @NonNullDecl
        @Override
        public <T extends PhysicsCapability> Optional<T> getCapability(@Nonnull Class<T> type) {
            Objects.requireNonNull(type, "type");
            if (supportsContinuousCollision && type == PhysicsContinuousCollisionCapability.class) {
                return Optional.of(type.cast(new PhysicsContinuousCollisionCapability() {
                }));
            }
            return Optional.empty();
        }

        @Nonnull
        @Override
        public PhysicsBody createStaticPlane(float groundY) {
            CountingBody body = new CountingBody(ShapeType.PLANE, PhysicsBodyType.STATIC);
            body.position.y = groundY;
            return body;
        }

        @Nonnull
        @Override
        public PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass) {
            CountingBody body = new CountingBody(ShapeType.BOX, PhysicsBodyType.DYNAMIC);
            body.halfExtents.set(halfX, halfY, halfZ);
            body.mass = mass;
            return body;
        }

        @Nonnull
        @Override
        public PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass) {
            return createBox(halfExtents.x, halfExtents.y, halfExtents.z, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createSphere(float radius, float mass) {
            CountingBody body = new CountingBody(ShapeType.SPHERE, PhysicsBodyType.DYNAMIC);
            body.radius = radius;
            body.mass = mass;
            return body;
        }

        @Nonnull
        @Override
        public PhysicsBody createCapsule(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            return createRoundHeightBody(ShapeType.CAPSULE, radius, halfHeight, axis, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createCylinder(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            return createRoundHeightBody(ShapeType.CYLINDER, radius, halfHeight, axis, mass);
        }

        @Nonnull
        @Override
        public PhysicsBody createCone(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            return createRoundHeightBody(ShapeType.CONE, radius, halfHeight, axis, mass);
        }

        @Nonnull
        @Override
        public Optional<PhysicsRayHit> raycastClosest(@Nonnull Vector3f from, @Nonnull Vector3f to) {
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
        }

        @Nonnull
        @Override
        public List<PhysicsJoint> getJoints() {
            return List.of();
        }

        @Nonnull
        private PhysicsBody createRoundHeightBody(@Nonnull ShapeType shapeType,
            float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            CountingBody body = new CountingBody(shapeType, PhysicsBodyType.DYNAMIC);
            body.radius = radius;
            body.halfHeight = halfHeight;
            body.axis = axis;
            body.mass = mass;
            return body;
        }
    }

    private static final class CountingBody implements PhysicsBody {

        @Nonnull
        private final ShapeType shapeType;
        @Nonnull
        private final Vector3f position = new Vector3f();
        @Nonnull
        private final Quaternionf rotation = new Quaternionf();
        @Nonnull
        private final Vector3f linearVelocity = new Vector3f();
        @Nonnull
        private final Vector3f angularVelocity = new Vector3f();
        @Nonnull
        private final Vector3f halfExtents = new Vector3f();
        @Nonnull
        private PhysicsBodyType bodyType;
        @Nonnull
        private PhysicsAxis axis = PhysicsAxis.Y;
        private float mass = 1.0f;
        private float radius;
        private float halfHeight;
        private float restitution;
        private float friction;
        private float linearDamping;
        private float angularDamping;
        private boolean sensor;
        private boolean continuousCollision;
        private int collisionGroup;
        private int collisionMask;

        private CountingBody(@Nonnull ShapeType shapeType, @Nonnull PhysicsBodyType bodyType) {
            this.shapeType = shapeType;
            this.bodyType = bodyType;
        }

        @Override
        public void setPosition(float x, float y, float z) {
            position.set(x, y, z);
        }

        @Override
        public void setPosition(@Nonnull Vector3f pos) {
            position.set(pos);
        }

        @Nonnull
        @Override
        public Vector3f getPosition() {
            return new Vector3f(position);
        }

        @Override
        public void setRotation(float x, float y, float z, float w) {
            rotation.set(x, y, z, w);
        }

        @Override
        public void setRotation(@Nonnull Quaternionf rot) {
            rotation.set(rot);
        }

        @Nonnull
        @Override
        public Quaternionf getRotation() {
            return new Quaternionf(rotation);
        }

        @Override
        public void setRestitution(float restitution) {
            this.restitution = restitution;
        }

        @Override
        public float getRestitution() {
            return restitution;
        }

        @Override
        public void setFriction(float friction) {
            this.friction = friction;
        }

        @Override
        public float getFriction() {
            return friction;
        }

        @Nonnull
        @Override
        public PhysicsBodyType getBodyType() {
            return bodyType;
        }

        @Override
        public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
            this.bodyType = bodyType;
        }

        @Override
        public boolean isStatic() {
            return bodyType == PhysicsBodyType.STATIC;
        }

        @Override
        public boolean isKinematic() {
            return bodyType == PhysicsBodyType.KINEMATIC;
        }

        @Override
        public void setKinematic(boolean kinematic) {
            bodyType = kinematic ? PhysicsBodyType.KINEMATIC : PhysicsBodyType.DYNAMIC;
        }

        @Override
        public void activate() {
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public boolean isSleeping() {
            return false;
        }

        @Override
        public void sleep() {
        }

        @Override
        public float getMass() {
            return mass;
        }

        @Override
        public void setMass(float mass) {
            this.mass = mass;
        }

        @Nonnull
        @Override
        public Vector3f getLinearVelocity() {
            return new Vector3f(linearVelocity);
        }

        @Override
        public void setLinearVelocity(@Nonnull Vector3f vel) {
            linearVelocity.set(vel);
        }

        @Override
        public void setLinearVelocity(float x, float y, float z) {
            linearVelocity.set(x, y, z);
        }

        @Nonnull
        @Override
        public Vector3f getAngularVelocity() {
            return new Vector3f(angularVelocity);
        }

        @Override
        public void setAngularVelocity(@Nonnull Vector3f vel) {
            angularVelocity.set(vel);
        }

        @Override
        public void setAngularVelocity(float x, float y, float z) {
            angularVelocity.set(x, y, z);
        }

        @Override
        public float getLinearDamping() {
            return linearDamping;
        }

        @Override
        public void setLinearDamping(float damping) {
            linearDamping = damping;
        }

        @Override
        public float getAngularDamping() {
            return angularDamping;
        }

        @Override
        public void setAngularDamping(float damping) {
            angularDamping = damping;
        }

        @Override
        public void applyCentralForce(@Nonnull Vector3f force) {
        }

        @Override
        public void applyCentralForce(float x, float y, float z) {
        }

        @Override
        public void applyForce(@Nonnull Vector3f force, @Nonnull Vector3f offset) {
        }

        @Override
        public void applyCentralImpulse(@Nonnull Vector3f impulse) {
        }

        @Override
        public void applyCentralImpulse(float x, float y, float z) {
        }

        @Override
        public void applyImpulse(@Nonnull Vector3f impulse, @Nonnull Vector3f offset) {
        }

        @Override
        public void applyTorque(@Nonnull Vector3f torque) {
        }

        @Override
        public void applyTorqueImpulse(@Nonnull Vector3f torqueImpulse) {
        }

        @Override
        public void clearForces() {
        }

        @Override
        public boolean isSensor() {
            return sensor;
        }

        @Override
        public void setSensor(boolean sensor) {
            this.sensor = sensor;
        }

        @Override
        public int getCollisionGroup() {
            return collisionGroup;
        }

        @Override
        public int getCollisionMask() {
            return collisionMask;
        }

        @Override
        public void setCollisionFilter(int group, int mask) {
            collisionGroup = group;
            collisionMask = mask;
        }

        @Override
        public boolean isContinuousCollisionEnabled() {
            return continuousCollision;
        }

        @Override
        public void setContinuousCollisionEnabled(boolean enabled) {
            continuousCollision = enabled;
        }

        @Nonnull
        @Override
        public ShapeType getShapeType() {
            return shapeType;
        }

        @Nonnull
        @Override
        public Vector3f getBoxHalfExtents() {
            return new Vector3f(halfExtents);
        }

        @Override
        public float getSphereRadius() {
            return radius;
        }

        @Override
        public float getHalfHeight() {
            return halfHeight;
        }

        @Nonnull
        @Override
        public PhysicsAxis getShapeAxis() {
            return axis;
        }

        @Override
        public float getCenterOfMassOffsetY() {
            return 0.0f;
        }
    }
}
