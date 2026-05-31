package dev.hytalemodding.impulse.core.plugin.snapshot;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable async snapshot frame for world-level physics readers.
 *
 * <p>{@code frameEpoch} and {@code worldEpoch} are publication-ordering keys.
 * {@code stepSequence} and {@code serverTick} are correlation metadata and must
 * not be used to decide whether a frame is current for the world topology.</p>
 */
public final class PublishedPhysicsSnapshotFrame {

    public enum Status {
        EMPTY,
        COMPLETE,
        PARTIAL,
        FAILED
    }

    private final long frameEpoch;
    private final long worldEpoch;

    /**
     * Impulse step-scheduler sequence assigned to the owner step
     * not a Hytale tick and not guaranteed contiguous in published frames
     */
    private final long stepSequence;

    /**
     * Hytale world tick observed when the owner step was scheduled.
     * This is not a physics step counter and may diverge from {@code stepSequence} under paused
     * worlds, backpressure, or future multi-rate scheduling
     */
    private final long serverTick;

    /**
     * Latest command-batch sequence whose owner-lane execution completed before this frame was
     * captured.
     * Command completion can precede inclusion in a later captured frame.
     */
    private final long lastIncludedCommandBatchSequence;
    @Nonnull
    private final Status status;
    private final int spatialIndexCellCount;
    private final long stepNanos;
    private final long snapshotNanos;
    @Nullable
    private final PublishedPhysicsBodyFrameStorage bodyStorage;
    @Nullable
    private volatile List<PublishedPhysicsSpaceFrame> spaces;

    public PublishedPhysicsSnapshotFrame(long frameEpoch,
        long worldEpoch,
        long stepSequence,
        long serverTick,
        @Nonnull Status status,
        int spatialIndexCellCount,
        long stepNanos,
        long snapshotNanos,
        @Nonnull List<PublishedPhysicsSpaceFrame> spaces) {
        this(frameEpoch,
            worldEpoch,
            stepSequence,
            serverTick,
            0L,
            status,
            spatialIndexCellCount,
            stepNanos,
            snapshotNanos,
            null,
            copyAndValidateSpaces(frameEpoch, worldEpoch, spaces));
    }

    public PublishedPhysicsSnapshotFrame(long frameEpoch,
        long worldEpoch,
        long stepSequence,
        long serverTick,
        long lastIncludedCommandBatchSequence,
        @Nonnull Status status,
        int spatialIndexCellCount,
        long stepNanos,
        long snapshotNanos,
        @Nonnull List<PublishedPhysicsSpaceFrame> spaces) {
        this(frameEpoch,
            worldEpoch,
            stepSequence,
            serverTick,
            lastIncludedCommandBatchSequence,
            status,
            spatialIndexCellCount,
            stepNanos,
            snapshotNanos,
            null,
            copyAndValidateSpaces(frameEpoch, worldEpoch, spaces));
    }

    private PublishedPhysicsSnapshotFrame(long frameEpoch,
        long worldEpoch,
        long stepSequence,
        long serverTick,
        long lastIncludedCommandBatchSequence,
        @Nonnull Status status,
        int spatialIndexCellCount,
        long stepNanos,
        long snapshotNanos,
        @Nonnull PublishedPhysicsBodyFrameStorage bodyStorage) {
        this(frameEpoch,
            worldEpoch,
            stepSequence,
            serverTick,
            lastIncludedCommandBatchSequence,
            status,
            spatialIndexCellCount,
            stepNanos,
            snapshotNanos,
            bodyStorage,
            null);
    }

    private PublishedPhysicsSnapshotFrame(long frameEpoch,
        long worldEpoch,
        long stepSequence,
        long serverTick,
        long lastIncludedCommandBatchSequence,
        @Nonnull Status status,
        int spatialIndexCellCount,
        long stepNanos,
        long snapshotNanos,
        @Nullable PublishedPhysicsBodyFrameStorage bodyStorage,
        @Nullable List<PublishedPhysicsSpaceFrame> spaces) {
        requireFrameValues(frameEpoch,
            worldEpoch,
            stepSequence,
            serverTick,
            lastIncludedCommandBatchSequence,
            status,
            spatialIndexCellCount,
            stepNanos,
            snapshotNanos);
        this.frameEpoch = frameEpoch;
        this.worldEpoch = worldEpoch;
        this.stepSequence = stepSequence;
        this.serverTick = serverTick;
        this.lastIncludedCommandBatchSequence = lastIncludedCommandBatchSequence;
        this.status = status;
        this.spatialIndexCellCount = spatialIndexCellCount;
        this.stepNanos = stepNanos;
        this.snapshotNanos = snapshotNanos;
        this.bodyStorage = bodyStorage;
        this.spaces = spaces;
    }

