package dev.hytalemodding.impulse.core.internal.resources.simulation;

import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import javax.annotation.Nonnull;

/**
 * World-level simulation policy for one runtime physics resource.
 */
public final class PhysicsSimulationRuntime {

    @Nonnull
    private final PhysicsWorldSettings worldSettings = new PhysicsWorldSettings();

    @Nonnull
    public PhysicsWorldSettings getWorldSettings() {
        return new PhysicsWorldSettings(worldSettings);
    }

    public void setWorldSettings(@Nonnull PhysicsWorldSettings settings) {
        worldSettings.copyFrom(settings);
    }

    public void copyFrom(@Nonnull PhysicsSimulationRuntime other) {
        worldSettings.copyFrom(other.worldSettings);
    }
}
