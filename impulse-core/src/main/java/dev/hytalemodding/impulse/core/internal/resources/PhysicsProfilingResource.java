package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Runtime-only PhysicsStore profiling counters split by phase.
 */
public final class PhysicsProfilingResource implements Resource<PhysicsStore> {

    private boolean enabled;
    private long snapshotNanos;
    private long registrationPublicationNanos;
    private long stepSubmitNanos;
    private int spaces;
    private int substeps;
    private int publishedBodies;
    private int registrationPublicationRebuilds;
    private int registrationPublicationSkips;
    private int schedulerSamples;
    private float schedulerInputDtSeconds;
    private float schedulerSubmittedDtSeconds;
    private float schedulerBacklogDtSeconds;
    private float droppedBacklogDtSeconds;
    private boolean dtCapHit;
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

    public void recordRegistrationPublication(long registrationPublicationNanos,
        boolean rebuilt) {
        this.registrationPublicationNanos = Math.max(0L, registrationPublicationNanos);
        registrationPublicationRebuilds = rebuilt ? 1 : 0;
        registrationPublicationSkips = rebuilt ? 0 : 1;
    }

    public void recordStepScheduling(float inputDtSeconds,
        float submittedDtSeconds,
        float backlogDtSeconds,
        float droppedBacklogDtSeconds,
        boolean dtCapHit) {
        schedulerSamples = 1;
        schedulerInputDtSeconds = safeDt(inputDtSeconds);
        schedulerSubmittedDtSeconds = safeDt(submittedDtSeconds);
        schedulerBacklogDtSeconds = safeDt(backlogDtSeconds);
        this.droppedBacklogDtSeconds = safeDt(droppedBacklogDtSeconds);
        this.dtCapHit = dtCapHit;
    }

    public void reset() {
        snapshotNanos = 0L;
        registrationPublicationNanos = 0L;
        stepSubmitNanos = 0L;
        spaces = 0;
        substeps = 0;
        publishedBodies = 0;
        registrationPublicationRebuilds = 0;
        registrationPublicationSkips = 0;
        schedulerSamples = 0;
        schedulerInputDtSeconds = 0.0f;
        schedulerSubmittedDtSeconds = 0.0f;
        schedulerBacklogDtSeconds = 0.0f;
        droppedBacklogDtSeconds = 0.0f;
        dtCapHit = false;
        nativePhaseStats = PhysicsStepPhaseStats.unavailable();
    }

    @Nonnull
    public StepSample latestStepSample() {
        return new StepSample(spaces,
            substeps,
            stepSubmitNanos,
            snapshotNanos,
            registrationPublicationNanos,
            publishedBodies,
            registrationPublicationRebuilds,
            registrationPublicationSkips,
            schedulerSamples,
            schedulerInputDtSeconds,
            schedulerSubmittedDtSeconds,
            schedulerBacklogDtSeconds,
            droppedBacklogDtSeconds,
            dtCapHit,
            nativePhaseStats);
    }

    public long getSnapshotNanos() {
        return snapshotNanos;
    }

    public long getRegistrationPublicationNanos() {
        return registrationPublicationNanos;
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

    public int getPublishedBodies() {
        return publishedBodies;
    }

    public int getRegistrationPublicationRebuilds() {
        return registrationPublicationRebuilds;
    }

    public int getRegistrationPublicationSkips() {
        return registrationPublicationSkips;
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
        copy.snapshotNanos = snapshotNanos;
        copy.registrationPublicationNanos = registrationPublicationNanos;
        copy.stepSubmitNanos = stepSubmitNanos;
        copy.spaces = spaces;
        copy.substeps = substeps;
        copy.publishedBodies = publishedBodies;
        copy.registrationPublicationRebuilds = registrationPublicationRebuilds;
        copy.registrationPublicationSkips = registrationPublicationSkips;
        copy.schedulerSamples = schedulerSamples;
        copy.schedulerInputDtSeconds = schedulerInputDtSeconds;
        copy.schedulerSubmittedDtSeconds = schedulerSubmittedDtSeconds;
        copy.schedulerBacklogDtSeconds = schedulerBacklogDtSeconds;
        copy.droppedBacklogDtSeconds = droppedBacklogDtSeconds;
        copy.dtCapHit = dtCapHit;
        copy.nativePhaseStats = nativePhaseStats;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsProfilingResource> getResourceType() {
        return PhysicsResourceTypes.profilingResourceType();
    }

    public record StepSample(int spaces,
                             int substeps,
                             long stepSubmitNanos,
                             long snapshotNanos,
                             long registrationPublicationNanos,
                             int publishedBodies,
                             int registrationPublicationRebuilds,
                             int registrationPublicationSkips,
                             int schedulerSamples,
                             float schedulerInputDtSeconds,
                             float schedulerSubmittedDtSeconds,
                             float schedulerBacklogDtSeconds,
                             float droppedBacklogDtSeconds,
                             boolean dtCapHit,
                             @Nonnull PhysicsStepPhaseStats nativePhaseStats) {

        public StepSample {
            spaces = Math.max(0, spaces);
            substeps = Math.max(0, substeps);
            stepSubmitNanos = Math.max(0L, stepSubmitNanos);
            snapshotNanos = Math.max(0L, snapshotNanos);
            registrationPublicationNanos = Math.max(0L, registrationPublicationNanos);
            publishedBodies = Math.max(0, publishedBodies);
            registrationPublicationRebuilds = Math.max(0, registrationPublicationRebuilds);
            registrationPublicationSkips = Math.max(0, registrationPublicationSkips);
            schedulerSamples = Math.max(0, schedulerSamples);
            schedulerInputDtSeconds = safeDt(schedulerInputDtSeconds);
            schedulerSubmittedDtSeconds = safeDt(schedulerSubmittedDtSeconds);
            schedulerBacklogDtSeconds = safeDt(schedulerBacklogDtSeconds);
            droppedBacklogDtSeconds = safeDt(droppedBacklogDtSeconds);
            Objects.requireNonNull(nativePhaseStats, "nativePhaseStats");
        }
    }

    private static float safeDt(float dtSeconds) {
        return Float.isFinite(dtSeconds) ? Math.max(0.0f, dtSeconds) : 0.0f;
    }
}
