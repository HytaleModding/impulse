package dev.hytalemodding.impulse.core.internal.simulation;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionPrewarmStats;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Internal owner-thread query that computes collision prewarm coverage for a spawn envelope.
 */
public record WorldCollisionPrewarmEnvelopeQuery(@Nonnull World world,
                                                 @Nonnull SpaceId spaceId,
                                                 int count,
                                                 float originX,
                                                 float originY,
                                                 float originZ,
                                                 int side,
                                                 float spacing,
                                                 int radius,
                                                 float fallEnvelopeMinY,
                                                 float horizontalDriftHaloBlocks,
                                                 long tick)
    implements PhysicsInternalQuery<WorldCollisionPrewarmStats> {

    public WorldCollisionPrewarmEnvelopeQuery {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(spaceId, "spaceId");
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        if (side <= 0) {
            throw new IllegalArgumentException("side must be positive");
        }
        if (!Float.isFinite(spacing) || spacing <= 0.0f) {
            throw new IllegalArgumentException("spacing must be finite and positive");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }
    }
}
