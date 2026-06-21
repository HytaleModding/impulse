package dev.hytalemodding.impulse.core.plugin.events;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Controls which backend physics events are collected during store tick steps.
 *
 * @deprecated Physics event plugin API is deprecated without replacement.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public enum PhysicsEventCollectionMode {
    /**
     * Steps physics spaces without collecting backend event batches.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    DISABLED("disabled"),

    /**
     * Collects backend contact events and publishes stable physics event frames.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
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
