package dev.hytalemodding.impulse.core.internal.systems.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCommand;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsSnapshotPublicationSystemTest {

    @Test
    void completedWorkerStepPublishesSnapshotWithoutDrain() throws Exception {
        BackendId backendId = new BackendId("test:async-publication");
        Impulse.registerBackend(new FakePhysicsBackend(backendId));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        profiling.setEnabled(true);

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource()) {
            worker.start("async-publication-test");
            resource.attachWorkerResource(worker);
            PhysicsSpace space = resource.createLiveSpace(backendId,
                "async-publication-test",
                PhysicsSpaceSettings.defaults());
            AtomicReference<PhysicsBody> bodyRef = new AtomicReference<>();
            RigidBodyKey bodyId = PhysicsWorkerAccess.call(worker,
                "create async publication body",
                () -> {
                    PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                    body.setPosition(1.0f, 2.0f, 3.0f);
                    bodyRef.set(body);
                    return resource.addBody(space.getId(),
                        body,
                        PhysicsBodyKind.BODY,
                        PhysicsBodyPersistenceMode.RUNTIME_ONLY);
                });
            assertEquals(new Vector3f(1.0f, 2.0f, 3.0f),
                resource.getBodySnapshot(bodyId).position());

            PhysicsWorkerAccess.run(worker, "move async publication body",
                () -> bodyRef.get().setPosition(4.0f, 5.0f, 6.0f));
            assertEquals(new Vector3f(1.0f, 2.0f, 3.0f),
                resource.getBodySnapshot(bodyId).position());

            PhysicsWorkerStepCommand command = new PhysicsWorkerStepCommand(resource,
                0.05f,
                true,
                1L,
                1L);
            assertTrue(worker.submitStepIfIdle(command));
            assertFalse(worker.submitStepIfIdle(new PhysicsWorkerStepCommand(resource,
                0.05f,
                false,
                2L,
                2L)));

            publishWhenReady(worker, resource, profiling);

            PhysicsBodySnapshot snapshot = resource.getBodySnapshot(bodyId);
            assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), snapshot.position());
            assertEquals(1, profiling.getCumulativeStep().getTickSamples());
            assertEquals(1, profiling.getCumulativeStep().getBodySnapshots());
            assertTrue(profiling.getCumulativeStep().getWorkerRunNanos() > 0L);
            assertTrue(profiling.getCumulativeStep().getWorkerQueuedNanos() >= 0L);
            assertFalse(worker.hasPendingStep());
        }
    }

    @Test
    void publicationSystemDrainsCompletedAsyncMutations() throws Exception {
        AtomicInteger mutations = new AtomicInteger();
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource()) {
            worker.start("async-mutation-publication-test");
            worker.submitMutation("test mutation", () -> {
                mutations.incrementAndGet();
                return PhysicsWorkerSnapshot.empty();
            });

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            int published = 0;
            while (System.nanoTime() < deadline && published == 0) {
                published += PhysicsSnapshotPublicationSystem.publishCompletedMutations(worker);
                if (published == 0) {
                    Thread.sleep(10L);
                }
            }

            assertEquals(1, published);
            assertEquals(1, mutations.get());
            assertEquals(0, worker.pendingMutations());
        }
    }

    @Test
    void publicationSystemCapsCompletedAsyncMutationDrainPerTick() throws Exception {
        AtomicInteger mutations = new AtomicInteger();
        int mutationCount = 80;
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource()) {
            worker.start("async-mutation-publication-cap-test");
            for (int index = 0; index < mutationCount; index++) {
                worker.submitMutation("test mutation " + index, () -> {
                    mutations.incrementAndGet();
                    return PhysicsWorkerSnapshot.empty();
                });
            }

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            while (System.nanoTime() < deadline && worker.pendingCommands() > 0) {
                Thread.sleep(10L);
            }
            assertEquals(0, worker.pendingCommands());
            assertEquals(mutationCount, mutations.get());

            int firstTickPublished = PhysicsSnapshotPublicationSystem.publishCompletedMutations(worker);

            assertEquals(64, firstTickPublished);
            assertEquals(mutationCount - 64, worker.pendingMutations());
            assertEquals(mutationCount - 64,
                PhysicsSnapshotPublicationSystem.publishCompletedMutations(worker));
            assertEquals(0, worker.pendingMutations());
        }
    }

    @Test
    void completedWorkerStepDoesNotRepublishAfterWorldMutation() throws Exception {
        BackendId backendId = new BackendId("test:stale-worker-publication");
        Impulse.registerBackend(new FakePhysicsBackend(backendId));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource()) {
            worker.start("stale-worker-publication-test");
            resource.attachWorkerResource(worker);
            PhysicsSpace space = resource.createLiveSpace(backendId,
                "stale-worker-publication-test",
                PhysicsSpaceSettings.defaults());
            RigidBodyKey bodyId = PhysicsWorkerAccess.call(worker,
                "create stale publication body",
                () -> {
                    PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                    body.setPosition(1.0f, 2.0f, 3.0f);
                    return resource.addBody(space.getId(),
                        body,
                        PhysicsBodyKind.BODY,
                        PhysicsBodyPersistenceMode.RUNTIME_ONLY);
                });

            PhysicsWorkerStepCommand command = new PhysicsWorkerStepCommand(resource,
                0.05f,
                false,
                1L,
                1L);
            assertTrue(worker.submitStepIfIdle(command));
            waitForPublishedFrame(command);
            assertEquals(1, command.publishedFrame().bodyCount());

            resource.destroyBody(bodyId);
            assertEquals(0, resource.getBodySnapshotCount());
            assertNull(resource.getBodyRegistrationView(bodyId));

            PhysicsSnapshotPublicationSystem.publishCompletedStep(worker, resource, profiling);

            assertFalse(worker.hasPendingStep());
            assertEquals(0, resource.getBodySnapshotCount());
            assertNull(resource.getBodyRegistrationView(bodyId));
            resource.detachWorkerResource(worker);
        }
    }

    private static void publishWhenReady(PhysicsWorldWorkerResource worker,
        PhysicsWorldRuntimeResource resource,
        PhysicsRuntimeProfilingResource profiling) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline) {
            PhysicsSnapshotPublicationSystem.publishCompletedStep(worker, resource, profiling);
            if (!worker.hasPendingStep()) {
                return;
            }
            Thread.sleep(10L);
        }
        PhysicsSnapshotPublicationSystem.publishCompletedStep(worker, resource, profiling);
        assertFalse(worker.hasPendingStep());
    }

    private static void waitForPublishedFrame(PhysicsWorkerStepCommand command)
        throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline && command.publishedFrame() == null) {
            Thread.sleep(10L);
        }
        assertTrue(command.publishedFrame() != null);
    }
}
