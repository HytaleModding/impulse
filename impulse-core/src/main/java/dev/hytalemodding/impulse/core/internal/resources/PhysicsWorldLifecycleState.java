package dev.hytalemodding.impulse.core.internal.resources;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotVisitor;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldSnapshotState.ApplyResult;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Coordinates world lifecycle state that becomes visible across physics frame boundaries.
 */
public final class PhysicsWorldLifecycleState {

    private final PhysicsWorldSnapshotState snapshotState = new PhysicsWorldSnapshotState();
    private final PhysicsWorldEventState eventState = new PhysicsWorldEventState();

    public long worldEpoch() {
        return snapshotState.worldEpoch();
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

    public void publishDetachedOwnerRegistrationViews(@Nonnull PhysicsBodyRegistry bodyRegistry) {
        bodyRegistry.publishLiveRegistrationViews();
    }

    public void markWorldChanged(@Nonnull PhysicsBodyRegistry bodyRegistry,
        boolean ownerExecutorAttached) {
        snapshotState.markWorldChanged();
        if (!ownerExecutorAttached) {
            bodyRegistry.publishLiveRegistrationViews();
        }
        eventState.publishEmpty(snapshotState.worldEpoch(), snapshotState.getLatestPublishedFrame());
    }
}
