package dev.hytalemodding.impulse.core.internal.physicsstore.registration;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.BodyBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.ColliderBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.CompletedStepPublicationSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.IdentityIndexSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.JointBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.PersistenceCaptureSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.PersistenceHydrationSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.RequestDrainSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.SpaceBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.StepSubmissionSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.TargetBindingSystem;
import dev.hytalemodding.impulse.core.internal.physicsstore.systems.TerrainColliderBindingSystem;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;

/**
 * Registers authoritative PhysicsStore ECS types after the early plugin has patched Hytale.
 */
public final class PhysicsStoreRegistration {

    private static final String REGISTRY_METHOD = "getPhysicsStoreRegistry";

    private PhysicsStoreRegistration() {
    }

    public static void register(@Nonnull PluginBase plugin) {
        ComponentRegistryProxy<PhysicsStore> registry = physicsStoreRegistry(plugin);

        PhysicsStoreTypes.setUuidComponentType(registry.registerComponent(UuidComponent.class,
            "Uuid",
            UuidComponent.CODEC));
        PhysicsStoreTypes.setSpaceComponentType(registry.registerComponent(SpaceComponent.class,
            "Space",
            SpaceComponent.CODEC));
        PhysicsStoreTypes.setBodyComponentType(registry.registerComponent(BodyComponent.class,
            "Body",
            BodyComponent.CODEC));
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

        PhysicsStoreTypes.setRuntimeResourceType(registry.registerResource(
            PhysicsRuntimeResource.class,
            PhysicsRuntimeResource::new));
        PhysicsStoreTypes.setRequestQueueResourceType(registry.registerResource(
            PhysicsRequestQueueResource.class,
            PhysicsRequestQueueResource::new));
        PhysicsStoreTypes.setIdentityIndexResourceType(registry.registerResource(
            PhysicsIdentityIndexResource.class,
            PhysicsIdentityIndexResource::new));
        PhysicsStoreTypes.setSnapshotResourceType(registry.registerResource(
            PhysicsSnapshotResource.class,
            PhysicsSnapshotResource::new));
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

        registry.registerSystem(new RequestDrainSystem());
        registry.registerSystem(new PersistenceHydrationSystem());
        registry.registerSystem(new IdentityIndexSystem());
        registry.registerSystem(new SpaceBindingSystem());
        registry.registerSystem(new BodyBindingSystem());
        registry.registerSystem(new ColliderBindingSystem());
        registry.registerSystem(new JointBindingSystem());
        registry.registerSystem(new TerrainColliderBindingSystem());
        registry.registerSystem(new TargetBindingSystem());
        registry.registerSystem(new CompletedStepPublicationSystem());
        registry.registerSystem(new PersistenceCaptureSystem());
        registry.registerSystem(new StepSubmissionSystem());
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
