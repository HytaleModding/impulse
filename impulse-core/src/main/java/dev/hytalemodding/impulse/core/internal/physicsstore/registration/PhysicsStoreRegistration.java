package dev.hytalemodding.impulse.core.internal.physicsstore.registration;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreHooks;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStepSchedulerResource.TickDecision;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStoreReadQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.BodyBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.BodyCommandApplicationSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.ColliderBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.CompletedStepPublicationSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.IdentityIndexSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.JointBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.PersistenceCaptureSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.PersistenceHydrationSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.PhysicsStoreQueuedReadSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.TerrainMutationDrainSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.SpaceBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.SpaceSettingsApplicationSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.StepSubmissionSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.StaleBodyRemovalSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.TargetBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.TerrainColliderBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.WorldCollisionIndexSystem;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.WorldCollisionComponent;
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

    public static void register(@Nonnull PluginBase plugin) {
        ComponentRegistryProxy<PhysicsStore> registry = physicsStoreRegistry(plugin);
        PhysicsStoreHooks.registerShutdownHook(SHUTDOWN_CLEANUP);
        PhysicsStoreHooks.registerTickGate(STEP_TICK_GATE);

        PhysicsStoreTypes.setUuidComponentType(registry.registerComponent(UuidComponent.class,
            "Uuid",
            UuidComponent.CODEC));
        PhysicsStoreTypes.setSpaceComponentType(registry.registerComponent(SpaceComponent.class,
            "Space",
            SpaceComponent.CODEC));
        PhysicsStoreTypes.setBodyComponentType(registry.registerComponent(BodyComponent.class,
            "Body",
            BodyComponent.CODEC));
        PhysicsStoreTypes.setBodyCommandComponentType(registry.registerComponent(
            BodyCommandComponent.class,
            "BodyCommand",
            BodyCommandComponent.CODEC));
        PhysicsStoreTypes.setDynamicsComponentType(registry.registerComponent(DynamicsComponent.class,
            "Dynamics",
            DynamicsComponent.CODEC));
        PhysicsStoreTypes.setColliderComponentType(registry.registerComponent(ColliderComponent.class,
            "Collider",
            ColliderComponent.CODEC));
        PhysicsStoreTypes.setShapeComponentType(registry.registerComponent(ShapeComponent.class,
            "Shape",
            ShapeComponent.CODEC));
        PhysicsStoreTypes.setMaterialComponentType(registry.registerComponent(MaterialComponent.class,
            "Material",
            MaterialComponent.CODEC));
        PhysicsStoreTypes.setCollisionFilterComponentType(registry.registerComponent(
            CollisionFilterComponent.class,
            "CollisionFilter",
            CollisionFilterComponent.CODEC));
        PhysicsStoreTypes.setJointComponentType(registry.registerComponent(JointComponent.class,
            "Joint",
            JointComponent.CODEC));
        PhysicsStoreTypes.setTargetComponentType(registry.registerComponent(TargetComponent.class,
            "Target",
            TargetComponent.CODEC));
        PhysicsStoreTypes.setTerrainColliderComponentType(registry.registerComponent(
            TerrainColliderComponent.class,
            "TerrainCollider",
            TerrainColliderComponent.CODEC));
        PhysicsStoreTypes.setWorldCollisionComponentType(registry.registerComponent(
            WorldCollisionComponent.class,
            "WorldCollision",
            WorldCollisionComponent.CODEC));
        PhysicsStoreTypes.setSolverSettingsComponentType(registry.registerComponent(
            SolverSettingsComponent.class,
            "SolverSettings",
            SolverSettingsComponent.CODEC));
        PhysicsStoreTypes.setVisualSyncSettingsComponentType(registry.registerComponent(
            VisualSyncSettingsComponent.class,
            "VisualSyncSettings",
            VisualSyncSettingsComponent.CODEC));
        PhysicsStoreTypes.setVisualMaterializationSettingsComponentType(registry.registerComponent(
            VisualMaterializationSettingsComponent.class,
            "VisualMaterializationSettings",
            VisualMaterializationSettingsComponent.CODEC));
        PhysicsStoreTypes.setCollisionLodSettingsComponentType(registry.registerComponent(
            CollisionLodSettingsComponent.class,
            "CollisionLodSettings",
            CollisionLodSettingsComponent.CODEC));
        PhysicsStoreTypes.setExtensionSettingsComponentType(registry.registerComponent(
            ExtensionSettingsComponent.class,
            "ExtensionSettings",
            ExtensionSettingsComponent.CODEC));

        PhysicsStoreTypes.setRuntimeResourceType(registry.registerResource(
            PhysicsRuntimeResource.class,
            PhysicsRuntimeResource::new));
        PhysicsStoreTypes.setWorldSettingsResourceType(registry.registerResource(
            PhysicsWorldSettingsResource.class,
            PhysicsWorldSettingsResource::new));
        PhysicsStoreTypes.setStepSchedulerResourceType(registry.registerResource(
            PhysicsStepSchedulerResource.class,
            PhysicsStepSchedulerResource::new));
        PhysicsStoreTypes.setSpaceCompatibilityIndexResourceType(registry.registerResource(
            PhysicsSpaceCompatibilityIndexResource.class,
            PhysicsSpaceCompatibilityIndexResource::new));
        PhysicsStoreTypes.setTerrainMutationQueueResourceType(registry.registerResource(
            PhysicsTerrainMutationQueueResource.class,
            PhysicsTerrainMutationQueueResource::new));
        PhysicsStoreTypes.setIdentityIndexResourceType(registry.registerResource(
            PhysicsIdentityIndexResource.class,
            PhysicsIdentityIndexResource::new));
        PhysicsStoreTypes.setSnapshotResourceType(registry.registerResource(
            PhysicsSnapshotResource.class,
            PhysicsSnapshotResource::new));
        PhysicsStoreTypes.setBodyRegistrationResourceType(registry.registerResource(
            PhysicsBodyRegistrationResource.class,
            PhysicsBodyRegistrationResource::new));
        PhysicsStoreTypes.setEventResourceType(registry.registerResource(
            PhysicsEventResource.class,
            PhysicsEventResource::new));
        PhysicsStoreTypes.setReadQueueResourceType(registry.registerResource(
            PhysicsStoreReadQueueResource.class,
            PhysicsStoreReadQueueResource::new));
        PhysicsStoreTypes.setTerrainPayloadResourceType(registry.registerResource(
            PhysicsTerrainPayloadResource.class,
            PhysicsTerrainPayloadResource::new));
        PhysicsStoreTypes.setWorldCollisionIndexResourceType(registry.registerResource(
            PhysicsWorldCollisionIndexResource.class,
            PhysicsWorldCollisionIndexResource::new));
        PhysicsStoreTypes.setPersistentStoreResourceType(registry.registerResource(
            PersistentPhysicsStoreResource.class,
            "PersistentPhysicsStore",
            PersistentPhysicsStoreResource.CODEC));
        PhysicsStoreTypes.setRestoreStatusResourceType(registry.registerResource(
            PhysicsRestoreStatusResource.class,
            PhysicsRestoreStatusResource::new));
        PhysicsStoreTypes.setProfilingResourceType(registry.registerResource(
            PhysicsProfilingResource.class,
            PhysicsProfilingResource::new));
        PhysicsStoreTypes.setDebugResourceType(registry.registerResource(
            PhysicsDebugResource.class,
            PhysicsDebugResource::new));

        registry.registerSystem(new PersistenceHydrationSystem());
        registry.registerSystem(new TerrainMutationDrainSystem());
        registry.registerSystem(new IdentityIndexSystem());
        registry.registerSystem(new WorldCollisionIndexSystem());
        registry.registerSystem(new SpaceBindingSystem());
        registry.registerSystem(new SpaceSettingsApplicationSystem());
        registry.registerSystem(new BodyBindingSystem());
        registry.registerSystem(new ColliderBindingSystem());
        registry.registerSystem(new JointBindingSystem());
        registry.registerSystem(new StaleBodyRemovalSystem());
        registry.registerSystem(new TerrainColliderBindingSystem());
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
                PhysicsTerrainMutationQueueResource.getResourceType(),
                PhysicsTerrainMutationQueueResource::clear));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsStepSchedulerResource.getResourceType(),
                PhysicsStepSchedulerResource::close));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsRuntimeResource.getResourceType(),
                PhysicsRuntimeResource::destroyBackendBindings));
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
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsTerrainPayloadResource.getResourceType(),
                PhysicsTerrainPayloadResource::clear));
        failure = runShutdownCleanup(failure,
            () -> cleanupResource(store,
                PhysicsWorldCollisionIndexResource.getResourceType(),
                PhysicsWorldCollisionIndexResource::clear));
        if (failure != null) {
            throw failure;
        }
    }

    private static void ensurePersistentResourcePresent(@Nonnull Store<PhysicsStore> store) {
        if (store.getResource(PersistentPhysicsStoreResource.getResourceType()) == null) {
            store.replaceResource(PersistentPhysicsStoreResource.getResourceType(),
                new PersistentPhysicsStoreResource());
        }
    }

    private static <T extends Resource<PhysicsStore>> void cleanupResource(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull ResourceType<PhysicsStore, T> type,
        @Nonnull Consumer<T> cleanup) {
        T resource = store.getResource(type);
        if (resource != null) {
            cleanup.accept(resource);
        }
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
    private static ComponentRegistryProxy<PhysicsStore> physicsStoreRegistry(
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
