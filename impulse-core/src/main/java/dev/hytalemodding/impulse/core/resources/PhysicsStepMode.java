package dev.hytalemodding.impulse.core.resources;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Controls how the world-level physics step is subdivided each server tick.
 */
public enum PhysicsStepMode {
    PROGRESSIVE_REFINEMENT("progressive_refinement"),
    ADAPTIVE("adaptive"),
    FIXED("fixed"),
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
