package dev.hytalemodding.impulse.api;

import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.runtime.legacy.LegacyPhysicsBackendRuntimeProvider;
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

    private static final Map<BackendId, PhysicsBackendRuntimeProvider> RUNTIME_PROVIDERS =
        new HashMap<>();

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
            RUNTIME_PROVIDERS.putIfAbsent(backend.getId(),
                new LegacyPhysicsBackendRuntimeProvider(backend));
            BACKEND_INIT_LOCKS.computeIfAbsent(backend.getId(), ignored -> new Object());
            INITIALIZED_BACKENDS.remove(backend.getId());
        }
    }

    /**
     * Register or replace an id-only backend runtime provider.
     */
    public static void registerRuntimeProvider(@Nonnull PhysicsBackendRuntimeProvider provider) {
        synchronized (REGISTRY_LOCK) {
            RUNTIME_PROVIDERS.put(provider.getId(), provider);
            BACKEND_INIT_LOCKS.computeIfAbsent(provider.getId(), ignored -> new Object());
            INITIALIZED_BACKENDS.remove(provider.getId());
        }
    }

    @Nonnull
    public static Collection<PhysicsBackend> getBackends() {
        synchronized (REGISTRY_LOCK) {
            return Collections.unmodifiableCollection(new ArrayList<>(BACKENDS.values()));
        }
    }

    @Nonnull
    public static Collection<PhysicsBackendRuntimeProvider> getRuntimeProviders() {
        synchronized (REGISTRY_LOCK) {
            return Collections.unmodifiableCollection(new ArrayList<>(RUNTIME_PROVIDERS.values()));
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

    @Nonnull
    public static PhysicsBackendRuntimeProvider getRuntimeProvider(@Nonnull BackendId backendId) {
        synchronized (REGISTRY_LOCK) {
            PhysicsBackendRuntimeProvider provider = RUNTIME_PROVIDERS.get(backendId);
            if (provider == null) {
                throw new IllegalStateException("No backend runtime provider registered with id: " + backendId);
            }
            return provider;
        }
    }

    @Nonnull
    public static PhysicsBackendRuntime createRuntime(@Nonnull BackendId backendId) {
        PhysicsBackendRuntimeProvider provider = getRuntimeProvider(backendId);
        ensureRuntimeProviderInitialized(backendId, provider);
        return provider.createRuntime();
    }

    /**
     * Create a space for the given backend id.
     *
     * <p>This method is safe to call concurrently after the backend is registered. The returned
     * space is live backend state and must still be owned by one serialized physics owner lane.</p>
     */
    @Nonnull
    public static PhysicsSpace createSpace(@Nonnull BackendId backendId) {
        return createSpace(backendId, SpaceId.next());
    }

    /**
     * Create a space for the given backend id and logical space id.
     *
     * <p>This method is safe to call concurrently after the backend is registered. The returned
     * space is live backend state and must still be owned by one serialized physics owner lane.</p>
     */
    @Nonnull
    public static PhysicsSpace createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId) {
        PhysicsBackend backend = getBackend(backendId);
        ensureBackendInitialized(backendId, backend);
        PhysicsSpace space = backend.createSpace(spaceId);
        if (!spaceId.equals(space.id())) {
            throw new IllegalStateException("Backend " + backendId
                + " created space id " + space.id() + " but expected " + spaceId);
        }
        return space;
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

    private static void ensureRuntimeProviderInitialized(@Nonnull BackendId backendId,
        @Nonnull PhysicsBackendRuntimeProvider provider) {
        Object initLock;
        synchronized (REGISTRY_LOCK) {
            if (INITIALIZED_BACKENDS.contains(backendId)) {
                LOGGER.log(Level.FINEST,
                    "Physics backend runtime " + backendId + " already initialized");
                return;
            }
            initLock = BACKEND_INIT_LOCKS.computeIfAbsent(backendId, ignored -> new Object());
        }

        synchronized (initLock) {
            synchronized (REGISTRY_LOCK) {
                if (INITIALIZED_BACKENDS.contains(backendId)) {
                    LOGGER.log(Level.FINEST,
                        "Physics backend runtime " + backendId + " already initialized");
                    return;
                }
            }

            LOGGER.log(Level.FINE,
                "Initializing physics backend runtime " + backendId + " on thread "
                    + Thread.currentThread().getName());
            provider.init();

            synchronized (REGISTRY_LOCK) {
                INITIALIZED_BACKENDS.add(backendId);
            }
            LOGGER.log(Level.INFO, "Initialized physics backend runtime " + backendId);
        }
    }
}
