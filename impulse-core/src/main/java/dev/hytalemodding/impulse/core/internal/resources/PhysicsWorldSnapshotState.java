package dev.hytalemodding.impulse.core.internal.resources;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotVisitor;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotStore;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * Snapshot and epoch state for a world physics resource.
 *
 * <p>The owner-side store is used while capturing immutable frames on the physics owner. The
 * reader-side store is only updated when a frame is applied for the current world epoch. Keeping
 * both stores and the epoch counters together prevents stale owner frames from repopulating
 * world-thread snapshots after topology changes.</p>
 */
public final class PhysicsWorldSnapshotState {

    private final PhysicsBodySnapshotStore bodySnapshots = new PhysicsBodySnapshotStore();
    private final PhysicsBodySnapshotStore ownerBodySnapshots = new PhysicsBodySnapshotStore();
    private final AtomicLong worldEpoch = new AtomicLong();
    private final AtomicLong snapshotFrameEpoch = new AtomicLong();
    @Nonnull
    private final AtomicReference<PublishedPhysicsSnapshotFrame> latestPublishedFrame =
        new AtomicReference<>(PublishedPhysicsSnapshotFrame.empty(0L, 0L));
    @Getter
    private volatile long latestSnapshotAppliedNanos;

    @Nullable
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull RigidBodyKey bodyKey) {
        return bodySnapshots.get(bodyKey);
    }

    @Nonnull
    public PhysicsBodySnapshot captureBodySnapshot(
        @Nonnull PhysicsBodyRegistration registration) {
        PhysicsBodySnapshot snapshot = bodySnapshots.get(registration.bodyKey());
        if (snapshot == null) {
            throw new IllegalStateException("No physics body snapshot is available for " + registration.bodyKey());
        }
        return snapshot;
    }

    public void putBodySnapshot(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        bodySnapshots.put(bodyKey, snapshot, spaceId, kind, persistenceMode);
        ownerBodySnapshots.put(bodyKey, snapshot, spaceId, kind, persistenceMode);
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
        Objects.requireNonNull(spaces, "spaces");
        Objects.requireNonNull(bodyRegistry, "bodyRegistry");
        Objects.requireNonNull(status, "status");

        long frameEpoch = snapshotFrameEpoch.incrementAndGet();
        long frameWorldEpoch = worldEpoch.get();
        long snapshotStartNanos = profilingEnabled ? System.nanoTime() : 0L;
        ownerBodySnapshots.refresh(spaces, bodyRegistry);
        int spatialIndexCellCount = ownerBodySnapshots.cellCount();

        int bodyCount = 0;
        for (PhysicsSpaceBinding space : spaces) {
            bodyCount += ownerBodySnapshots.bodyCount(space.spaceId());
        }

        long snapshotNanos = profilingEnabled ? System.nanoTime() - snapshotStartNanos : 0L;
        PublishedPhysicsSnapshotFrame.Builder frameBuilder = PublishedPhysicsSnapshotFrame.compactBuilder(frameEpoch,
            frameWorldEpoch,
            stepSequence,
            serverTick,
            status,
            spatialIndexCellCount,
            stepNanos,
            snapshotNanos,
            spaces.size(),
            bodyCount);
        for (PhysicsSpaceBinding space : spaces) {
            SpaceId spaceId = space.spaceId();
            int spaceBodyCount = ownerBodySnapshots.bodyCount(spaceId);
            frameBuilder.addSpace(spaceId, frameWorldEpoch, spaceBodyCount);
            ownerBodySnapshots.forEachIndexed(spaceId,
                (bodyKey, snapshot, bodySpaceId, kind, persistenceMode) -> frameBuilder.addBody(bodyKey,
                    bodySpaceId,
                    frameWorldEpoch,
                    frameWorldEpoch,
                    kind,
                    persistenceMode,
                    snapshot));
        }
        PublishedPhysicsSnapshotFrame frame = frameBuilder.build();
        publishLatestFrameIfWorldCurrent(frame);
        return frame;
    }

    @Nonnull
    public ApplyResult applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.worldEpoch() != worldEpoch.get()) {
            return new ApplyResult(0, false);
        }

        PhysicsBodySnapshotStore.ApplyStats stats =
            bodySnapshots.applyPublishedFrame(frame);
        latestSnapshotAppliedNanos = System.nanoTime();
        return new ApplyResult(stats.applied(), true);
    }

    public record ApplyResult(int appliedCount, boolean currentWorldEpoch) {
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame getLatestPublishedFrame() {
        return latestPublishedFrame.get();
    }

    public long worldEpoch() {
        return worldEpoch.get();
    }

    public int getBodySnapshotCount() {
        return bodySnapshots.bodyCount();
    }

    public int getBodySnapshotCount(@Nonnull SpaceId spaceId) {
        return bodySnapshots.bodyCount(spaceId);
    }

    public int getBodySnapshotCellCount() {
        return bodySnapshots.cellCount();
    }

    public void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        bodySnapshots.forEach(spaceId, consumer);
    }

    public void forEachIndexedBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        bodySnapshots.forEachIndexed(spaceId, visitor);
    }

    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        return bodySnapshots.forEachNear(spaceId, center, radius, consumer);
    }

    public int forEachIndexedBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        return bodySnapshots.forEachIndexedNear(spaceId, center, radius, visitor);
    }

    public void removeBodySnapshot(@Nonnull RigidBodyKey bodyKey) {
        bodySnapshots.remove(bodyKey);
        ownerBodySnapshots.remove(bodyKey);
    }

    public void clearBodySnapshots() {
        bodySnapshots.clear();
        ownerBodySnapshots.clear();
    }

    public void markWorldChanged() {
        long newWorldEpoch = worldEpoch.incrementAndGet();
        publishLatestFrame(PublishedPhysicsSnapshotFrame.empty(snapshotFrameEpoch.get(), newWorldEpoch));
    }

    private void publishLatestFrameIfWorldCurrent(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        publishLatestFrame(frame, true);
    }

    private void publishLatestFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        publishLatestFrame(frame, false);
    }

    private void publishLatestFrame(@Nonnull PublishedPhysicsSnapshotFrame frame,
        boolean requireCurrentWorldEpoch) {
        latestPublishedFrame.updateAndGet(current -> {
            if (requireCurrentWorldEpoch && frame.worldEpoch() != worldEpoch.get()) {
                return current;
            }
            return isNewerFrame(frame, current) ? frame : current;
        });
    }

    private static boolean isNewerFrame(@Nonnull PublishedPhysicsSnapshotFrame candidate,
        @Nonnull PublishedPhysicsSnapshotFrame current) {
        if (candidate.worldEpoch() != current.worldEpoch()) {
            return candidate.worldEpoch() > current.worldEpoch();
        }
        return candidate.frameEpoch() >= current.frameEpoch();
    }
}
