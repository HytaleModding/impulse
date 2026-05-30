package dev.hytalemodding.impulse.core.plugin.events;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import javax.annotation.Nullable;

/**
 * Value-only owner-thread outcome for one submitted physics command batch.
 *
 * @param commandBatchSequence owner FIFO sequence assigned when the command batch was submitted
 * @param submittedServerTick server tick supplied by the caller when the batch was recorded
 * @param ownerCompletedNanoTime monotonic nano time sampled when the owner-thread command-batch
 *     outcome event was published, or {@code 0} when unavailable
 * @param commandCount number of recorded command operations in the batch
 * @param bodyKeyReferenceCount number of body-key references copied from recorded operations
 * @param firstBodyKey first copied body key seen in recorded order, if any
 * @param jointKeyReferenceCount number of joint-key references copied from recorded operations
 * @param firstJointKey first copied joint key seen in recorded order, if any
 * @param liveOwnerTransactionCount number of opaque live-owner transactions in the batch
 * @param allApplied whether every command operation in the batch applied successfully
 * @param firstRejectedCommandSequence one-based command sequence for the first rejection, or
 *     {@code 0} when no rejection is known
 * @param firstRejectedMessage copied rejection reason for the first rejection, if any
 */
public record PhysicsCommandBatchEvent(long commandBatchSequence,
                                       long submittedServerTick,
                                       long ownerCompletedNanoTime,
                                       int commandCount,
                                       int bodyKeyReferenceCount,
                                       @Nullable RigidBodyKey firstBodyKey,
                                       int jointKeyReferenceCount,
                                       @Nullable JointKey firstJointKey,
                                       int liveOwnerTransactionCount,
                                       boolean allApplied,
                                       long firstRejectedCommandSequence,
                                       @Nullable String firstRejectedMessage) {

    public PhysicsCommandBatchEvent(long commandBatchSequence,
        long submittedServerTick,
        long ownerCompletedNanoTime,
        int commandCount,
        boolean allApplied,
        long firstRejectedCommandSequence,
        @Nullable String firstRejectedMessage) {
        this(commandBatchSequence,
            submittedServerTick,
            ownerCompletedNanoTime,
            commandCount,
            0,
            null,
            0,
            null,
            0,
            allApplied,
            firstRejectedCommandSequence,
            firstRejectedMessage);
    }

    public PhysicsCommandBatchEvent(long commandBatchSequence,
        long submittedServerTick,
        int commandCount,
        boolean allApplied,
        long firstRejectedCommandSequence,
        @Nullable String firstRejectedMessage) {
        this(commandBatchSequence,
            submittedServerTick,
            0L,
            commandCount,
            0,
            null,
            0,
            null,
            0,
            allApplied,
            firstRejectedCommandSequence,
            firstRejectedMessage);
    }

    public PhysicsCommandBatchEvent {
        commandBatchSequence = Math.max(0L, commandBatchSequence);
        submittedServerTick = Math.max(0L, submittedServerTick);
        ownerCompletedNanoTime = Math.max(0L, ownerCompletedNanoTime);
        commandCount = Math.max(0, commandCount);
        bodyKeyReferenceCount = Math.max(0, bodyKeyReferenceCount);
        if (bodyKeyReferenceCount == 0) {
            firstBodyKey = null;
        }
        jointKeyReferenceCount = Math.max(0, jointKeyReferenceCount);
        if (jointKeyReferenceCount == 0) {
            firstJointKey = null;
        }
        liveOwnerTransactionCount = Math.max(0, liveOwnerTransactionCount);
        firstRejectedCommandSequence = Math.max(0L, firstRejectedCommandSequence);
        if (allApplied) {
            firstRejectedCommandSequence = 0L;
            firstRejectedMessage = null;
        }
    }

    public boolean hasOwnerCompletionTimestamp() {
        return ownerCompletedNanoTime > 0L;
    }

    public boolean referencesBodies() {
        return bodyKeyReferenceCount > 0;
    }

    public boolean referencesJoints() {
        return jointKeyReferenceCount > 0;
    }

    public boolean hasLiveOwnerTransactions() {
        return liveOwnerTransactionCount > 0;
    }
}
