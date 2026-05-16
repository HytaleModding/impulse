package dev.hytalemodding.impulse.core.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

/**
 * Runtime-only profiling state for hot Impulse simulation paths.
 *
 * <p>This resource tracks coarse performance and selectivity metrics for the
 * world step and entity sync phases. It is intentionally operational only and
 * is not part of persisted world physics state.</p>
 */
@Getter
public class PhysicsRuntimeProfilingResource implements Resource<EntityStore> {

    @Setter
    private boolean enabled;

    private final StepSnapshot cumulativeStep = new StepSnapshot();
    private final StepSnapshot latestStep = new StepSnapshot();
    private final StepSnapshot worstStep = new StepSnapshot();
    private final SyncSnapshot cumulativeSync = new SyncSnapshot();
    private final SyncSnapshot latestSync = new SyncSnapshot();
    private final SyncSnapshot worstSync = new SyncSnapshot();

    @Nullable
    private transient SyncCollector activeSyncCollector;

    public PhysicsRuntimeProfilingResource() {
    }

    public void recordStep(int spaces, int substeps, long nanos) {
        StepSnapshot snapshot = new StepSnapshot();
        snapshot.recordTickSample();
        snapshot.setSpaces(spaces);
        snapshot.setSubsteps(substeps);
        snapshot.setTickNanos(nanos);

        latestStep.copyFrom(snapshot);
        cumulativeStep.add(snapshot);
        if (snapshot.getTickNanos() >= worstStep.getTickNanos()) {
            worstStep.copyFrom(snapshot);
        }
    }

    @Nonnull
    public SyncCollector beginSyncSample() {
        SyncCollector collector = new SyncCollector();
        activeSyncCollector = collector;
        return collector;
    }

    public void finishSyncSample(@Nonnull SyncCollector collector, long nanos) {
        SyncSnapshot snapshot = new SyncSnapshot();
        snapshot.copyFrom(collector.snapshot(nanos));
        snapshot.recordTickSample();

        latestSync.copyFrom(snapshot);
        cumulativeSync.add(snapshot);
        if (snapshot.getTickNanos() >= worstSync.getTickNanos()) {
            worstSync.copyFrom(snapshot);
        }
        if (activeSyncCollector == collector) {
            activeSyncCollector = null;
        }
    }

    public void reset() {
        cumulativeStep.reset();
        latestStep.reset();
        worstStep.reset();
        cumulativeSync.reset();
        latestSync.reset();
        worstSync.reset();
        activeSyncCollector = null;
    }

    @Nonnull
    @Override
    public PhysicsRuntimeProfilingResource clone() {
        PhysicsRuntimeProfilingResource copy = new PhysicsRuntimeProfilingResource();
        copy.enabled = enabled;
        copy.cumulativeStep.copyFrom(cumulativeStep);
        copy.latestStep.copyFrom(latestStep);
        copy.worstStep.copyFrom(worstStep);
        copy.cumulativeSync.copyFrom(cumulativeSync);
        copy.latestSync.copyFrom(latestSync);
        copy.worstSync.copyFrom(worstSync);
        return copy;
    }

