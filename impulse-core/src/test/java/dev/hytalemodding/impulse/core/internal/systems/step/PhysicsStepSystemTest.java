package dev.hytalemodding.impulse.core.internal.systems.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCompletion;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class PhysicsStepSystemTest {

    @Test
    void submittedStepKeepsSchedulerSequenceSeparateFromServerTick() throws Exception {
        PhysicsStepSystem system = new PhysicsStepSystem();
        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            worker.start("step-system-sequence-test");
            resource.attachWorkerResource(worker);

            // Worker steps run detached from the scheduling tick, so the published
            // frame must preserve the scheduling metadata captured at submission.
            system.submitStepIfIdle(worker, resource, 0.05f, profiling, 42L);

            PhysicsWorkerStepCompletion completion = pollCompletedStep(worker);
            PublishedPhysicsSnapshotFrame frame = completion.frame();
            assertNotNull(frame);
            assertEquals(1L, frame.stepSequence());
            assertEquals(42L, frame.serverTick());
        }
    }

    @Nonnull
    private static PhysicsWorkerStepCompletion pollCompletedStep(
        @Nonnull PhysicsWorldWorkerResource worker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline) {
            PhysicsWorkerStepCompletion completion = worker.pollCompletedStep();
            if (completion != null) {
                return completion;
            }
            Thread.sleep(10L);
        }
        fail("Timed out waiting for physics step completion");
        throw new AssertionError();
    }
}
