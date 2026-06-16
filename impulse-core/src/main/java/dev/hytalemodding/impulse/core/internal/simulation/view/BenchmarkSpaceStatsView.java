package dev.hytalemodding.impulse.core.internal.simulation.view;

/**
 * Copied store tick lane counters used by stress and fall-envelope diagnostics.
 */
public record BenchmarkSpaceStatsView(int bodies,
                                      int dynamicBodies,
                                      int awakeDynamicBodies,
                                      int sleepingDynamicBodies,
                                      int detachedBodies,
                                      int rawBodies,
                                      int worldCollisionBodies,
                                      int belowPlaneBodies,
                                      int belowTerrainBodies,
                                      int belowWorldMinBodies,
                                      int belowVoidBodies,
                                      int terrainBaselineBodies,
                                      int missingTerrainBaselineBodies,
                                      float minTerrainBottomClearance,
                                      float minDynamicBodyY,
                                      float maxDynamicBodyY) {
}
