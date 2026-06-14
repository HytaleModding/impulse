package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Objects;
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
    private int spaces;
    private int substeps;
    private int queuedRequests;
    private int publishedBodies;
    @Nonnull
    private PhysicsStepPhaseStats nativePhaseStats = PhysicsStepPhaseStats.unavailable();

    public PhysicsProfilingResource() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void recordStep(long stepSubmitNanos,
        int spaces,
        int substeps,
        @Nonnull PhysicsStepPhaseStats nativePhaseStats) {
        this.stepSubmitNanos = Math.max(0L, stepSubmitNanos);
        this.spaces = Math.max(0, spaces);
        this.substeps = Math.max(0, substeps);
        this.nativePhaseStats = Objects.requireNonNull(nativePhaseStats, "nativePhaseStats");
    }

    public void recordSnapshot(long snapshotNanos, int publishedBodies) {
        this.snapshotNanos = Math.max(0L, snapshotNanos);
        this.publishedBodies = Math.max(0, publishedBodies);
    }

    public void reset() {
        requestDrainNanos = 0L;
        bindingNanos = 0L;
        snapshotNanos = 0L;
        persistenceCaptureNanos = 0L;
        stepSubmitNanos = 0L;
        spaces = 0;
        substeps = 0;
        queuedRequests = 0;
        publishedBodies = 0;
        nativePhaseStats = PhysicsStepPhaseStats.unavailable();
    }

    @Nonnull
    public StepSample latestStepSample() {
        return new StepSample(spaces,
            substeps,
            stepSubmitNanos,
            snapshotNanos,
            publishedBodies,
            nativePhaseStats);
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

    public int getSpaces() {
        return spaces;
    }

    public int getSubsteps() {
        return substeps;
    }

    public int getQueuedRequests() {
        return queuedRequests;
    }

    public int getPublishedBodies() {
        return publishedBodies;
    }

    @Nonnull
    public PhysicsStepPhaseStats getNativePhaseStats() {
        return nativePhaseStats;
    }

    @Nonnull
    @Override
    public PhysicsProfilingResource clone() {
        PhysicsProfilingResource copy = new PhysicsProfilingResource();
        copy.enabled = enabled;
        copy.requestDrainNanos = requestDrainNanos;
        copy.bindingNanos = bindingNanos;
        copy.snapshotNanos = snapshotNanos;
        copy.persistenceCaptureNanos = persistenceCaptureNanos;
        copy.stepSubmitNanos = stepSubmitNanos;
        copy.spaces = spaces;
        copy.substeps = substeps;
        copy.queuedRequests = queuedRequests;
        copy.publishedBodies = publishedBodies;
        copy.nativePhaseStats = nativePhaseStats;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsProfilingResource> getResourceType() {
        return PhysicsStoreTypes.profilingResourceType();
    }

    public record StepSample(int spaces,
                             int substeps,
                             long stepSubmitNanos,
                             long snapshotNanos,
                             int publishedBodies,
                             @Nonnull PhysicsStepPhaseStats nativePhaseStats) {

        public StepSample {
            spaces = Math.max(0, spaces);
            substeps = Math.max(0, substeps);
            stepSubmitNanos = Math.max(0L, stepSubmitNanos);
            snapshotNanos = Math.max(0L, snapshotNanos);
            publishedBodies = Math.max(0, publishedBodies);
            Objects.requireNonNull(nativePhaseStats, "nativePhaseStats");
        }
    }
}
