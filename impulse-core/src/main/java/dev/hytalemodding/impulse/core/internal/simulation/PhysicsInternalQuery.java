package dev.hytalemodding.impulse.core.internal.simulation;

/**
 * Internal owner-thread read that returns copied physics data without widening plugin ABI.
 *
 * @param <R> immutable query result type
 */
public sealed interface PhysicsInternalQuery<R> permits BenchmarkSpaceStatsQuery,
    PhysicsDebugContactsQuery,
    PhysicsDebugJointsQuery,
    PhysicsSpaceRuntimeStatsQuery,
    WorldCollisionPrewarmEnvelopeQuery {
}
