package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandBatch;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandMetadata;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Internal frozen command payload plus the public metadata view returned to plugins.
 */
public final class RecordedPhysicsCommandBatch {

    @Nonnull
    private final PhysicsCommandMetadata metadata;
    private final long commandWorldEpoch;
    @Nonnull
    private final PhysicsCommandOperations operations;
    @Nonnull
    private final PhysicsCommandBatch publicBatch;
    private final boolean bodyCreationCommands;
    private final int bodyKeyReferenceCount;
    @Nullable
    private final RigidBodyKey firstBodyKey;
    private final int jointKeyReferenceCount;
    @Nullable
    private final JointKey firstJointKey;
    private final int liveOwnerTransactionCount;

    RecordedPhysicsCommandBatch(@Nonnull PhysicsCommandMetadata metadata,
        long commandWorldEpoch,
        @Nonnull PhysicsCommandOperations operations) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.commandWorldEpoch = Math.max(0L, commandWorldEpoch);
        this.operations = Objects.requireNonNull(operations, "operations");
        this.publicBatch = new PhysicsCommandBatch(metadata, operations.size());
        this.bodyCreationCommands = operations.hasBodyCreationCommands();
        PhysicsCommandOperations.EntityReferences references = operations.entityReferences();
        this.bodyKeyReferenceCount = references.bodyKeyReferenceCount();
        this.firstBodyKey = references.firstBodyKey();
        this.jointKeyReferenceCount = references.jointKeyReferenceCount();
        this.firstJointKey = references.firstJointKey();
        this.liveOwnerTransactionCount = references.liveOwnerTransactionCount();
    }

    @Nonnull
    public PhysicsCommandMetadata metadata() {
        return metadata;
    }

    long commandWorldEpoch() {
        return commandWorldEpoch;
    }

    int commandCount() {
        return operations.size();
    }

    @Nonnull
    PhysicsCommandOperations operations() {
        return operations;
    }

    @Nonnull
    public PhysicsCommandBatch publicBatch() {
        return publicBatch;
    }

    /**
     * Returns whether the frozen batch can create rigid bodies.
     *
     * <p>This is a batch-level flag, not a per-body-key list. It lets the reader side guard the
     * publication gap between owner completion and registration-view snapshot application without
     * allocating one tracking object per spawned body.</p>
     */
    public boolean hasBodyCreationCommands() {
        return bodyCreationCommands;
    }

    public int bodyKeyReferenceCount() {
        return bodyKeyReferenceCount;
    }

    @Nullable
    public RigidBodyKey firstBodyKey() {
        return firstBodyKey;
    }

    public int jointKeyReferenceCount() {
        return jointKeyReferenceCount;
    }

    @Nullable
    public JointKey firstJointKey() {
        return firstJointKey;
    }

    public int liveOwnerTransactionCount() {
        return liveOwnerTransactionCount;
    }
}
