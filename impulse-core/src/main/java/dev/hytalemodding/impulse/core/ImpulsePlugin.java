package dev.hytalemodding.impulse.core;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.event.WorldEventType;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommandContributionRegistry;
import dev.hytalemodding.impulse.core.internal.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.internal.modules.ImpulseSubPluginRegistration;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsStoreRegistration;
import dev.hytalemodding.impulse.core.internal.store.integration.PhysicsStoreEarlyPluginProbe;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsWorldResourceAttachmentSystem;
import dev.hytalemodding.impulse.core.internal.systems.debug.PhysicsDebugSystem;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsStoreEventPublicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsBodyAttachmentIndexSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsGeneratedProxyCleanupSystem;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsComponentTypes;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.core.plugin.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;

public final class ImpulsePlugin extends JavaPlugin {

    private static ImpulsePlugin instance;
    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    @Getter
    private ComponentType<EntityStore, BodyAttachmentComponent> bodyAttachmentComponentType;

    @Getter
    private ComponentType<EntityStore, GeneratedVisualProxyComponent> generatedVisualProxyComponentType;

    @Getter
    private ResourceType<EntityStore, PhysicsWorldResource> physicsWorldResourceType;

    @Getter
    private ResourceType<EntityStore, PhysicsDebugResource> physicsDebugResourceType;

    @Getter
    private ResourceType<EntityStore, PhysicsRuntimeProfilingResource> physicsRuntimeProfilingResourceType;

    @Getter
    private ResourceType<EntityStore, PhysicsProjectionIndexResource> physicsProjectionIndexResourceType;

    @Getter
    private WorldEventType<EntityStore, PhysicsEventFramePublishedEvent> physicsEventFramePublishedEventType;

    @Getter
    private SystemGroup<EntityStore> persistenceRestoreGroup;

    @Nullable
    private BackendId defaultBackendId;

    public ImpulsePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static ImpulsePlugin get() {
        return instance;
    }

    @Nullable
    public BackendId getDefaultBackendId() {
        return defaultBackendId;
    }

    @Override
    protected void setup() {
        PhysicsStoreEarlyPluginProbe.requireAvailable();
        ComponentRegistryProxy<PhysicsStore> physicsStoreRegistry =
            PhysicsStoreRegistration.physicsStoreRegistry(this);
        registerPhysicsStoreComponents(physicsStoreRegistry);
        PhysicsStoreRegistration.register(physicsStoreRegistry);
        ImpulseSubPluginRegistration.register(this);
        discoverBackends();

        registerEntityStoreComponents();
        registerSystems();
        registerCommands();
    }

    @Override
    protected void start() {
        registerCrucibleSuites();
    }

    @Override
    protected void shutdown() {
        ImpulseCommandContributionRegistry.unregister();
    }

    /**
     * Registers optional Crucible suites after Crucible has loaded.
     * Core owns these suites because they validate Impulse API and ECS behavior,
     * not example command behavior.
     */
    private void registerCrucibleSuites() {
        try {
            PluginManager pluginManager = HytaleServer.get().getPluginManager();
            PluginBase cruciblePlugin = pluginManager.getPlugin(
                new PluginIdentifier("com.ionforgelabs", "crucible"));
            if (cruciblePlugin == null) {
                return;
            }
            ClassLoader crucibleLoader = ((JavaPlugin) cruciblePlugin).getClassLoader();
            Class<?> suitesClass = Class.forName(
                "dev.hytalemodding.impulse.core.internal.crucible.ImpulseCrucibleSuites",
                true,
                crucibleLoader);
            suitesClass.getMethod("register", ClassLoader.class).invoke(null, crucibleLoader);
        } catch (ClassNotFoundException e) {
            // Crucible is not installed.
        } catch (ReflectiveOperationException e) {
            LOGGER.at(Level.WARNING)
                .log("Failed to register Impulse Crucible suites: %s", e.getMessage());
        }
    }

