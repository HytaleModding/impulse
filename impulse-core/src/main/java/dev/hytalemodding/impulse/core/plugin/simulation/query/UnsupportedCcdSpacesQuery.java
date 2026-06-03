package dev.hytalemodding.impulse.core.plugin.simulation.query;

import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;

import java.util.List;

/**
 * Owner-lane query for spaces whose backend does not support continuous collision detection.
 */
public record UnsupportedCcdSpacesQuery() implements PhysicsQuery<List<SpaceSummary>> {
}
