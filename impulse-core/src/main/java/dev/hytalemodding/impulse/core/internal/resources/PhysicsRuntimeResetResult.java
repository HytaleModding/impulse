package dev.hytalemodding.impulse.core.internal.resources;

/**
 * Summary of runtime physics state removed while preserving world-level space topology.
 */
public record PhysicsRuntimeResetResult(int removedBodies, int removedJoints, int keptSpaces) {
}