    @SuppressWarnings("removal")
    private void discoverBackends() {
        for (PhysicsBackendRuntimeProvider provider : BackendDiscovery.discoverRuntimeProviders(
            backendSearchRoots(),
            getClassLoader())) {
            Impulse.registerRuntimeProvider(provider);
        }
        for (PhysicsBackend backend : BackendDiscovery.discover(backendSearchRoots(),
            getClassLoader())) {
            Impulse.registerBackend(backend);
        }

        for (PhysicsBackendRuntimeProvider provider : Impulse.getRuntimeProviders()) {
            LOGGER.at(Level.INFO).log("Registered physics backend runtime %s", provider.getId());
        }

        if (Impulse.getRuntimeProviders().isEmpty()) {
            throw new IllegalStateException("No physics backends discovered");
        }

        defaultBackendId = selectDefaultRuntimeProviderId(Impulse.getRuntimeProviders());
        if (defaultBackendId != null) {
            LOGGER.at(Level.INFO).log("Using default physics backend %s", defaultBackendId);
            return;
        }

        LOGGER.at(Level.INFO).log("Multiple physics backends discovered; no default backend "
            + "selected. Pass --backend=<id> when creating spaces. Available backends: %s",
            getAvailableBackendIds());
    }

    @Nonnull
    private List<Path> backendSearchRoots() {
        List<Path> paths = new ArrayList<>();
        paths.add(PluginManager.MODS_PATH);
        paths.addAll(Options.getOptionSet().valuesOf(Options.MODS_DIRECTORIES));
        return paths;
    }

    @Nullable
    static BackendId selectDefaultRuntimeProviderId(
        @Nonnull Collection<PhysicsBackendRuntimeProvider> providers) {
        if (providers.size() != 1) {
            return null;
        }

        return providers.iterator().next().getId();
    }

    @Nonnull
    private String getAvailableBackendIds() {
        StringBuilder ids = new StringBuilder();
        for (PhysicsBackendRuntimeProvider backend : Impulse.getRuntimeProviders()) {
            if (!ids.isEmpty()) {
                ids.append(", ");
            }
            ids.append(backend.getId().value());
        }
        return ids.toString();
    }

