package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import javax.annotation.Nonnull;

/**
 * Runtime-only PhysicsStore profiling counters split by phase.
 */
public final class PhysicsProfilingResource implements Resource<PhysicsStore> {

    private boolean enabled;
    private long requestDrainNanos;
    private long bindingNanos;
    private long snapshotNanos;
    private long persistenceCaptureNanos;
    private long stepSubmitNanos;
    private int queuedRequests;
    private int publishedBodies;

    public PhysicsProfilingResource() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void recordLatest(long requestDrainNanos,
        long bindingNanos,
        long snapshotNanos,
        long persistenceCaptureNanos,
        long stepSubmitNanos,
        int queuedRequests,
        int publishedBodies) {
        this.requestDrainNanos = requestDrainNanos;
        this.bindingNanos = bindingNanos;
        this.snapshotNanos = snapshotNanos;
        this.persistenceCaptureNanos = persistenceCaptureNanos;
        this.stepSubmitNanos = stepSubmitNanos;
        this.queuedRequests = queuedRequests;
        this.publishedBodies = publishedBodies;
    }

    public long getRequestDrainNanos() {
        return requestDrainNanos;
    }

    public long getBindingNanos() {
        return bindingNanos;
    }

    public long getSnapshotNanos() {
        return snapshotNanos;
    }

    public long getPersistenceCaptureNanos() {
        return persistenceCaptureNanos;
    }

    public long getStepSubmitNanos() {
        return stepSubmitNanos;
    }

    public int getQueuedRequests() {
        return queuedRequests;
    }

    public int getPublishedBodies() {
        return publishedBodies;
    }

    @Nonnull
    @Override
    public PhysicsProfilingResource clone() {
        PhysicsProfilingResource copy = new PhysicsProfilingResource();
        copy.enabled = enabled;
        copy.recordLatest(requestDrainNanos,
            bindingNanos,
            snapshotNanos,
            persistenceCaptureNanos,
            stepSubmitNanos,
            queuedRequests,
            publishedBodies);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsProfilingResource> getResourceType() {
        return PhysicsStoreTypes.profilingResourceType();
    }
}
