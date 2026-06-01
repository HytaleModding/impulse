package dev.hytalemodding.impulse.core.plugin.settings;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Controls which backend physics events are collected during owner-lane steps.
 */
public enum PhysicsEventCollectionMode {
    /**
     * Steps physics spaces without collecting backend event batches.
     */
    DISABLED("disabled"),

    /**
     * Collects backend contact events and publishes stable physics event frames.
     */
    CONTACTS("contacts");

    @Nonnull
    private final String serializedName;

    PhysicsEventCollectionMode(@Nonnull String serializedName) {
        this.serializedName = serializedName;
    }

    @Nonnull
    public String getSerializedName() {
        return serializedName;
    }

    public boolean collectsBackendEvents() {
        return this == CONTACTS;
    }

    @Nonnull
    public static PhysicsEventCollectionMode parse(@Nonnull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PhysicsEventCollectionMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown physics event collection mode: " + value);
    }
}
