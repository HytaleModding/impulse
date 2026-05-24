package dev.hytalemodding.impulse.core.internal.systems.step;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCommand;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Submits world physics steps to the physics worker without draining them on
 * the main tick.
 */
public class PhysicsStepSystem extends TickingSystem<ChunkStore> implements AutoCloseable {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final float MAX_ACCUMULATED_STEP_DT = 0.25f;

    @Nonnull
    private final Map<Store<ChunkStore>, StepSchedulerState> statesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());
    private volatile boolean closed;

    public PhysicsStepSystem() {
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
        if (closed) {
            return;
        }

        var world = store.getExternalData().getWorld();
        var entityStore = world.getEntityStore().getStore();
        PhysicsWorldResource resource = entityStore.getResource(
            PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource profiling = entityStore.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        PhysicsWorldWorkerResource worker = entityStore.getResource(
            PhysicsWorldWorkerResource.getResourceType());
        if (worker.isClosed()) {
            return;
        }

        submitStepIfIdle(stateFor(store),
            worker,
            resource,
            dt,
            profiling,
            Math.max(0L, world.getTick()));
    }

    void submitStepIfIdle(@Nonnull StepSchedulerState state,
        @Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull PhysicsWorldResource resource,
        float dt,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long serverTick) {
        if (resource.getStepSchedulingMode()
            != PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT) {
            submitCurrentTickStepIfIdle(state, worker, resource, dt, profiling, serverTick);
            return;
        }

        submitAccumulatedStepIfIdle(state, worker, resource, dt, profiling, serverTick);
    }

    private void submitCurrentTickStepIfIdle(@Nonnull StepSchedulerState state,
        @Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull PhysicsWorldResource resource,
        float dt,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long serverTick) {
        boolean profilingEnabled = profiling.isEnabled();
        synchronized (state) {
            state.clearAccumulatedStepDt();
        }
        if (worker.hasPendingStep()) {
            if (profilingEnabled) {
                profiling.recordStepSkippedPending(worker.pendingStepAgeNanos());
            }
            return;
        }

        PhysicsWorkerStepCommand command;
        synchronized (state) {
            command = new PhysicsWorkerStepCommand(resource,
                dt,
                profilingEnabled,
                state.nextStepSequence(),
                serverTick);
        }
        try {
            if (worker.submitStepIfIdle(command)) {
                synchronized (state) {
                    state.consumeStepSequence();
                }
                return;
            }
            if (profilingEnabled) {
                profiling.recordStepSkippedPending(worker.pendingStepAgeNanos());
            }
        } catch (RejectedExecutionException exception) {
            if (!worker.isClosed()) {
                LOGGER.at(Level.WARNING).log(
                    "Skipping async physics step because the worker path is unavailable: %s",
                    exception.getMessage());
            }
        }
    }

    private void submitAccumulatedStepIfIdle(@Nonnull StepSchedulerState state,
        @Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull PhysicsWorldResource resource,
        float dt,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long serverTick) {
        boolean profilingEnabled = profiling.isEnabled();
        float safeDt = safeStepDt(dt);
        float maxAccumulatedDt = maxAccumulatedStepDt(resource);
        StepSchedulerSample sample;
        PhysicsWorkerStepCommand command;
        synchronized (state) {
            sample = state.accumulate(safeDt, maxAccumulatedDt);
            if (worker.hasPendingStep()) {
                recordSchedulerSample(profiling,
                    profilingEnabled,
                    safeDt,
                    0.0f,
                    state.accumulatedStepDt(),
                    sample.droppedDt(),
                    sample.capHit());
                if (profilingEnabled) {
                    profiling.recordStepSkippedPending(worker.pendingStepAgeNanos());
                }
                return;
            }
            command = new PhysicsWorkerStepCommand(resource,
                state.submittedStepDt(),
                profilingEnabled,
                state.nextStepSequence(),
                serverTick);
        }
        try {
            if (worker.submitStepIfIdle(command)) {
                float submittedDt;
                synchronized (state) {
                    submittedDt = state.consumeSubmittedStepDt();
                }
                recordSchedulerSample(profiling,
                    profilingEnabled,
                    safeDt,
                    submittedDt,
                    0.0f,
                    sample.droppedDt(),
                    sample.capHit());
                return;
            }
            synchronized (state) {
                recordSchedulerSample(profiling,
                    profilingEnabled,
                    safeDt,
                    0.0f,
                    state.accumulatedStepDt(),
                    sample.droppedDt(),
                    sample.capHit());
            }
            if (profilingEnabled) {
                profiling.recordStepSkippedPending(worker.pendingStepAgeNanos());
            }
        } catch (RejectedExecutionException exception) {
            synchronized (state) {
                recordSchedulerSample(profiling,
                    profilingEnabled,
                    safeDt,
                    0.0f,
                    state.accumulatedStepDt(),
                    sample.droppedDt(),
                    sample.capHit());
            }
            if (!worker.isClosed()) {
                LOGGER.at(Level.WARNING).log(
                    "Skipping async physics step because the worker path is unavailable: %s",
                    exception.getMessage());
            }
        }
    }

    @Nonnull
    private StepSchedulerState stateFor(@Nonnull Store<ChunkStore> store) {
        synchronized (statesByStore) {
            return statesByStore.computeIfAbsent(store, ignored -> new StepSchedulerState());
        }
    }

    private static void recordSchedulerSample(@Nonnull PhysicsRuntimeProfilingResource profiling,
        boolean profilingEnabled,
        float inputDt,
        float submittedDt,
        float backlogDt,
        float droppedDt,
        boolean capHit) {
        if (profilingEnabled) {
            profiling.recordStepScheduling(inputDt, submittedDt, backlogDt, droppedDt, capHit);
        }
    }

    private static float safeStepDt(float dt) {
        return Float.isFinite(dt) ? Math.max(dt, 0.0f) : 0.0f;
    }

    static float maxAccumulatedStepDt(@Nonnull PhysicsWorldResource resource) {
        float maxStepDt = resource.getMaxStepDt();
        float safeMaxStepDt = Float.isFinite(maxStepDt) && maxStepDt > 0.0f
            ? maxStepDt
            : PhysicsWorldResource.DEFAULT_MAX_STEP_DT;
        return Math.min(MAX_ACCUMULATED_STEP_DT,
            safeMaxStepDt * PhysicsWorldResource.MAX_SIMULATION_STEPS);
    }

    static final class StepSchedulerState {

        /**
         * Impulse-local sequence for correlating worker step commands with published
         * snapshot frames. This is not the Hytale world tick.
         */
        private long nextStepSequence = 1L;
        private double accumulatedStepDt;

        @Nonnull
        private StepSchedulerSample accumulate(float dt, float maxAccumulatedDt) {
            double candidate = accumulatedStepDt + Math.max(0.0f, dt);
            double capped = Math.min(candidate, Math.max(0.0f, maxAccumulatedDt));
            accumulatedStepDt = capped;
            double dropped = Math.max(0.0, candidate - capped);
            return new StepSchedulerSample((float) dropped, dropped > 0.0);
        }

        private float submittedStepDt() {
            return (float) accumulatedStepDt;
        }

        private float consumeSubmittedStepDt() {
            float submittedDt = submittedStepDt();
            clearAccumulatedStepDt();
            consumeStepSequence();
            return submittedDt;
        }

        private void clearAccumulatedStepDt() {
            accumulatedStepDt = 0.0;
        }

        private void consumeStepSequence() {
            nextStepSequence++;
        }

        private float accumulatedStepDt() {
            return (float) accumulatedStepDt;
        }

        private long nextStepSequence() {
            return nextStepSequence;
        }
    }

    private record StepSchedulerSample(float droppedDt, boolean capHit) {
    }
}
