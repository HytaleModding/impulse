package dev.hytalemodding.impulse.core.internal.registration;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreHooks;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStoreTypes;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource.TickDecision;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStoreReadQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.internal.systems.BodyBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.BodyCommandApplicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.ColliderBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.CompletedStepPublicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.IdentityIndexSystem;
import dev.hytalemodding.impulse.core.internal.systems.JointBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.PersistenceCaptureSystem;
import dev.hytalemodding.impulse.core.internal.systems.PersistenceHydrationSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsStoreQueuedReadSystem;
import dev.hytalemodding.impulse.core.internal.systems.SpaceBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.SpaceSettingsApplicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.StepSubmissionSystem;
import dev.hytalemodding.impulse.core.internal.systems.StaleBodyRemovalSystem;
import dev.hytalemodding.impulse.core.internal.systems.TargetBindingSystem;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registers authoritative PhysicsStore ECS types after the early plugin has patched Hytale.
 */
public final class PhysicsStoreRegistration {

    private static final String REGISTRY_METHOD = "getPhysicsStoreRegistry";
    @Nonnull
    private static final Consumer<PhysicsStore> SHUTDOWN_CLEANUP =
        PhysicsStoreRegistration::clearRuntimeStateBeforeShutdown;
    @Nonnull
    private static final PhysicsStoreHooks.TickGate STEP_TICK_GATE =
        PhysicsStoreRegistration::shouldTickPhysicsStore;

    private PhysicsStoreRegistration() {
    }

    public static void register(@Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        PhysicsStoreHooks.registerShutdownHook(SHUTDOWN_CLEANUP);
        PhysicsStoreHooks.registerTickGate(STEP_TICK_GATE);

        PhysicsResourceTypes.registerResourceTypes(registry);
        PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes(registry);

        registry.registerSystem(new PersistenceHydrationSystem());
        registry.registerSystem(new IdentityIndexSystem());
        PhysicsChunkStoreTypes.registerPhysicsStoreSystems(registry);
        registry.registerSystem(new SpaceBindingSystem());
        registry.registerSystem(new SpaceSettingsApplicationSystem());
        registry.registerSystem(new BodyBindingSystem());
        registry.registerSystem(new ColliderBindingSystem());
        registry.registerSystem(new JointBindingSystem());
        registry.registerSystem(new StaleBodyRemovalSystem());
        registry.registerSystem(new BodyCommandApplicationSystem());
        registry.registerSystem(new TargetBindingSystem());
        registry.registerSystem(new CompletedStepPublicationSystem());
        registry.registerSystem(new PhysicsStoreQueuedReadSystem());
        registry.registerSystem(new PersistenceCaptureSystem());
        registry.registerSystem(new StepSubmissionSystem());
    }

    private static void clearRuntimeStateBeforeShutdown(@Nonnull PhysicsStore physicsStore) {
        Store<PhysicsStore> store = physicsStore.getStore();
        if (store.isShutdown()) {
            return;
        }
        RuntimeException failure = null;
        failure = runShutdownCleanup(failure,
            () -> ensurePersistentResourcePresent(store));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsStepSchedulerResource.getResourceType(),
                PhysicsStepSchedulerResource::close));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsRuntimeResource.getResourceType(),
                PhysicsRuntimeResource::destroyBackendBindings));
        failure = runShutdownCleanup(failure,
            () -> PhysicsChunkStoreTypes.clearPhysicsStoreRuntimeResources(store));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsIdentityIndexResource.getResourceType(),
                PhysicsIdentityIndexResource::clear));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsSpaceCompatibilityIndexResource.getResourceType(),
                PhysicsSpaceCompatibilityIndexResource::clear));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsSnapshotResource.getResourceType(),
                PhysicsSnapshotResource::clear));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsBodyRegistrationResource.getResourceType(),
                PhysicsBodyRegistrationResource::clear));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsEventResource.getResourceType(),
                PhysicsEventResource::clear));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsProfilingResource.getResourceType(),
                PhysicsProfilingResource::reset));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsStoreReadQueueResource.getResourceType(),
                PhysicsStoreReadQueueResource::clear));
        if (failure != null) {
            throw failure;
        }
    }

    private static void ensurePersistentResourcePresent(@Nonnull Store<PhysicsStore> store) {
        store.getResource(PersistentPhysicsStoreResource.getResourceType());
    }

    private static <T extends Resource<PhysicsStore>> void cleanupResource(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull ResourceType<PhysicsStore, T> type,
        @Nonnull Consumer<T> cleanup) {
        T resource = store.getResource(type);
        cleanup.accept(resource);
    }

    private static boolean shouldTickPhysicsStore(@Nonnull PhysicsStore physicsStore, float dt) {
        Store<PhysicsStore> store = physicsStore.getStore();
        if (store.isShutdown()) {
            return true;
        }
        PhysicsWorldSettings settings = store.getResource(PhysicsWorldSettingsResource.getResourceType())
            .getSettings();
        TickDecision decision = store.getResource(PhysicsStepSchedulerResource.getResourceType())
            .beforeStoreTick(dt,
                settings.getStepSchedulingMode(),
                maxSubmittedDtSeconds(settings),
                System.nanoTime());
        if (decision.shouldTick()) {
            return true;
        }
        recordPendingStepSkip(store, decision);
        return false;
    }

    private static void recordPendingStepSkip(@Nonnull Store<PhysicsStore> store,
        @Nonnull TickDecision decision) {
        Store<EntityStore> entityStore = store.getExternalData()
            .getWorld()
            .getEntityStore()
            .getStore();
        if (entityStore.isShutdown()) {
            return;
        }
        PhysicsRuntimeProfilingResource profiling = entityStore.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        if (!profiling.isEnabled()) {
            return;
        }
        profiling.recordStepSkippedPending(decision.pendingStepAgeNanos());
        profiling.recordStepScheduling(decision.inputDtSeconds(),
            decision.submittedDtSeconds(),
            decision.backlogDtSeconds(),
            decision.droppedBacklogDtSeconds(),
            decision.dtCapHit());
    }

    private static float maxSubmittedDtSeconds(@Nonnull PhysicsWorldSettings settings) {
        float maxStepDt = settings.getMaxStepDt() > 0.0f
            ? settings.getMaxStepDt()
            : PhysicsWorldSettings.DEFAULT_MAX_STEP_DT;
        int maxSteps = switch (settings.getStepMode()) {
            case FIXED, CCD -> settings.getSimulationSteps();
            case ADAPTIVE, PROGRESSIVE_REFINEMENT -> PhysicsWorldSettings.MAX_SIMULATION_STEPS;
        };
        return maxStepDt * maxSteps;
    }

    @Nullable
    private static RuntimeException runShutdownCleanup(@Nullable RuntimeException failure,
        @Nonnull Runnable cleanup) {
        try {
            cleanup.run();
            return failure;
        } catch (RuntimeException exception) {
            if (failure == null) {
                return exception;
            }
            failure.addSuppressed(exception);
            return failure;
        }
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public static ComponentRegistryProxy<PhysicsStore> physicsStoreRegistry(
        @Nonnull PluginBase plugin) {
        try {
            Method method = plugin.getClass().getMethod(REGISTRY_METHOD);
            return (ComponentRegistryProxy<PhysicsStore>) method.invoke(plugin);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Impulse requires the PhysicsStore early plugin to "
                + "patch PluginBase." + REGISTRY_METHOD + "()", exception);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access patched PhysicsStore registry method",
                exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("PhysicsStore registry method failed", cause);
        }
    }
}
