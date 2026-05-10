package dev.hytalemodding.impulse.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * Entry point and backend registry for Impulse.
 */
public final class Impulse {

    private static final Logger LOGGER = Logger.getLogger("Impulse");

    private static final Object REGISTRY_LOCK = new Object();

    private static final Map<BackendId, PhysicsBackend> BACKENDS = new HashMap<>();

    private static final Map<BackendId, Object> BACKEND_INIT_LOCKS = new HashMap<>();

    private static final Set<BackendId> INITIALIZED_BACKENDS = new HashSet<>();

    private Impulse() {
    }

    /**
     * Register or replace a backend implementation.
     * <p>
     * This method is thread-safe.
     */
    public static void registerBackend(@Nonnull PhysicsBackend backend) {
        synchronized (REGISTRY_LOCK) {
            BACKENDS.put(backend.getId(), backend);
            BACKEND_INIT_LOCKS.computeIfAbsent(backend.getId(), ignored -> new Object());
            INITIALIZED_BACKENDS.remove(backend.getId());
        }
    }

    @Nonnull
    public static Collection<PhysicsBackend> getBackends() {
        synchronized (REGISTRY_LOCK) {
            return Collections.unmodifiableCollection(new ArrayList<>(BACKENDS.values()));
        }
    }

    @Nonnull
    public static PhysicsBackend getBackend(@Nonnull BackendId backendId) {
        synchronized (REGISTRY_LOCK) {
            PhysicsBackend backend = BACKENDS.get(backendId);
            if (backend == null) {
                throw new IllegalStateException("No backend registered with id: " + backendId);
            }
            return backend;
        }
    }

    /**
     * Create a space for the given backend id.
     */
    @Nonnull
    public static PhysicsSpace createSpace(@Nonnull BackendId backendId) {
        PhysicsBackend backend = getBackend(backendId);
        ensureBackendInitialized(backendId, backend);
        return backend.createSpace();
    }

    private static void ensureBackendInitialized(@Nonnull BackendId backendId,
        @Nonnull PhysicsBackend backend) {
        Object initLock;
        synchronized (REGISTRY_LOCK) {
            if (INITIALIZED_BACKENDS.contains(backendId)) {
                LOGGER.log(Level.FINEST,
                    "Physics backend " + backendId + " already initialized");
                return;
            }
            initLock = BACKEND_INIT_LOCKS.computeIfAbsent(backendId, ignored -> new Object());
        }

        synchronized (initLock) {
            synchronized (REGISTRY_LOCK) {
                if (INITIALIZED_BACKENDS.contains(backendId)) {
                    LOGGER.log(Level.FINEST,
                        "Physics backend " + backendId + " already initialized");
                    return;
                }
            }

            LOGGER.log(Level.FINE,
                "Initializing physics backend " + backendId + " on thread "
                    + Thread.currentThread().getName());
            backend.init();

            synchronized (REGISTRY_LOCK) {
                INITIALIZED_BACKENDS.add(backendId);
            }
            LOGGER.log(Level.INFO, "Initialized physics backend " + backendId);
        }
    }
}
