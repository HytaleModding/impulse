package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend.InMemoryPhysicsSpace;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState.BodySyncState;
import dev.hytalemodding.impulse.core.internal.resources.chunk.PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState;
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResetResult;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsWorldResourceStateTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void worldSettingsAreCopiedAcrossResourceBoundary() {
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();

        PhysicsWorldSettings copy = resource.getWorldSettings();
        copy.setSimulationSteps(4);

        assertEquals(PhysicsWorldSettings.MIN_SIMULATION_STEPS,
            resource.getWorldSettings().getSimulationSteps());

        resource.setWorldSettings(copy);
        assertEquals(4, resource.getWorldSettings().getSimulationSteps());

        copy.setSimulationSteps(2);
        assertEquals(4, resource.getWorldSettings().getSimulationSteps());
    }

    @Test
    void bodySyncStateTracksLastSyncAndClampsNegativeSkipTime() {
        BodySyncState syncState = new BodySyncState();

        syncState.recordSync(new Vector3f(1.0f, 2.0f, 3.0f),
            new Quaternionf().rotateY(0.5f),
            true);
        syncState.recordSkip(-5.0f);
        syncState.recordSkip(0.75f);

        assertTrue(syncState.isInitialized());
        assertTrue(syncState.isSleeping());
        assertEquals(0.75f, syncState.getSecondsSinceSync(), 0.0001f);
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), syncState.getLastSyncedPosition());
        assertEquals(new Quaternionf().rotateY(0.5f), syncState.getLastSyncedRotation());
    }

    @Test
    void chunkBoundaryPauseStateCopiesProvidedVectors() {
        ChunkBoundaryPauseState state =
            new ChunkBoundaryPauseState();
        Vector3f linear = new Vector3f(1.0f, 2.0f, 3.0f);
        Vector3f angular = new Vector3f(4.0f, 5.0f, 6.0f);

        state.set(42L, PhysicsBodyType.KINEMATIC, linear, angular);
        linear.zero();
        angular.zero();

        assertEquals(42L, state.getTargetChunkIndex());
        assertEquals(PhysicsBodyType.KINEMATIC, state.getOriginalBodyType());
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), state.getLinearVelocity());
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), state.getAngularVelocity());
    }

    @Test
    void resetRuntimeStateKeepingSpacesReplacesNativeSpacesAndClearsRuntimeState() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:reset-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.streamingWorldCollision();
        settings.getSolverSettings().setSolverIterations(7);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxMaterialized(64);
        PhysicsSpace space = resource.createSpace(backend.getId(), "test-world", settings, true);
        space.setGravity(0.0f, -3.0f, 0.0f);

        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBodyId firstId = resource.addBody(PhysicsBodyId.random(),
            space.getId(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.addBody(space.getId(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        space.createFixedJoint(first, second, new Vector3f(), new Vector3f());
        resource.markContinuousCollisionForced(first);
        resource.markBodyControlled(firstId);
        resource.updateChunkBoundarySafeState(firstId, new Vector3f(1.0f), new Quaternionf());

        PhysicsRuntimeResetResult reset =
            resource.resetRuntimeStateKeepingSpaces("test-world");

        InMemoryPhysicsSpace original = backend.createdSpaces().get(0);
        InMemoryPhysicsSpace replacement = backend.createdSpaces().get(1);
        assertEquals(2, reset.removedBodies());
        assertEquals(1, reset.removedJoints());
        assertEquals(1, reset.keptSpaces());
        assertTrue(original.isClosed());
        assertFalse(replacement.isClosed());
        assertSame(replacement, resource.requireDefaultSpace());
        assertNotSame(original, resource.requireDefaultSpace());
        assertEquals(space.getId(), resource.requireDefaultSpaceId());
        assertEquals(new Vector3f(0.0f, -3.0f, 0.0f), replacement.getGravity());
        assertEquals(0, replacement.bodyCount());
        assertEquals(0, replacement.jointCount());
        assertEquals(7, replacement.getSolverIterations());

        PhysicsSpaceSettings preserved = resource.getSpaceSettings(space.getId());
        assertEquals(WorldCollisionMode.STREAMING, preserved.getWorldCollisionSettings().getWorldCollisionMode());
        assertEquals(64, preserved.getVisualMaterializationSettings().getDetachedVisualMaxMaterialized());
        assertEquals(0, resource.getBodyRegistrationViews().size());
        assertEquals(0, resource.getBodySnapshotCount());
        assertNull(resource.getBody(firstId));
        assertFalse(resource.isBodyControlled(firstId));
        assertNull(resource.getChunkBoundarySafeState(firstId));
        assertTrue(resource.getForcedContinuousCollisionBodies().isEmpty());
    }

    @Test
    void destroyBodyClearsRuntimeStateAndSnapshotIndexes() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:destroy-cleanup-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpace space = resource.createSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setContinuousCollisionEnabled(true);
        PhysicsBodyId bodyId = resource.addBody(space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.markContinuousCollisionForced(body);
        resource.markBodyControlled(bodyId);
        resource.updateChunkBoundarySafeState(bodyId, new Vector3f(1.0f), new Quaternionf());
        resource.pauseChunkBoundaryBody(bodyId,
            42L,
            PhysicsBodyType.DYNAMIC,
            new Vector3f(2.0f, 0.0f, 0.0f),
            new Vector3f(0.0f, 3.0f, 0.0f));
        resource.getBodySnapshot(bodyId);

        resource.destroyBody(bodyId);

        assertEquals(0, space.bodyCount());
        assertNull(resource.getBody(bodyId));
        assertFalse(resource.isBodyControlled(bodyId));
        assertNull(resource.getChunkBoundarySafeState(bodyId));
        assertNull(resource.getChunkBoundaryPauseState(bodyId));
        assertTrue(resource.getForcedContinuousCollisionBodies().isEmpty());
        assertEquals(0, resource.getBodySnapshotCount());
        assertEquals(0, resource.getBodySnapshotCount(space.getId()));
        assertEquals(0, resource.getBodySnapshotCellCount());
        assertThrows(IllegalArgumentException.class, () -> resource.getBodySnapshot(bodyId));
    }

    @Test
    void clearBodiesDestroysRegisteredBackendBodiesAndJoints() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:clear-bodies-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpace space = resource.createSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBodyId firstId = resource.addBody(space.getId(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PhysicsBodyId secondId = resource.addBody(space.getId(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        space.createFixedJoint(first, second, new Vector3f(), new Vector3f());
        resource.markContinuousCollisionForced(first);
        resource.markBodyControlled(firstId);
        resource.updateChunkBoundarySafeState(firstId, new Vector3f(1.0f), new Quaternionf());
        resource.pauseChunkBoundaryBody(firstId,
            42L,
            PhysicsBodyType.DYNAMIC,
            new Vector3f(2.0f, 0.0f, 0.0f),
            new Vector3f(0.0f, 3.0f, 0.0f));

        assertEquals(2, resource.refreshBodySnapshots());
        assertEquals(2, space.bodyCount());
        assertEquals(1, space.jointCount());
        assertEquals(2, resource.getBodyRegistrationCount());

        resource.clearBodies();

        assertSame(space, resource.requireDefaultSpace());
        assertEquals(0, space.bodyCount());
        assertEquals(0, space.jointCount());
        assertEquals(0, resource.getBodyRegistrationCount());
        assertEquals(0, resource.getBodySnapshotCount());
        assertEquals(0, resource.getBodySnapshotCellCount());
        assertNull(resource.getBody(firstId));
        assertNull(resource.getBody(secondId));
        assertFalse(resource.isBodyControlled(firstId));
        assertNull(resource.getChunkBoundarySafeState(firstId));
        assertNull(resource.getChunkBoundaryPauseState(firstId));
        assertTrue(resource.getForcedContinuousCollisionBodies().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> resource.getBodySnapshot(firstId));
    }

    @Test
    void bodySnapshotStoreRefreshesQueriesAndDropsStaleBodies() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:snapshot-store-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpace space = resource.createSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBody near = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        near.setPosition(0.0f, 0.0f, 0.0f);
        PhysicsBody far = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        far.setPosition(40.0f, 0.0f, 0.0f);
        PhysicsBodyId nearId = resource.addBody(space.getId(),
            near,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.addBody(space.getId(),
            far,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        assertEquals(2, resource.refreshBodySnapshots());
        assertEquals(2, resource.getBodySnapshotCount(space.getId()));
        AtomicInteger nearMatches = new AtomicInteger();
        int candidates = resource.forEachBodySnapshotNear(space.getId(),
            new Vector3f(),
            8.0f,
            entry -> {
                assertEquals(nearId, entry.bodyId());
                nearMatches.incrementAndGet();
            });
        assertEquals(1, candidates);
        assertEquals(1, nearMatches.get());

        space.removeBody(far);
        assertEquals(1, resource.refreshBodySnapshots());

        assertEquals(1, resource.getBodySnapshotCount());
        assertEquals(1, resource.getBodySnapshotCount(space.getId()));
        AtomicInteger remainingSnapshots = new AtomicInteger();
        resource.forEachBodySnapshot(space.getId(), entry -> {
            assertEquals(nearId, entry.bodyId());
            remainingSnapshots.incrementAndGet();
        });
        assertEquals(1, remainingSnapshots.get());
        resource.clearBodies();
        assertEquals(0, resource.getBodySnapshotCount());
        assertEquals(0, resource.getBodySnapshotCellCount());
    }

    @Test
    void bodySnapshotStoreIgnoresUnregisteredSpaceBodies() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:snapshot-store-unregistered-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpace space = resource.createSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBody registered = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        registered.setPosition(0.0f, 0.0f, 0.0f);
        PhysicsBody unregistered = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        unregistered.setPosition(2.0f, 0.0f, 0.0f);
        PhysicsBodyId registeredId = resource.addBody(space.getId(),
            registered,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        space.addBody(unregistered);

        assertEquals(1, resource.refreshBodySnapshots());
        assertEquals(1, resource.getBodySnapshotCount(space.getId()));

        AtomicInteger snapshots = new AtomicInteger();
        resource.forEachBodySnapshot(space.getId(), entry -> {
            assertEquals(registeredId, entry.bodyId());
            assertEquals(PhysicsBodyKind.BODY, entry.kind());
            assertEquals(PhysicsBodyPersistenceMode.PERSISTENT, entry.persistenceMode());
            snapshots.incrementAndGet();
        });
        assertEquals(1, snapshots.get());
    }

    @Test
    void asyncBodyAddQueuesWithoutImmediateRegistration() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:async-body-add-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            worker.start("async-body-add");
            resource.attachWorkerResource(worker);
            PhysicsSpace space = resource.createSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults(),
                true);
            AtomicReference<PhysicsBody> bodyRef = new AtomicReference<>();
            worker.submitAndDrain(() -> {
                PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                body.setPosition(1.0f, 2.0f, 3.0f);
                bodyRef.set(body);
                return PhysicsWorkerSnapshot.empty();
            });

            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            worker.submitMutation("block async topology", () -> {
                blockerStarted.countDown();
                assertTrue(releaseBlocker.await(2, TimeUnit.SECONDS));
                return PhysicsWorkerSnapshot.empty();
            });
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

            PhysicsBodyId bodyId = PhysicsBodyId.random();
            PhysicsMutationHandle<PhysicsBodyId> handle = resource.addBodyAsync(bodyId,
                space.getId(),
                bodyRef.get(),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY);

            assertEquals(bodyId, handle.value());
            assertFalse(handle.isDone());
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertTrue(resource.isBodyCreationPending(bodyId));

            releaseBlocker.countDown();
            pollMutations(worker, 2);

            assertTrue(handle.completedSuccessfully());
            assertFalse(handle.failed());
            assertFalse(resource.isBodyCreationPending(bodyId));
            assertEquals(bodyId, handle.join());
            PhysicsBody registeredBody = resource.callOnPhysicsOwner("read registered test body",
                () -> resource.requireBodyRegistration(bodyId).body());
            assertSame(bodyRef.get(), registeredBody);
            assertEquals(new Vector3f(1.0f, 2.0f, 3.0f),
                resource.getBodySnapshot(bodyId).position());
            resource.detachWorkerResource(worker);
        }
    }

    @Test
    void asyncBodyAddHandleObservesWorkerFailureWithoutPublicationDrain() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:async-body-add-failure-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            worker.start("async-body-add-failure");
            resource.attachWorkerResource(worker);
            PhysicsSpace space = resource.createSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults(),
                true);
            AtomicReference<PhysicsBody> bodyRef = new AtomicReference<>();
            worker.submitAndDrain(() -> {
                bodyRef.set(space.createBox(0.5f, 0.5f, 0.5f, 1.0f));
                return PhysicsWorkerSnapshot.empty();
            });

            PhysicsBodyId bodyId = PhysicsBodyId.random();
            PhysicsMutationHandle<PhysicsBodyId> handle = resource.addBodyAsync(bodyId,
                new SpaceId(Integer.MAX_VALUE),
                bodyRef.get(),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY);

            ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));

            assertEquals(bodyId, handle.value());
            assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
            assertInstanceOf(IllegalArgumentException.class, handle.failure());
            assertTrue(handle.failed());
            assertFalse(handle.completedSuccessfully());
            assertThrows(IllegalArgumentException.class, handle::throwIfFailed);
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertFalse(resource.isBodyCreationPending(bodyId));
            assertEquals(1, worker.pendingMutations());

            pollMutations(worker, 1);
            resource.detachWorkerResource(worker);
        }
    }

    @Test
    void publicPhysicsOwnerApiRunsOnAttachedWorker() throws Exception {
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            worker.start("public-owner-api");
            resource.attachWorkerResource(worker);

            AtomicReference<String> callThreadName = new AtomicReference<>();
            int value = resource.callOnPhysicsOwner("public physics owner call", () -> {
                assertTrue(worker.isWorkerThread());
                callThreadName.set(Thread.currentThread().getName());
                return 42;
            });

            assertEquals(42, value);
            assertTrue(callThreadName.get().contains("public-owner-api"));

            AtomicReference<String> mutationThreadName = new AtomicReference<>();
            PhysicsMutationHandle<String> handle = resource.enqueuePhysicsMutation(
                "public physics owner mutation",
                "reserved-value",
                () -> {
                    assertTrue(worker.isWorkerThread());
                    mutationThreadName.set(Thread.currentThread().getName());
                });

            assertEquals("reserved-value", handle.value());
            assertEquals("reserved-value", handle.completion().toCompletableFuture()
                .get(2, TimeUnit.SECONDS));
            assertTrue(handle.completedSuccessfully());
            assertTrue(mutationThreadName.get().contains("public-owner-api"));
            pollMutations(worker, 1);
            resource.detachWorkerResource(worker);
        }
    }

    @Test
    void ownerRoutingRunsInlineWithoutWorkerAndAfterDetach() throws Exception {
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        Thread testThread = Thread.currentThread();

        AtomicReference<Thread> inlineMutationThread = new AtomicReference<>();
        resource.runOnPhysicsOwner("inline mutation", () -> inlineMutationThread.set(Thread.currentThread()));
        assertSame(testThread, inlineMutationThread.get());

        Thread inlineCallThread = resource.callOnPhysicsOwner("inline call", Thread::currentThread);
        assertSame(testThread, inlineCallThread);

        AtomicReference<Thread> inlineAsyncThread = new AtomicReference<>();
        PhysicsMutationHandle<String> inlineHandle = resource.enqueuePhysicsMutation("inline async",
            "inline",
            () -> inlineAsyncThread.set(Thread.currentThread()));
        assertSame(testThread, inlineAsyncThread.get());
        assertTrue(inlineHandle.completedSuccessfully());
        assertEquals("inline", inlineHandle.join());

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            worker.start("owner-routing-detach");
            resource.attachWorkerResource(worker);
            Thread workerCallThread = resource.callOnPhysicsOwner("worker call", Thread::currentThread);
            assertTrue(workerCallThread.getName().contains("owner-routing-detach"));

            resource.detachWorkerResource(worker);
            Thread detachedCallThread = resource.callOnPhysicsOwner("detached inline call",
                Thread::currentThread);
            assertSame(testThread, detachedCallThread);
        }
    }

    @Test
    void liveBackendAccessAssertionRejectsOutsidePhysicsOwner() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:owner-guard-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            worker.start("owner-guard");
            resource.attachWorkerResource(worker);

            PhysicsSpace space = resource.createSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults(),
                true);
            PhysicsBody body = resource.callOnPhysicsOwner("create test body", () -> {
                PhysicsBody created = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                resource.addBody(space.getId(),
                    created,
                    PhysicsBodyKind.BODY,
                    PhysicsBodyPersistenceMode.RUNTIME_ONLY);
                return created;
            });

            assertThrows(IllegalStateException.class, () -> resource.assertCanAccessLiveBackendDirectly(
                "direct test access"));

            resource.runOnPhysicsOwner("move test body", () -> {
                resource.assertCanAccessLiveBackendDirectly("owner test access");
                body.setPosition(1.0f, 2.0f, 3.0f);
            });
            assertEquals(new Vector3f(1.0f, 2.0f, 3.0f),
                resource.callOnPhysicsOwner("read test body position", body::getPosition));

            resource.detachWorkerResource(worker);
        }
    }

    @Test
    void resetRuntimeStatePublishesEmptyFrameAndRejectsPreResetSnapshot() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:reset-snapshot-frame-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpace space = resource.createSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBodyId bodyId = resource.addBody(space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        PublishedPhysicsSnapshotFrame preResetFrame = resource.capturePublishedSnapshotFrame(10L,
            20L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            30L,
            false);
        assertEquals(1, preResetFrame.bodyCount());
        assertEquals(1, resource.getBodySnapshotCount());

        PhysicsRuntimeResetResult reset =
            resource.resetRuntimeStateKeepingSpaces("test-world");

        PublishedPhysicsSnapshotFrame resetFrame = resource.getLatestPublishedFrame();
        assertEquals(1, reset.removedBodies());
        assertEquals(0, resource.getBodySnapshotCount());
        assertEquals(PublishedPhysicsSnapshotFrame.Status.EMPTY, resetFrame.status());
        assertEquals(0, resetFrame.bodyCount());
        assertTrue(resetFrame.worldEpoch() > preResetFrame.worldEpoch());

        assertEquals(0, resource.applyPublishedSnapshotFrame(preResetFrame));
        assertEquals(0, resource.getBodySnapshotCount());
        assertNull(resource.getBodyRegistrationView(bodyId));
    }

    @Test
    void inlineWorldEpochAndVisualInterestCountersDoNotLoseConcurrentUpdates() throws Exception {
        int threads = 8;
        int iterations = 500;
        int expectedUpdates = threads * iterations;

        PhysicsWorldRuntimeResource epochResource = new PhysicsWorldRuntimeResource();
        runConcurrently(threads, iterations, epochResource::clearBodies);

        assertEquals(expectedUpdates, epochResource.getLatestPublishedFrame().worldEpoch());

        PhysicsWorldRuntimeResource visualInterestResource = new PhysicsWorldRuntimeResource();
        Set<Long> ticks = ConcurrentHashMap.newKeySet();
        runConcurrently(threads, iterations,
            () -> ticks.add(visualInterestResource.advanceVisualInterestTick()));

        assertEquals(expectedUpdates, ticks.size());
        assertEquals(expectedUpdates, ticks.stream().mapToLong(Long::longValue).max().orElseThrow());
    }

    private static void runConcurrently(int threads,
        int iterations,
        Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(threads);
        try {
            for (int thread = 0; thread < threads; thread++) {
                futures.add(executor.submit(() -> {
                    assertTrue(start.await(2, TimeUnit.SECONDS));
                    for (int iteration = 0; iteration < iterations; iteration++) {
                        action.run();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static void pollMutations(PhysicsWorldWorkerResource worker,
        int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        int completed = 0;
        while (System.nanoTime() < deadline) {
            completed += worker.pollCompletedMutations(8).size();
            if (completed >= expected) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expected, completed);
    }
}
