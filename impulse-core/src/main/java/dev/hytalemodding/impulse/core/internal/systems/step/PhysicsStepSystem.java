package dev.hytalemodding.impulse.core.internal.systems.step;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerStepCommand;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Chunk-tick scheduler for physics steps.
 *
 * <p>This system only submits work to the per-world physics owner lane. It does not wait for step
 * results or apply snapshots; {@code PhysicsSnapshotPublicationSystem} performs reader-side
 * publication later on the entity-store tick.</p>
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
        PhysicsOwnerResource owner = entityStore.getResource(
            PhysicsOwnerResource.getResourceType());
        if (owner.isClosed()) {
            return;
        }
        PersistentPhysicsWorldResource persistent = entityStore.getResource(
            PersistentPhysicsWorldResource.getResourceType());

        submitStepIfRestoreReady(stateFor(store),
            persistent,
            owner,
            resource,
            dt,
            profiling,
            Math.max(0L, world.getTick()));
    }

    boolean submitStepIfRestoreReady(@Nonnull StepSchedulerState state,
        @Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsOwnerResource owner,
        @Nonnull PhysicsWorldResource resource,
        float dt,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long serverTick) {
        if (!PhysicsStepRestoreGate.canSubmitStep(persistent)) {
            /*
             * Restore replaces runtime topology. Dropping catch-up dt avoids replaying stale
             * backlog against newly restored spaces after restore completes.
             */
            state.clearAccumulatedStepDt();
            return false;
        }
        submitStepIfIdle(state, owner, resource, dt, profiling, serverTick);
        return true;
    }

    void submitStepIfIdle(@Nonnull StepSchedulerState state,
        @Nonnull PhysicsOwnerResource owner,
        @Nonnull PhysicsWorldResource resource,
        float dt,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long serverTick) {
        PhysicsWorldSettings settings = resource.getWorldSettings();
        if (settings.getStepSchedulingMode()
            != PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT) {
            submitCurrentTickStepIfIdle(state, owner, resource, dt, profiling, serverTick);
            return;
        }

        submitAccumulatedStepIfIdle(state, owner, resource, dt, profiling, serverTick);
    }

    private void submitCurrentTickStepIfIdle(@Nonnull StepSchedulerState state,
        @Nonnull PhysicsOwnerResource owner,
        @Nonnull PhysicsWorldResource resource,
        float dt,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long serverTick) {
        boolean profilingEnabled = profiling.isEnabled();
        state.clearAccumulatedStepDt();
        if (owner.hasPendingStep()) {
            if (profilingEnabled) {
                profiling.recordStepSkippedPending(owner.pendingStepAgeNanos());
            }
            return;
        }

        PhysicsOwnerStepCommand command = state.currentTickStepCommand(resource,
            dt,
            profilingEnabled,
            serverTick);
        try {
            if (owner.submitStepIfIdle(command)) {
                state.consumeStepSequence();
                return;
            }
            if (profilingEnabled) {
                profiling.recordStepSkippedPending(owner.pendingStepAgeNanos());
            }
        } catch (RejectedExecutionException exception) {
            if (!owner.isClosed()) {
                LOGGER.at(Level.WARNING).log(
                    "Skipping async physics step because the owner lane is unavailable: %s",
                    exception.getMessage());
            }
        }
    }

    private void submitAccumulatedStepIfIdle(@Nonnull StepSchedulerState state,
        @Nonnull PhysicsOwnerResource owner,
        @Nonnull PhysicsWorldResource resource,
        float dt,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long serverTick) {
        boolean profilingEnabled = profiling.isEnabled();
        float safeDt = safeStepDt(dt);
        float maxAccumulatedDt = maxAccumulatedStepDt(resource);
        AccumulatedStepPreparation preparation = state.prepareAccumulatedStepCommand(safeDt,
            maxAccumulatedDt,
            owner::hasPendingStep,
            resource,
            profilingEnabled,
            serverTick);
        StepSchedulerSample sample = preparation.sample();
        if (preparation.hasPendingStep()) {
            recordSchedulerSample(profiling,
                profilingEnabled,
                safeDt,
                0.0f,
                preparation.backlogDt(),
                sample.droppedDt(),
                sample.capHit());
            if (profilingEnabled) {
                profiling.recordStepSkippedPending(owner.pendingStepAgeNanos());
            }
            return;
        }
        PhysicsOwnerStepCommand command = preparation.commandOrThrow();
        try {
            if (owner.submitStepIfIdle(command)) {
                float submittedDt = state.consumeSubmittedStepDt();
                recordSchedulerSample(profiling,
                    profilingEnabled,
                    safeDt,
                    submittedDt,
                    0.0f,
                    sample.droppedDt(),
                    sample.capHit());
                return;
            }
            recordSchedulerSample(profiling,
                profilingEnabled,
                safeDt,
                0.0f,
                state.accumulatedStepDt(),
                sample.droppedDt(),
                sample.capHit());
            if (profilingEnabled) {
                profiling.recordStepSkippedPending(owner.pendingStepAgeNanos());
            }
        } catch (RejectedExecutionException exception) {
            recordSchedulerSample(profiling,
                profilingEnabled,
                safeDt,
                0.0f,
                state.accumulatedStepDt(),
                sample.droppedDt(),
                sample.capHit());
            if (!owner.isClosed()) {
                LOGGER.at(Level.WARNING).log(
                    "Skipping async physics step because the owner lane is unavailable: %s",
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
        PhysicsWorldSettings settings = resource.getWorldSettings();
        float maxStepDt = settings.getMaxStepDt();
        float safeMaxStepDt = Float.isFinite(maxStepDt) && maxStepDt > 0.0f
            ? maxStepDt
            : PhysicsWorldSettings.DEFAULT_MAX_STEP_DT;
        /*
         * Fixed and CCD modes do not refine catch-up dt inside the owner lane: they
         * use the configured substep count directly. Cap accumulated submissions
         * to that exact budget and drop the rest through the scheduler profiling
         * path. Adaptive/progressive modes keep the wider refinement window.
         */
        int stepBudget = switch (settings.getStepMode()) {
            case FIXED, CCD -> Math.clamp(settings.getSimulationSteps(),
                PhysicsWorldSettings.MIN_SIMULATION_STEPS,
                PhysicsWorldSettings.MAX_SIMULATION_STEPS);
            case ADAPTIVE, PROGRESSIVE_REFINEMENT -> PhysicsWorldSettings.MAX_SIMULATION_STEPS;
        };
        return Math.min(MAX_ACCUMULATED_STEP_DT, safeMaxStepDt * stepBudget);
    }

    static final class StepSchedulerState {

        private final Object lock = new Object();

        /**
         * Impulse-local sequence for correlating owner step commands with published
         * snapshot frames. This is not the Hytale world tick.
         */
        private long nextStepSequence = 1L;

        /*
         * Accumulated scheduler dt is intentionally local to this ChunkStore scheduler. It is
         * submitted only when the owner lane has no in-flight step.
         */
        private double accumulatedStepDt;

        @Nonnull
        private AccumulatedStepPreparation prepareAccumulatedStepCommand(float dt,
            float maxAccumulatedDt,
            @Nonnull BooleanSupplier hasPendingStep,
            @Nonnull PhysicsWorldResource resource,
            boolean profilingEnabled,
            long serverTick) {
            synchronized (lock) {
                StepSchedulerSample sample = accumulateLocked(dt, maxAccumulatedDt);
                if (hasPendingStep.getAsBoolean()) {
                    return new AccumulatedStepPreparation(sample,
                        (float) accumulatedStepDt,
                        null);
                }
                PhysicsOwnerStepCommand command = new PhysicsOwnerStepCommand(resource,
                    (float) accumulatedStepDt,
                    profilingEnabled,
                    nextStepSequence,
                    serverTick);
                return new AccumulatedStepPreparation(sample, 0.0f, command);
            }
        }

        @Nonnull
        PhysicsOwnerStepCommand currentTickStepCommand(@Nonnull PhysicsWorldResource resource,
            float dt,
            boolean profilingEnabled,
            long serverTick) {
            synchronized (lock) {
                return new PhysicsOwnerStepCommand(resource,
                    dt,
                    profilingEnabled,
                    nextStepSequence,
                    serverTick);
            }
        }

        @Nonnull
        private StepSchedulerSample accumulateLocked(float dt, float maxAccumulatedDt) {
            double candidate = accumulatedStepDt + Math.max(0.0f, dt);
            double capped = Math.clamp(maxAccumulatedDt, 0.0f, candidate);
            accumulatedStepDt = capped;
            double dropped = Math.max(0.0, candidate - capped);
            return new StepSchedulerSample((float) dropped, dropped > 0.0);
        }

        private float consumeSubmittedStepDt() {
            synchronized (lock) {
                float submittedDt = (float) accumulatedStepDt;
                accumulatedStepDt = 0.0;
                nextStepSequence++;
                return submittedDt;
            }
        }

        private void clearAccumulatedStepDt() {
            synchronized (lock) {
                accumulatedStepDt = 0.0;
            }
        }

        private void consumeStepSequence() {
            synchronized (lock) {
                nextStepSequence++;
            }
        }

        private float accumulatedStepDt() {
            synchronized (lock) {
                return (float) accumulatedStepDt;
            }
        }
    }

    private record AccumulatedStepPreparation(@Nonnull StepSchedulerSample sample,
        float backlogDt,
        @Nullable PhysicsOwnerStepCommand command) {

        private boolean hasPendingStep() {
            return command == null;
        }

        @Nonnull
        private PhysicsOwnerStepCommand commandOrThrow() {
            if (command == null) {
                throw new IllegalStateException("No accumulated step command was prepared");
            }
            return command;
        }
    }

    private record StepSchedulerSample(float droppedDt, boolean capHit) {
    }
}
