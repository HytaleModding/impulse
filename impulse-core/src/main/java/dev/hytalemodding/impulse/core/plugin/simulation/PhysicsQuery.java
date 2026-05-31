package dev.hytalemodding.impulse.core.plugin.simulation;

/**
 * Data-only owner-lane read that returns copied physics data.
 *
 * @param <R> immutable query result type
 */
public sealed interface PhysicsQuery<R> permits RaycastClosestQuery,
    RaycastClosestBatchQuery,
    RaycastAllQuery,
    SpaceBodyCountQuery,
    SpaceSummaryQuery,
    CcdSupportQuery,
    UnsupportedCcdSpacesQuery,
    SolverCapabilityQuery,
    RigidBodyStateQuery,
    RuntimeJointCountQuery {
}
