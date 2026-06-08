package dev.hytalemodding.impulse.core.internal.resources.owner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
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
import java.util.concurrent.atomic.AtomicReference;
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
    void submittedStepDrainsPreCutoffMutationBacklogAfterActiveCommand() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch preCutoffMutationStarted = new CountDownLatch(1);
        CountDownLatch releasePreCutoffMutation = new CountDownLatch(1);
        CountDownLatch postCutoffMutationStarted = new CountDownLatch(1);
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("strict-pre-step-drain");

            lane.submitMutation("active mutation", () -> {
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            lane.submitMutation("pre-cutoff mutation", () -> {
                preCutoffMutationStarted.countDown();
                assertTrue(releasePreCutoffMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });

            PhysicsOwnerStepCommand step = new PhysicsOwnerStepCommand(
                new LegacyLiveHandleTestResource(),
                0.05f,
                false,
                1L,
                1L);
            assertTrue(lane.submitStepIfIdle(step));

            lane.submitMutation("post-cutoff mutation", () -> {
                postCutoffMutationStarted.countDown();
                return PhysicsOwnerSnapshot.empty();
            });

            releaseActiveMutation.countDown();
            assertTrue(preCutoffMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertNull(pollStepCompletion(lane, Duration.ofMillis(SHORT_TIMEOUT_MILLIS)),
                "step must not complete before queued pre-cutoff mutations drain");
            assertFalse(postCutoffMutationStarted.await(SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "post-cutoff mutations must not run before the accepted step");

            releasePreCutoffMutation.countDown();
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNotNull(completedStep,
                "pre-cutoff mutations must drain before the accepted physics step");
            assertTrue(completedStep.completedSuccessfully());
            assertTrue(postCutoffMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(3, pollMutationCompletions(lane, 3).size());
        } finally {
            releaseActiveMutation.countDown();
            releasePreCutoffMutation.countDown();
        }
    }

    @Test
    void submittedStepDrainsAllPreCutoffMutationsBeforePostCutoffBacklog() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch firstPreCutoffStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstPreCutoff = new CountDownLatch(1);
        CountDownLatch secondPreCutoffStarted = new CountDownLatch(1);
        CountDownLatch releaseSecondPreCutoff = new CountDownLatch(1);
        CountDownLatch postCutoffStarted = new CountDownLatch(1);
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("strict-pre-step-drain-multiple");

            lane.submitMutation("active mutation", () -> {
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            lane.submitMutation("first pre-cutoff mutation", () -> {
                firstPreCutoffStarted.countDown();
                assertTrue(releaseFirstPreCutoff.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            lane.submitMutation("second pre-cutoff mutation", () -> {
                secondPreCutoffStarted.countDown();
                assertTrue(releaseSecondPreCutoff.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });

            assertTrue(lane.submitStepIfIdle(new PhysicsOwnerStepCommand(
                new LegacyLiveHandleTestResource(),
                0.05f,
                false,
                1L,
                1L)));

            lane.submitMutation("post-cutoff mutation", () -> {
                postCutoffStarted.countDown();
                return PhysicsOwnerSnapshot.empty();
            });

            releaseActiveMutation.countDown();
            assertTrue(firstPreCutoffStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertNull(pollStepCompletion(lane, Duration.ofMillis(SHORT_TIMEOUT_MILLIS)));
            assertFalse(postCutoffStarted.await(SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            releaseFirstPreCutoff.countDown();
            assertTrue(secondPreCutoffStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertNull(pollStepCompletion(lane, Duration.ofMillis(SHORT_TIMEOUT_MILLIS)));
            assertFalse(postCutoffStarted.await(SHORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            releaseSecondPreCutoff.countDown();
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNotNull(completedStep);
            assertTrue(completedStep.completedSuccessfully());
            assertEquals(2, completedStep.preStepDrainedMutations());
            assertEquals(1, completedStep.lateMutationBacklogAtStep());
            assertTrue(postCutoffStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(4, pollMutationCompletions(lane, 4).size());
        } finally {
            releaseActiveMutation.countDown();
            releaseFirstPreCutoff.countDown();
            releaseSecondPreCutoff.countDown();
        }
    }

    @Test
    void submittedStepDoesNotWaitForPostCutoffMutationBacklog() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch postCutoffMutationStarted = new CountDownLatch(1);
        CountDownLatch releasePostCutoffMutation = new CountDownLatch(1);
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("post-cutoff-deferred");

            lane.submitMutation("active mutation", () -> {
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            PhysicsOwnerStepCommand step = new PhysicsOwnerStepCommand(
                new LegacyLiveHandleTestResource(),
                0.05f,
                false,
                1L,
                1L);
            assertTrue(lane.submitStepIfIdle(step));

            lane.submitMutation("post-cutoff mutation", () -> {
                postCutoffMutationStarted.countDown();
                assertTrue(releasePostCutoffMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });

            releaseActiveMutation.countDown();
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNotNull(completedStep,
                "post-cutoff mutations must not delay an already-accepted physics step");
            assertTrue(completedStep.completedSuccessfully());
            assertTrue(postCutoffMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            releasePostCutoffMutation.countDown();
            assertEquals(2, pollMutationCompletions(lane, 2).size());
        } finally {
            releaseActiveMutation.countDown();
            releasePostCutoffMutation.countDown();
        }
    }

    @Test
    void failedPreCutoffMutationIsReportedAndDoesNotPreventAcceptedStep() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch failingPreCutoffStarted = new CountDownLatch(1);
        RuntimeException expectedFailure = new RuntimeException("pre-cutoff boom");
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("strict-pre-step-drain-failure");

            lane.submitMutation("active mutation", () -> {
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            lane.submitMutation("failing pre-cutoff mutation", () -> {
                failingPreCutoffStarted.countDown();
                throw expectedFailure;
            });

            assertTrue(lane.submitStepIfIdle(new PhysicsOwnerStepCommand(
                new LegacyLiveHandleTestResource(),
                0.05f,
                false,
                1L,
                1L)));

            releaseActiveMutation.countDown();
            assertTrue(failingPreCutoffStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNotNull(completedStep);
            assertTrue(completedStep.completedSuccessfully());
            assertEquals(1, completedStep.preStepDrainedMutations());
            assertTrue(completedStep.preStepDrainRunNanos() >= 0L);

            List<PhysicsOwnerMutationCompletion> completions = pollMutationCompletions(lane, 2);
            assertEquals(2, completions.size());
            assertTrue(completions.get(0).completedSuccessfully());
            assertEquals(expectedFailure, completions.get(1).executionFailure());
        } finally {
            releaseActiveMutation.countDown();
        }
    }

    @Test
    void completionCallbackCanWaitForQueuedMutationWithoutHoldingOwnerWorker() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch preCutoffMutationStarted = new CountDownLatch(1);
        CountDownLatch callbackFinished = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("completion-callback-wait");

            lane.submitMutation("active mutation", () -> {
                order.add("active");
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            PhysicsMutationHandle<Void> preCutoff = lane.submitMutation("pre-cutoff mutation",
                () -> {
                    order.add("pre");
                    preCutoffMutationStarted.countDown();
                    return PhysicsOwnerSnapshot.empty();
                });
            preCutoff.completion().whenComplete((ignored, failure) -> {
                try {
                    assertNull(failure);
                    PhysicsMutationHandle<Void> callbackMutation = lane.enqueue("callback mutation",
                        null,
                        () -> order.add("callback"));
                    awaitHandle(callbackMutation);
                } catch (Throwable throwable) {
                    callbackFailure.set(throwable);
                } finally {
                    callbackFinished.countDown();
                }
            });

            assertTrue(lane.submitStepIfIdle(new PhysicsOwnerStepCommand(
                new RecordingStepResource(order),
                0.05f,
                false,
                1L,
                1L)));

            releaseActiveMutation.countDown();
            assertTrue(preCutoffMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertTrue(callbackFinished.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNull(callbackFailure.get());
            assertNotNull(completedStep);
            assertTrue(completedStep.completedSuccessfully());
            assertEquals(List.of("active", "pre", "step", "callback"), order);
            assertEquals(3, pollMutationCompletions(lane, 3).size());
        } finally {
            releaseActiveMutation.countDown();
        }
    }

    @Test
    void ownerBridgeCallAsyncCompletionRunsOutsideOwnerContextAndQueuesAfterAcceptedStep()
        throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch bridgeCallStarted = new CountDownLatch(1);
        CountDownLatch callbackObserved = new CountDownLatch(1);
        CountDownLatch callbackMutationStarted = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        AtomicReference<String> completedValue = new AtomicReference<>();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("owner-bridge-call-async-completion");

            lane.submitMutation("active mutation", () -> {
                order.add("active");
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            CompletableFuture<String> bridgeCall = PhysicsOwnerBridge.callAsync(lane,
                "bridge async call",
                () -> {
                    order.add("bridge");
                    bridgeCallStarted.countDown();
                    return "value";
                });
            bridgeCall.whenComplete((value, failure) -> {
                try {
                    assertNull(failure);
                    assertFalse(lane.isOwnerContext());
                    completedValue.set(value);
                    lane.enqueue("callback mutation", null, () -> {
                        order.add("callback");
                        callbackMutationStarted.countDown();
                    });
                } catch (Throwable throwable) {
                    callbackFailure.set(throwable);
                } finally {
                    callbackObserved.countDown();
                }
            });

            assertTrue(lane.submitStepIfIdle(new PhysicsOwnerStepCommand(
                new RecordingStepResource(order),
                0.05f,
                false,
                1L,
                1L)));

            releaseActiveMutation.countDown();
            assertTrue(bridgeCallStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertTrue(callbackObserved.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNull(callbackFailure.get());
            assertEquals("value", completedValue.get());
            assertNotNull(completedStep);
            assertTrue(completedStep.completedSuccessfully());
            assertTrue(callbackMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(List.of("active", "bridge", "step", "callback"), order);
            assertEquals(2, pollMutationCompletions(lane, 2).size());
        } finally {
            releaseActiveMutation.countDown();
        }
    }

    @Test
    void enqueueCallCompletionRunsOutsideOwnerContextAndQueuesAfterAcceptedStep()
        throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch callStarted = new CountDownLatch(1);
        CountDownLatch callbackObserved = new CountDownLatch(1);
        CountDownLatch callbackMutationStarted = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        AtomicReference<String> completedValue = new AtomicReference<>();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("enqueue-call-completion");

            lane.submitMutation("active mutation", () -> {
                order.add("active");
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            CompletableFuture<String> call = lane.enqueueCall("queued call", () -> {
                order.add("call");
                callStarted.countDown();
                return "value";
            });
            call.whenComplete((value, failure) -> {
                try {
                    assertNull(failure);
                    assertFalse(lane.isOwnerContext());
                    completedValue.set(value);
                    lane.enqueue("callback mutation", null, () -> {
                        order.add("callback");
                        callbackMutationStarted.countDown();
                    });
                } catch (Throwable throwable) {
                    callbackFailure.set(throwable);
                } finally {
                    callbackObserved.countDown();
                }
            });

            assertTrue(lane.submitStepIfIdle(new PhysicsOwnerStepCommand(
                new RecordingStepResource(order),
                0.05f,
                false,
                1L,
                1L)));

            releaseActiveMutation.countDown();
            assertTrue(callStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertTrue(callbackObserved.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNull(callbackFailure.get());
            assertEquals("value", completedValue.get());
            assertNotNull(completedStep);
            assertTrue(completedStep.completedSuccessfully());
            assertTrue(callbackMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(List.of("active", "call", "step", "callback"), order);
            assertEquals(2, pollMutationCompletions(lane, 2).size());
        } finally {
            releaseActiveMutation.countDown();
        }
    }

    @Test
    void completionCallbackSubmittedMutationRunsAfterAcceptedStep() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch preCutoffMutationStarted = new CountDownLatch(1);
        CountDownLatch callbackObserved = new CountDownLatch(1);
        CountDownLatch callbackMutationStarted = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("completion-callback-reentrant-async");

            lane.submitMutation("active mutation", () -> {
                order.add("active");
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            PhysicsMutationHandle<Void> preCutoff = lane.submitMutation("pre-cutoff mutation",
                () -> {
                    order.add("pre");
                    preCutoffMutationStarted.countDown();
                    return PhysicsOwnerSnapshot.empty();
                });
            preCutoff.completion().whenComplete((ignored, failure) -> {
                try {
                    assertNull(failure);
                    assertFalse(lane.isOwnerContext());
                    lane.enqueue("callback mutation", null, () -> {
                        order.add("callback");
                        callbackMutationStarted.countDown();
                    });
                } catch (Throwable throwable) {
                    callbackFailure.set(throwable);
                } finally {
                    callbackObserved.countDown();
                }
            });

            assertTrue(lane.submitStepIfIdle(new PhysicsOwnerStepCommand(
                new RecordingStepResource(order),
                0.05f,
                false,
                1L,
                1L)));

            releaseActiveMutation.countDown();
            assertTrue(preCutoffMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertTrue(callbackObserved.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNull(callbackFailure.get());
            assertNotNull(completedStep);
            assertTrue(completedStep.completedSuccessfully());
            assertTrue(callbackMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(List.of("active", "pre", "step", "callback"), order);
            assertEquals(3, pollMutationCompletions(lane, 3).size());
        } finally {
            releaseActiveMutation.countDown();
        }
    }

    @Test
    void completionCallbackSynchronousOwnerWaitIsRejected() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch callbackObserved = new CountDownLatch(1);
        AtomicReference<Throwable> syncFailure = new AtomicReference<>();
        AtomicReference<Throwable> callFailure = new AtomicReference<>();
        AtomicReference<Throwable> drainFailure = new AtomicReference<>();
        AtomicReference<Throwable> crossLaneSyncFailure = new AtomicReference<>();
        AtomicBoolean callRan = new AtomicBoolean();
        AtomicBoolean drainRan = new AtomicBoolean();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            PhysicsOwnerLaneResource otherLane = scheduler.createLane();
            lane.start("completion-callback-sync-reject");
            otherLane.start("completion-callback-cross-sync-reject");

            lane.submitMutation("active mutation", () -> {
                order.add("active");
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            PhysicsMutationHandle<Void> preCutoff = lane.submitMutation("pre-cutoff mutation",
                () -> {
                    order.add("pre");
                    return PhysicsOwnerSnapshot.empty();
                });
            preCutoff.completion().whenComplete((ignored, failure) -> {
                try {
                    assertNull(failure);
                    lane.run("sync callback mutation", () -> order.add("sync-callback"));
                } catch (Throwable throwable) {
                    syncFailure.set(throwable);
                }
                try {
                    lane.call("sync callback call", () -> {
                        callRan.set(true);
                        return "rejected";
                    });
                } catch (Throwable throwable) {
                    callFailure.set(throwable);
                }
                try {
                    lane.submitAndDrain(() -> {
                        drainRan.set(true);
                        return PhysicsOwnerSnapshot.empty();
                    });
                } catch (Throwable throwable) {
                    drainFailure.set(throwable);
                }
                try {
                    otherLane.run("cross-lane sync callback mutation",
                        () -> order.add("cross-sync-callback"));
                } catch (Throwable throwable) {
                    crossLaneSyncFailure.set(throwable);
                } finally {
                    callbackObserved.countDown();
                }
            });

            assertTrue(lane.submitStepIfIdle(new PhysicsOwnerStepCommand(
                new RecordingStepResource(order),
                0.05f,
                false,
                1L,
                1L)));

            releaseActiveMutation.countDown();
            assertTrue(callbackObserved.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNotNull(syncFailure.get());
            assertInstanceOf(RejectedExecutionException.class, syncFailure.get());
            assertNotNull(callFailure.get());
            assertInstanceOf(RejectedExecutionException.class, callFailure.get());
            assertFalse(callRan.get());
            assertNotNull(drainFailure.get());
            assertInstanceOf(RejectedExecutionException.class, drainFailure.get());
            assertFalse(drainRan.get());
            assertNotNull(crossLaneSyncFailure.get());
            assertInstanceOf(RejectedExecutionException.class, crossLaneSyncFailure.get());
            assertNotNull(completedStep);
            assertTrue(completedStep.completedSuccessfully());
            assertEquals(List.of("active", "pre", "step"), order);
            assertEquals(2, pollMutationCompletions(lane, 2).size());
            assertTrue(otherLane.pollCompletedMutations(1).isEmpty());
        } finally {
            releaseActiveMutation.countDown();
        }
    }

    @Test
    void completionCallbackCloseIsRejectedWithoutClosingLane() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch callbackObserved = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("completion-callback-close-reject");

            lane.submitMutation("active mutation", () -> {
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            PhysicsMutationHandle<Void> mutation = lane.submitMutation("complete then close",
                PhysicsOwnerSnapshot::empty);
            mutation.completion().whenComplete((ignored, failure) -> {
                try {
                    assertNull(failure);
                    assertFalse(lane.isOwnerContext());
                    assertThrows(RejectedExecutionException.class, lane::close);
                } catch (Throwable throwable) {
                    callbackFailure.set(throwable);
                } finally {
                    callbackObserved.countDown();
                }
            });

            releaseActiveMutation.countDown();
            assertTrue(callbackObserved.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            assertNull(callbackFailure.get());
            assertFalse(lane.isClosed());
            assertEquals(2, pollMutationCompletions(lane, 2).size());
        } finally {
            releaseActiveMutation.countDown();
        }
    }

    @Test
    void schedulerCloseWaitsForCompletionCallbacks() throws Exception {
        CountDownLatch callbackStarted = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        AtomicBoolean closeReturned = new AtomicBoolean();
        PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            Duration.ofSeconds(2L));
        try {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("close-waits-for-completion");

            PhysicsMutationHandle<Void> mutation = lane.submitMutation("blocked callback",
                PhysicsOwnerSnapshot::empty);
            mutation.completion().whenComplete((ignored, failure) -> {
                callbackStarted.countDown();
                try {
                    assertTrue(releaseCallback.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(callbackStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            CompletableFuture<Void> close = CompletableFuture.runAsync(() -> {
                scheduler.close();
                closeReturned.set(true);
            });

            assertFalse(closeReturned.get());
            assertFalse(close.isDone());

            releaseCallback.countDown();
            close.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            assertTrue(closeReturned.get());
        } finally {
            releaseCallback.countDown();
            if (!closeReturned.get()) {
                scheduler.close();
            }
        }
    }

    @Test
    void laneCloseWaitsForCompletionCallbacks() throws Exception {
        CountDownLatch callbackStarted = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        AtomicBoolean closeReturned = new AtomicBoolean();
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            Duration.ofSeconds(2L))) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("lane-close-waits-for-completion");

            PhysicsMutationHandle<Void> mutation = lane.submitMutation("blocked lane callback",
                PhysicsOwnerSnapshot::empty);
            mutation.completion().whenComplete((ignored, failure) -> {
                callbackStarted.countDown();
                try {
                    assertTrue(releaseCallback.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(callbackStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            CompletableFuture<Void> close = CompletableFuture.runAsync(() -> {
                lane.close();
                closeReturned.set(true);
            });

            assertFalse(closeReturned.get());
            assertFalse(close.isDone());

            releaseCallback.countDown();
            close.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            assertTrue(closeReturned.get());
            assertTrue(lane.isClosed());
        } finally {
            releaseCallback.countDown();
        }
    }

    @Test
    void schedulerCloseFromCompletionCallbackIsRejected() throws Exception {
        CountDownLatch mutationStarted = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        CountDownLatch callbackObserved = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("scheduler-close-callback-reject");

            PhysicsMutationHandle<Void> mutation = lane.submitMutation("complete then scheduler close",
                () -> {
                    mutationStarted.countDown();
                    assertTrue(releaseMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                    return PhysicsOwnerSnapshot.empty();
                });
            assertTrue(mutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            mutation.completion().whenComplete((ignored, failure) -> {
                try {
                    assertNull(failure);
                    assertThrows(RejectedExecutionException.class, scheduler::close);
                } catch (Throwable throwable) {
                    callbackFailure.set(throwable);
                } finally {
                    callbackObserved.countDown();
                }
            });

            releaseMutation.countDown();
            assertTrue(callbackObserved.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            assertNull(callbackFailure.get());
            assertEquals(1, pollMutationCompletions(lane, 1).size());
        } finally {
            releaseMutation.countDown();
        }
    }

    @Test
    void ownerContextCloseIsRejectedWithoutClosingLane() throws Exception {
        AtomicBoolean rejectionObserved = new AtomicBoolean();
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("owner-context-close-reject");

            PhysicsMutationHandle<Void> mutation = lane.enqueue("close from owner", null, () -> {
                assertTrue(lane.isOwnerContext());
                assertThrows(RejectedExecutionException.class, lane::close);
                rejectionObserved.set(true);
            });

            awaitHandle(mutation);

            assertTrue(rejectionObserved.get());
            assertFalse(lane.isClosed());
            assertEquals(1, pollMutationCompletions(lane, 1).size());
        }
    }

    @Test
    void completedStepReportsPreStepDrainBackpressure() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch preCutoffMutationStarted = new CountDownLatch(1);
        CountDownLatch releasePreCutoffMutation = new CountDownLatch(1);
        CountDownLatch releasePostCutoffMutation = new CountDownLatch(1);
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(2,
            8,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("pre-step-drain-stats");

            lane.submitMutation("active mutation", () -> {
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            lane.submitMutation("pre-cutoff mutation", () -> {
                preCutoffMutationStarted.countDown();
                assertTrue(releasePreCutoffMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });

            PhysicsOwnerStepCommand step = new PhysicsOwnerStepCommand(
                new LegacyLiveHandleTestResource(),
                0.05f,
                false,
                1L,
                1L);
            assertTrue(lane.submitStepIfIdle(step));

            lane.submitMutation("post-cutoff mutation", () -> {
                assertTrue(releasePostCutoffMutation.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
                return PhysicsOwnerSnapshot.empty();
            });

            releaseActiveMutation.countDown();
            assertTrue(preCutoffMutationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            releasePreCutoffMutation.countDown();
            PhysicsOwnerStepCompletion completedStep = pollStepCompletion(lane,
                Duration.ofMillis(TIMEOUT_MILLIS));

            assertNotNull(completedStep);
            assertEquals(1, completedStep.preStepDrainedMutations());
            assertTrue(completedStep.preStepDrainRunNanos() > 0L);
            assertEquals(1, completedStep.lateMutationBacklogAtStep());

            releasePostCutoffMutation.countDown();
            assertEquals(2, pollMutationCompletions(lane, 2).size());
        } finally {
            releaseActiveMutation.countDown();
            releasePreCutoffMutation.countDown();
            releasePostCutoffMutation.countDown();
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

    @Test
    void unpolledMutationCompletionsApplyQueueBackpressure() throws Exception {
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            2,
            CLOSE_TIMEOUT)) {
            PhysicsOwnerLaneResource lane = scheduler.createLane();
            lane.start("pending-completion-backpressure");

            PhysicsMutationHandle<Void> first = lane.submitMutation("first",
                PhysicsOwnerSnapshot::empty);
            PhysicsMutationHandle<Void> second = lane.submitMutation("second",
                PhysicsOwnerSnapshot::empty);
            awaitHandle(first);
            awaitHandle(second);

            assertThrows(RejectedExecutionException.class,
                () -> lane.submitMutation("third", PhysicsOwnerSnapshot::empty));

            assertEquals(2, pollMutationCompletions(lane, 2).size());
            PhysicsMutationHandle<Void> third = lane.submitMutation("third",
                PhysicsOwnerSnapshot::empty);
            awaitHandle(third);
            assertEquals(1, pollMutationCompletions(lane, 1).size());
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

    private static final class RecordingStepResource extends LegacyLiveHandleTestResource {

        @Nonnull
        private final List<String> order;

        private RecordingStepResource(@Nonnull List<String> order) {
            this.order = order;
        }

        @Nonnull
        @Override
        public PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrame(long stepSequence,
            long serverTick,
            @Nonnull PublishedPhysicsSnapshotFrame.Status status,
            long stepNanos,
            boolean profilingEnabled,
            @Nonnull List<PhysicsFrameEvent> physicsEvents,
            int droppedBackendEventCount) {
            order.add("step");
            return super.capturePublishedSnapshotFrame(stepSequence,
                serverTick,
                status,
                stepNanos,
                profilingEnabled,
                physicsEvents,
                droppedBackendEventCount);
        }
    }
}
