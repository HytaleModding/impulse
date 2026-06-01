package dev.hytalemodding.impulse.core.plugin.settings;

import lombok.Getter;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * World-level physics simulation settings.
 *
 * <p>These values control how the world schedules and subdivides physics steps. They are not
 * per-space solver tuning; {@link PhysicsSolverSettings} remains part of
 * {@link PhysicsSpaceSettings} because solver parameters are applied to individual backend spaces.</p>
 */
public class PhysicsWorldSettings {

    public static final int MIN_SIMULATION_STEPS = 1;
    public static final int MAX_SIMULATION_STEPS = 16;
    public static final float DEFAULT_MAX_STEP_DT = 1f / 30f;
    @Nonnull
    public static final PhysicsStepSchedulingMode DEFAULT_STEP_SCHEDULING_MODE =
        PhysicsStepSchedulingMode.DROP_PENDING_DT;
    @Nonnull
    public static final PhysicsEventCollectionMode DEFAULT_EVENT_COLLECTION_MODE =
        PhysicsEventCollectionMode.DISABLED;

    @Getter
    private int simulationSteps = MIN_SIMULATION_STEPS;
    @Nonnull
    private PhysicsStepMode stepMode = PhysicsStepMode.PROGRESSIVE_REFINEMENT;
    @Nonnull
    private PhysicsStepSchedulingMode stepSchedulingMode = DEFAULT_STEP_SCHEDULING_MODE;
    @Nonnull
    private PhysicsEventCollectionMode eventCollectionMode = DEFAULT_EVENT_COLLECTION_MODE;
    @Getter
    private float maxStepDt = DEFAULT_MAX_STEP_DT;

    public PhysicsWorldSettings() {
    }

    public PhysicsWorldSettings(@Nonnull PhysicsWorldSettings settings) {
        copyFrom(settings);
    }

    public void copyFrom(@Nonnull PhysicsWorldSettings settings) {
        Objects.requireNonNull(settings, "settings");
        simulationSteps = settings.simulationSteps;
        stepMode = settings.stepMode;
        stepSchedulingMode = settings.stepSchedulingMode;
        eventCollectionMode = settings.eventCollectionMode;
        maxStepDt = settings.maxStepDt;
    }

    @Nonnull
    public static PhysicsWorldSettings defaults() {
        return new PhysicsWorldSettings();
    }

    public void setSimulationSteps(int simulationSteps) {
        if (simulationSteps < MIN_SIMULATION_STEPS || simulationSteps > MAX_SIMULATION_STEPS) {
            throw new IllegalArgumentException("Simulation steps must be between "
                + MIN_SIMULATION_STEPS + " and " + MAX_SIMULATION_STEPS);
        }
        this.simulationSteps = simulationSteps;
    }

    @Nonnull
    public PhysicsStepMode getStepMode() {
        return stepMode;
    }

    public void setStepMode(@Nonnull PhysicsStepMode stepMode) {
        this.stepMode = Objects.requireNonNull(stepMode, "stepMode");
    }

    @Nonnull
    public PhysicsStepSchedulingMode getStepSchedulingMode() {
        return stepSchedulingMode;
    }

    public void setStepSchedulingMode(@Nonnull PhysicsStepSchedulingMode stepSchedulingMode) {
        this.stepSchedulingMode = Objects.requireNonNull(stepSchedulingMode,
            "stepSchedulingMode");
    }

    @Nonnull
    public PhysicsEventCollectionMode getEventCollectionMode() {
        return eventCollectionMode;
    }

    public void setEventCollectionMode(@Nonnull PhysicsEventCollectionMode eventCollectionMode) {
        this.eventCollectionMode = Objects.requireNonNull(eventCollectionMode,
            "eventCollectionMode");
    }

    public void setMaxStepDt(float maxStepDt) {
        if (!Float.isFinite(maxStepDt) || maxStepDt <= 0.0f) {
            throw new IllegalArgumentException("Max step dt must be finite and > 0");
        }
        this.maxStepDt = maxStepDt;
    }
}
