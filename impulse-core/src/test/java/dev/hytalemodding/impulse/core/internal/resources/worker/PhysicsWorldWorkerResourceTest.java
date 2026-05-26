package dev.hytalemodding.impulse.core.internal.resources.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerMutationCompletion;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCommand;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PhysicsWorldWorkerResourceTest {

    @Test
    void startSubmitAndDrainRunsCommandOnWorldWorker() throws Exception {
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            AtomicReference<String> threadName = new AtomicReference<>();

            resource.start("unit-world");
            var result = resource.submitAndDrain(() -> {
                threadName.set(Thread.currentThread().getName());
                return new PhysicsWorkerSnapshot(1, 2, 3, 4, 5L, 6L);
            });

            assertTrue(resource.isStarted());
            assertEquals(1L, result.sequence());
            assertEquals(2, result.snapshot().substeps());
            assertEquals("Impulse physics worker [unit-world]", threadName.get());
            assertEquals(0, resource.pendingCommands());

            resource.close();
            assertTrue(resource.isClosed());
            assertFalse(resource.isStarted());
            assertThrows(RejectedExecutionException.class,
                () -> resource.submitAndDrain(PhysicsWorkerSnapshot::empty));
        }
    }

    @Test
    void submitBeforeStartIsRejected() {
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {

            assertThrows(RejectedExecutionException.class,
                () -> resource.submitAndDrain(PhysicsWorkerSnapshot::empty));
            assertThrows(RejectedExecutionException.class,
                () -> resource.submitMutation("queued mutation", PhysicsWorkerSnapshot::empty));
        }
    }

    @Test
    void queuedMutationsCompleteWithoutCallerDrain() throws Exception {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            AtomicInteger mutations = new AtomicInteger();
            resource.start("mutation-queue");

            resource.submitMutation("blocking mutation", () -> {
                blockerStarted.countDown();
                assertTrue(releaseBlocker.await(2, TimeUnit.SECONDS));
                mutations.incrementAndGet();
                return PhysicsWorkerSnapshot.empty();
            });
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

            resource.submitMutation("queued mutation", () -> {
                mutations.incrementAndGet();
                return new PhysicsWorkerSnapshot(0, 0, 0, 0, 0L, 0L);
            });

            assertEquals(2, resource.pendingMutations());
            assertTrue(resource.pollCompletedMutations(8).isEmpty());

            releaseBlocker.countDown();
            List<?> completions = pollMutationCompletions(resource, 2);

            assertEquals(2, completions.size());
            assertEquals(2, mutations.get());
            assertEquals(0, resource.pendingMutations());
        } finally {
            releaseBlocker.countDown();
        }
    }

    @Test
    void failedMutationCompletionReportsExecutionFailure() throws Exception {
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            resource.start("mutation-failure");

            resource.submitMutation("failing mutation", () -> {
                throw new IllegalStateException("boom");
            });

            List<PhysicsWorkerMutationCompletion> completions = pollTypedMutationCompletions(resource,
                1);

            assertEquals(1, completions.size());
            assertEquals("failing mutation", completions.getFirst().operation());
            assertInstanceOf(IllegalStateException.class, completions.getFirst().executionFailure());
            assertFalse(completions.getFirst().completedSuccessfully());
            assertEquals(0, resource.pendingMutations());
        }
    }

    @Test
    void mutationHandleCompletesWhenResourceClosesAfterSubmission() throws Exception {
        CountDownLatch mutationStarted = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            resource.start("mutation-close");

            PhysicsMutationHandle<Void> handle = resource.submitMutation("slow mutation", () -> {
                mutationStarted.countDown();
                assertTrue(releaseMutation.await(2, TimeUnit.SECONDS));
                return PhysicsWorkerSnapshot.empty();
            });
            assertTrue(mutationStarted.await(2, TimeUnit.SECONDS));

            Thread closer = new Thread(resource::close, "physics-worker-close-test");
            closer.start();
            releaseMutation.countDown();
            closer.join(TimeUnit.SECONDS.toMillis(2L));

            assertFalse(closer.isAlive());
            assertTrue(resource.isClosed());
            assertTrue(handle.completedSuccessfully());
            assertThrows(RejectedExecutionException.class,
                () -> resource.submitMutation("after close", PhysicsWorkerSnapshot::empty));
        } finally {
            releaseMutation.countDown();
        }
    }

    @Test
    void pendingStepAgeTracksSubmittedButUnpublishedStep() throws Exception {
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            resource.start("pending-step-age");

            resource.submitMutation("blocking mutation", () -> {
                blockerStarted.countDown();
                assertTrue(releaseBlocker.await(2, TimeUnit.SECONDS));
                return PhysicsWorkerSnapshot.empty();
            });
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

            PhysicsWorkerStepCommand command = new PhysicsWorkerStepCommand(new PhysicsWorldRuntimeResource(),
                0.05f,
                false,
                1L,
                1L);
            assertTrue(resource.submitStepIfIdle(command));
            Thread.sleep(5L);

            assertTrue(resource.hasPendingStep());
            assertTrue(resource.pendingStepAgeNanos() > 0L);

            releaseBlocker.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            while (System.nanoTime() < deadline && resource.hasPendingStep()) {
                resource.pollCompletedStep();
                Thread.sleep(10L);
            }
            resource.pollCompletedStep();
            assertFalse(resource.hasPendingStep());
            assertEquals(0L, resource.pendingStepAgeNanos());
        } finally {
            releaseBlocker.countDown();
        }
    }

    @Test
    void cloneDoesNotShareStartedRunner() {
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            resource.start("clone-source");

            try (PhysicsWorldWorkerResource copy = resource.clone()) {

                assertTrue(resource.isStarted());
                assertFalse(copy.isStarted());
                assertFalse(copy.isClosed());
            }
        }
    }

    private static List<?> pollMutationCompletions(PhysicsWorldWorkerResource resource,
        int expected) throws InterruptedException {
        return pollTypedMutationCompletions(resource, expected);
    }

    private static List<PhysicsWorkerMutationCompletion> pollTypedMutationCompletions(
        PhysicsWorldWorkerResource resource,
        int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        List<PhysicsWorkerMutationCompletion> completions = List.of();
        while (System.nanoTime() < deadline) {
            completions = resource.pollCompletedMutations(8);
            if (completions.size() == expected) {
                return completions;
            }
            Thread.sleep(10L);
        }
        return completions;
    }
}
