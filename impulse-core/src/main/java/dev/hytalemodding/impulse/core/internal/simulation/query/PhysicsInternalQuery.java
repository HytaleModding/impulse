package dev.hytalemodding.impulse.core.internal.simulation.query;

/**
 * Internal owner-lane read that returns copied physics data without widening plugin ABI.
 *
 * @param <R> immutable query result type
 */
public sealed interface PhysicsInternalQuery<R> permits BenchmarkSpaceStatsQuery,
    PhysicsDebugContactsQuery,
    PhysicsDebugJointsQuery,
    PhysicsSpaceRuntimeStatsQuery,
    WorldCollisionPrewarmEnvelopeQuery {
}
