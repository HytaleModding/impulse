package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Owner-lane query for copied space diagnostics.
 *
 * <p>A {@code null} space id requests summaries for every registered physics space.</p>
 */
public record SpaceSummaryQuery(@Nullable SpaceId spaceId) implements PhysicsQuery<List<SpaceSummary>> {
}
