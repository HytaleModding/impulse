package dev.hytalemodding.impulse.core.internal.systems.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.TestPhysicsOwnerLane;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerStepCommand;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import java.util.concurrent.CountDownLatch;
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

    @Test
    void completedStepRecordsPreStepDrainBackpressure() throws Exception {
        CountDownLatch activeMutationStarted = new CountDownLatch(1);
        CountDownLatch releaseActiveMutation = new CountDownLatch(1);
        CountDownLatch preCutoffMutationStarted = new CountDownLatch(1);
        CountDownLatch releasePreCutoffMutation = new CountDownLatch(1);
        CountDownLatch releasePostCutoffMutation = new CountDownLatch(1);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        profiling.setEnabled(true);
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane()) {
            owner.start("publication-pipeline-pre-step-drain-test");
            owner.submitMutation("active mutation", () -> {
                activeMutationStarted.countDown();
                assertTrue(releaseActiveMutation.await(2L, TimeUnit.SECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(activeMutationStarted.await(2L, TimeUnit.SECONDS));

            owner.submitMutation("pre-cutoff mutation", () -> {
                preCutoffMutationStarted.countDown();
                assertTrue(releasePreCutoffMutation.await(2L, TimeUnit.SECONDS));
                return PhysicsOwnerSnapshot.empty();
            });

            assertTrue(owner.submitStepIfIdle(new PhysicsOwnerStepCommand(resource,
                0.05f,
                false,
                1L,
                1L)));

            owner.submitMutation("post-cutoff mutation", () -> {
                assertTrue(releasePostCutoffMutation.await(2L, TimeUnit.SECONDS));
                return PhysicsOwnerSnapshot.empty();
            });

            releaseActiveMutation.countDown();
            assertTrue(preCutoffMutationStarted.await(2L, TimeUnit.SECONDS));
            releasePreCutoffMutation.countDown();

            PhysicsEventFrame eventFrame = publishStepWhenReady(owner, resource, profiling);

            assertNotNull(eventFrame);
            assertEquals(1, profiling.getCumulativeStep().getTickSamples());
            assertEquals(1, profiling.getCumulativeStep().getPreStepDrainedMutations());
            assertTrue(profiling.getCumulativeStep().getPreStepDrainRunNanos() > 0L);
            assertEquals(1, profiling.getCumulativeStep().getLateMutationBacklogAtStep());
            assertEquals(1, profiling.getCumulativeStep().getMaxLateMutationBacklogAtStep());

            releasePostCutoffMutation.countDown();
            assertEquals(3, waitForCompletedMutations(owner, 3));
        } finally {
            releaseActiveMutation.countDown();
            releasePreCutoffMutation.countDown();
            releasePostCutoffMutation.countDown();
        }
    }

    private static PhysicsEventFrame publishStepWhenReady(TestPhysicsOwnerLane owner,
        LegacyLiveHandleTestResource resource,
        PhysicsRuntimeProfilingResource profiling) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        PhysicsEventFrame eventFrame = null;
        while (System.nanoTime() < deadline && eventFrame == null) {
            eventFrame = PhysicsPublicationPipeline.publishCompletedStep(owner,
                resource,
                profiling,
                1L);
            if (eventFrame == null) {
                Thread.sleep(10L);
            }
        }
        return eventFrame;
    }

    private static int waitForCompletedMutations(TestPhysicsOwnerLane owner,
        int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        int completions = 0;
        while (System.nanoTime() < deadline && completions < expected) {
            completions += PhysicsPublicationPipeline.publishCompletedMutations(owner);
            if (completions < expected) {
                Thread.sleep(10L);
            }
        }
        return completions;
    }

}
