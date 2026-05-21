package dev.hytalemodding.impulse.core.plugin.resources;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Controls how the world-level physics step is subdivided each server tick.
 */
public enum PhysicsStepMode {
    /**
     * Uses configured steps as a minimum and increases substeps from the
     * max-step-dt budget.
     */
    PROGRESSIVE_REFINEMENT("progressive_refinement"),

    /**
     * Uses configured steps as a minimum, then increases substeps when the tick
     * dt or body travel risk requires more refinement.
     */
    ADAPTIVE("adaptive"),

    /**
     * Uses exactly the configured simulation step count every tick.
     */
    FIXED("fixed"),

    /**
     * Uses fixed substeps while temporarily enabling backend continuous
     * collision on dynamic bodies when the backend supports it.
     */
    CCD("ccd");

    @Nonnull
    private final String serializedName;

    PhysicsStepMode(@Nonnull String serializedName) {
        this.serializedName = serializedName;
    }

    @Nonnull
    public String getSerializedName() {
        return serializedName;
    }

    @Nonnull
    public String describeSimulationSteps() {
        return switch (this) {
            case PROGRESSIVE_REFINEMENT, ADAPTIVE -> "minimum substeps";
            case FIXED, CCD -> "fixed substeps";
        };
    }

    @Nonnull
    public static PhysicsStepMode parse(@Nonnull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PhysicsStepMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown physics step mode: " + value);
    }
}
