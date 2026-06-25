package dev.hytalemodding.impulse.api;

import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
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
public final class ImpulseBackendRegistry {

    private static final Logger LOGGER = Logger.getLogger("Impulse");

    private static final Object REGISTRY_LOCK = new Object();

    private static final Map<BackendId, PhysicsBackendRuntimeProvider> RUNTIME_PROVIDERS =
        new HashMap<>();

    private static final Map<BackendId, Object> BACKEND_INIT_LOCKS = new HashMap<>();

    private static final Set<BackendId> INITIALIZED_BACKENDS = new HashSet<>();

    private ImpulseBackendRegistry() {
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
    public static Collection<PhysicsBackendRuntimeProvider> getRuntimeProviders() {
        synchronized (REGISTRY_LOCK) {
            return Collections.unmodifiableCollection(new ArrayList<>(RUNTIME_PROVIDERS.values()));
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
