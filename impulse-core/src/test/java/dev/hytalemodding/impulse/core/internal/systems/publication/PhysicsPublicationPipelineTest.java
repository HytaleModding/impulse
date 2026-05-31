package dev.hytalemodding.impulse.core.internal.systems.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.core.internal.resources.owner.TestPhysicsOwnerLane;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerSnapshot;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PhysicsPublicationPipelineTest {

    @Test
    void completedMutationsDrainThroughPublicationPipelineCap() throws Exception {
        AtomicInteger mutations = new AtomicInteger();
        int mutationCount = 80;
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane()) {
            owner.start("publication-pipeline-mutation-drain-test");
            for (int index = 0; index < mutationCount; index++) {
                owner.submitMutation("test mutation " + index, () -> {
                    mutations.incrementAndGet();
                    return PhysicsOwnerSnapshot.empty();
                });
            }

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            while (System.nanoTime() < deadline && owner.pendingCommands() > 0) {
                Thread.sleep(10L);
            }

            assertEquals(64, PhysicsPublicationPipeline.publishCompletedMutations(owner));
            assertEquals(16, PhysicsPublicationPipeline.publishCompletedMutations(owner));
            assertEquals(mutationCount, mutations.get());
        }
    }

}
