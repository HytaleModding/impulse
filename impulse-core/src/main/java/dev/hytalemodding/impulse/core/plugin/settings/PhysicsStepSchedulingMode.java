package dev.hytalemodding.impulse.core.plugin.settings;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Controls how the world-level scheduler handles elapsed {@code dt} while a
 * previous store tick step is still unpublished.
 */
public enum PhysicsStepSchedulingMode {
    /**
     * Pending store tick steps and post-skip catch-up {@code dt} are dropped.
     */
    DROP_PENDING_DT("drop_pending_dt"),

    /**
     * Pending store tick steps accumulate elapsed {@code dt}; the next accepted step
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
            case DROP_PENDING_DT -> "drop pending dt and prevent post-skip catch-up";
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
