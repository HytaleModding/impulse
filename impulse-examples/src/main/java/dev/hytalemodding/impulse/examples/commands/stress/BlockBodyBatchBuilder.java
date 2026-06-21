package dev.hytalemodding.impulse.examples.commands.stress;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

final class BlockBodyBatchBuilder {

    private static final int POSITION_STRIDE = 3;

    private final long bodyUuidRunId = UUID.randomUUID().getMostSignificantBits();
    private long[] bodyUuidMostSignificantBits;
    private long[] bodyUuidLeastSignificantBits;
    private float[] positions;
    private int size;
    private boolean sealed;

    BlockBodyBatchBuilder(int expectedBodies) {
        int capacity = Math.max(1, expectedBodies);
        bodyUuidMostSignificantBits = new long[capacity];
        bodyUuidLeastSignificantBits = new long[capacity];
        positions = new float[capacity * POSITION_STRIDE];
    }

    @Nonnull
    BlockBodyBatchBuilder addBody(float positionX,
        float positionY,
        float positionZ) {
        return addBody(bodyUuidRunId,
            size + 1L,
            positionX,
            positionY,
            positionZ);
    }

    @Nonnull
    BlockBodyBatchBuilder addBody(long bodyUuidMostSignificantBits,
        long bodyUuidLeastSignificantBits,
        float positionX,
        float positionY,
        float positionZ) {
        assertMutable();
        ensureCapacity(size + 1);
        this.bodyUuidMostSignificantBits[size] = bodyUuidMostSignificantBits;
        this.bodyUuidLeastSignificantBits[size] = bodyUuidLeastSignificantBits;
        int positionOffset = size * POSITION_STRIDE;
        positions[positionOffset] = positionX;
        positions[positionOffset + 1] = positionY;
        positions[positionOffset + 2] = positionZ;
        size++;
        return this;
    }

    void seal() {
        sealed = true;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    @Nonnull
    UUID bodyUuid(int index) {
        checkIndex(index);
        return new UUID(bodyUuidMostSignificantBits[index],
            bodyUuidLeastSignificantBits[index]);
    }

    float positionX(int index) {
        return position(index, 0);
    }

    float positionY(int index) {
        return position(index, 1);
    }

    float positionZ(int index) {
        return position(index, 2);
    }

    private float position(int index, int slot) {
        checkIndex(index);
        return positions[index * POSITION_STRIDE + slot];
    }

    private void ensureCapacity(int required) {
        if (required <= bodyUuidMostSignificantBits.length) {
            return;
        }
        int nextCapacity = Math.max(required,
            bodyUuidMostSignificantBits.length + (bodyUuidMostSignificantBits.length >> 1) + 1);
        bodyUuidMostSignificantBits = Arrays.copyOf(bodyUuidMostSignificantBits, nextCapacity);
        bodyUuidLeastSignificantBits = Arrays.copyOf(bodyUuidLeastSignificantBits, nextCapacity);
        positions = Arrays.copyOf(positions, nextCapacity * POSITION_STRIDE);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }

    private void assertMutable() {
        if (sealed) {
            throw new IllegalStateException("Block body batch builder is already sealed");
        }
    }
}
