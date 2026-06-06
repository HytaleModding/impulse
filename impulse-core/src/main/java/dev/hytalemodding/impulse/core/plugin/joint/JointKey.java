package dev.hytalemodding.impulse.core.plugin.joint;

import dev.hytalemodding.impulse.core.internal.math.UuidMath;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Stable Impulse-side identity for a physics joint.
 *
 * <p>Backend {@code PhysicsJoint} handles are live owner-lane objects. This id is the handle
 * component state and plugin-facing lifecycle code should retain.</p>
 */
public final class JointKey {

    private final long mostSignificantBits;
    private final long leastSignificantBits;

    public JointKey(@Nonnull UUID value) {
        this(Objects.requireNonNull(value, "value").getMostSignificantBits(),
            value.getLeastSignificantBits());
    }

    private JointKey(long mostSignificantBits,
        long leastSignificantBits) {
        this.mostSignificantBits = mostSignificantBits;
        this.leastSignificantBits = leastSignificantBits;
    }

    @Nonnull
    public static JointKey random() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long mostSignificantBits = UuidMath.version4MostSignificantBits(random.nextLong());
        long leastSignificantBits = UuidMath.ietfVariantLeastSignificantBits(random.nextLong());
        return new JointKey(mostSignificantBits, leastSignificantBits);
    }

    @Nonnull
    public static JointKey of(@Nonnull UUID value) {
        return new JointKey(value);
    }

    @Nonnull
    public static JointKey of(long mostSignificantBits,
        long leastSignificantBits) {
        return new JointKey(mostSignificantBits, leastSignificantBits);
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
            || other instanceof JointKey key
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
