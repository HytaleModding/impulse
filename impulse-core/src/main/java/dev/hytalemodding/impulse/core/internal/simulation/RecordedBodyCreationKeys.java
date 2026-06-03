package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Frozen created-body key metadata for one recorded command batch.
 */
public final class RecordedBodyCreationKeys {

    private static final RecordedBodyCreationKeys EMPTY =
        new RecordedBodyCreationKeys(new long[0], new long[0], 0);

    @Nonnull
    private final long[] mostSignificantBits;
    @Nonnull
    private final long[] leastSignificantBits;
    private final int size;

    private RecordedBodyCreationKeys(@Nonnull long[] mostSignificantBits,
        @Nonnull long[] leastSignificantBits,
        int size) {
        this.mostSignificantBits = mostSignificantBits;
        this.leastSignificantBits = leastSignificantBits;
        this.size = size;
    }

    @Nonnull
    public static RecordedBodyCreationKeys empty() {
        return EMPTY;
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    @Nonnull
    public RigidBodyKey bodyKey(int index) {
        checkIndex(index);
        return RigidBodyKey.of(mostSignificantBits[index], leastSignificantBits[index]);
    }

    @Nullable
    public RigidBodyKey singleBodyKey() {
        return size == 1 ? bodyKey(0) : null;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    public static final class Builder {

        private long[] mostSignificantBits = new long[4];
        private long[] leastSignificantBits = new long[4];
        private int size;

        public void add(@Nonnull RigidBodyKey bodyKey) {
            add(bodyKey.mostSignificantBits(), bodyKey.leastSignificantBits());
        }

        void add(long bodyKeyMostSignificantBits,
            long bodyKeyLeastSignificantBits) {
            ensureCapacity(size + 1);
            mostSignificantBits[size] = bodyKeyMostSignificantBits;
            leastSignificantBits[size] = bodyKeyLeastSignificantBits;
            size++;
        }

        public void addAll(@Nonnull long[] bodyKeyMostSignificantBits,
            @Nonnull long[] bodyKeyLeastSignificantBits,
            int count) {
            if (count <= 0) {
                return;
            }
            ensureCapacity(size + count);
            System.arraycopy(bodyKeyMostSignificantBits, 0, mostSignificantBits, size, count);
            System.arraycopy(bodyKeyLeastSignificantBits, 0, leastSignificantBits, size, count);
            size += count;
        }

        @Nonnull
        public RecordedBodyCreationKeys build() {
            if (size == 0) {
                return empty();
            }
            return new RecordedBodyCreationKeys(
                mostSignificantBits.length == size
                    ? mostSignificantBits
                    : Arrays.copyOf(mostSignificantBits, size),
                leastSignificantBits.length == size
                    ? leastSignificantBits
                    : Arrays.copyOf(leastSignificantBits, size),
                size);
        }

        private void ensureCapacity(int required) {
            if (required <= mostSignificantBits.length) {
                return;
            }
            int nextCapacity = Math.max(required,
                mostSignificantBits.length + (mostSignificantBits.length >> 1));
            mostSignificantBits = Arrays.copyOf(mostSignificantBits, nextCapacity);
            leastSignificantBits = Arrays.copyOf(leastSignificantBits, nextCapacity);
        }
    }
}
