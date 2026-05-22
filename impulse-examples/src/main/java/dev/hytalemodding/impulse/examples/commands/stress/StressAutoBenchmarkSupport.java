package dev.hytalemodding.impulse.examples.commands.stress;

import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class StressAutoBenchmarkSupport {

    static final int DEFAULT_RAMP_START_COUNT = 250;
    static final int DEFAULT_STAGE_TICKS = 200;
    static final int DEFAULT_WARMUP_TICKS = 60;
    private static final int[] DEFAULT_RAMP_COUNTS = {
        250,
        500,
        1_000,
        2_000,
        4_000,
        6_000,
        8_000,
        10_000
    };
    private static final int MAX_RAMP_COUNT = DEFAULT_RAMP_COUNTS[DEFAULT_RAMP_COUNTS.length - 1];
    private static final double STOP_TPS = 8.0;
    private static final double WARN_TPS = 15.0;

    private StressAutoBenchmarkSupport() {
    }

    @Nonnull
    static List<Integer> rampCounts(int startCount, int targetCount) {
        int target = Math.clamp(targetCount, 1, MAX_RAMP_COUNT);
        int start = Math.clamp(startCount, 1, target);
        List<Integer> counts = new ArrayList<>();
        addCount(counts, start);
        for (int count : DEFAULT_RAMP_COUNTS) {
            if (count > start && count <= target) {
                addCount(counts, count);
            }
        }
        addCount(counts, target);
        return counts;
    }

    private static void addCount(@Nonnull List<Integer> counts, int count) {
        if (counts.isEmpty() || counts.get(counts.size() - 1) != count) {
            counts.add(count);
        }
    }

    static boolean shouldAddFallbackPlane(@Nonnull BenchmarkWorldCollision worldCollision) {
        return worldCollision != BenchmarkWorldCollision.STREAMING;
    }

    static double totalProfiledMillis(double stepMillis,
        double snapshotMillis,
        double syncMillis,
        double worldCollisionMillis) {
        return stepMillis + snapshotMillis + syncMillis + worldCollisionMillis;
    }

    @Nonnull
    static StageHealth assessStage(@Nonnull BenchmarkWorldCollision worldCollision,
        @Nonnull StageMetrics metrics) {
        List<String> stops = new ArrayList<>();
        if (metrics.observedTickRate() < STOP_TPS) {
            stops.add("observedTickRate<8");
        }
        if (worldCollision == BenchmarkWorldCollision.STREAMING
            && metrics.worldCollisionBodies() == 0) {
            stops.add("worldCollisionBodies=0");
        }
        if (metrics.belowWorldMinBodies() > 0) {
            stops.add("belowWorldMinBodies=" + metrics.belowWorldMinBodies());
        }
        if (metrics.belowVoidBodies() > 0) {
            stops.add("belowVoidBodies=" + metrics.belowVoidBodies());
        }
        int belowGroundLimit = Math.max(5, (int) Math.ceil(metrics.count() * 0.01));
        if (worldCollision == BenchmarkWorldCollision.STREAMING) {
            if (metrics.belowTerrainBodies() > belowGroundLimit) {
                stops.add("belowTerrainBodies=" + metrics.belowTerrainBodies()
                    + ">" + belowGroundLimit);
            }
        } else if (metrics.belowPlaneBodies() > belowGroundLimit) {
            stops.add("belowPlaneBodies=" + metrics.belowPlaneBodies()
                + ">" + belowGroundLimit);
        }
        if (worldCollision == BenchmarkWorldCollision.STREAMING
            && metrics.missingChunks() > 0) {
            stops.add("missingChunks=" + metrics.missingChunks());
        }
        if (!stops.isEmpty()) {
            return new StageHealth(StageStatus.STOP, String.join("; ", stops));
        }
        if (metrics.observedTickRate() < WARN_TPS) {
            return new StageHealth(StageStatus.WARN, "observedTickRate<15");
        }
        return new StageHealth(StageStatus.PASS, "within gates");
    }

    enum BenchmarkWorldCollision {
        NONE("none"),
        STREAMING("streaming");

        private final String serialized;

        BenchmarkWorldCollision(@Nonnull String serialized) {
            this.serialized = serialized;
        }

        @Nonnull
        String serialized() {
            return serialized;
        }

        @Nullable
        static BenchmarkWorldCollision from(@Nonnull String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "none", "off", "disabled" -> NONE;
                case "streaming", "stream", "world" -> STREAMING;
                default -> null;
            };
        }
    }

    enum BenchmarkCollisionPolicy {
        DEFAULT("current"),
        WORLD("world"),
        FULL("full");

        private final String serialized;

        BenchmarkCollisionPolicy(@Nonnull String serialized) {
            this.serialized = serialized;
        }

        @Nonnull
        String serialized() {
            return serialized;
        }

        boolean appliesFilter() {
            return this != DEFAULT;
        }

        int group() {
            return PhysicsCollisionFilters.DYNAMIC_BODY;
        }

        int mask() {
            return this == WORLD
                ? PhysicsCollisionFilters.TERRAIN
                : PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY;
        }

        @Nullable
        static BenchmarkCollisionPolicy from(@Nonnull String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "world", "terrain", "terrain-only", "world-only" -> WORLD;
                case "full", "body", "bodies", "body-body", "dynamic" -> FULL;
                default -> null;
            };
        }
    }

    enum StageStatus {
        PASS,
        WARN,
        STOP
    }

    record StageMetrics(int count,
                        double observedTickRate,
                        int worldCollisionBodies,
                        int belowPlaneBodies,
                        int belowTerrainBodies,
                        int belowWorldMinBodies,
                        int belowVoidBodies,
                        int missingChunks) {
    }

    record StageHealth(@Nonnull StageStatus status, @Nonnull String reason) {
    }
}
