package dev.hytalemodding.impulse.core.internal.systems.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PhysicsPublicationPipelineTest {

    @Test
    void completedMutationsDrainThroughPublicationPipelineCap() throws Exception {
        AtomicInteger mutations = new AtomicInteger();
        int mutationCount = 80;
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource()) {
            worker.start("publication-pipeline-mutation-drain-test");
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

            assertEquals(64, PhysicsPublicationPipeline.publishCompletedMutations(worker));
            assertEquals(16, PhysicsPublicationPipeline.publishCompletedMutations(worker));
            assertEquals(mutationCount, mutations.get());
        }
    }
}
