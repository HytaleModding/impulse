package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PhysicsStepSchedulerResourceTest {

    @Test
    void submittedStepRunsAsynchronouslyAndSkipsWhilePending() throws Exception {
        PhysicsStepSchedulerResource scheduler = new PhysicsStepSchedulerResource();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        PhysicsStepSchedulerResource.StepInput input = scheduler.acceptStepInput(0.05f,
            PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT,
            0.10f);
        assertTrue(scheduler.submitStep(input, () -> {
            entered.countDown();
            awaitOrFail(release);
            return new PhysicsStepSchedulerResource.CompletedStep(1,
                2,
                123L,
                PhysicsStepPhaseStats.unavailable());
        }, 10L));

        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTrue(scheduler.isStepPending());

        PhysicsStepSchedulerResource.TickDecision skipped = scheduler.beforeStoreTick(0.05f,
            PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT,
            0.10f,
            20L);
        assertFalse(skipped.shouldTick());
        assertEquals(10L, skipped.pendingStepAgeNanos());
        assertEquals(0.05f, skipped.backlogDtSeconds(), 0.0001f);
        assertNull(scheduler.pollCompletedStep());

        release.countDown();
        PhysicsStepSchedulerResource.TickDecision allowed = awaitAllowedTick(scheduler);
        assertTrue(allowed.shouldTick());

        PhysicsStepSchedulerResource.CompletedStep completed = scheduler.pollCompletedStep();
        assertNotNull(completed);
        assertEquals(1, completed.spaces());
        assertEquals(2, completed.substeps());
        assertEquals(123L, completed.stepSubmitNanos());
        assertEquals(0.05f, completed.input().submittedDtSeconds(), 0.0001f);
        assertFalse(scheduler.isStepPending());
        scheduler.close();
    }

    @Test
    void whenIdleCompletesAfterPendingStepFinishes() throws Exception {
        PhysicsStepSchedulerResource scheduler = new PhysicsStepSchedulerResource();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        PhysicsStepSchedulerResource.StepInput input = scheduler.acceptStepInput(0.05f,
            PhysicsStepSchedulingMode.DROP_PENDING_DT,
            0.10f);
        assertTrue(scheduler.submitStep(input, () -> {
            entered.countDown();
            awaitOrFail(release);
            return new PhysicsStepSchedulerResource.CompletedStep(1,
                1,
                10L,
                PhysicsStepPhaseStats.unavailable());
        }, 10L));
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        CompletableFuture<Void> idle = scheduler.whenIdle().toCompletableFuture();
        assertFalse(idle.isDone());

        release.countDown();
        idle.get(5, TimeUnit.SECONDS);
        assertFalse(scheduler.isStepPending());
        scheduler.close();
    }

    private static PhysicsStepSchedulerResource.TickDecision awaitAllowedTick(
        PhysicsStepSchedulerResource scheduler) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            PhysicsStepSchedulerResource.TickDecision decision = scheduler.beforeStoreTick(0.05f,
                PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT,
                0.10f,
                30L + attempt);
            if (decision.shouldTick()) {
                return decision;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Scheduler did not allow the tick after step completion");
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for scheduler test latch", exception);
        }
    }
}