    @Nonnull
    public static PublishedPhysicsSnapshotFrame empty(long frameEpoch, long worldEpoch) {
        return new PublishedPhysicsSnapshotFrame(frameEpoch,
            worldEpoch,
            0L,
            0L,
            0L,
            Status.EMPTY,
            0,
            0L,
            0L,
            List.of());
    }

    @Nonnull
    public static Builder compactBuilder(long frameEpoch,
        long worldEpoch,
        long stepSequence,
        long serverTick,
        @Nonnull Status status,
        int spatialIndexCellCount,
        long stepNanos,
        long snapshotNanos,
        int expectedSpaces,
        int expectedBodies) {
        return new Builder(frameEpoch,
            worldEpoch,
            stepSequence,
            serverTick,
            status,
            spatialIndexCellCount,
            stepNanos,
            snapshotNanos,
            expectedSpaces,
            expectedBodies);
    }

    @Nonnull
    public static Builder compactBuilder(long frameEpoch,
        long worldEpoch,
        long stepSequence,
        long serverTick,
        long lastIncludedCommandBatchSequence,
        @Nonnull Status status,
        int spatialIndexCellCount,
        long stepNanos,
        long snapshotNanos,
        int expectedSpaces,
        int expectedBodies) {
        return new Builder(frameEpoch,
            worldEpoch,
            stepSequence,
            serverTick,
            lastIncludedCommandBatchSequence,
            status,
            spatialIndexCellCount,
            stepNanos,
            snapshotNanos,
            expectedSpaces,
            expectedBodies);
    }

    public long frameEpoch() {
        return frameEpoch;
    }

    public long worldEpoch() {
        return worldEpoch;
    }

    public long stepSequence() {
        return stepSequence;
    }

    public long serverTick() {
        return serverTick;
    }

    public long lastIncludedCommandBatchSequence() {
        return lastIncludedCommandBatchSequence;
    }

    /**
     * Returns whether this snapshot frame is known to include owner-lane execution of the
     * submitted command batch sequence.
     *
     * <p>This is the latest owner-executed command-batch sequence included by the captured
     * snapshot. It does not mean a separate ECS visual sync pass has already consumed the frame.
     * Batches at or below this sequence are included in this frame's copied body data.</p>
     */
    public boolean includesCommandBatch(long commandBatchSequence) {
        return commandBatchSequence > 0L
            && lastIncludedCommandBatchSequence >= commandBatchSequence;
    }

    /**
     * Returns the server-tick distance from a submitted command to this snapshot frame, or
     * {@code 0} when this frame has no usable server-tick metadata.
     */
    public long serverTickLatencyFromSubmittedTick(long submittedServerTick) {
        long submitted = Math.max(0L, submittedServerTick);
        if (serverTick <= 0L || serverTick < submitted) {
            return 0L;
        }
        return serverTick - submitted;
    }

    @Nonnull
    public Status status() {
        return status;
    }

    public int spatialIndexCellCount() {
        return spatialIndexCellCount;
    }

    public long stepNanos() {
        return stepNanos;
    }

    public long snapshotNanos() {
        return snapshotNanos;
    }

    @Nonnull
    public List<PublishedPhysicsSpaceFrame> spaces() {
        List<PublishedPhysicsSpaceFrame> currentSpaces = spaces;
        if (currentSpaces != null) {
            return currentSpaces;
        }
        PublishedPhysicsBodyFrameStorage storage = bodyStorage;
        if (storage == null) {
            return List.of();
        }
        List<PublishedPhysicsSpaceFrame> materialized = materializeSpaces(storage, frameEpoch, worldEpoch);
        spaces = materialized;
        return materialized;
    }

    public int spaceCount() {
        PublishedPhysicsBodyFrameStorage storage = bodyStorage;
        return storage != null ? storage.spaceCount() : spaces().size();
    }

    public int bodyCount() {
        PublishedPhysicsBodyFrameStorage storage = bodyStorage;
        if (storage != null) {
            return storage.bodyCount();
        }
        int count = 0;
        List<PublishedPhysicsSpaceFrame> currentSpaces = spaces();
        for (PublishedPhysicsSpaceFrame currentSpace : currentSpaces) {
            count += currentSpace.bodyCount();
        }
        return count;
    }

    public void forEachSpace(@Nonnull Consumer<? super PublishedPhysicsSpaceFrame> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        List<PublishedPhysicsSpaceFrame> currentSpaces = spaces();
        for (PublishedPhysicsSpaceFrame currentSpace : currentSpaces) {
            consumer.accept(currentSpace);
        }
    }

