package dev.hytalemodding.impulse.core.internal.modules.worldcollision.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import org.junit.jupiter.api.Test;

class WorldCollisionPerfReportCommandTest {

    @Test
    void preStepDrainSummaryReportsAverageLatestAndMaxBackpressure() {
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        profiling.recordStep(1,
            1,
            20L,
            0,
            0,
            0L,
            0L,
            0L,
            1_000_000_000L,
            PhysicsStepPhaseStats.unavailable(),
            2,
            4_000_000L,
            1);
        profiling.recordStep(1,
            1,
            10L,
            0,
            0,
            0L,
            0L,
            0L,
            1_010_000_000L,
            PhysicsStepPhaseStats.unavailable(),
            4,
            8_000_000L,
            3);

        assertEquals("Physics pre-step drain avg completedStep drained/runMs/lateBacklog=3.0/6.000/2.0 "
                + "latest drained/lateBacklog=4/3 max drained/lateBacklog=4/3",
            WorldCollisionPerfReportCommand.formatPreStepDrainSummary(profiling.getCumulativeStep(),
                profiling.getLatestStep()));
    }

    @Test
    void preStepDrainSummaryRequiresCompletedStepSamples() {
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();

        profiling.recordStepScheduling(0.05f, 0.05f, 0.0f, 0.0f, false);

        assertFalse(WorldCollisionPerfReportCommand.hasCompletedStepSamples(
            profiling.getCumulativeStep()));

        profiling.recordStep(1,
            1,
            20L,
            0,
            0,
            0L,
            0L,
            0L,
            1_000_000_000L,
            PhysicsStepPhaseStats.unavailable(),
            1,
            2_000_000L,
            0);

        assertTrue(WorldCollisionPerfReportCommand.hasCompletedStepSamples(
            profiling.getCumulativeStep()));
    }

    @Test
    void preStepDrainSummaryUsesLatestCompletedStepAfterSkippedPendingTick() {
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        profiling.recordStep(1,
            1,
            20L,
            0,
            0,
            0L,
            0L,
            0L,
            1_000_000_000L,
            PhysicsStepPhaseStats.unavailable(),
            4,
            8_000_000L,
            3);

        profiling.recordStepSkippedPending(2_000_000L);

        assertEquals("Physics pre-step drain avg completedStep drained/runMs/lateBacklog=4.0/8.000/3.0 "
                + "latest drained/lateBacklog=4/3 max drained/lateBacklog=4/3",
            WorldCollisionPerfReportCommand.formatPreStepDrainSummary(profiling.getCumulativeStep(),
                profiling.getLatestCompletedStep()));
    }
}
