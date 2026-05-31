package dev.hytalemodding.impulse.core.internal.resources.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerMutationCompletion;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCommand;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCompletion;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    void synchronousWorkerCallWrapsErrorsWithCauseSummary() {
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            resource.start("worker-error");

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> PhysicsWorkerAccess.call(resource, "load native backend", () -> {
                    throw new UnsatisfiedLinkError("missing native symbol");
                }));

            assertEquals("Physics worker operation load native backend failed: "
                + "UnsatisfiedLinkError: missing native symbol", thrown.getMessage());
            assertInstanceOf(UnsatisfiedLinkError.class, thrown.getCause());
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
    void submittedStepRunsBeforeQueuedMutationBacklogAfterActiveCommand() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch queuedMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseQueuedMutation = new CountDownLatch(1);
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            resource.start("step-priority");

            resource.submitMutation("active mutation", () -> {
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(2, TimeUnit.SECONDS));
                return PhysicsWorkerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(2, TimeUnit.SECONDS));

            resource.submitMutation("queued long mutation", () -> {
                queuedMutationStarted.countDown();
                assertTrue(releaseQueuedMutation.await(2, TimeUnit.SECONDS));
                return PhysicsWorkerSnapshot.empty();
            });

            PhysicsWorkerStepCommand step = new PhysicsWorkerStepCommand(
                new PhysicsWorldRuntimeResource(),
                0.05f,
                false,
                1L,
                1L);
            assertTrue(resource.submitStepIfIdle(step));

            releaseActiveMutation.countDown();
            PhysicsWorkerStepCompletion completed = pollStepCompletion(resource,
                Duration.ofMillis(500L));

            assertNotNull(completed,
                "queued mutations must not starve an already-submitted physics step");
            assertTrue(completed.completedSuccessfully());
            releaseQueuedMutation.countDown();
        } finally {
            releaseActiveMutation.countDown();
            releaseQueuedMutation.countDown();
        }
    }

    @Test
    void stepPriorityDoesNotReorderMutationsWithinMutationLane() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        try (PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(8,
            Duration.ofSeconds(2L))) {
            resource.start("mutation-fifo-with-step-priority");
            List<String> order = Collections.synchronizedList(new ArrayList<>());

            resource.submitMutation("active mutation", () -> {
                order.add("active");
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(2, TimeUnit.SECONDS));
                return PhysicsWorkerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(2, TimeUnit.SECONDS));

            resource.submitMutation("queued mutation 1", () -> {
                order.add("mutation-1");
                return PhysicsWorkerSnapshot.empty();
            });
            resource.submitMutation("queued mutation 2", () -> {
                order.add("mutation-2");
                return PhysicsWorkerSnapshot.empty();
            });
            PhysicsWorkerStepCommand step = new PhysicsWorkerStepCommand(
                new PhysicsWorldRuntimeResource(),
                0.05f,
                false,
                1L,
                1L);
            assertTrue(resource.submitStepIfIdle(step));

            releaseActiveMutation.countDown();
            assertNotNull(pollStepCompletion(resource, Duration.ofSeconds(2L)));
            pollMutationCompletions(resource, 3);

            assertEquals(List.of("active", "mutation-1", "mutation-2"), order);
        } finally {
            releaseActiveMutation.countDown();
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

    @Nullable
    private static PhysicsWorkerStepCompletion pollStepCompletion(
        PhysicsWorldWorkerResource resource,
        @Nonnull Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        PhysicsWorkerStepCompletion completion = null;
        while (System.nanoTime() < deadline) {
            completion = resource.pollCompletedStep();
            if (completion != null) {
                return completion;
            }
            Thread.sleep(10L);
        }
        return resource.pollCompletedStep();
    }
}
