package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend.InMemoryPhysicsSpace;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState.BodySyncState;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState;
import dev.hytalemodding.impulse.core.internal.resources.owner.TestPhysicsOwnerLane;
import dev.hytalemodding.impulse.core.internal.simulation.MutablePhysicsCommandContext;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerStepCommand;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsOwnerAccess;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsCommandBatchEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsSnapshotPublicationEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsStepEvent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyStateQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyStateView;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceBodyCountQuery;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
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
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsWorldResourceStateTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void worldSettingsAreCopiedAcrossResourceBoundary() {
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();

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
    void spaceSettingsAreCopiedAcrossResourceBoundary() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:space-settings-copy-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpaceSettings initial = PhysicsSpaceSettings.streamingWorldCollision();
        initial.getSolverSettings().setSolverIterations(7);
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            initial);

        PhysicsSpaceSettings copy = resource.getSpaceSettings(space.id());
        copy.getSolverSettings().setSolverIterations(3);
        copy.getWorldCollisionSettings().setWorldCollisionMode(WorldCollisionMode.NONE);

        assertEquals(7, resource.getSpaceSettings(space.id()).getSolverSettings().getSolverIterations());
        assertEquals(WorldCollisionMode.STREAMING,
            resource.getSpaceSettings(space.id()).getWorldCollisionSettings().getWorldCollisionMode());
        assertEquals(7, ((InMemoryPhysicsSpace) space).getSolverIterations());

        resource.setSpaceSettings(space.id(), copy);
        assertEquals(3, resource.getSpaceSettings(space.id()).getSolverSettings().getSolverIterations());
        assertEquals(WorldCollisionMode.NONE,
            resource.getSpaceSettings(space.id()).getWorldCollisionSettings().getWorldCollisionMode());
        assertEquals(3, ((InMemoryPhysicsSpace) space).getSolverIterations());

        copy.getSolverSettings().setSolverIterations(5);
        assertEquals(3, resource.getSpaceSettings(space.id()).getSolverSettings().getSolverIterations());
    }

    @Test
    void copyFromCopiesWorldSettingsWithoutLiveSpaceMetadata() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:copy-topology-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource source = new LegacyLiveHandleTestResource();
        PhysicsWorldSettings sourceSettings = source.getWorldSettings();
        sourceSettings.setSimulationSteps(4);
        source.setWorldSettings(sourceSettings);
        PhysicsSpaceSettings sourceSpaceSettings = PhysicsSpaceSettings.streamingWorldCollision();
        PhysicsSpace sourceSpace = source.createLiveSpace(backend.getId(),
            "source-world",
            sourceSpaceSettings);

        LegacyLiveHandleTestResource target = new LegacyLiveHandleTestResource();
        PhysicsSpace targetSpace = target.createLiveSpace(backend.getId(),
            "target-world",
            PhysicsSpaceSettings.defaults());

        target.copyFrom(source);

        assertEquals(4, target.getWorldSettings().getSimulationSteps());
        assertEquals(0, target.getSpaceCount());
        assertTrue(target.getSpaceIds().isEmpty());
        assertFalse(target.hasSpace(sourceSpace.id()));
        assertThrows(IllegalStateException.class, () -> target.getSpaceSettings(sourceSpace.id()));
        assertTrue(((InMemoryPhysicsSpace) targetSpace).isClosed());
        assertFalse(((InMemoryPhysicsSpace) sourceSpace).isClosed());
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
    void simulationCommandBufferMutatesBodiesOnOwnerLane() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-command-buffer-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();
        var handle = resource.submitCommands(99L, 2, commands -> commands
                .spawnBody(bodyKey, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .mass(1.0f)
                    .dynamic()
                    .position(1.0f, 2.0f, 3.0f)
                    .kind(PhysicsBodyKind.BODY)
                    .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY))
                .body(bodyKey)
                .setVelocity(4.0f, 5.0f, 6.0f, 0.1f, 0.2f, 0.3f, true));
        var results = handle.completion().toCompletableFuture().join();

        assertTrue(handle.completionSummary().toCompletableFuture().join().allApplied());
        assertEquals(2, results.size());
        assertEquals(1L, results.getFirst().commandSequence());
        assertEquals(2L, results.get(1).commandSequence());
        PhysicsBody body = resource.getBody(bodyKey);
        assertNotNull(body);
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), body.getPosition());
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), body.getLinearVelocity());
        assertEquals(new Vector3f(0.1f, 0.2f, 0.3f), body.getAngularVelocity());
    }

    @Test
    void staleCommandBatchRejectsWithoutMutatingAfterWorldEpochChange() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-stale-command-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();
        MutablePhysicsCommandContext commands = resource.createMutableCommandContext(107L);
        commands.spawnBody(bodyKey, spawn -> spawn
            .space(space.id())
            .box(0.5f, 0.5f, 0.5f)
            .dynamic());

        resource.resetRuntimeStateKeepingSpaces("test-world");

        var results = resource.submitRecordedCommands(commands)
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(1, results.size());
        assertEquals(PhysicsCommandResult.Status.REJECTED, results.getFirst().status());
        assertTrue(results.getFirst().commandBatchSequence() > 0L);
        assertEquals(107L, results.getFirst().submittedServerTick());
        assertEquals(0L, results.getFirst().includedSnapshotFrameEpoch());
        assertTrue(results.getFirst().message().contains("stale"));
        assertNull(resource.getBody(bodyKey));
        assertEquals(0, resource.query(new SpaceBodyCountQuery(space.id()))
            .completion()
            .toCompletableFuture()
            .join());
    }

    @Test
    void sameEpochCommandBuffersExecuteInFifoOrderAfterTopologyMutation() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-fifo-command-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey firstKey = RigidBodyKey.random();
        RigidBodyKey secondKey = RigidBodyKey.random();
        MutablePhysicsCommandContext first = resource.createMutableCommandContext(108L);
        first.spawnBody(firstKey, spawn -> spawn
            .space(space.id())
            .box(0.5f, 0.5f, 0.5f)
            .dynamic());
        MutablePhysicsCommandContext second = resource.createMutableCommandContext(108L);
        second.spawnBody(secondKey, spawn -> spawn
            .space(space.id())
            .box(0.5f, 0.5f, 0.5f)
            .dynamic());

        var firstResults = resource.submitRecordedCommands(first)
            .completion()
            .toCompletableFuture()
            .join();
        var secondResults = resource.submitRecordedCommands(second)
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(PhysicsCommandResult.Status.APPLIED, firstResults.getFirst().status());
        assertEquals(PhysicsCommandResult.Status.APPLIED, secondResults.getFirst().status());
        assertNotNull(resource.getBody(firstKey));
        assertNotNull(resource.getBody(secondKey));
        assertEquals(2, resource.query(new SpaceBodyCountQuery(space.id()))
            .completion()
            .toCompletableFuture()
            .join());
    }

    @Test
    void simulationCommandCanSetSpaceGravity() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-space-gravity-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());

        var results = resource.submitCommands(104L,
                commands -> commands.setSpaceGravity(space.id(), 0.0f, -9.81f, 0.0f))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(1, results.size());
        assertEquals(PhysicsCommandResult.Status.APPLIED, results.getFirst().status());
        assertEquals(new Vector3f(0.0f, -9.81f, 0.0f), space.getGravity());
    }

    @Test
    void simulationDslRecipesSubmitCopiedCommands() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-dsl-submit-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();

        var results = resource.submitCommands(104L, commands -> commands
                .spawnBody(bodyKey, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .position(1.0f, 2.0f, 3.0f)
                    .dynamic())
                .body(bodyKey)
                .setVelocity(new Vector3f(2.0f, 0.0f, 0.0f), new Vector3f(), true))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(2, results.size());
        PhysicsBody body = resource.getBody(bodyKey);
        assertNotNull(body);
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), body.getPosition());
        assertEquals(new Vector3f(2.0f, 0.0f, 0.0f), body.getLinearVelocity());
    }

    @Test
    void commandCompletionExposesOwnerExecutionMetadataBeforeSnapshotPublication() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-command-latency-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();

        var results = resource.submitCommands(123L, commands -> commands
                .spawnBody(bodyKey, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()))
            .completion()
            .toCompletableFuture()
            .join();

        PhysicsCommandResult result = results.getFirst();
        assertEquals(PhysicsCommandResult.Status.APPLIED, result.status());
        assertEquals(1L, result.commandSequence());
        assertTrue(result.commandBatchSequence() > 0L);
        assertEquals(123L, result.submittedServerTick());
        assertEquals(0L, result.includedSnapshotFrameEpoch());
        PublishedPhysicsSnapshotFrame frameAtCompletion = resource.getLatestPublishedFrame();
        assertEquals(PublishedPhysicsSnapshotFrame.Status.EMPTY, frameAtCompletion.status());
        assertEquals(0, frameAtCompletion.bodyCount());

        PublishedPhysicsSnapshotFrame published = resource.capturePublishedSnapshotFrame(77L,
            124L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);

        assertTrue(published.frameEpoch() > result.includedSnapshotFrameEpoch());
        assertEquals(1, published.bodyCount());
        assertEquals(124L, published.serverTick());
    }

    @Test
    void commandCompletionPublishesValueOnlyEventFrameBeforeSnapshotVisibility() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-command-event-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();

        var handle = resource.submitCommands(127L, commands -> commands
            .spawnBody(bodyKey, spawn -> spawn
                .space(space.id())
                .box(0.5f, 0.5f, 0.5f)
                .dynamic()));
        var results = handle.completion()
            .toCompletableFuture()
            .join();

        PhysicsCommandResult result = results.getFirst();
        PhysicsEventFrame eventFrame = resource.getLatestEventFrame();
        PhysicsCommandBatchEvent event = eventFrame.commandBatches().getFirst();

        assertEquals(1, eventFrame.commandBatchCount());
        assertEquals(resource.worldEpoch(), eventFrame.worldEpoch());
        assertEquals(0L, eventFrame.latestCapturedSnapshotFrameEpoch());
        assertEquals(0L, eventFrame.latestCapturedSnapshotLastIncludedCommandBatchSequence());
        assertFalse(handle.isIncludedInLatestCapturedSnapshot(eventFrame));
        assertEquals(0L, handle.capturedSnapshotServerTickLatency(eventFrame));
        assertEquals(result.commandBatchSequence(), event.commandBatchSequence());
        assertEquals(127L, event.submittedServerTick());
        assertEquals(1, event.commandCount());
        assertTrue(event.allApplied());
        assertEquals(0L, event.firstRejectedCommandSequence());
        assertNull(event.firstRejectedMessage());

        PublishedPhysicsSnapshotFrame published = resource.capturePublishedSnapshotFrame(79L,
            128L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);

        assertTrue(published.lastIncludedCommandBatchSequence() >= event.commandBatchSequence());
        assertTrue(handle.isIncludedInSnapshotFrame(published));
        assertEquals(1L, handle.capturedSnapshotServerTickLatency(published));
        PhysicsEventFrame capturedEventFrame = resource.getLatestEventFrame();
        assertTrue(handle.isIncludedInLatestCapturedSnapshot(capturedEventFrame));
        assertEquals(1L, handle.capturedSnapshotServerTickLatency(capturedEventFrame));
        assertEquals(0L, result.includedSnapshotFrameEpoch());
    }

    @Test
    void snapshotCaptureAndPublicationPublishValueOnlyEventFrames() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-snapshot-event-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();
        resource.submitCommands(129L, commands -> commands
                .spawnBody(bodyKey, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()))
            .completion()
            .toCompletableFuture()
            .join();

        PublishedPhysicsSnapshotFrame captured = resource.capturePublishedSnapshotFrame(80L,
            130L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            12L,
            false);
        PhysicsEventFrame stepFrame = resource.getLatestEventFrame();
        PhysicsStepEvent stepEvent = stepFrame.steps().getFirst();

        assertEquals(1, stepFrame.stepCount());
        assertEquals(0, stepFrame.commandBatchCount());
        assertEquals(captured.frameEpoch(), stepFrame.latestCapturedSnapshotFrameEpoch());
        assertEquals(captured.lastIncludedCommandBatchSequence(),
            stepFrame.latestCapturedSnapshotLastIncludedCommandBatchSequence());
        assertEquals(80L, stepEvent.stepSequence());
        assertEquals(130L, stepEvent.serverTick());
        assertEquals(captured.frameEpoch(), stepEvent.snapshotFrameEpoch());
        assertEquals(captured.status(), stepEvent.snapshotStatus());
        assertEquals(captured.lastIncludedCommandBatchSequence(), stepEvent.lastIncludedCommandBatchSequence());
        assertEquals(1, stepEvent.bodyCount());

        int applied = resource.applyPublishedSnapshotFrame(captured);
        PhysicsEventFrame publicationFrame = resource.getLatestEventFrame();
        PhysicsSnapshotPublicationEvent publicationEvent = publicationFrame.snapshotPublications().getFirst();

        assertEquals(1, applied);
        assertEquals(1, publicationFrame.snapshotPublicationCount());
        assertEquals(0, publicationFrame.stepCount());
        assertEquals(captured.frameEpoch(), publicationEvent.snapshotFrameEpoch());
        assertEquals(captured.stepSequence(), publicationEvent.stepSequence());
        assertEquals(captured.serverTick(), publicationEvent.serverTick());
        assertEquals(captured.lastIncludedCommandBatchSequence(),
            publicationEvent.lastIncludedCommandBatchSequence());
        assertEquals(applied, publicationEvent.appliedBodyCount());
    }

    @Test
    void eventFrameDistinguishesOlderCapturedSnapshotFromLaterCommandCompletion() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-event-latency-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());

        PublishedPhysicsSnapshotFrame capturedBeforeCommand = resource.capturePublishedSnapshotFrame(81L,
            131L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);
        long capturedLastIncludedCommandBatchSequence = capturedBeforeCommand.lastIncludedCommandBatchSequence();

        var handle = resource.submitCommands(132L, commands -> commands
            .setSpaceGravity(space.id(), 0.0f, -4.0f, 0.0f));
        var results = handle.completion()
            .toCompletableFuture()
            .join();
        PhysicsCommandResult commandResult = results.getFirst();
        PhysicsEventFrame commandEventFrame = resource.getLatestEventFrame();
        PhysicsCommandBatchEvent commandEvent = commandEventFrame.commandBatches().getFirst();

        assertEquals(commandResult.commandBatchSequence(), commandEvent.commandBatchSequence());
        assertTrue(commandEvent.commandBatchSequence() > capturedLastIncludedCommandBatchSequence);
        assertFalse(commandEventFrame.latestCapturedSnapshotIncludesCommandBatch(
            commandEvent.commandBatchSequence()));
        assertFalse(commandEventFrame.latestCapturedSnapshotIncludes(commandEvent));
        assertFalse(handle.isIncludedInLatestCapturedSnapshot(commandEventFrame));

        int applied = resource.applyPublishedSnapshotFrame(capturedBeforeCommand);
        PhysicsEventFrame publicationEventFrame = resource.getLatestEventFrame();
        PhysicsSnapshotPublicationEvent publicationEvent =
            publicationEventFrame.snapshotPublications().getFirst();

        assertEquals(0, applied);
        assertEquals(capturedBeforeCommand.frameEpoch(), publicationEvent.snapshotFrameEpoch());
        assertEquals(capturedLastIncludedCommandBatchSequence, publicationEvent.lastIncludedCommandBatchSequence());
        assertFalse(publicationEventFrame.latestCapturedSnapshotIncludesCommandBatch(
            commandEvent.commandBatchSequence()));
        assertFalse(handle.isIncludedInLatestCapturedSnapshot(publicationEventFrame));
    }

    @Test
    void publishedSnapshotCaptureKeepsBodiesInCompactStorageUntilPublicListsAreRequested() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:compact-published-frame-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();
        resource.submitCommands(125L, commands -> commands
                .spawnBody(bodyKey, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()))
            .completion()
            .toCompletableFuture()
            .join();

        PublishedPhysicsSnapshotFrame published = resource.capturePublishedSnapshotFrame(78L,
            126L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);
        List<RigidBodyKey> visitedBodies = new ArrayList<>();

        published.forEachBodyCursor(body -> visitedBodies.add(body.bodyId()));

        assertEquals(1, published.bodyCount());
        assertEquals(List.of(bodyKey), visitedBodies);
        assertNotNull(published.spaces());
    }

    @Test
    void liveOwnerTransactionExecutesAsOpaqueAdvancedEscapeHatch() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-owner-transaction-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();

        var results = resource.submitCommands(105L, commands -> commands.liveOwnerTransaction(
                "register diagnostic body",
                access -> {
                    PhysicsBody body = access.requireSpace(space.id())
                        .createSphere(0.25f, 1.0f);
                    access.addBody(space.id(),
                        body,
                        PhysicsBodyKind.TEMPORARY,
                        PhysicsBodyPersistenceMode.RUNTIME_ONLY);
                }))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(1, results.size());
        assertEquals(PhysicsCommandResult.Status.REJECTED, results.getFirst().status());
        assertEquals(1L, results.getFirst().commandSequence());
        assertTrue(results.getFirst().message().contains("id runtime"));
        assertEquals(0, space.bodyCount());
    }

    @Test
    void liveOwnerTransactionAccessCannotBeRetainedAfterCallback() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-owner-access-scope-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        AtomicReference<PhysicsOwnerAccess> captured = new AtomicReference<>();

        var results = resource.submitCommands(106L, commands -> commands.liveOwnerTransaction(
                "capture owner access",
                access -> {
                    captured.set(access);
                    assertSame(space, access.requireSpace(space.id()));
                }))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(PhysicsCommandResult.Status.REJECTED, results.getFirst().status());
        assertNull(captured.get());
        assertTrue(results.getFirst().message().contains("id runtime"));
    }

    @Test
    void liveOwnerTransactionFailureIsReportedAsRejectedOpaqueCommand() {
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();

        var results = resource.submitCommands(107L, commands -> commands.liveOwnerTransaction(
                "failing diagnostic body mutation",
                access -> {
                    throw new IllegalStateException("diagnostic failure");
                }))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(1, results.size());
        assertEquals(PhysicsCommandResult.Status.REJECTED, results.getFirst().status());
        assertEquals(1L, results.getFirst().commandSequence());
        assertTrue(results.getFirst().commandBatchSequence() > 0L);
        assertEquals(107L, results.getFirst().submittedServerTick());
        assertEquals(0L, results.getFirst().includedSnapshotFrameEpoch());
        assertTrue(results.getFirst().message().contains("id runtime"));
    }

    @Test
    void simulationQueryReturnsCopiedOwnerDataWithoutLiveHandles() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-query-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();
        PhysicsBody body = space.createSphere(0.5f, 1.0f);
        body.setPosition(0.0f, 0.0f, 0.0f);
        resource.addBody(bodyKey,
            space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        int bodyCount = resource.query(new SpaceBodyCountQuery(space.id()))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(1, bodyCount);
    }

    @Test
    void duplicateBodyKeyDoesNotLeaveUnregisteredBackendBodyInSpace() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:duplicate-body-key-no-leak-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();
        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);

        resource.addBody(bodyKey,
            space.id(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        assertThrows(IllegalArgumentException.class, () -> resource.addBody(bodyKey,
            space.id(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY));

        assertEquals(1, space.bodyCount());
        assertFalse(space.getBodies().contains(second));
        assertSame(first, resource.getBody(bodyKey));
    }

    @Test
    void simulationSpawnSettingsAndStateQueryUseCopiedData() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-spawn-settings-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyKey = RigidBodyKey.random();
        resource.submitCommands(101L, commands -> commands
                .spawnBody(bodyKey, spawn -> spawn
                    .space(space.id())
                    .sphere(0.25f)
                    .mass(1.0f)
                    .type(PhysicsBodyType.KINEMATIC)
                    .position(7.0f, 8.0f, 9.0f)
                    .settings(RigidBodySpawnSettings.of(0.35f, 0.2f, 0.03f, 0.4f, 7, 11, true))
                    .kind(PhysicsBodyKind.TEMPORARY)
                    .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY)))
            .completion()
            .toCompletableFuture()
            .join();

        PhysicsBody body = resource.getBody(bodyKey);
        assertNotNull(body);
        assertEquals(PhysicsBodyType.KINEMATIC, body.getBodyType());
        assertEquals(new Vector3f(7.0f, 8.0f, 9.0f), body.getPosition());
        assertEquals(0.35f, body.getFriction(), 0.0001f);
        assertEquals(0.2f, body.getRestitution(), 0.0001f);
        assertEquals(0.03f, body.getLinearDamping(), 0.0001f);
        assertEquals(0.4f, body.getAngularDamping(), 0.0001f);
        assertEquals(7, body.getCollisionGroup());
        assertEquals(11, body.getCollisionMask());
        assertTrue(body.isSensor());

        RigidBodyStateView state = resource.query(new RigidBodyStateQuery(bodyKey))
            .completion()
            .toCompletableFuture()
            .join()
            .orElseThrow();
        assertEquals(bodyKey, state.bodyKey());
        assertEquals(PhysicsBodyType.KINEMATIC, state.bodyType());
        assertEquals(new Vector3f(7.0f, 8.0f, 9.0f), state.pose().position());
    }

    @Test
    void simulationTemplatedBulkSpawnAddsBodiesWithSharedCopiedSettings() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-template-spawn-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey firstKey = RigidBodyKey.random();
        RigidBodyKey secondKey = RigidBodyKey.random();
        resource.submitCommands(101L, commands -> commands.spawnBodies(2,
                space.id(),
                PhysicsShapeSpec.box(0.4f, 0.4f, 0.4f),
                1.0f,
                PhysicsBodyType.DYNAMIC,
                RigidBodySpawnSettings.material(0.25f, 0.1f),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                spawns -> spawns
                    .body(firstKey, 1.0f, 2.0f, 3.0f)
                    .body(secondKey, 4.0f, 5.0f, 6.0f)))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(2, space.bodyCount());
        PhysicsBody first = resource.getBody(firstKey);
        PhysicsBody second = resource.getBody(secondKey);
        assertNotNull(first);
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), first.getPosition());
        assertNotNull(second);
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), second.getPosition());
        assertEquals(0.25f, first.getFriction(), 0.0001f);
        assertEquals(0.1f, second.getRestitution(), 0.0001f);
    }

    @Test
    void simulationCommandCanDestroyJointBetweenBodiesWithoutStoredJointKey() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:simulation-destroy-joint-between-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey bodyAKey = RigidBodyKey.random();
        RigidBodyKey bodyBKey = RigidBodyKey.random();
        PhysicsBody bodyA = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody bodyB = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        resource.addBody(bodyAKey,
            space.id(),
            bodyA,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        resource.addBody(bodyBKey,
            space.id(),
            bodyB,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsJoint pointJoint = space.createPointJoint(bodyA, bodyB, new Vector3f(), new Vector3f());
        resource.addJoint(space.id(), pointJoint);

        resource.submitCommands(102L,
                commands -> commands.destroyJointBetween(null, space.id(), bodyAKey, bodyBKey))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(0, space.jointCount());

        JointKey jointKey = JointKey.random();
        resource.submitCommands(103L, 2, commands -> commands
                .joint(jointKey, joint -> joint
                    .space(space.id())
                    .bodies(bodyAKey, bodyBKey)
                    .point(new Vector3f(), new Vector3f()))
                .destroyJointBetween(jointKey, space.id(), bodyAKey, bodyBKey))
            .completion()
            .toCompletableFuture()
            .join();

        assertEquals(0, space.jointCount());
    }

    @Test
    void resetRuntimeStateKeepingSpacesReplacesNativeSpacesAndClearsRuntimeState() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:reset-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.streamingWorldCollision();
        settings.getSolverSettings().setSolverIterations(7);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxMaterialized(64);
        PhysicsSpace space = resource.createLiveSpace(backend.getId(), "test-world", settings);
        space.setGravity(0.0f, -3.0f, 0.0f);

        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey firstId = resource.addBody(RigidBodyKey.random(),
            space.id(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.addBody(space.id(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsJoint fixedJoint = space.createFixedJoint(first, second, new Vector3f(), new Vector3f());
        resource.addJoint(space.id(), fixedJoint);
        resource.markContinuousCollisionForced(firstId);
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
        PhysicsSpace restoredSpace = resource.callOwner("resolve reset replacement space",
            () -> resource.getLiveSpace(space.id()));
        assertSame(replacement, restoredSpace);
        assertNotSame(original, restoredSpace);
        assertEquals(new Vector3f(0.0f, -3.0f, 0.0f), replacement.getGravity());
        assertEquals(0, replacement.bodyCount());
        assertEquals(0, replacement.jointCount());
        assertEquals(7, replacement.getSolverIterations());

        PhysicsSpaceSettings preserved = resource.getSpaceSettings(space.id());
        assertEquals(WorldCollisionMode.STREAMING, preserved.getWorldCollisionSettings().getWorldCollisionMode());
        assertEquals(64, preserved.getVisualMaterializationSettings().getDetachedVisualMaxMaterialized());
        assertEquals(0, resource.getBodyRegistrationViews().size());
        assertEquals(0, resource.getBodySnapshotCount());
        assertNull(resource.getBody(firstId));
        assertFalse(resource.isBodyControlled(firstId));
        assertNull(resource.getChunkBoundarySafeState(firstId));
        assertForcedCcdRestoreDoesNotAffectReusedBodyId(resource, replacement, firstId);
    }

    @Test
    void destroyBodyClearsRuntimeStateAndSnapshotIndexes() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:destroy-cleanup-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setContinuousCollisionEnabled(true);
        RigidBodyKey bodyId = resource.addBody(space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.markContinuousCollisionForced(bodyId);
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
        assertEquals(0, resource.getBodySnapshotCount());
        assertEquals(0, resource.getBodySnapshotCount(space.id()));
        assertEquals(0, resource.getBodySnapshotCellCount());
        assertThrows(IllegalArgumentException.class, () -> resource.getBodySnapshot(bodyId));
        assertForcedCcdRestoreDoesNotAffectReusedBodyId(resource, space, bodyId);
    }

    @Test
    void clearBodiesDestroysRegisteredBackendBodiesAndJoints() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:clear-bodies-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey firstId = resource.addBody(space.id(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        RigidBodyKey secondId = resource.addBody(space.id(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PhysicsJoint fixedJoint = space.createFixedJoint(first, second, new Vector3f(), new Vector3f());
        resource.addJoint(space.id(), fixedJoint);
        resource.markContinuousCollisionForced(firstId);
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

        PhysicsSpace retainedSpace = resource.callOwner("resolve clear bodies space",
            () -> resource.getLiveSpace(space.id()));
        assertSame(space, retainedSpace);
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
        assertThrows(IllegalArgumentException.class, () -> resource.getBodySnapshot(firstId));
        assertForcedCcdRestoreDoesNotAffectReusedBodyId(resource, space, firstId);
    }

    @Test
    void bodySnapshotStoreRefreshesQueriesAndDropsStaleBodies() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:snapshot-store-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        PhysicsBody near = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        near.setPosition(0.0f, 0.0f, 0.0f);
        PhysicsBody far = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        far.setPosition(40.0f, 0.0f, 0.0f);
        RigidBodyKey nearId = resource.addBody(space.id(),
            near,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.addBody(space.id(),
            far,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        assertEquals(2, resource.refreshBodySnapshots());
        assertEquals(2, resource.getBodySnapshotCount(space.id()));
        AtomicInteger nearMatches = new AtomicInteger();
        int candidates = resource.forEachBodySnapshotNear(space.id(),
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
        assertEquals(1, resource.getBodySnapshotCount(space.id()));
        AtomicInteger remainingSnapshots = new AtomicInteger();
        resource.forEachBodySnapshot(space.id(), entry -> {
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

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        PhysicsBody registered = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        registered.setPosition(0.0f, 0.0f, 0.0f);
        PhysicsBody unregistered = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        unregistered.setPosition(2.0f, 0.0f, 0.0f);
        RigidBodyKey registeredId = resource.addBody(space.id(),
            registered,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        space.addBody(unregistered);

        assertEquals(1, resource.refreshBodySnapshots());
        assertEquals(1, resource.getBodySnapshotCount(space.id()));

        AtomicInteger snapshots = new AtomicInteger();
        resource.forEachBodySnapshot(space.id(), entry -> {
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

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("async-body-add");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults());
            AtomicReference<PhysicsBody> bodyRef = new AtomicReference<>();
            owner.submitAndDrain(() -> {
                PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                body.setPosition(1.0f, 2.0f, 3.0f);
                bodyRef.set(body);
                return PhysicsOwnerSnapshot.empty();
            });

            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            owner.submitMutation("block async topology", () -> {
                blockerStarted.countDown();
                assertTrue(releaseBlocker.await(2, TimeUnit.SECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

            RigidBodyKey bodyId = RigidBodyKey.random();
            PhysicsMutationHandle<RigidBodyKey> handle = resource.addBodyAsync(bodyId,
                space.id(),
                bodyRef.get(),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY);

            assertEquals(bodyId, handle.value());
            assertFalse(handle.isDone());
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertFalse(resource.isBodyCreationPending(bodyId));

            releaseBlocker.countDown();
            pollMutations(owner, 2);

            assertTrue(handle.completedSuccessfully());
            assertFalse(handle.failed());
            assertFalse(resource.isBodyCreationPending(bodyId));
            assertEquals(bodyId, handle.join());
            PhysicsBody registeredBody = resource.callOwner("read registered test body",
                () -> resource.getBody(bodyId));
            assertSame(bodyRef.get(), registeredBody);
            assertEquals(new Vector3f(1.0f, 2.0f, 3.0f),
                resource.getBodySnapshot(bodyId).position());
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void asyncBodyAddHandleObservesOwnerFailureWithoutPublicationDrain() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:async-body-add-failure-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("async-body-add-failure");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults());
            AtomicReference<PhysicsBody> bodyRef = new AtomicReference<>();
            owner.submitAndDrain(() -> {
                bodyRef.set(space.createBox(0.5f, 0.5f, 0.5f, 1.0f));
                return PhysicsOwnerSnapshot.empty();
            });

            RigidBodyKey bodyId = RigidBodyKey.random();
            PhysicsMutationHandle<RigidBodyKey> handle = resource.addBodyAsync(bodyId,
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
            assertEquals(1, owner.pendingMutations());

            pollMutations(owner, 1);
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void ownerCommandSpawnPublishesRegistrationViewsWithSnapshotFrame() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:owner-command-registration-view-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-view");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults());

            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            owner.submitMutation("block command-buffer spawn", () -> {
                blockerStarted.countDown();
                assertTrue(releaseBlocker.await(2, TimeUnit.SECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

            RigidBodyKey bodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(201L, commands -> commands
                .spawnBody(bodyId, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()));

            assertFalse(handle.completion().toCompletableFuture().isDone());
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertEquals(0, resource.getBodyRegistrationCount());

            releaseBlocker.countDown();
            var results = handle.completion()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);

            assertEquals(PhysicsCommandResult.Status.APPLIED, results.getFirst().status());
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertEquals(0, resource.getBodyRegistrationCount());
            assertTrue(resource.isBodyCreationPending(bodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                202L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);

            assertEquals(1, frame.bodyCount());
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertEquals(0, resource.getBodyRegistrationCount());

            resource.applyPublishedSnapshotFrame(frame);

            assertNotNull(resource.getBodyRegistrationView(bodyId));
            assertEquals(1, resource.getBodyRegistrationCount());
            assertFalse(resource.isBodyCreationPending(bodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void multiSingleSpawnCommandTracksOnlySpawnedPendingKeys() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:owner-command-registration-multi-single-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-multi-single");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults());

            RigidBodyKey firstBodyId = RigidBodyKey.random();
            RigidBodyKey secondBodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(301L, commands -> {
                commands.spawnBody(firstBodyId, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
                commands.spawnBody(secondBodyId, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
            });

            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertTrue(resource.isBodyCreationPending(firstBodyId));
            assertTrue(resource.isBodyCreationPending(secondBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(firstBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(secondBodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));
            assertFalse(resource.hasPublishedOrPendingBodyRegistration(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                302L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertFalse(resource.isBodyCreationPending(firstBodyId));
            assertFalse(resource.isBodyCreationPending(secondBodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void bulkSpawnCommandTracksOnlySpawnedPendingKeys() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:owner-command-registration-bulk-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-bulk");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults());

            RigidBodyKey firstBodyId = RigidBodyKey.random();
            RigidBodyKey secondBodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(311L, commands -> commands.spawnBodies(2, spawns -> {
                spawns.body(firstBodyId, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
                spawns.body(secondBodyId, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
            }));

            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertTrue(resource.isBodyCreationPending(firstBodyId));
            assertTrue(resource.isBodyCreationPending(secondBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(firstBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(secondBodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));
            assertFalse(resource.hasPublishedOrPendingBodyRegistration(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                312L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertFalse(resource.isBodyCreationPending(firstBodyId));
            assertFalse(resource.isBodyCreationPending(secondBodyId));
            assertNotNull(resource.getBodyRegistrationView(firstBodyId));
            assertNotNull(resource.getBodyRegistrationView(secondBodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void templateSpawnCommandTracksOnlySpawnedPendingKeys() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:owner-command-registration-template-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-template");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults());

            RigidBodyKey firstBodyId = RigidBodyKey.random();
            RigidBodyKey secondBodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(321L, commands -> commands.spawnBodies(2,
                space.id(),
                PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
                1.0f,
                PhysicsBodyType.DYNAMIC,
                RigidBodySpawnSettings.defaults(),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                spawns -> spawns
                    .body(firstBodyId, 0.0f, 0.0f, 0.0f)
                    .body(secondBodyId, 1.0f, 0.0f, 0.0f)));

            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertTrue(resource.isBodyCreationPending(firstBodyId));
            assertTrue(resource.isBodyCreationPending(secondBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(firstBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(secondBodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));
            assertFalse(resource.hasPublishedOrPendingBodyRegistration(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                322L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertFalse(resource.isBodyCreationPending(firstBodyId));
            assertFalse(resource.isBodyCreationPending(secondBodyId));
            assertNotNull(resource.getBodyRegistrationView(firstBodyId));
            assertNotNull(resource.getBodyRegistrationView(secondBodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void rejectedSpawnCommandClearsPendingKeyAfterIncludedSnapshotFrame() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:owner-command-registration-rejected-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-rejected");
            resource.attachOwnerExecutor(owner);

            RigidBodyKey bodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(331L, commands -> commands
                .spawnBody(bodyId, spawn -> spawn
                    .space(new SpaceId(Integer.MAX_VALUE))
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()));

            var results = handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(PhysicsCommandResult.Status.REJECTED, results.getFirst().status());
            assertTrue(resource.isBodyCreationPending(bodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                332L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertFalse(resource.isBodyCreationPending(bodyId));
            assertNull(resource.getBodyRegistrationView(bodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void partiallyRejectedBulkSpawnClearsAllRequestedPendingKeysAfterIncludedSnapshotFrame() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:owner-command-registration-partial-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        RigidBodyKey createdBodyId = RigidBodyKey.random();
        RigidBodyKey duplicateBodyId = RigidBodyKey.random();
        resource.addBody(duplicateBodyId,
            space.id(),
            space.createBox(0.5f, 0.5f, 0.5f, 1.0f),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-partial");
            resource.attachOwnerExecutor(owner);

            var handle = resource.submitCommands(341L, commands -> commands.spawnBodies(2, spawns -> {
                spawns.body(createdBodyId, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
                spawns.body(duplicateBodyId, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
            }));

            var results = handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(PhysicsCommandResult.Status.REJECTED, results.getFirst().status());
            assertTrue(resource.isBodyCreationPending(createdBodyId));
            assertTrue(resource.isBodyCreationPending(duplicateBodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                342L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertNotNull(resource.getBodyRegistrationView(createdBodyId));
            assertNotNull(resource.getBodyRegistrationView(duplicateBodyId));
            assertFalse(resource.isBodyCreationPending(createdBodyId));
            assertFalse(resource.isBodyCreationPending(duplicateBodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void ownerDetachPublishesCommandSpawnRegistrationViewsAndClearsPendingKeys() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:owner-command-registration-detach-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-detach");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults());

            RigidBodyKey bodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(351L, commands -> commands
                .spawnBody(bodyId, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()));

            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertNull(resource.getBodyRegistrationView(bodyId));
            assertTrue(resource.isBodyCreationPending(bodyId));

            resource.detachOwnerExecutor(owner);

            assertNotNull(resource.getBodyRegistrationView(bodyId));
            assertFalse(resource.isBodyCreationPending(bodyId));
        }
    }

    @Test
    void runtimePhysicsOwnerRoutingRunsOnAttachedOwner() throws Exception {
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        Thread testThread = Thread.currentThread();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("runtime-owner-routing");
            resource.attachOwnerExecutor(owner);

            AtomicReference<Thread> callThread = new AtomicReference<>();
            int value = resource.callOwner("runtime physics owner call", () -> {
                assertTrue(owner.isOwnerContext());
                callThread.set(Thread.currentThread());
                return 42;
            });

            assertEquals(42, value);
            assertNotSame(testThread, callThread.get());

            AtomicReference<Thread> mutationThread = new AtomicReference<>();
            PhysicsMutationHandle<String> handle = resource.enqueueOwnerMutation(
                "runtime physics owner mutation",
                "reserved-value",
                () -> {
                    assertTrue(owner.isOwnerContext());
                    mutationThread.set(Thread.currentThread());
                });

            assertEquals("reserved-value", handle.value());
            assertEquals("reserved-value", handle.completion().toCompletableFuture()
                .get(2, TimeUnit.SECONDS));
            assertTrue(handle.completedSuccessfully());
            assertNotSame(testThread, mutationThread.get());
            pollMutations(owner, 1);
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void ownerRoutingRunsInlineWithoutOwnerAndAfterDetach() throws Exception {
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        Thread testThread = Thread.currentThread();

        AtomicReference<Thread> inlineMutationThread = new AtomicReference<>();
        resource.runOwnerMutation("inline mutation", () -> inlineMutationThread.set(Thread.currentThread()));
        assertSame(testThread, inlineMutationThread.get());

        Thread inlineCallThread = resource.callOwner("inline call", Thread::currentThread);
        assertSame(testThread, inlineCallThread);

        AtomicReference<Thread> inlineAsyncThread = new AtomicReference<>();
        PhysicsMutationHandle<String> inlineHandle = resource.enqueueOwnerMutation("inline async",
            "inline",
            () -> inlineAsyncThread.set(Thread.currentThread()));
        assertSame(testThread, inlineAsyncThread.get());
        assertTrue(inlineHandle.completedSuccessfully());
        assertEquals("inline", inlineHandle.join());

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-routing-detach");
            resource.attachOwnerExecutor(owner);
            Thread ownerCallThread = resource.callOwner("owner call", () -> {
                assertTrue(owner.isOwnerContext());
                return Thread.currentThread();
            });
            assertNotSame(testThread, ownerCallThread);

            resource.detachOwnerExecutor(owner);
            Thread detachedCallThread = resource.callOwner("detached inline call",
                Thread::currentThread);
            assertSame(testThread, detachedCallThread);
        }
    }

    @Test
    void liveBackendAccessAssertionRejectsOutsidePhysicsOwner() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:owner-guard-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-guard");
            resource.attachOwnerExecutor(owner);

            PhysicsSpace space = resource.createLiveSpace(backend.getId(),
                "test-world",
                PhysicsSpaceSettings.defaults());
            PhysicsBody body = resource.callOwner("create test body", () -> {
                PhysicsBody created = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                resource.addBody(space.id(),
                    created,
                    PhysicsBodyKind.BODY,
                    PhysicsBodyPersistenceMode.RUNTIME_ONLY);
                return created;
            });

            assertThrows(IllegalStateException.class, () -> resource.assertCanAccessLiveBackendDirectly(
                "direct test access"));

            resource.runOwnerMutation("move test body", () -> {
                resource.assertCanAccessLiveBackendDirectly("owner test access");
                body.setPosition(1.0f, 2.0f, 3.0f);
            });
            assertEquals(new Vector3f(1.0f, 2.0f, 3.0f),
                resource.callOwner("read test body position", body::getPosition));

            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void resetRuntimeStatePublishesEmptyFrameAndRejectsPreResetSnapshot() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:reset-snapshot-frame-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey bodyId = resource.addBody(space.id(),
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

        LegacyLiveHandleTestResource epochResource = new LegacyLiveHandleTestResource();
        runConcurrently(threads, iterations, epochResource::clearBodies);

        assertEquals(expectedUpdates, epochResource.getLatestPublishedFrame().worldEpoch());

        LegacyLiveHandleTestResource visualInterestResource = new LegacyLiveHandleTestResource();
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

    private static void assertForcedCcdRestoreDoesNotAffectReusedBodyId(
        @Nonnull LegacyLiveHandleTestResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull RigidBodyKey bodyId) {
        PhysicsBody replacement = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        replacement.setContinuousCollisionEnabled(true);
        resource.addBody(bodyId,
            space.id(),
            replacement,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PhysicsWorldSettings settings = resource.getWorldSettings();
        settings.setStepMode(PhysicsStepMode.FIXED);
        resource.setWorldSettings(settings);

        new PhysicsOwnerStepCommand(resource, 0.05f, false).run();

        assertTrue(replacement.isContinuousCollisionEnabled());
        resource.destroyBody(bodyId);
    }

    private static void pollMutations(TestPhysicsOwnerLane owner,
        int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        int completed = 0;
        while (System.nanoTime() < deadline) {
            completed += owner.pollCompletedMutations(8).size();
            if (completed >= expected) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expected, completed);
    }

}
