package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCommand;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
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
        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        profiling.setEnabled(true);

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource()) {
            worker.start("async-publication-test");
            resource.attachWorkerResource(worker);
            PhysicsSpace space = resource.createSpace(backendId,
                "async-publication-test",
                PhysicsSpaceSettings.defaults(),
                true);
            AtomicReference<PhysicsBody> bodyRef = new AtomicReference<>();
            PhysicsBodyId bodyId = PhysicsWorkerAccess.call(worker,
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

    private static void publishWhenReady(PhysicsWorldWorkerResource worker,
        PhysicsWorldResource resource,
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
}
