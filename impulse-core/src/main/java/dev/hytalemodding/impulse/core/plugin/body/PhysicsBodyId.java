package dev.hytalemodding.impulse.core.plugin.body;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Stable Impulse-side identity for a physics body.
 *
 * <p>Backend {@code PhysicsBody} handles may change when spaces migrate between
 * backends. This id is the durable handle used by ECS attachments, persistence,
 * snapshots, and physics-owner command queues.</p>
 */
public record PhysicsBodyId(@Nonnull UUID value) {

    public PhysicsBodyId {
    }

    @Nonnull
    public static PhysicsBodyId random() {
        return new PhysicsBodyId(UUID.randomUUID());
    }

    @Nonnull
    public static PhysicsBodyId of(@Nonnull UUID value) {
        return new PhysicsBodyId(value);
    }

    @Nonnull
    @Override
    public String toString() {
        return value.toString();
    }
}
