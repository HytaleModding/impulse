package dev.hytalemodding.impulse.core.plugin.simulation.query;

/**
 * Owner-lane query for whether every registered space supports continuous collision detection.
 */
public record CcdSupportQuery() implements PhysicsQuery<Boolean> {
}
