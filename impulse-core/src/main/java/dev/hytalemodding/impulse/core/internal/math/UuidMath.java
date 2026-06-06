package dev.hytalemodding.impulse.core.internal.math;

/**
 * Bit helpers for UUID-compatible identifiers.
 *
 * <p>Impulse random physics keys use {@code ThreadLocalRandom} longs because they are non-secret
 * identifiers. This keeps UUID-shaped v4 values without paying for {@code UUID.randomUUID()}'s
 * cryptographically strong generator on runtime allocation paths.</p>
 */
public final class UuidMath {

    // UUID v4 stores the version in bits 12-15 of the most-significant long.
    private static final long UUID_VERSION_MASK = 0xffffffffffff0fffL;
    private static final long UUID_VERSION_4_BITS = 0x0000000000004000L;
    // RFC 4122/IETF UUIDs store variant bits 10xx in the top bits of the least-significant long.
    private static final long UUID_VARIANT_MASK = 0x3fffffffffffffffL;
    private static final long UUID_IETF_VARIANT_BITS = 0x8000000000000000L;

    private UuidMath() {
    }

    public static long version4MostSignificantBits(long randomBits) {
        return (randomBits & UUID_VERSION_MASK) | UUID_VERSION_4_BITS;
    }

    public static long ietfVariantLeastSignificantBits(long randomBits) {
        return (randomBits & UUID_VARIANT_MASK) | UUID_IETF_VARIANT_BITS;
    }
}
