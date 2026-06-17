package dev.hytalemodding.impulse.physicschunk.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsRuntimeProfiling.StepDrainSnapshotView;
import org.junit.jupiter.api.Test;

class WorldCollisionPerfReportCommandTest {

    @Test
    void preStepDrainSummaryReportsAverageLatestAndMaxBackpressure() {
        StepDrainSample cumulative = new StepDrainSample(2,
            6,
            4,
            12_000_000L,
            4,
            3);
        StepDrainSample latest = new StepDrainSample(1,
            4,
            4,
            8_000_000L,
            3,
            3);

        assertEquals("Physics pre-step drain avg completedStep drained/runMs/lateBacklog=3.0/6.000/2.0 "
                + "latest drained/lateBacklog=4/3 max drained/lateBacklog=4/3",
            WorldCollisionPerfReportCommand.formatPreStepDrainSummary(cumulative, latest));
    }

    @Test
    void preStepDrainSummaryRequiresCompletedStepSamples() {
        assertFalse(WorldCollisionPerfReportCommand.hasCompletedStepSamples(
            new StepDrainSample(0, 0, 0, 0L, 0, 0)));

        assertTrue(WorldCollisionPerfReportCommand.hasCompletedStepSamples(
            new StepDrainSample(1, 1, 1, 2_000_000L, 0, 0)));
    }

    @Test
    void preStepDrainSummaryUsesLatestCompletedStepAfterSkippedPendingTick() {
        StepDrainSample cumulative = new StepDrainSample(1,
            4,
            4,
            8_000_000L,
            3,
            3);
        StepDrainSample latestCompleted = new StepDrainSample(1,
            4,
            4,
            8_000_000L,
            3,
            3);

        assertEquals("Physics pre-step drain avg completedStep drained/runMs/lateBacklog=4.0/8.000/3.0 "
                + "latest drained/lateBacklog=4/3 max drained/lateBacklog=4/3",
            WorldCollisionPerfReportCommand.formatPreStepDrainSummary(cumulative,
                latestCompleted));
    }

    private record StepDrainSample(int tickSamples,
        int preStepDrainedMutations,
        int maxPreStepDrainedMutations,
        long preStepDrainRunNanos,
        int lateMutationBacklogAtStep,
        int maxLateMutationBacklogAtStep) implements StepDrainSnapshotView {

        @Override
        public int getTickSamples() {
            return tickSamples;
        }

        @Override
        public int getPreStepDrainedMutations() {
            return preStepDrainedMutations;
        }

        @Override
        public int getMaxPreStepDrainedMutations() {
            return maxPreStepDrainedMutations;
        }

        @Override
        public long getPreStepDrainRunNanos() {
            return preStepDrainRunNanos;
        }

        @Override
        public int getLateMutationBacklogAtStep() {
            return lateMutationBacklogAtStep;
        }

        @Override
        public int getMaxLateMutationBacklogAtStep() {
            return maxLateMutationBacklogAtStep;
        }
    }
}
