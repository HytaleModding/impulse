package dev.hytalemodding.impulse.core.internal.resources.snapshot;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotStore;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSpaceFrame;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Snapshot and epoch state for a world physics resource.
 *
 * <p>The worker-side store is used while capturing immutable frames on the physics owner. The
 * reader-side store is only updated when a frame is applied for the current world epoch. Keeping
 * both stores and the epoch counters together prevents stale worker frames from repopulating
 * world-thread snapshots after topology changes.</p>
 */
public final class PhysicsWorldSnapshotState {

    private final PhysicsBodySnapshotStore bodySnapshots = new PhysicsBodySnapshotStore();
    private final PhysicsBodySnapshotStore workerBodySnapshots = new PhysicsBodySnapshotStore();
    private final AtomicLong worldEpoch = new AtomicLong();
    private final AtomicLong snapshotFrameEpoch = new AtomicLong();
    @Nonnull
    private final AtomicReference<PublishedPhysicsSnapshotFrame> latestPublishedFrame =
        new AtomicReference<>(PublishedPhysicsSnapshotFrame.empty(0L, 0L));
    private volatile long latestSnapshotAppliedNanos;

    @Nullable
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBodyId bodyId) {
        return bodySnapshots.get(bodyId);
    }

    @Nonnull
    public PhysicsBodySnapshot captureBodySnapshot(
        @Nonnull PhysicsWorldResource.BodyRegistration registration) {
        PhysicsBody body = registration.body();
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshot.from(body);
        putBodySnapshot(registration.id(),
            snapshot,
            registration.spaceId(),
            registration.kind(),
            registration.persistenceMode());
        return snapshot;
    }

    public void putBodySnapshot(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        bodySnapshots.put(bodyId, snapshot, spaceId, kind, persistenceMode);
        workerBodySnapshots.put(bodyId, snapshot, spaceId, kind, persistenceMode);
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrame(
        @Nonnull Collection<PhysicsSpace> spaces,
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
        workerBodySnapshots.refresh(spaces, bodyRegistry);
        int spatialIndexCellCount = workerBodySnapshots.cellCount();

        List<PublishedPhysicsSpaceFrame> spaceFrames = new ArrayList<>(spaces.size());
        for (PhysicsSpace space : spaces) {
            SpaceId spaceId = space.getId();
            List<PublishedPhysicsBodySnapshot> bodyFrames = new ArrayList<>(
                workerBodySnapshots.bodyCount(spaceId));
            workerBodySnapshots.forEach(spaceId, entry -> bodyFrames.add(
                PublishedPhysicsBodySnapshot.from(entry.bodyId(),
                    entry.spaceId(),
                    frameEpoch,
                    frameWorldEpoch,
                    frameWorldEpoch,
                    frameWorldEpoch,
                    entry.kind(),
                    entry.persistenceMode(),
                    entry.snapshot())));
            spaceFrames.add(new PublishedPhysicsSpaceFrame(spaceId,
                frameEpoch,
                frameWorldEpoch,
                frameWorldEpoch,
                bodyFrames));
        }

        long snapshotNanos = profilingEnabled ? System.nanoTime() - snapshotStartNanos : 0L;
        PublishedPhysicsSnapshotFrame frame = new PublishedPhysicsSnapshotFrame(frameEpoch,
            frameWorldEpoch,
            stepSequence,
            serverTick,
            status,
            spatialIndexCellCount,
            stepNanos,
            snapshotNanos,
            spaceFrames);
        publishLatestFrameIfWorldCurrent(frame);
        return frame;
    }

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame,
        @Nonnull PhysicsBodyRegistry bodyRegistry) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(bodyRegistry, "bodyRegistry");
        if (frame.worldEpoch() != worldEpoch.get()) {
            return 0;
        }

        bodySnapshots.clear();
        int applied = 0;
        for (PublishedPhysicsSpaceFrame spaceFrame : frame.spaces()) {
            for (PublishedPhysicsBodySnapshot bodyFrame : spaceFrame.bodies()) {
                PhysicsWorldResource.BodyRegistration registration =
                    bodyRegistry.getRegistration(bodyFrame.bodyId());
                if (registration == null || !registration.spaceId().equals(bodyFrame.spaceId())) {
                    continue;
                }
                PhysicsBodySnapshot snapshot = new PhysicsBodySnapshot(bodyFrame.position(),
                    bodyFrame.rotation(),
                    bodyFrame.linearVelocity(),
                    bodyFrame.angularVelocity(),
                    bodyFrame.bodyType(),
                    bodyFrame.sleeping(),
                    bodyFrame.sensor(),
                    bodyFrame.centerOfMassOffsetY(),
                    bodyFrame.shapeType(),
                    bodyFrame.boxHalfExtents(),
                    bodyFrame.sphereRadius(),
                    bodyFrame.halfHeight(),
                    bodyFrame.shapeAxis(),
                    bodyFrame.planeGroundY());
                bodySnapshots.put(bodyFrame.bodyId(),
                    snapshot,
                    bodyFrame.spaceId(),
                    registration.kind(),
                    registration.persistenceMode());
                applied++;
            }
        }
        latestSnapshotAppliedNanos = System.nanoTime();
        return applied;
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame getLatestPublishedFrame() {
        return latestPublishedFrame.get();
    }

    public long getLatestSnapshotAppliedNanos() {
        return latestSnapshotAppliedNanos;
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
        @Nonnull Consumer<PhysicsWorldResource.BodySnapshotEntry> consumer) {
        bodySnapshots.forEach(spaceId, consumer);
    }

    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsWorldResource.BodySnapshotEntry> consumer) {
        return bodySnapshots.forEachNear(spaceId, center, radius, consumer);
    }

    public void removeBodySnapshot(@Nonnull PhysicsBodyId bodyId) {
        bodySnapshots.remove(bodyId);
        workerBodySnapshots.remove(bodyId);
    }

    public void clearBodySnapshots() {
        bodySnapshots.clear();
        workerBodySnapshots.clear();
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
