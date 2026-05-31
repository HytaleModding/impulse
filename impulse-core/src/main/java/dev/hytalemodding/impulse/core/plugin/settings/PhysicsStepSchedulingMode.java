package dev.hytalemodding.impulse.core.plugin.settings;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Controls how the world-level scheduler handles elapsed {@code dt} while a
 * previous owner step is still unpublished.
 */
public enum PhysicsStepSchedulingMode {
    /**
     * Pending owner-lane steps do not add their {@code dt} to the next accepted step.
     */
    DROP_PENDING_DT("drop_pending_dt"),

    /**
     * Pending owner-lane steps accumulate elapsed {@code dt}; the next accepted step
     * catches up once, bounded by the scheduler's hard cap.
     */
    ACCUMULATE_PENDING_DT("accumulate_pending_dt");

    @Nonnull
    private final String serializedName;

    PhysicsStepSchedulingMode(@Nonnull String serializedName) {
        this.serializedName = serializedName;
    }

    @Nonnull
    public String getSerializedName() {
        return serializedName;
    }

    @Nonnull
    public String describePendingStepBehavior() {
        return switch (this) {
            case DROP_PENDING_DT -> "drop dt while an owner step is pending";
            case ACCUMULATE_PENDING_DT -> "accumulate pending dt for one capped catch-up step";
        };
    }

    @Nonnull
    public static PhysicsStepSchedulingMode parse(@Nonnull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PhysicsStepSchedulingMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown physics step scheduling mode: " + value);
    }
}
