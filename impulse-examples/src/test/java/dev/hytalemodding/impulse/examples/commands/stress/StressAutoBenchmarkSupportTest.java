package dev.hytalemodding.impulse.examples.commands.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.examples.commands.stress.StressAutoBenchmarkSupport.BenchmarkCollisionPolicy;
import dev.hytalemodding.impulse.examples.commands.stress.StressAutoBenchmarkSupport.BenchmarkWorldCollision;
import dev.hytalemodding.impulse.examples.commands.stress.StressAutoBenchmarkSupport.StageMetrics;
import dev.hytalemodding.impulse.examples.commands.stress.StressAutoBenchmarkSupport.StageStatus;
import org.junit.jupiter.api.Test;

class StressAutoBenchmarkSupportTest {

    @Test
    void rampCountsClipDefaultSequenceToTarget() {
        assertEquals(
            java.util.List.of(250, 500, 1_000, 2_000),
            StressAutoBenchmarkSupport.rampCounts(250, 2_000));
    }

    @Test
    void rampCountsStartAtTargetWhenTargetIsBelowStart() {
        assertEquals(
            java.util.List.of(100),
            StressAutoBenchmarkSupport.rampCounts(250, 100));
    }

    @Test
    void rampCountsClipAboveTenThousand() {
        assertEquals(
            java.util.List.of(250, 500, 1_000, 2_000, 4_000, 6_000, 8_000, 10_000),
            StressAutoBenchmarkSupport.rampCounts(250, 12_000));
    }

    @Test
    void fullCollisionPolicyAppliesTerrainAndDynamicMasks() {
        assertTrue(BenchmarkCollisionPolicy.FULL.appliesFilter());
        assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY, BenchmarkCollisionPolicy.FULL.group());
        assertEquals(
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY,
            BenchmarkCollisionPolicy.FULL.mask());
    }

    @Test
    void streamingWorldCollisionDisablesFallbackPlane() {
        assertFalse(StressAutoBenchmarkSupport.shouldAddFallbackPlane(BenchmarkWorldCollision.STREAMING));
        assertTrue(StressAutoBenchmarkSupport.shouldAddFallbackPlane(BenchmarkWorldCollision.NONE));
    }

    @Test
    void stageHealthStopsOnLowTps() {
        var health = StressAutoBenchmarkSupport.assessStage(BenchmarkWorldCollision.NONE,
            new StageMetrics(1_000, 7.9, 0, 0, 0, 0, 0, 0));

        assertEquals(StageStatus.STOP, health.status());
    }

    @Test
    void stageHealthStopsOnMissingStreamingWorldCollision() {
        var health = StressAutoBenchmarkSupport.assessStage(BenchmarkWorldCollision.STREAMING,
            new StageMetrics(1_000, 20.0, 0, 0, 0, 0, 0, 0));

        assertEquals(StageStatus.STOP, health.status());
    }

    @Test
    void stageHealthStopsOnTerrainTunnelingCounters() {
        var health = StressAutoBenchmarkSupport.assessStage(BenchmarkWorldCollision.STREAMING,
            new StageMetrics(1_000, 20.0, 10, 16, 16, 0, 0, 0));

        assertEquals(StageStatus.STOP, health.status());
    }

    @Test
    void stageHealthIgnoresFixedPlaneWhenStreamingTerrainIsClear() {
        var health = StressAutoBenchmarkSupport.assessStage(BenchmarkWorldCollision.STREAMING,
            new StageMetrics(1_000, 20.0, 10, 16, 0, 0, 0, 0));

        assertEquals(StageStatus.PASS, health.status());
    }

    @Test
    void stageHealthStillUsesFallbackPlaneWithoutStreamingTerrain() {
        var health = StressAutoBenchmarkSupport.assessStage(BenchmarkWorldCollision.NONE,
            new StageMetrics(1_000, 20.0, 0, 16, 0, 0, 0, 0));

        assertEquals(StageStatus.STOP, health.status());
    }

    @Test
    void stageHealthStopsOnStreamingMissingChunks() {
        var health = StressAutoBenchmarkSupport.assessStage(BenchmarkWorldCollision.STREAMING,
            new StageMetrics(1_000, 20.0, 10, 0, 0, 0, 0, 1));

        assertEquals(StageStatus.STOP, health.status());
    }

    @Test
    void profiledTotalIncludesSnapshotTime() {
        assertEquals(10.0,
            StressAutoBenchmarkSupport.totalProfiledMillis(2.0, 3.0, 4.0, 1.0));
    }
}
