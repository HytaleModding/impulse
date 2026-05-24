package dev.hytalemodding.impulse.core.internal.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PhysicsWorkerRunnerTest {

    @Test
    void executesCommandsOnSingleWorkerThreadInFifoOrder() throws Exception {
        try (PhysicsWorkerRunner runner = new PhysicsWorkerRunner("Impulse test physics worker", 8)) {
            List<String> threadNames = new ArrayList<>();
            List<Integer> order = new ArrayList<>();

            CompletableFuture<PhysicsWorkerResult> first = runner.submit(() -> {
                threadNames.add(Thread.currentThread().getName());
                order.add(1);
                assertTrue(runner.isWorkerThread());
                return new PhysicsWorkerSnapshot(1, 2, 3, 4, 5L, 6L);
            });
            CompletableFuture<PhysicsWorkerResult> second = runner.submit(() -> {
                threadNames.add(Thread.currentThread().getName());
                order.add(2);
                assertTrue(runner.isWorkerThread());
                return PhysicsWorkerSnapshot.empty();
            });

            PhysicsWorkerResult firstResult = first.get(2, TimeUnit.SECONDS);
            PhysicsWorkerResult secondResult = second.get(2, TimeUnit.SECONDS);

            assertEquals(List.of(1, 2), order);
            assertEquals(1L, firstResult.sequence());
            assertEquals(2L, secondResult.sequence());
            assertEquals("Impulse test physics worker", threadNames.get(0));
            assertEquals(threadNames.get(0), threadNames.get(1));
            assertEquals(1, firstResult.snapshot().spaces());
            assertEquals(2, firstResult.snapshot().substeps());
            assertTrue(firstResult.queuedNanos() >= 0L);
            assertTrue(firstResult.runNanos() >= 0L);
            assertEquals(0, runner.pendingCommands());
        }
    }

    @Test
    void reportsCommandFailuresThroughFuture() throws Exception {
        RuntimeException failure = new RuntimeException("boom");
        try (PhysicsWorkerRunner runner = new PhysicsWorkerRunner("Impulse failing physics worker", 2)) {
            CompletableFuture<PhysicsWorkerResult> result = runner.submit(() -> {
                throw failure;
            });

            ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> result.get(2, TimeUnit.SECONDS));
            assertSame(failure, thrown.getCause());

            PhysicsWorkerResult next = runner.submit(PhysicsWorkerSnapshot::empty)
                .get(2, TimeUnit.SECONDS);
            assertEquals(2L, next.sequence());
            assertEquals(0, runner.pendingCommands());
        }
    }

    @Test
    void rejectsWhenQueueIsFull() throws Exception {
        try (PhysicsWorkerRunner runner = new PhysicsWorkerRunner("Impulse bounded physics worker", 1)) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CompletableFuture<PhysicsWorkerResult> running = runner.submit(() -> {
                started.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
                return PhysicsWorkerSnapshot.empty();
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));

            CompletableFuture<PhysicsWorkerResult> queued = runner.submit(PhysicsWorkerSnapshot::empty);
            CompletableFuture<PhysicsWorkerResult> rejected = runner.submit(PhysicsWorkerSnapshot::empty);

            ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> rejected.get(2, TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class, thrown.getCause());
            assertEquals("physics worker command queue is full", thrown.getCause().getMessage());
            assertEquals(2, runner.pendingCommands());

            release.countDown();
            running.get(2, TimeUnit.SECONDS);
            queued.get(2, TimeUnit.SECONDS);
            assertPendingCommands(runner, 0);
        }
    }

    @Test
    void rejectsAfterShutdown() {
        try (PhysicsWorkerRunner runner = new PhysicsWorkerRunner("Impulse closed physics worker", 1)) {
            assertTrue(runner.shutdown(Duration.ofSeconds(2L)));
            assertFalse(runner.isAccepting());

            CompletableFuture<PhysicsWorkerResult> rejected = runner.submit(PhysicsWorkerSnapshot::empty);
            ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> rejected.get(2, TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class, thrown.getCause());
        }
    }

    @Test
    void shutdownPreservesInterruptButStillWaitsForWorkerExit() {
        try (PhysicsWorkerRunner runner = new PhysicsWorkerRunner("Impulse interrupted shutdown worker", 1)) {
            try {
                Thread.currentThread().interrupt();

                assertTrue(runner.shutdown(Duration.ofSeconds(2L)));
                assertTrue(Thread.interrupted());
                assertFalse(runner.isAccepting());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void drainsQueuedCommandsSubmittedBeforeShutdown() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (PhysicsWorkerRunner runner = new PhysicsWorkerRunner("Impulse draining physics worker", 2)) {
            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<PhysicsWorkerResult> running = runner.submit(() -> {
                started.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
                return new PhysicsWorkerSnapshot(1, 1, 1, 1, 1L, 1L);
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CompletableFuture<PhysicsWorkerResult> queued = runner.submit(() ->
                new PhysicsWorkerSnapshot(2, 2, 2, 2, 2L, 2L));

            release.countDown();
            assertTrue(runner.shutdown(Duration.ofSeconds(2L)));

            assertEquals(1, running.get(2, TimeUnit.SECONDS).snapshot().spaces());
            assertEquals(2, queued.get(2, TimeUnit.SECONDS).snapshot().spaces());
            assertEquals(0, runner.pendingCommands());
        } finally {
            release.countDown();
        }
    }

    @Test
    void shutdownDoesNotInterruptActiveStepAndDrainsQueuedWork() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (PhysicsWorkerRunner runner = new PhysicsWorkerRunner("Impulse active shutdown worker", 2)) {
            CountDownLatch started = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean();

            CompletableFuture<PhysicsWorkerResult> running = runner.submit(() -> {
                started.countDown();
                try {
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                    throw e;
                }
                return new PhysicsWorkerSnapshot(3, 3, 3, 3, 3L, 3L);
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CompletableFuture<PhysicsWorkerResult> queued = runner.submit(() ->
                new PhysicsWorkerSnapshot(4, 4, 4, 4, 4L, 4L));

            CompletableFuture<Boolean> shutdown = CompletableFuture.supplyAsync(() ->
                runner.shutdown(Duration.ofSeconds(2L)));
            assertFalse(shutdown.isDone());
            assertFalse(running.isDone());
            assertFalse(queued.isDone());

            release.countDown();

            assertTrue(shutdown.get(2, TimeUnit.SECONDS));
            assertFalse(runner.isAccepting());
            assertFalse(interrupted.get());
            assertEquals(3, running.get(2, TimeUnit.SECONDS).snapshot().spaces());
            assertEquals(4, queued.get(2, TimeUnit.SECONDS).snapshot().spaces());
            assertEquals(0, runner.pendingCommands());
        } finally {
            release.countDown();
        }
    }

    @Test
    void snapshotValuesClampToNonNegativeCounters() {
        PhysicsWorkerSnapshot snapshot = new PhysicsWorkerSnapshot(-1, -2, -3, -4, -5L, -6L);

        assertEquals(0, snapshot.spaces());
        assertEquals(0, snapshot.substeps());
        assertEquals(0, snapshot.bodySnapshots());
        assertEquals(0, snapshot.spatialIndexCells());
        assertEquals(0L, snapshot.stepNanos());
        assertEquals(0L, snapshot.snapshotNanos());
    }

    private static void assertPendingCommands(PhysicsWorkerRunner runner,
        int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline) {
            if (runner.pendingCommands() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expected, runner.pendingCommands());
    }
}