    public static ResourceType<EntityStore, PhysicsRuntimeProfilingResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsRuntimeProfilingResourceType();
    }

    @Getter
    public static final class StepSnapshot {

        private int tickSamples;
        @Setter
        private int spaces;
        @Setter
        private int substeps;
        @Setter
        private long tickNanos;

        public void recordTickSample() {
            tickSamples++;
        }

        public void copyFrom(@Nonnull StepSnapshot other) {
            tickSamples = other.tickSamples;
            spaces = other.spaces;
            substeps = other.substeps;
            tickNanos = other.tickNanos;
        }

        public void add(@Nonnull StepSnapshot other) {
            tickSamples += other.tickSamples;
            spaces += other.spaces;
            substeps += other.substeps;
            tickNanos += other.tickNanos;
        }

        public void reset() {
            tickSamples = 0;
            spaces = 0;
            substeps = 0;
            tickNanos = 0L;
        }
    }

    @Getter
    public static final class SyncSnapshot {

        private int tickSamples;
        @Setter
        private int bodiesInspected;
        @Setter
        private int bodiesSynced;
        @Setter
        private int transitionSyncs;
        @Setter
        private int keepaliveSyncs;
        @Setter
        private int skippedSleeping;
        @Setter
        private int skippedThreshold;
        @Setter
        private int skippedVisualDeadzone;
        @Setter
        private int skippedVisualRange;
        @Setter
        private int skippedStatic;
        @Setter
        private int skippedMissingSpace;
        @Setter
        private long tickNanos;

        public void recordTickSample() {
            tickSamples++;
        }

        public void copyFrom(@Nonnull SyncSnapshot other) {
            tickSamples = other.tickSamples;
            bodiesInspected = other.bodiesInspected;
            bodiesSynced = other.bodiesSynced;
            transitionSyncs = other.transitionSyncs;
            keepaliveSyncs = other.keepaliveSyncs;
            skippedSleeping = other.skippedSleeping;
            skippedThreshold = other.skippedThreshold;
            skippedVisualDeadzone = other.skippedVisualDeadzone;
            skippedVisualRange = other.skippedVisualRange;
            skippedStatic = other.skippedStatic;
            skippedMissingSpace = other.skippedMissingSpace;
            tickNanos = other.tickNanos;
        }

        public void add(@Nonnull SyncSnapshot other) {
            tickSamples += other.tickSamples;
            bodiesInspected += other.bodiesInspected;
            bodiesSynced += other.bodiesSynced;
            transitionSyncs += other.transitionSyncs;
            keepaliveSyncs += other.keepaliveSyncs;
            skippedSleeping += other.skippedSleeping;
            skippedThreshold += other.skippedThreshold;
            skippedVisualDeadzone += other.skippedVisualDeadzone;
            skippedVisualRange += other.skippedVisualRange;
            skippedStatic += other.skippedStatic;
            skippedMissingSpace += other.skippedMissingSpace;
            tickNanos += other.tickNanos;
        }

        public void reset() {
            tickSamples = 0;
            bodiesInspected = 0;
            bodiesSynced = 0;
            transitionSyncs = 0;
            keepaliveSyncs = 0;
            skippedSleeping = 0;
            skippedThreshold = 0;
            skippedVisualDeadzone = 0;
            skippedVisualRange = 0;
            skippedStatic = 0;
            skippedMissingSpace = 0;
            tickNanos = 0L;
        }
    }

    public static final class SyncCollector {

        private final AtomicInteger bodiesInspected = new AtomicInteger();
        private final AtomicInteger bodiesSynced = new AtomicInteger();
        private final AtomicInteger transitionSyncs = new AtomicInteger();
        private final AtomicInteger keepaliveSyncs = new AtomicInteger();
        private final AtomicInteger skippedSleeping = new AtomicInteger();
        private final AtomicInteger skippedThreshold = new AtomicInteger();
        private final AtomicInteger skippedVisualDeadzone = new AtomicInteger();
        private final AtomicInteger skippedVisualRange = new AtomicInteger();
        private final AtomicInteger skippedStatic = new AtomicInteger();
        private final AtomicInteger skippedMissingSpace = new AtomicInteger();

        public void incrementBodiesInspected() {
            bodiesInspected.incrementAndGet();
        }

        public void incrementBodiesSynced() {
            bodiesSynced.incrementAndGet();
        }

        public void incrementTransitionSyncs() {
            transitionSyncs.incrementAndGet();
        }

        public void incrementKeepaliveSyncs() {
            keepaliveSyncs.incrementAndGet();
        }

        public void incrementSkippedSleeping() {
            skippedSleeping.incrementAndGet();
        }

        public void incrementSkippedThreshold() {
            skippedThreshold.incrementAndGet();
        }

        public void incrementSkippedVisualDeadzone() {
            skippedVisualDeadzone.incrementAndGet();
        }

        public void incrementSkippedVisualRange() {
            skippedVisualRange.incrementAndGet();
        }

        public void incrementSkippedStatic() {
            skippedStatic.incrementAndGet();
        }

        public void incrementSkippedMissingSpace() {
            skippedMissingSpace.incrementAndGet();
        }

        @Nonnull
        private SyncSnapshot snapshot(long nanos) {
            SyncSnapshot snapshot = new SyncSnapshot();
            snapshot.setBodiesInspected(bodiesInspected.get());
            snapshot.setBodiesSynced(bodiesSynced.get());
            snapshot.setTransitionSyncs(transitionSyncs.get());
            snapshot.setKeepaliveSyncs(keepaliveSyncs.get());
            snapshot.setSkippedSleeping(skippedSleeping.get());
            snapshot.setSkippedThreshold(skippedThreshold.get());
            snapshot.setSkippedVisualDeadzone(skippedVisualDeadzone.get());
            snapshot.setSkippedVisualRange(skippedVisualRange.get());
            snapshot.setSkippedStatic(skippedStatic.get());
            snapshot.setSkippedMissingSpace(skippedMissingSpace.get());
            snapshot.setTickNanos(nanos);
            return snapshot;
        }
    }
}
