package dev.hytalemodding.impulse.core.plugin.simulation.query;

/**
 * Owner-lane query for the current live joint count across all physics spaces.
 */
public record RuntimeJointCountQuery() implements PhysicsQuery<Integer> {
}
