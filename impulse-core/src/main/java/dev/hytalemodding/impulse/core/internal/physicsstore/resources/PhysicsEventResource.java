package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsStepEvent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Latest copied PhysicsStore event frame for EntityStore publication and diagnostics.
 */
public final class PhysicsEventResource implements Resource<PhysicsStore> {

    @Nonnull
    private volatile PhysicsEventFrame latestFrame = PhysicsEventFrame.empty(0L);
    private long nextFrameSequence;

    public PhysicsEventResource() {
    }

    @Nonnull
    public PhysicsEventFrame getLatestFrame() {
        return latestFrame;
    }

    @Nonnull
    public PhysicsEventFrame publishStepFrame(long snapshotSequence,
        long serverTick,
        int bodyCount,
        long stepNanos,
        long snapshotNanos,
        @Nonnull List<PhysicsFrameEvent> physicsEvents,
        int droppedBackendEventCount) {
        long safeSnapshotSequence = Math.max(0L, snapshotSequence);
        PhysicsStepEvent stepEvent = new PhysicsStepEvent(safeSnapshotSequence,
            serverTick,
            safeSnapshotSequence,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            bodyCount,
            stepNanos,
            snapshotNanos);
        PhysicsEventFrame frame = new PhysicsEventFrame(++nextFrameSequence,
            safeSnapshotSequence,
            safeSnapshotSequence,
            safeSnapshotSequence,
            serverTick,
            List.of(stepEvent),
            List.of(),
            Objects.requireNonNull(physicsEvents, "physicsEvents"),
            droppedBackendEventCount);
        latestFrame = frame;
        return frame;
    }

    public void clear() {
        latestFrame = PhysicsEventFrame.empty(0L);
        nextFrameSequence = 0L;
    }

    @Nonnull
    @Override
    public PhysicsEventResource clone() {
        PhysicsEventResource copy = new PhysicsEventResource();
        copy.latestFrame = latestFrame;
        copy.nextFrameSequence = nextFrameSequence;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsEventResource> getResourceType() {
        return PhysicsStoreTypes.eventResourceType();
    }
}
