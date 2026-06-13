package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import javax.annotation.Nonnull;

/**
 * Authored backend solver and activation tuning for one PhysicsStore space row.
 */
public final class SolverSettingsComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<SolverSettingsComponent> CODEC = BuilderCodec.builder(
            SolverSettingsComponent.class,
            SolverSettingsComponent::new)
        .append(new KeyedCodec<>("SolverIterations", Codec.INTEGER, false),
            (component, value) -> component.solverIterations = value != null
                ? value
                : PhysicsSolverSettings.DEFAULT_SOLVER_ITERATIONS,
            SolverSettingsComponent::getSolverIterations)
        .add()
        .append(new KeyedCodec<>("StabilizationIterations", Codec.INTEGER, false),
            (component, value) -> component.stabilizationIterations = value != null
                ? value
                : PhysicsSolverSettings.DEFAULT_STABILIZATION_ITERATIONS,
            SolverSettingsComponent::getStabilizationIterations)
        .add()
        .append(new KeyedCodec<>("DynamicSleepLinearThreshold", Codec.FLOAT, false),
            (component, value) -> component.dynamicSleepLinearThreshold = value != null
                ? value
                : PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_LINEAR_THRESHOLD,
            SolverSettingsComponent::getDynamicSleepLinearThreshold)
        .add()
        .append(new KeyedCodec<>("DynamicSleepAngularThreshold", Codec.FLOAT, false),
            (component, value) -> component.dynamicSleepAngularThreshold = value != null
                ? value
                : PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_ANGULAR_THRESHOLD,
            SolverSettingsComponent::getDynamicSleepAngularThreshold)
        .add()
        .append(new KeyedCodec<>("DynamicSleepTimeUntilSleep", Codec.FLOAT, false),
            (component, value) -> component.dynamicSleepTimeUntilSleep = value != null
                ? value
                : PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_TIME_UNTIL_SLEEP,
            SolverSettingsComponent::getDynamicSleepTimeUntilSleep)
        .add()
        .build();

    private int solverIterations = PhysicsSolverSettings.DEFAULT_SOLVER_ITERATIONS;
    private int stabilizationIterations = PhysicsSolverSettings.DEFAULT_STABILIZATION_ITERATIONS;
    private float dynamicSleepLinearThreshold =
        PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_LINEAR_THRESHOLD;
    private float dynamicSleepAngularThreshold =
        PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_ANGULAR_THRESHOLD;
    private float dynamicSleepTimeUntilSleep =
        PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_TIME_UNTIL_SLEEP;

    public SolverSettingsComponent() {
    }

    public SolverSettingsComponent(@Nonnull PhysicsSolverSettings settings) {
        solverIterations = settings.getSolverIterations();
        stabilizationIterations = settings.getStabilizationIterations();
        dynamicSleepLinearThreshold = settings.getDynamicSleepLinearThreshold();
        dynamicSleepAngularThreshold = settings.getDynamicSleepAngularThreshold();
        dynamicSleepTimeUntilSleep = settings.getDynamicSleepTimeUntilSleep();
    }

    public SolverSettingsComponent(int solverIterations,
        int stabilizationIterations,
        float dynamicSleepLinearThreshold,
        float dynamicSleepAngularThreshold,
        float dynamicSleepTimeUntilSleep) {
        this.solverIterations = solverIterations;
        this.stabilizationIterations = stabilizationIterations;
        this.dynamicSleepLinearThreshold = dynamicSleepLinearThreshold;
        this.dynamicSleepAngularThreshold = dynamicSleepAngularThreshold;
        this.dynamicSleepTimeUntilSleep = dynamicSleepTimeUntilSleep;
    }

    public int getSolverIterations() {
        return solverIterations;
    }

    public void setSolverIterations(int solverIterations) {
        this.solverIterations = solverIterations;
    }

    public int getStabilizationIterations() {
        return stabilizationIterations;
    }

    public void setStabilizationIterations(int stabilizationIterations) {
        this.stabilizationIterations = stabilizationIterations;
    }

    public float getDynamicSleepLinearThreshold() {
        return dynamicSleepLinearThreshold;
    }

    public void setDynamicSleepLinearThreshold(float dynamicSleepLinearThreshold) {
        this.dynamicSleepLinearThreshold = dynamicSleepLinearThreshold;
    }

    public float getDynamicSleepAngularThreshold() {
        return dynamicSleepAngularThreshold;
    }

    public void setDynamicSleepAngularThreshold(float dynamicSleepAngularThreshold) {
        this.dynamicSleepAngularThreshold = dynamicSleepAngularThreshold;
    }

    public float getDynamicSleepTimeUntilSleep() {
        return dynamicSleepTimeUntilSleep;
    }

    public void setDynamicSleepTimeUntilSleep(float dynamicSleepTimeUntilSleep) {
        this.dynamicSleepTimeUntilSleep = dynamicSleepTimeUntilSleep;
    }

    public void copyTo(@Nonnull PhysicsSpaceSettings settings) {
        copyTo(settings.getSolverSettings());
    }

    public void copyTo(@Nonnull PhysicsSolverSettings settings) {
        settings.setSolverIterations(solverIterations);
        settings.setStabilizationIterations(stabilizationIterations);
        settings.setDynamicSleepTuning(dynamicSleepLinearThreshold,
            dynamicSleepAngularThreshold,
            dynamicSleepTimeUntilSleep);
    }

    @Nonnull
    public static ComponentType<PhysicsStore, SolverSettingsComponent> getComponentType() {
        return PhysicsStoreTypes.solverSettingsComponentType();
    }

    @Nonnull
    @Override
    public SolverSettingsComponent clone() {
        return new SolverSettingsComponent(solverIterations,
            stabilizationIterations,
            dynamicSleepLinearThreshold,
            dynamicSleepAngularThreshold,
            dynamicSleepTimeUntilSleep);
    }
}
