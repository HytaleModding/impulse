package dev.hytalemodding.impulse.core.internal.resources.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldLifecycleState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsSnapshotPublicationEvent;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PhysicsWorldLifecycleStateTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void stalePublishedFrameIsRejectedAfterWorldEpochChanges() {
        Fixture fixture = createFixture("stale-frame");
        UUID bodyUuid = registerBox(fixture);
        PublishedPhysicsSnapshotFrame staleFrame = fixture.state.capturePublishedSnapshotFrame(
            List.of(fixture.binding),
            fixture.registry,
            10L,
            20L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);

        fixture.state.markWorldChanged(fixture.registry, true);

        assertEquals(0, fixture.state.applyPublishedSnapshotFrame(staleFrame, fixture.registry, 21L));
        assertEquals(0, fixture.state.bodySnapshotCount());
        assertNull(fixture.registry.getPublishedRegistrationView(bodyUuid));
        assertEquals(0, fixture.state.latestEventFrame().snapshotPublicationCount());
    }

    @Test
    void currentPublishedFrameAppliesReaderSnapshotState() {
        Fixture fixture = createFixture("current-frame");
        UUID bodyUuid = registerBox(fixture);
        long appliedBefore = fixture.state.latestSnapshotAppliedNanos();

        PublishedPhysicsSnapshotFrame frame = fixture.state.capturePublishedSnapshotFrame(
            List.of(fixture.binding),
            fixture.registry,
            11L,
            21L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);

        assertEquals(1, fixture.state.applyPublishedSnapshotFrame(frame, fixture.registry, 22L));
        assertEquals(1, fixture.state.bodySnapshotCount());
        assertNotNull(fixture.state.getBodySnapshot(bodyUuid));
        assertTrue(fixture.state.latestSnapshotAppliedNanos() >= appliedBefore);
    }

    @Test
    void currentFramePublicationCreatesSnapshotPublicationEvent() {
        Fixture fixture = createFixture("publication-event");
        registerBox(fixture);
        PublishedPhysicsSnapshotFrame frame = fixture.state.capturePublishedSnapshotFrame(
            List.of(fixture.binding),
            fixture.registry,
            14L,
            42L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);

        int applied = fixture.state.applyPublishedSnapshotFrame(frame, fixture.registry, 43L);
        PhysicsEventFrame eventFrame = fixture.state.latestEventFrame();
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
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider("test:lifecycle-" + name + "-"
                + BACKEND_COUNTER.incrementAndGet());
        PhysicsBackendRuntime runtime = provider.createRuntime();
        SpaceId spaceId = new SpaceId(1);
        int backendSpaceId = runtime.createSpace(spaceId);
        PhysicsSpaceBinding binding = new PhysicsSpaceBinding(provider.getId(),
            spaceId,
            new BackendSpaceHandle(backendSpaceId),
            runtime);
        return new Fixture(new PhysicsWorldLifecycleState(),
            new PhysicsBodyRegistry(),
            binding);
    }

    private static UUID registerBox(Fixture fixture) {
        long backendBodyId = fixture.binding.runtime().createBody(fixture.binding.backendSpaceHandle().value(),
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        UUID bodyUuid = UUID.randomUUID();
        fixture.registry.registerBody(bodyUuid,
            new BackendBodyHandle(backendBodyId),
            fixture.binding.spaceId(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        return bodyUuid;
    }

    private record Fixture(PhysicsWorldLifecycleState state,
                           PhysicsBodyRegistry registry,
                           PhysicsSpaceBinding binding) {
    }
}
