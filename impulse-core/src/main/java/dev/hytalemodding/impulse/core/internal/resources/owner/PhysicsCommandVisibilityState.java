package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.internal.simulation.batch.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;

/**
 * Tracks command execution state that controls when command-created bodies are visible to readers.
 */
public final class PhysicsCommandVisibilityState {

    /*
     * Monotonic owner-command sequence assigned at submission. It is separate from owner step
     * sequence and Hytale world ticks because commands can complete between published snapshots.
     */
    private final AtomicLong commandBatchSequence = new AtomicLong();

    /*
     * Highest command batch that has finished owner-lane execution. Snapshot capture copies this
     * value as the frame's last-included command-batch sequence.
     */
    private final AtomicLong completedCommandBatchSequence = new AtomicLong();

    /*
     * Reader-side body materialization can observe command completion before the next published
     * registration frame has applied. Track exact command-created body keys so missing unrelated
     * registrations can still be cleaned up during that publication gap.
     */
    private final Object2LongOpenHashMap<RigidBodyKey> pendingCommandBodyCreationSequences =
        new Object2LongOpenHashMap<>();

    /*
     * Command contexts capture this epoch. Runtime resets and topology replacement increment it so
     * stale pre-reset command batches reject before dispatching against the new world state.
     */
    private final AtomicLong commandWorldEpoch = new AtomicLong();

    /*
     * Topology changes inside a command batch should update snapshot world epoch, but should not
     * make the next same-epoch FIFO command batch stale. This depth suppresses command epoch bumps
     * during command execution.
     */
    private final ThreadLocal<Integer> commandBatchExecutionDepth = ThreadLocal.withInitial(() -> 0);

    public long nextCommandBatchSequence() {
        return commandBatchSequence.incrementAndGet();
    }

    public long commandWorldEpoch() {
        return commandWorldEpoch.get();
    }

    public boolean markWorldChanged() {
        if (commandBatchExecutionDepth.get() != 0) {
            return false;
        }
        commandWorldEpoch.incrementAndGet();
        return true;
    }

    public int enterCommandBatchExecution() {
        int depth = commandBatchExecutionDepth.get();
        commandBatchExecutionDepth.set(depth + 1);
        return depth;
    }

    public void exitCommandBatchExecution(int previousDepth) {
        if (previousDepth == 0) {
            commandBatchExecutionDepth.remove();
        } else {
            commandBatchExecutionDepth.set(previousDepth);
        }
    }

    public void markCommandBatchCompleted(long commandBatchSequence) {
        completedCommandBatchSequence.accumulateAndGet(commandBatchSequence, Math::max);
    }

    public long completedCommandBatchSequence() {
        return completedCommandBatchSequence.get();
    }

    public boolean trackBodyCreationPublication(@Nonnull RecordedPhysicsCommandBatch batch,
        boolean ownerExecutorAttached) {
        if (!batch.hasBodyCreationCommands() || !ownerExecutorAttached) {
            return false;
        }
        long commandBatchSequence = batch.metadata().commandBatchSequence();
        synchronized (pendingCommandBodyCreationSequences) {
            for (int index = 0; index < batch.bodyCreationKeyCount(); index++) {
                RigidBodyKey bodyKey = batch.bodyCreationKey(index);
                pendingCommandBodyCreationSequences.put(bodyKey,
                    Math.max(pendingCommandBodyCreationSequences.getLong(bodyKey),
                        commandBatchSequence));
            }
        }
        return true;
    }

    public void clearBodyCreationPublication(@Nonnull RecordedPhysicsCommandBatch batch) {
        long commandBatchSequence = batch.metadata().commandBatchSequence();
        synchronized (pendingCommandBodyCreationSequences) {
            for (int index = 0; index < batch.bodyCreationKeyCount(); index++) {
                RigidBodyKey bodyKey = batch.bodyCreationKey(index);
                long current = pendingCommandBodyCreationSequences.getLong(bodyKey);
                if (current <= commandBatchSequence) {
                    pendingCommandBodyCreationSequences.removeLong(bodyKey);
                }
            }
        }
    }

    public void applyLastIncludedCommandBatchSequence(long lastIncludedCommandBatchSequence) {
        synchronized (pendingCommandBodyCreationSequences) {
            pendingCommandBodyCreationSequences.object2LongEntrySet()
                .removeIf(entry -> entry.getLongValue() <= lastIncludedCommandBatchSequence);
        }
    }

    public boolean isBodyCreationPending(@Nonnull RigidBodyKey bodyKey,
        boolean directBodyCreationPending) {
        return directBodyCreationPending
            || isCommandBodyCreationPending(bodyKey);
    }

    private boolean isCommandBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        synchronized (pendingCommandBodyCreationSequences) {
            return pendingCommandBodyCreationSequences.containsKey(bodyKey);
        }
    }
}
