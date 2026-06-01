package dev.hytalemodding.impulse.core.internal.resources.owner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class PhysicsOwnerLaneSchedulerTest {

    private static final Duration CLOSE_TIMEOUT = Duration.ofMillis(250L);
    private static final long SHORT_TIMEOUT_MILLIS = 100L;
    private static final long TIMEOUT_MILLIS = 2_000L;

    @Test
    void defaultPoolSizeLeavesCpuHeadroomForBackendSolvers() {
        assertEquals(1, PhysicsOwnerLaneScheduler.DEFAULT_POOL_SIZE);
    }

    @Test
    void sameLaneSerializesQueuedWorkEvenWhenPoolHasMultipleThreads() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("same-lane");

            lane.submitMutation("first", () -> {
                firstStarted.countDown();
                assertTrue(releaseFirst.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(firstStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            lane.submitMutation("second", () -> {
                secondStarted.countDown();
                return PhysicsOwnerSnapshot.empty();
            });

            assertFalse(secondStarted.await(SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            assertTrue(secondStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(2, pollMutationCompletions(lane, 2).size());
        } finally {
            releaseFirst.countDown();
        }
    }

    @Test
    void differentLanesRunInParallelWhenPoolSizeAllowsIt() throws Exception {
        CountDownLatch laneOneStarted = new CountDownLatch(1);
        CountDownLatch releaseLaneOne = new CountDownLatch(1);
        CountDownLatch laneTwoStarted = new CountDownLatch(1);
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource laneOne = scheduler.createLane();
            PhysicsOwnerLaneResource laneTwo = scheduler.createLane();
            laneOne.start("lane-one");
            laneTwo.start("lane-two");

            laneOne.submitMutation("block lane one", () -> {
                laneOneStarted.countDown();
                assertTrue(releaseLaneOne.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(laneOneStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            laneTwo.submitMutation("run lane two", () -> {
                laneTwoStarted.countDown();
                return PhysicsOwnerSnapshot.empty();
            });

            assertTrue(laneTwoStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            releaseLaneOne.countDown();
            assertEquals(1, pollMutationCompletions(laneOne, 1).size());
            assertEquals(1, pollMutationCompletions(laneTwo, 1).size());
        } finally {
            releaseLaneOne.countDown();
        }
    }

    @Test
    void mutationsRunFifoWithinOneLane() throws Exception {
        CountDownLatch activeStarted = new CountDownLatch(1);
        CountDownLatch releaseActive = new CountDownLatch(1);
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            List<String> order = Collections.synchronizedList(new ArrayList<>());
            lane.start("mutation-fifo");

            lane.submitMutation("active", () -> {
                order.add("active");
                activeStarted.countDown();
                assertTrue(releaseActive.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            lane.submitMutation("queued one", () -> {
                order.add("queued-1");
                return PhysicsOwnerSnapshot.empty();
            });
            lane.submitMutation("queued two", () -> {
                order.add("queued-2");
                return PhysicsOwnerSnapshot.empty();
            });

            releaseActive.countDown();
            assertEquals(3, pollMutationCompletions(lane, 3).size());

            assertEquals(List.of("active", "queued-1", "queued-2"), order);
        } finally {
            releaseActive.countDown();
        }
    }

    @Test
    void submittedStepRunsBeforeQueuedMutationBacklogAfterActiveCommand() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch queuedMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseQueuedMutation = new CountDownLatch(1);
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("step-priority");

            lane.submitMutation("active mutation", () -> {
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            lane.submitMutation("queued mutation", () -> {
                queuedMutationStarted.countDown();
                assertTrue(releaseQueuedMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });

            PhysicsOwnerStepCommand step = new PhysicsOwnerStepCommand(
                new LegacyLiveHandleTestResource(),
                0.05f,
                false,
                1L,
                1L);
            assertTrue(lane.submitStepIfIdle(step));

            releaseActiveMutation.countDown();
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(500L));

            assertNotNull(completedStep,
                "queued mutations must not starve an already-submitted physics step");
            assertTrue(completedStep.completedSuccessfully());

            releaseQueuedMutation.countDown();
            assertTrue(queuedMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(2, pollMutationCompletions(lane, 2).size());
        } finally {
            releaseActiveMutation.countDown();
            releaseQueuedMutation.countDown();
        }
    }

    @Test
    void closedLaneRejectsNewWorkAndClosedSchedulerRejectsNewLanes() {
        PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            2,
            CLOSE_TIMEOUT);
        PhysicsOwnerLaneResource lane = scheduler.createLane();
        lane.start("close-reject");

        lane.close();

        assertTrue(lane.isClosed());
        assertFalse(lane.isStarted());
        assertThrows(RejectedExecutionException.class,
            () -> lane.submitMutation("after close", PhysicsOwnerSnapshot::empty));
        assertThrows(RejectedExecutionException.class,
            () -> lane.call("after close", () -> "rejected"));

        scheduler.close();
        assertThrows(RejectedExecutionException.class, scheduler::createLane);
    }

    @Test
    void nestedSameLaneOwnerCallsRunInlineWithoutQueuing() throws Exception {
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            List<String> order = Collections.synchronizedList(new ArrayList<>());
            lane.start("nested-inline");

            PhysicsMutationHandle<Void> outer = lane.enqueue("outer", null, () -> {
                assertTrue(lane.isOwnerContext());
                order.add("outer-before");
                lane.run("nested run", () -> order.add("nested-run"));
                String nestedValue = lane.call("nested call", () -> {
                    order.add("nested-call");
                    return "done";
                });
                order.add(nestedValue);
            });

            awaitHandle(outer);

            assertEquals(List.of("outer-before", "nested-run", "nested-call", "done"), order);
            assertEquals(1, pollMutationCompletions(lane, 1).size());
        }
    }

    @Test
    void completedMutationFutureIsNotReportedAsPendingCommand() throws Exception {
        CountDownLatch commandStarted = new CountDownLatch(1);
        CountDownLatch releaseCommand = new CountDownLatch(1);
        CountDownLatch completionObserved = new CountDownLatch(1);
        AtomicInteger pendingAtCompletion = new AtomicInteger(-1);
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("completion-pending");

            PhysicsMutationHandle<Void> handle = lane.submitMutation("blocked", () -> {
                commandStarted.countDown();
                assertTrue(releaseCommand.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(commandStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            handle.completion().whenComplete((ignored, failure) -> {
                pendingAtCompletion.set(lane.pendingCommands());
                completionObserved.countDown();
            });

            releaseCommand.countDown();
            assertTrue(completionObserved.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            assertEquals(0, pendingAtCompletion.get());
        } finally {
            releaseCommand.countDown();
        }
    }

    @Test
    void synchronousOwnerCallFromAnotherLaneContextIsRejected() throws Exception {
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource laneOne = scheduler.createLane();
            PhysicsOwnerLaneResource laneTwo = scheduler.createLane();
            AtomicBoolean crossLaneCallRan = new AtomicBoolean();
            laneOne.start("lane-one");
            laneTwo.start("lane-two");

            PhysicsMutationHandle<Void> outer = laneOne.enqueue("outer", null, () -> {
                assertTrue(laneOne.isOwnerContext());
                assertThrows(RejectedExecutionException.class,
                    () -> laneTwo.call("cross-lane call", () -> {
                        crossLaneCallRan.set(true);
                        return "not allowed";
                    }));
            });

            awaitHandle(outer);

            assertFalse(crossLaneCallRan.get());
            assertEquals(1, pollMutationCompletions(laneOne, 1).size());
            assertTrue(laneTwo.pollCompletedMutations(1).isEmpty());
        }
    }

    @Test
    void pollCompletedMutationsReturnsBoundedFifoBatches() throws Exception {
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("bounded-poll");

            PhysicsMutationHandle<Void> first = lane.submitMutation("first",
                PhysicsOwnerSnapshot::empty);
            PhysicsMutationHandle<Void> second = lane.submitMutation("second",
                PhysicsOwnerSnapshot::empty);
            PhysicsMutationHandle<Void> third = lane.submitMutation("third",
                PhysicsOwnerSnapshot::empty);
            awaitHandle(first);
            awaitHandle(second);
            awaitHandle(third);

            List<PhysicsOwnerMutationCompletion> firstBatch = lane.pollCompletedMutations(2);
            List<PhysicsOwnerMutationCompletion> secondBatch = lane.pollCompletedMutations(2);

            assertEquals(List.of("first", "second"), operations(firstBatch));
            assertEquals(List.of("third"), operations(secondBatch));
            assertTrue(lane.pollCompletedMutations(2).isEmpty());
        }
    }

    private static List<PhysicsOwnerMutationCompletion> pollMutationCompletions(
        @Nonnull PhysicsOwnerLaneResource lane,
        int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
        List<PhysicsOwnerMutationCompletion> completions = new ArrayList<>(expected);
        while (System.nanoTime() < deadline) {
            completions.addAll(lane.pollCompletedMutations(expected - completions.size()));
            if (completions.size() == expected) {
                return completions;
            }
            Thread.sleep(10L);
        }
        return completions;
    }

    private static void awaitHandle(@Nonnull PhysicsMutationHandle<?> handle)
        throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<?> future = handle.completion().toCompletableFuture();
        future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static List<String> operations(
        @Nonnull List<PhysicsOwnerMutationCompletion> completions) {
        return completions.stream()
            .map(PhysicsOwnerMutationCompletion::operation)
            .toList();
    }

    private static PhysicsOwnerStepCompletion pollStepCompletion(
        @Nonnull PhysicsOwnerLaneResource lane,
        @Nonnull Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        PhysicsOwnerStepCompletion completion = null;
        while (System.nanoTime() < deadline) {
            completion = lane.pollCompletedStep();
            if (completion != null) {
                return completion;
            }
            Thread.sleep(10L);
        }
        return lane.pollCompletedStep();
    }
}
