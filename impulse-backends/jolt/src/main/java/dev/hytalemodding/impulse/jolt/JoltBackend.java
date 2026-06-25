package dev.hytalemodding.impulse.jolt;

import dev.hytalemodding.impulse.api.BackendId;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public final class JoltBackend {

    public static final BackendId ID = new BackendId("impulse:jolt");
    private static final Logger LOGGER = Logger.getLogger("Impulse");

    private volatile boolean initialized;
    private JoltNativeLibrary nativeLibrary;

    @Nonnull
    public BackendId getId() {
        return ID;
    }

    public synchronized void init() {
        if (initialized) {
            return;
        }

        nativeLibrary = JoltNative.open();
        initialized = true;
        LOGGER.log(Level.INFO, "Jolt backend initialized");
    }

    @Nonnull
    synchronized JoltNativeLibrary nativeLibrary() {
        if (!initialized) {
            init();
        }
        return nativeLibrary;
    }
}
