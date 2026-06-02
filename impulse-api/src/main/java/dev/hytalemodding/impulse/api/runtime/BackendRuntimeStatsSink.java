package dev.hytalemodding.impulse.api.runtime;

/**
 * Primitive runtime-stat callback.
 */
@FunctionalInterface
public interface BackendRuntimeStatsSink {

    void accept(int bodyCount,
        int colliderCount,
        int activeBodyCount,
        int contactPairCount,
        int contactManifoldCount,
        int contactPointCount,
        int dynamicDynamicContactPairCount,
        int terrainContactPairCount,
        int activeIslandCount,
        int jointCount,
        boolean available);
}