    private static void registerPhysicsStoreComponents(
        @Nonnull ComponentRegistryProxy<PhysicsStore> physicsRegistry) {
        PhysicsComponentTypes.setUuidComponentType(physicsRegistry.registerComponent(
            UuidComponent.class,
            "Uuid",
            UuidComponent.CODEC));
        PhysicsComponentTypes.setSpaceComponentType(physicsRegistry.registerComponent(
            SpaceComponent.class,
            "Space",
            SpaceComponent.CODEC));
        PhysicsComponentTypes.setBodyComponentType(physicsRegistry.registerComponent(
            BodyComponent.class,
            "Body",
            BodyComponent.CODEC));
        PhysicsComponentTypes.setBodyCommandComponentType(physicsRegistry.registerComponent(
            BodyCommandComponent.class,
            "BodyCommand",
            BodyCommandComponent.CODEC));
        PhysicsComponentTypes.setDynamicsComponentType(physicsRegistry.registerComponent(
            DynamicsComponent.class,
            "Dynamics",
            DynamicsComponent.CODEC));
        PhysicsComponentTypes.setColliderComponentType(physicsRegistry.registerComponent(
            ColliderComponent.class,
            "Collider",
            ColliderComponent.CODEC));
        PhysicsComponentTypes.setShapeComponentType(physicsRegistry.registerComponent(
            ShapeComponent.class,
            "Shape",
            ShapeComponent.CODEC));
        PhysicsComponentTypes.setMaterialComponentType(physicsRegistry.registerComponent(
            MaterialComponent.class,
            "Material",
            MaterialComponent.CODEC));
        PhysicsComponentTypes.setCollisionFilterComponentType(physicsRegistry.registerComponent(
            CollisionFilterComponent.class,
            "CollisionFilter",
            CollisionFilterComponent.CODEC));
        PhysicsComponentTypes.setJointComponentType(physicsRegistry.registerComponent(
            JointComponent.class,
            "Joint",
            JointComponent.CODEC));
        PhysicsComponentTypes.setTargetComponentType(physicsRegistry.registerComponent(
            TargetComponent.class,
            "Target",
            TargetComponent.CODEC));
        PhysicsComponentTypes.setTerrainColliderComponentType(physicsRegistry.registerComponent(
            TerrainColliderComponent.class,
            "TerrainCollider",
            TerrainColliderComponent.CODEC));
        PhysicsComponentTypes.setWorldCollisionComponentType(physicsRegistry.registerComponent(
            WorldCollisionComponent.class,
            "WorldCollision",
            WorldCollisionComponent.CODEC));
        PhysicsComponentTypes.setSolverSettingsComponentType(physicsRegistry.registerComponent(
            SolverSettingsComponent.class,
            "SolverSettings",
            SolverSettingsComponent.CODEC));
        PhysicsComponentTypes.setVisualSyncSettingsComponentType(physicsRegistry.registerComponent(
            VisualSyncSettingsComponent.class,
            "VisualSyncSettings",
            VisualSyncSettingsComponent.CODEC));
        PhysicsComponentTypes.setVisualMaterializationSettingsComponentType(
            physicsRegistry.registerComponent(VisualMaterializationSettingsComponent.class,
                "VisualMaterializationSettings",
                VisualMaterializationSettingsComponent.CODEC));
        PhysicsComponentTypes.setCollisionLodSettingsComponentType(physicsRegistry.registerComponent(
            CollisionLodSettingsComponent.class,
            "CollisionLodSettings",
            CollisionLodSettingsComponent.CODEC));
        PhysicsComponentTypes.setExtensionSettingsComponentType(physicsRegistry.registerComponent(
            ExtensionSettingsComponent.class,
            "ExtensionSettings",
            ExtensionSettingsComponent.CODEC));
    }

    private void registerEntityStoreComponents() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        bodyAttachmentComponentType = entityRegistry.registerComponent(
            BodyAttachmentComponent.class,
            "BodyAttachment",
            BodyAttachmentComponent.CODEC);
        generatedVisualProxyComponentType = entityRegistry.registerComponent(
            GeneratedVisualProxyComponent.class,
            "GeneratedVisualProxy",
            GeneratedVisualProxyComponent.CODEC);
        physicsWorldResourceType = entityRegistry.registerResource(PhysicsWorldResource.class,
            PhysicsWorldRuntimeResource::new);
        physicsDebugResourceType = entityRegistry.registerResource(PhysicsDebugResource.class,
            PhysicsDebugResource::new);
        physicsRuntimeProfilingResourceType = entityRegistry.registerResource(
            PhysicsRuntimeProfilingResource.class,
            PhysicsRuntimeProfilingResource::new);
        physicsProjectionIndexResourceType = entityRegistry.registerResource(
            PhysicsProjectionIndexResource.class,
            PhysicsProjectionIndexResource::new);
        physicsEventFramePublishedEventType =
            entityRegistry.registerWorldEventType(PhysicsEventFramePublishedEvent.class);
    }

    private void registerSystems() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        persistenceRestoreGroup = entityRegistry.registerSystemGroup();
        entityRegistry.registerSystem(new PhysicsBodyAttachmentIndexSystem());
        entityRegistry.registerSystem(new PhysicsGeneratedProxyCleanupSystem());
        entityRegistry.registerSystem(new PhysicsSyncSystem());
        entityRegistry.registerSystem(new PhysicsDebugSystem());
        entityRegistry.registerSystem(new PhysicsStoreEventPublicationSystem());
        entityRegistry.registerSystem(new PhysicsWorldResourceAttachmentSystem());
    }

    private void registerCommands() {
        CommandRegistry commandRegistry = getCommandRegistry();
        ImpulseCommandContributionRegistry.register(commandRegistry);
    }

}