    public void forEachBody(@Nonnull Consumer<? super PublishedPhysicsBodySnapshot> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        PublishedPhysicsBodyFrameStorage storage = bodyStorage;
        if (storage != null) {
            for (int spaceIndex = 0; spaceIndex < storage.spaceCount(); spaceIndex++) {
                int start = storage.spaceBodyStart(spaceIndex);
                int end = start + storage.spaceBodyCount(spaceIndex);
                for (int bodyIndex = start; bodyIndex < end; bodyIndex++) {
                    consumer.accept(storage.bodySnapshot(bodyIndex));
                }
            }
            return;
        }
        List<PublishedPhysicsSpaceFrame> currentSpaces = spaces();
        for (PublishedPhysicsSpaceFrame currentSpace : currentSpaces) {
            currentSpace.forEachBody(consumer);
        }
    }

    public void forEachBodyCursor(@Nonnull Consumer<? super PublishedPhysicsBodySnapshotCursor> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        PublishedPhysicsBodyFrameStorage storage = bodyStorage;
        if (storage != null) {
            storage.forEachBodyCursor(consumer);
            return;
        }
        forEachBody(consumer);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublishedPhysicsSnapshotFrame that)) {
            return false;
        }
        return frameEpoch == that.frameEpoch
            && worldEpoch == that.worldEpoch
            && stepSequence == that.stepSequence
            && serverTick == that.serverTick
            && lastIncludedCommandBatchSequence == that.lastIncludedCommandBatchSequence
            && spatialIndexCellCount == that.spatialIndexCellCount
            && stepNanos == that.stepNanos
            && snapshotNanos == that.snapshotNanos
            && status == that.status
            && spaces().equals(that.spaces());
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(frameEpoch);
        result = 31 * result + Long.hashCode(worldEpoch);
        result = 31 * result + Long.hashCode(stepSequence);
        result = 31 * result + Long.hashCode(serverTick);
        result = 31 * result + Long.hashCode(lastIncludedCommandBatchSequence);
        result = 31 * result + status.hashCode();
        result = 31 * result + Integer.hashCode(spatialIndexCellCount);
        result = 31 * result + Long.hashCode(stepNanos);
        result = 31 * result + Long.hashCode(snapshotNanos);
        result = 31 * result + spaces().hashCode();
        return result;
    }

    @Nonnull
    @Override
    public String toString() {
        return "PublishedPhysicsSnapshotFrame[frameEpoch=" + frameEpoch
            + ", worldEpoch=" + worldEpoch
            + ", stepSequence=" + stepSequence
            + ", serverTick=" + serverTick
            + ", lastIncludedCommandBatchSequence=" + lastIncludedCommandBatchSequence
            + ", status=" + status
            + ", spatialIndexCellCount=" + spatialIndexCellCount
            + ", stepNanos=" + stepNanos
            + ", snapshotNanos=" + snapshotNanos
            + ", spaceCount=" + spaceCount()
            + ", bodyCount=" + bodyCount()
            + ']';
    }

    private static List<PublishedPhysicsSpaceFrame> copyAndValidateSpaces(long frameEpoch,
        long worldEpoch,
        @Nonnull List<PublishedPhysicsSpaceFrame> spaces) {
        List<PublishedPhysicsSpaceFrame> copiedSpaces =
            List.copyOf(Objects.requireNonNull(spaces, "spaces"));
        for (PublishedPhysicsSpaceFrame copiedSpace : copiedSpaces) {
            requireSpaceInFrame(frameEpoch, worldEpoch, copiedSpace);
        }
        return copiedSpaces;
    }

    private static List<PublishedPhysicsSpaceFrame> materializeSpaces(
        @Nonnull PublishedPhysicsBodyFrameStorage storage,
        long frameEpoch,
        long worldEpoch) {
        List<PublishedPhysicsSpaceFrame> materialized = new ArrayList<>(storage.spaceCount());
        for (int spaceIndex = 0; spaceIndex < storage.spaceCount(); spaceIndex++) {
            int start = storage.spaceBodyStart(spaceIndex);
            int count = storage.spaceBodyCount(spaceIndex);
            List<PublishedPhysicsBodySnapshot> bodies = new ArrayList<>(count);
            for (int offset = 0; offset < count; offset++) {
                bodies.add(storage.bodySnapshot(start + offset));
            }
            materialized.add(new PublishedPhysicsSpaceFrame(storage.spaceId(spaceIndex),
                frameEpoch,
                worldEpoch,
                storage.spaceEpoch(spaceIndex),
                bodies));
        }
        return List.copyOf(materialized);
    }

    private static void requireFrameValues(long frameEpoch,
        long worldEpoch,
        long stepSequence,
        long serverTick,
        long lastIncludedCommandBatchSequence,
        @Nonnull Status status,
        int spatialIndexCellCount,
        long stepNanos,
        long snapshotNanos) {
        requireNonNegativeEpoch(frameEpoch, "frameEpoch");
        requireNonNegativeEpoch(worldEpoch, "worldEpoch");
        requireNonNegativeEpoch(stepSequence, "stepSequence");
        requireNonNegativeEpoch(serverTick, "serverTick");
        requireNonNegativeEpoch(lastIncludedCommandBatchSequence, "lastIncludedCommandBatchSequence");
        Objects.requireNonNull(status, "status");
        if (spatialIndexCellCount < 0) {
            throw new IllegalArgumentException("spatialIndexCellCount cannot be negative");
        }
        requireNonNegativeEpoch(stepNanos, "stepNanos");
        requireNonNegativeEpoch(snapshotNanos, "snapshotNanos");
    }

    private static void requireSpaceInFrame(long frameEpoch,
        long worldEpoch,
        @Nonnull PublishedPhysicsSpaceFrame space) {
        if (space.frameEpoch() != frameEpoch) {
            throw new IllegalArgumentException("space frame epoch does not match snapshot frame");
        }
        if (space.worldEpoch() != worldEpoch) {
            throw new IllegalArgumentException("space world epoch does not match snapshot frame");
        }
    }

    private static void requireNonNegativeEpoch(long epoch, @Nonnull String label) {
        if (epoch < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }

    public static final class Builder {

        private final long frameEpoch;
        private final long worldEpoch;
        private final long stepSequence;
        private final long serverTick;
        private final long lastIncludedCommandBatchSequence;
        @Nonnull
        private final Status status;
        private final int spatialIndexCellCount;
        private final long stepNanos;
        private final long snapshotNanos;
        private final PublishedPhysicsBodyFrameStorage.Builder bodyStorage;

        private Builder(long frameEpoch,
            long worldEpoch,
            long stepSequence,
            long serverTick,
            @Nonnull Status status,
            int spatialIndexCellCount,
            long stepNanos,
            long snapshotNanos,
            int expectedSpaces,
            int expectedBodies) {
            this(frameEpoch,
                worldEpoch,
                stepSequence,
                serverTick,
                0L,
                status,
                spatialIndexCellCount,
                stepNanos,
                snapshotNanos,
                expectedSpaces,
                expectedBodies);
        }

        private Builder(long frameEpoch,
            long worldEpoch,
            long stepSequence,
            long serverTick,
            long lastIncludedCommandBatchSequence,
            @Nonnull Status status,
            int spatialIndexCellCount,
            long stepNanos,
            long snapshotNanos,
            int expectedSpaces,
            int expectedBodies) {
            requireFrameValues(frameEpoch,
                worldEpoch,
                stepSequence,
                serverTick,
                lastIncludedCommandBatchSequence,
                status,
                spatialIndexCellCount,
                stepNanos,
                snapshotNanos);
            this.frameEpoch = frameEpoch;
            this.worldEpoch = worldEpoch;
            this.stepSequence = stepSequence;
            this.serverTick = serverTick;
            this.lastIncludedCommandBatchSequence = lastIncludedCommandBatchSequence;
            this.status = status;
            this.spatialIndexCellCount = spatialIndexCellCount;
            this.stepNanos = stepNanos;
            this.snapshotNanos = snapshotNanos;
            this.bodyStorage = PublishedPhysicsBodyFrameStorage.builder(frameEpoch,
                worldEpoch,
                expectedSpaces,
                expectedBodies);
        }

        @Nonnull
        public Builder addSpace(@Nonnull SpaceId spaceId, long spaceEpoch, int bodyCount) {
            bodyStorage.addSpace(spaceId, spaceEpoch, bodyCount);
            return this;
        }

        @Nonnull
        public Builder addBody(@Nonnull RigidBodyKey bodyKey,
            @Nonnull SpaceId spaceId,
            long spaceEpoch,
            long registrationGeneration,
            @Nonnull PhysicsBodyKind kind,
            @Nonnull PhysicsBodyPersistenceMode persistenceMode,
            @Nonnull PhysicsBodySnapshot snapshot) {
            bodyStorage.addBody(bodyKey,
                spaceId,
                spaceEpoch,
                registrationGeneration,
                kind,
                persistenceMode,
                snapshot);
            return this;
        }

        @Nonnull
        public PublishedPhysicsSnapshotFrame build() {
            return new PublishedPhysicsSnapshotFrame(frameEpoch,
                worldEpoch,
                stepSequence,
                serverTick,
                lastIncludedCommandBatchSequence,
                status,
                spatialIndexCellCount,
                stepNanos,
                snapshotNanos,
                bodyStorage.build());
        }
    }
}
