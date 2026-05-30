package dev.hytalemodding.impulse.core.internal.simulation;

/**
 * Copied owner-thread runtime counters for one physics space.
 */
public record PhysicsSpaceRuntimeStatsView(int bodies,
                                           int dynamicBodies,
                                           int awakeDynamicBodies,
                                           int sleepingDynamicBodies,
                                           int staticBodies,
                                           int kinematicBodies,
                                           int entityOwnedBodies,
                                           int detachedBodies,
                                           int worldCollisionBodies,
                                           int planeBodies,
                                           int rawBodies,
                                           int joints,
                                           int contacts,
                                           boolean runtimeStatsAvailable,
                                           int runtimeBodyCount,
                                           int runtimeColliderCount,
                                           int runtimeActiveBodyCount,
                                           int runtimeContactPairCount,
                                           int runtimeContactManifoldCount,
                                           int runtimeContactPointCount,
                                           int runtimeDynamicDynamicContactPairCount,
                                           int runtimeTerrainContactPairCount,
                                           int runtimeActiveIslandCount,
                                           int runtimeJointCount) {
}
