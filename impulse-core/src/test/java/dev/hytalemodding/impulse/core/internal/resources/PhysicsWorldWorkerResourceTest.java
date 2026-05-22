package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PhysicsWorldWorkerResourceTest {

    @Test
    void startSubmitAndDrainRunsCommandOnWorldWorker() throws Exception {
        PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L));
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

    @Test
    void submitBeforeStartIsRejected() {
        PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L));

        assertThrows(RejectedExecutionException.class,
            () -> resource.submitAndDrain(PhysicsWorkerSnapshot::empty));
    }

    @Test
    void cloneDoesNotShareStartedRunner() {
        PhysicsWorldWorkerResource resource = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L));
        resource.start("clone-source");

        PhysicsWorldWorkerResource copy = resource.clone();

        assertTrue(resource.isStarted());
        assertFalse(copy.isStarted());
        assertFalse(copy.isClosed());

        resource.close();
        copy.close();
    }
}
