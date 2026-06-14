package dev.hytalemodding.impulse.core.internal.physicsstore.registration;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.PluginBase;
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
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.PhysicsStoreReadRequestSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.TerrainMutationDrainSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.SpaceBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.SpaceSettingsApplicationSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.StepSubmissionSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.StaleBodyRemovalSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.TargetBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.TerrainColliderBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.WorldCollisionIndexSystem;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
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

    private PhysicsStoreRegistration() {
    }

    public static void register(@Nonnull PluginBase plugin) {
        ComponentRegistryProxy<PhysicsStore> registry = physicsStoreRegistry(plugin);
        PhysicsStoreHooks.registerShutdownHook(SHUTDOWN_CLEANUP);

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
        registry.registerSystem(new PhysicsStoreReadRequestSystem());
        registry.registerSystem(new CompletedStepPublicationSystem());
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
            () -> store.getResource(PhysicsTerrainMutationQueueResource.getResourceType()).clear());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsRuntimeResource.getResourceType()).destroyBackendBindings());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsIdentityIndexResource.getResourceType()).clear());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType()).clear());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsSnapshotResource.getResourceType()).clear());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsBodyRegistrationResource.getResourceType()).clear());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsEventResource.getResourceType()).clear());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsProfilingResource.getResourceType()).reset());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsStoreReadQueueResource.getResourceType()).clear());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsTerrainPayloadResource.getResourceType()).clear());
        failure = runShutdownCleanup(failure,
            () -> store.getResource(PhysicsWorldCollisionIndexResource.getResourceType()).clear());
        if (failure != null) {
            throw failure;
        }
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
