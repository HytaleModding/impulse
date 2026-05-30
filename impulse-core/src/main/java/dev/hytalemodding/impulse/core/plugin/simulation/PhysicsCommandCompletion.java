package dev.hytalemodding.impulse.core.plugin.simulation;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.RandomAccess;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Completion summary for a submitted physics command batch.
 *
 * <p>All-applied and all-rejected summaries may expose lightweight generated result lists instead
 * of storing one result object per command. Code that only needs status should prefer
 * {@link #allApplied()} and {@link #firstRejected()}.</p>
 */
public final class PhysicsCommandCompletion {

    @Nonnull
    private final List<PhysicsCommandResult> results;
    private final boolean allApplied;
    @Nullable
    private final PhysicsCommandResult firstRejected;

    private PhysicsCommandCompletion(@Nonnull List<PhysicsCommandResult> results,
        boolean allApplied,
        @Nullable PhysicsCommandResult firstRejected) {
        this.results = Objects.requireNonNull(results, "results");
        this.allApplied = allApplied;
        this.firstRejected = firstRejected;
    }

    @Nonnull
    public static PhysicsCommandCompletion of(@Nonnull List<PhysicsCommandResult> results) {
        List<PhysicsCommandResult> copied = List.copyOf(results);
        PhysicsCommandResult firstRejected = firstRejected(copied);
        return new PhysicsCommandCompletion(copied, firstRejected == null, firstRejected);
    }

    @Nonnull
    public static PhysicsCommandCompletion allApplied(@Nonnull PhysicsCommandMetadata metadata,
        int commandCount) {
        int size = Math.max(0, commandCount);
        List<PhysicsCommandResult> results = size == 0
            ? List.of()
            : new AppliedCommandResultList(metadata, size);
        return new PhysicsCommandCompletion(results, true, null);
    }

    @Nonnull
    public static PhysicsCommandCompletion allRejected(@Nonnull PhysicsCommandMetadata metadata,
        int commandCount,
        @Nonnull String message) {
        int size = Math.max(0, commandCount);
        if (size == 0) {
            return new PhysicsCommandCompletion(List.of(), false, null);
        }
        String copiedMessage = Objects.requireNonNull(message, "message");
        List<PhysicsCommandResult> results = new RejectedCommandResultList(metadata,
            size,
            copiedMessage);
        return new PhysicsCommandCompletion(results,
            false,
            PhysicsCommandResult.rejected(metadata, 1L, copiedMessage));
    }

    @Nonnull
    public List<PhysicsCommandResult> results() {
        return results;
    }

    public boolean allApplied() {
        return allApplied;
    }

    @Nonnull
    public Optional<PhysicsCommandResult> firstRejected() {
        return Optional.ofNullable(firstRejected);
    }

    @Nullable
    private static PhysicsCommandResult firstRejected(@Nonnull List<PhysicsCommandResult> results) {
        for (PhysicsCommandResult result : results) {
            if (result.status() == PhysicsCommandResult.Status.REJECTED) {
                return result;
            }
        }
        return null;
    }

    private static final class AppliedCommandResultList extends AbstractList<PhysicsCommandResult>
        implements RandomAccess {

        @Nonnull
        private final PhysicsCommandMetadata metadata;
        private final int size;

        private AppliedCommandResultList(@Nonnull PhysicsCommandMetadata metadata, int size) {
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            this.size = size;
        }

        @Nonnull
        @Override
        public PhysicsCommandResult get(int index) {
            Objects.checkIndex(index, size);
            return PhysicsCommandResult.applied(metadata, index + 1L);
        }

        @Override
        public int size() {
            return size;
        }
    }

    private static final class RejectedCommandResultList extends AbstractList<PhysicsCommandResult>
        implements RandomAccess {

        @Nonnull
        private final PhysicsCommandMetadata metadata;
        private final int size;
        @Nonnull
        private final String message;

        private RejectedCommandResultList(@Nonnull PhysicsCommandMetadata metadata,
            int size,
            @Nonnull String message) {
            this.metadata = Objects.requireNonNull(metadata, "metadata");
            this.size = size;
            this.message = Objects.requireNonNull(message, "message");
        }

        @Nonnull
        @Override
        public PhysicsCommandResult get(int index) {
            Objects.checkIndex(index, size);
            return PhysicsCommandResult.rejected(metadata, index + 1L, message);
        }

        @Override
        public int size() {
            return size;
        }
    }
}
