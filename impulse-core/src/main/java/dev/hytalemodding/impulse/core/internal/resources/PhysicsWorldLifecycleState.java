package dev.hytalemodding.impulse.core.internal.resources;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotVisitor;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsCommandVisibilityState;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldSnapshotState.ApplyResult;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandCompletion;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Coordinates world lifecycle state that becomes visible across physics frame boundaries.
 */
public final class PhysicsWorldLifecycleState {

    private final PhysicsWorldSnapshotState snapshotState = new PhysicsWorldSnapshotState();
    private final PhysicsWorldEventState eventState = new PhysicsWorldEventState();
    private final PhysicsCommandVisibilityState commandVisibility = new PhysicsCommandVisibilityState();

    public long worldEpoch() {
        return snapshotState.worldEpoch();
    }

    public long commandWorldEpoch() {
        return commandVisibility.commandWorldEpoch();
    }

    public long nextCommandBatchSequence() {
        return commandVisibility.nextCommandBatchSequence();
    }

    @Nonnull
    public PhysicsEventFrame latestEventFrame() {
        return eventState.getLatestFrame();
    }

    @Nullable
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull RigidBodyKey bodyKey) {
        return snapshotState.getBodySnapshot(bodyKey);
    }

    @Nonnull
    public PhysicsBodySnapshot captureBodySnapshot(@Nonnull PhysicsBodyRegistration registration) {
        return snapshotState.captureBodySnapshot(registration);
    }

    public void putBodySnapshot(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        snapshotState.putBodySnapshot(bodyKey, snapshot, spaceId, kind, persistenceMode);
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrame(
        @Nonnull Collection<PhysicsSpaceBinding> spaces,
        @Nonnull PhysicsBodyRegistry bodyRegistry,
        long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled) {
        return capturePublishedSnapshotFrame(spaces,
            bodyRegistry,
            stepSequence,
            serverTick,
            status,
            stepNanos,
            profilingEnabled,
            List.of(),
            0);
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrame(
        @Nonnull Collection<PhysicsSpaceBinding> spaces,
        @Nonnull PhysicsBodyRegistry bodyRegistry,
        long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled,
        @Nonnull List<PhysicsFrameEvent> physicsEvents,
        int droppedBackendEventCount) {
        PublishedPhysicsSnapshotFrame frame = snapshotState.capturePublishedSnapshotFrame(spaces,
            bodyRegistry,
            stepSequence,
            serverTick,
            commandVisibility.completedCommandBatchSequence(),
            status,
            stepNanos,
            profilingEnabled);
        eventState.publishStepCaptured(frame.worldEpoch(),
            frame,
            physicsEvents,
            droppedBackendEventCount);
        return frame;
    }

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame,
        @Nonnull PhysicsBodyRegistry bodyRegistry,
        long publicationServerTick) {
        ApplyResult result = snapshotState.applyPublishedSnapshotFrame(frame);
        if (result.currentWorldEpoch()) {
            bodyRegistry.applyPublishedRegistrationFrame(frame);
            commandVisibility.applyLastIncludedCommandBatchSequence(
                frame.lastIncludedCommandBatchSequence());
            eventState.publishSnapshotPublication(snapshotState.worldEpoch(),
                frame,
                result.appliedCount(),
                publicationServerTick);
        }
        return result.appliedCount();
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame latestPublishedFrame() {
        return snapshotState.getLatestPublishedFrame();
    }

    public long latestSnapshotAppliedNanos() {
        return snapshotState.getLatestSnapshotAppliedNanos();
    }

    public int bodySnapshotCount() {
        return snapshotState.getBodySnapshotCount();
    }

    public int bodySnapshotCount(@Nonnull SpaceId spaceId) {
        return snapshotState.getBodySnapshotCount(spaceId);
    }

    public int bodySnapshotCellCount() {
        return snapshotState.getBodySnapshotCellCount();
    }

    public void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        snapshotState.forEachBodySnapshot(spaceId, consumer);
    }

    public void forEachIndexedBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        snapshotState.forEachIndexedBodySnapshot(spaceId, visitor);
    }

    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        return snapshotState.forEachBodySnapshotNear(spaceId, center, radius, consumer);
    }

    public int forEachIndexedBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        return snapshotState.forEachIndexedBodySnapshotNear(spaceId, center, radius, visitor);
    }

    public void removeBodySnapshot(@Nonnull RigidBodyKey bodyKey) {
        snapshotState.removeBodySnapshot(bodyKey);
    }

    public void clearBodySnapshots() {
        snapshotState.clearBodySnapshots();
    }

    public boolean isBodyCreationPending(@Nonnull RigidBodyKey bodyKey,
        boolean directBodyCreationPending,
        boolean ownerExecutorAttached) {
        return commandVisibility.isBodyCreationPending(bodyKey, directBodyCreationPending);
    }

    public boolean trackBodyCreationPublication(@Nonnull RecordedPhysicsCommandBatch batch,
        boolean ownerExecutorAttached) {
        return commandVisibility.trackBodyCreationPublication(batch, ownerExecutorAttached);
    }

    public void clearBodyCreationPublication(@Nonnull RecordedPhysicsCommandBatch batch) {
        commandVisibility.clearBodyCreationPublication(batch);
    }

    public void clearBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        commandVisibility.clearBodyCreationPending(bodyKey);
    }

    public void publishDetachedOwnerRegistrationViews(@Nonnull PhysicsBodyRegistry bodyRegistry) {
        bodyRegistry.publishLiveRegistrationViews();
        commandVisibility.applyLastIncludedCommandBatchSequence(
            commandVisibility.completedCommandBatchSequence());
    }

    public void markWorldChanged(@Nonnull PhysicsBodyRegistry bodyRegistry,
        boolean ownerExecutorAttached) {
        snapshotState.markWorldChanged();
        if (!ownerExecutorAttached) {
            bodyRegistry.publishLiveRegistrationViews();
        }
        if (commandVisibility.markWorldChanged()) {
            eventState.publishEmpty(snapshotState.worldEpoch(), snapshotState.getLatestPublishedFrame());
        }
    }

    @Nonnull
    public PhysicsCommandCompletion executeCommandBatch(@Nonnull RecordedPhysicsCommandBatch batch,
        @Nonnull Function<RecordedPhysicsCommandBatch, PhysicsCommandCompletion> executor) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(executor, "executor");
        int depth = commandVisibility.enterCommandBatchExecution();
        try {
            PhysicsCommandCompletion completion = executor.apply(batch);
            commandVisibility.markCommandBatchCompleted(batch.metadata().commandBatchSequence());
            eventState.publishCommandCompletion(snapshotState.worldEpoch(),
                snapshotState.getLatestPublishedFrame(),
                batch,
                completion);
            return completion;
        } finally {
            commandVisibility.exitCommandBatchExecution(depth);
        }
    }
}
