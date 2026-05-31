package dev.hytalemodding.impulse.core.internal.resources.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsSnapshotPublicationEvent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PhysicsWorldLifecycleStateTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void stalePublishedFrameIsRejectedAfterWorldEpochChanges() {
        Fixture fixture = createFixture("stale-frame");
        RigidBodyKey bodyId = registerBox(fixture);
        PublishedPhysicsSnapshotFrame staleFrame = fixture.resource.capturePublishedSnapshotFrame(10L,
            20L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);

        fixture.resource.clearBodies();

        assertEquals(0, fixture.resource.applyPublishedSnapshotFrame(staleFrame));
        assertEquals(0, fixture.resource.getBodySnapshotCount());
        assertNull(fixture.resource.getBodyRegistrationView(bodyId));
        assertEquals(0, fixture.resource.getLatestEventFrame().snapshotPublicationCount());
    }

    @Test
    void currentPublishedFrameAppliesReaderSnapshotState() {
        Fixture fixture = createFixture("current-frame");
        RigidBodyKey bodyId = registerBox(fixture);
        long appliedBefore = fixture.resource.getLatestSnapshotAppliedNanos();

        PublishedPhysicsSnapshotFrame frame = fixture.resource.capturePublishedSnapshotFrame(11L,
            21L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);

        assertEquals(1, fixture.resource.applyPublishedSnapshotFrame(frame));
        assertEquals(1, fixture.resource.getBodySnapshotCount());
        assertNotNull(fixture.resource.getBodySnapshot(bodyId));
        assertTrue(fixture.resource.getLatestSnapshotAppliedNanos() >= appliedBefore);
    }

    @Test
    void commandRegistrationViewsPublishOnlyWhenCurrentFrameApplies() throws Exception {
        Fixture fixture = createFixture("registration-view");
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            worker.start("lifecycle-registration-view");
            fixture.resource.attachWorkerResource(worker);

            RigidBodyKey bodyId = RigidBodyKey.random();
            var handle = fixture.resource.submitCommands(30L, commands -> commands
                .spawnBody(bodyId, spawn -> spawn
                    .space(fixture.space.getId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()));
            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertNull(fixture.resource.getBodyRegistrationView(bodyId));
            assertTrue(fixture.resource.isBodyCreationPending(bodyId));

            PublishedPhysicsSnapshotFrame frame = fixture.resource.capturePublishedSnapshotFrame(12L,
                31L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);

            assertNull(fixture.resource.getBodyRegistrationView(bodyId));
            assertEquals(1, fixture.resource.applyPublishedSnapshotFrame(frame));
            assertNotNull(fixture.resource.getBodyRegistrationView(bodyId));
            assertFalse(fixture.resource.isBodyCreationPending(bodyId));

            fixture.resource.detachWorkerResource(worker);
        }
    }

    @Test
    void appliedFrameUpdatesCommandWatermarkVisibility() {
        Fixture fixture = createFixture("command-watermark");
        PhysicsCommandResult result = fixture.resource.submitCommands(40L,
                commands -> commands.setSpaceGravity(fixture.space.getId(), 0.0f, -4.0f, 0.0f))
            .completion()
            .toCompletableFuture()
            .join()
            .getFirst();
        PublishedPhysicsSnapshotFrame frame = fixture.resource.capturePublishedSnapshotFrame(13L,
            41L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);

        fixture.resource.applyPublishedSnapshotFrame(frame);
        PhysicsEventFrame eventFrame = fixture.resource.getLatestEventFrame();

        assertTrue(frame.commandBatchSequenceWatermark() >= result.commandBatchSequence());
        assertTrue(eventFrame.latestSnapshotIncludesCommandBatch(result.commandBatchSequence()));
        assertEquals(frame.commandBatchSequenceWatermark(),
            eventFrame.latestSnapshotCommandBatchSequenceWatermark());
    }

    @Test
    void currentFramePublicationCreatesSnapshotPublicationEvent() {
        Fixture fixture = createFixture("publication-event");
        registerBox(fixture);
        PublishedPhysicsSnapshotFrame frame = fixture.resource.capturePublishedSnapshotFrame(14L,
            42L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);

        int applied = fixture.resource.applyPublishedSnapshotFrame(frame, 43L);
        PhysicsEventFrame eventFrame = fixture.resource.getLatestEventFrame();
        PhysicsSnapshotPublicationEvent event = eventFrame.latestSnapshotPublication();

        assertEquals(1, applied);
        assertEquals(1, eventFrame.snapshotPublicationCount());
        assertNotNull(event);
        assertEquals(frame.frameEpoch(), event.snapshotFrameEpoch());
        assertEquals(frame.worldEpoch(), event.worldEpoch());
        assertEquals(frame.stepSequence(), event.stepSequence());
        assertEquals(frame.serverTick(), event.serverTick());
        assertEquals(frame.commandBatchSequenceWatermark(), event.commandBatchSequenceWatermark());
        assertEquals(43L, event.publicationServerTick());
        assertTrue(event.publicationNanoTime() > 0L);
        assertEquals(applied, event.appliedBodyCount());
    }

    private static Fixture createFixture(String name) {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:lifecycle-" + name + "-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        return new Fixture(resource, space);
    }

    private static RigidBodyKey registerBox(Fixture fixture) {
        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        return fixture.resource.addBody(fixture.space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
    }

    private record Fixture(PhysicsWorldRuntimeResource resource,
                           PhysicsSpace space) {
    }
}
