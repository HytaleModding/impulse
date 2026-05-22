package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;

/**
 * Optional backend runtime counters for a live physics space.
 *
 * <p>These counters are diagnostic hints only. Backends may report them from different
 * internal structures, and unsupported backends should return {@link #unavailable()}.</p>
 */
public record PhysicsRuntimeStats(boolean available,
                                  int bodyCount,
                                  int colliderCount,
                                  int activeBodyCount,
                                  int contactPairCount,
                                  int contactManifoldCount,
                                  int contactPointCount,
                                  int dynamicDynamicContactPairCount,
                                  int terrainContactPairCount,
                                  int activeIslandCount,
                                  int jointCount) {

    private static final PhysicsRuntimeStats UNAVAILABLE = new PhysicsRuntimeStats(false,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0);

    public PhysicsRuntimeStats {
        if (!available) {
            bodyCount = 0;
            colliderCount = 0;
            activeBodyCount = 0;
            contactPairCount = 0;
            contactManifoldCount = 0;
            contactPointCount = 0;
            dynamicDynamicContactPairCount = 0;
            terrainContactPairCount = 0;
            activeIslandCount = 0;
            jointCount = 0;
        } else {
            bodyCount = Math.max(0, bodyCount);
            colliderCount = Math.max(0, colliderCount);
            activeBodyCount = Math.max(0, activeBodyCount);
            contactPairCount = Math.max(0, contactPairCount);
            contactManifoldCount = Math.max(0, contactManifoldCount);
            contactPointCount = Math.max(0, contactPointCount);
            dynamicDynamicContactPairCount = Math.max(0, dynamicDynamicContactPairCount);
            terrainContactPairCount = Math.max(0, terrainContactPairCount);
            activeIslandCount = Math.max(0, activeIslandCount);
            jointCount = Math.max(0, jointCount);
        }
    }

    @Nonnull
    public static PhysicsRuntimeStats available(int bodyCount,
        int colliderCount,
        int activeBodyCount,
        int contactPairCount,
        int contactManifoldCount,
        int contactPointCount,
        int dynamicDynamicContactPairCount,
        int terrainContactPairCount,
        int activeIslandCount,
        int jointCount) {
        return new PhysicsRuntimeStats(true,
            bodyCount,
            colliderCount,
            activeBodyCount,
            contactPairCount,
            contactManifoldCount,
            contactPointCount,
            dynamicDynamicContactPairCount,
            terrainContactPairCount,
            activeIslandCount,
            jointCount);
    }

    @Nonnull
    public static PhysicsRuntimeStats unavailable() {
        return UNAVAILABLE;
    }
}
