package dev.hytalemodding.impulse.core.plugin.simulation;

/**
 * Owner-thread query for the current live joint count across all physics spaces.
 */
public record RuntimeJointCountQuery() implements PhysicsQuery<Integer> {
}
