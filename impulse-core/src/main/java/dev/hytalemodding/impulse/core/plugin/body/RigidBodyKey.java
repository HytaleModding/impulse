package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.core.internal.math.UuidMath;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Stable Impulse-side identity for a physics body.
 *
 * <p>Backend {@code PhysicsBody} handles may change when spaces migrate between
 * backends. This id is the durable handle used by ECS attachments, persistence,
 * snapshots, and physics-owner command queues.</p>
 */
public final class RigidBodyKey {

    private final long mostSignificantBits;
    private final long leastSignificantBits;

    public RigidBodyKey(@Nonnull UUID value) {
        this(Objects.requireNonNull(value, "value").getMostSignificantBits(),
            value.getLeastSignificantBits());
    }

    private RigidBodyKey(long mostSignificantBits,
        long leastSignificantBits) {
        this.mostSignificantBits = mostSignificantBits;
        this.leastSignificantBits = leastSignificantBits;
    }

    @Nonnull
    public static RigidBodyKey random() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long mostSignificantBits = UuidMath.version4MostSignificantBits(random.nextLong());
        long leastSignificantBits = UuidMath.ietfVariantLeastSignificantBits(random.nextLong());
        return new RigidBodyKey(mostSignificantBits, leastSignificantBits);
    }

    @Nonnull
    public static RigidBodyKey of(@Nonnull UUID value) {
        return new RigidBodyKey(value);
    }

    @Nonnull
    public static RigidBodyKey of(long mostSignificantBits,
        long leastSignificantBits) {
        return new RigidBodyKey(mostSignificantBits, leastSignificantBits);
    }

    @Nonnull
    public UUID value() {
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    public long mostSignificantBits() {
        return mostSignificantBits;
    }

    public long leastSignificantBits() {
        return leastSignificantBits;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof RigidBodyKey key
                && mostSignificantBits == key.mostSignificantBits
                && leastSignificantBits == key.leastSignificantBits;
    }

    @Override
    public int hashCode() {
        long bits = mostSignificantBits ^ leastSignificantBits;
        return (int) (bits >> Integer.SIZE) ^ (int) bits;
    }

    @Nonnull
    @Override
    public String toString() {
        return value().toString();
    }
}
