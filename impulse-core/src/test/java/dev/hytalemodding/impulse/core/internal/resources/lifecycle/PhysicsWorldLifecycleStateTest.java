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
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsSnapshotPublicationEvent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
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
        assertEquals(43L, event.publicationServerTick());
        assertTrue(event.publicationNanoTime() > 0L);
        assertEquals(applied, event.appliedBodyCount());
    }

    private static Fixture createFixture(String name) {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:lifecycle-" + name + "-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults());
        return new Fixture(resource, space);
    }

    private static RigidBodyKey registerBox(Fixture fixture) {
        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        return fixture.resource.addBody(fixture.space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
    }

    private record Fixture(LegacyLiveHandleTestResource resource,
                           PhysicsSpace space) {
    }
}
