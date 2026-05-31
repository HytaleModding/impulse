package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.internal.simulation.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;

/**
 * Tracks command execution state that controls when command-created bodies are visible to readers.
 */
public final class PhysicsCommandVisibilityState {

    /*
     * Monotonic owner-command sequence assigned at submission. It is separate from worker step
     * sequence and Hytale world ticks because commands can complete between published snapshots.
     */
    private final AtomicLong commandBatchSequence = new AtomicLong();
    /*
     * Highest command batch that has finished owner-thread execution. Snapshot capture copies this
     * value as the frame's last-included command-batch sequence.
     */
    private final AtomicLong completedCommandBatchSequence = new AtomicLong();
    /*
     * Reader-side body materialization can observe command completion before the next published
     * registration frame has applied. Track that gap at command-batch sequence granularity so bulk
     * spawns do not need per-body pending keys.
     */
    private final AtomicLong submittedBodyCreationCommandBatchSequence = new AtomicLong();
    private final AtomicLong appliedLastIncludedCommandBatchSequence = new AtomicLong();
    /*
     * Single-spawn command batches use exact pending keys so control anchors and attached bodies do
     * not make unrelated bodies look pending. Multi-spawn/template paths keep the sequence-level
     * fallback above to avoid high-allocation tracking on stress paths.
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
        boolean workerAttached) {
        if (!batch.hasBodyCreationCommands() || !workerAttached) {
            return false;
        }
        RigidBodyKey singleSpawnBodyKey = batch.singleSpawnBodyKey();
        if (singleSpawnBodyKey == null) {
            submittedBodyCreationCommandBatchSequence.accumulateAndGet(
                batch.metadata().commandBatchSequence(),
                Math::max);
            return true;
        }

        synchronized (pendingCommandBodyCreationSequences) {
            pendingCommandBodyCreationSequences.put(singleSpawnBodyKey,
                Math.max(pendingCommandBodyCreationSequences.getLong(singleSpawnBodyKey),
                    batch.metadata().commandBatchSequence()));
        }
        return true;
    }

    public void clearBodyCreationPublication(@Nonnull RecordedPhysicsCommandBatch batch) {
        RigidBodyKey singleSpawnBodyKey = batch.singleSpawnBodyKey();
        if (singleSpawnBodyKey == null) {
            appliedLastIncludedCommandBatchSequence.accumulateAndGet(
                batch.metadata().commandBatchSequence(),
                Math::max);
            return;
        }
        synchronized (pendingCommandBodyCreationSequences) {
            long current = pendingCommandBodyCreationSequences.getLong(singleSpawnBodyKey);
            if (current <= batch.metadata().commandBatchSequence()) {
                pendingCommandBodyCreationSequences.removeLong(singleSpawnBodyKey);
            }
        }
    }

    public void applyLastIncludedCommandBatchSequence(long lastIncludedCommandBatchSequence) {
        appliedLastIncludedCommandBatchSequence.accumulateAndGet(lastIncludedCommandBatchSequence, Math::max);
        synchronized (pendingCommandBodyCreationSequences) {
            var iterator = pendingCommandBodyCreationSequences.object2LongEntrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getLongValue() <= lastIncludedCommandBatchSequence) {
                    iterator.remove();
                }
            }
        }
    }

    public boolean isBodyCreationPending(@Nonnull RigidBodyKey bodyKey,
        boolean directBodyCreationPending,
        boolean workerAttached) {
        return directBodyCreationPending
            || isCommandBodyCreationPending(bodyKey)
            || hasPendingCommandBodyCreationPublication(workerAttached);
    }

    private boolean isCommandBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        synchronized (pendingCommandBodyCreationSequences) {
            return pendingCommandBodyCreationSequences.containsKey(bodyKey);
        }
    }

    private boolean hasPendingCommandBodyCreationPublication(boolean workerAttached) {
        /*
         * This is intentionally conservative: any unapplied body-creation batch keeps new-body
         * readers from assuming that a missing published registration view means the body is absent.
         */
        return workerAttached
            && submittedBodyCreationCommandBatchSequence.get()
                > appliedLastIncludedCommandBatchSequence.get();
    }
}
