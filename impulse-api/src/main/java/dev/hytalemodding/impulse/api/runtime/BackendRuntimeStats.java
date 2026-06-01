package dev.hytalemodding.impulse.api.runtime;

/**
 * Copied backend runtime counters.
 */
public record BackendRuntimeStats(int bodyCount,
                                  int colliderCount,
                                  int activeBodyCount,
                                  int contactPairCount,
                                  int contactManifoldCount,
                                  int contactPointCount,
                                  int dynamicDynamicContactPairCount,
                                  int terrainContactPairCount,
                                  int activeIslandCount,
                                  int jointCount,
                                  boolean available) {

    public static BackendRuntimeStats unavailable() {
        return new BackendRuntimeStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }
}
