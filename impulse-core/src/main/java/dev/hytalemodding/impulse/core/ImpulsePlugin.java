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
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommand;
import dev.hytalemodding.impulse.core.internal.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.internal.modules.ImpulseSubPluginRegistration;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerLaneScheduler;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsChunkBoundarySystem;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsCollisionLodSystem;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsWorldCollisionStreamingSystem;
import dev.hytalemodding.impulse.core.internal.systems.debug.PhysicsDebugSystem;
import dev.hytalemodding.impulse.core.internal.systems.body.RigidBodyLifecycleCleanupSystem;
import dev.hytalemodding.impulse.core.internal.systems.body.RigidBodyReconciliationSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsBodyHydrationSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsJointHydrationSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsSpaceBootstrapSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsWorldSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PhysicsRuntimeHolderSystem;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsSnapshotPublicationSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlSessionCleanupSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsKinematicControlSystem;
import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsStepSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsBodyAttachmentIndexSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsDetachedVisualMaterializationSystem;
import dev.hytalemodding.impulse.core.internal.systems.owner.PhysicsOwnerLifecycleSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKinematicTargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyLifecycleComponent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistenceResource;
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
    static final String OWNER_POOL_SIZE_PROPERTY = "impulse.ownerPool.size";

    @Getter
    private ComponentType<EntityStore, PhysicsBodyAttachmentComponent> physicsBodyAttachmentComponentType;

    @Getter
    private ComponentType<EntityStore, RigidBodyComponent> rigidBodyComponentType;

    @Getter
    private ComponentType<EntityStore, RigidBodyKinematicTargetComponent> rigidBodyKinematicTargetComponentType;

    @Getter
    private ComponentType<EntityStore, RigidBodyLifecycleComponent> rigidBodyLifecycleComponentType;

    @Getter
    private ComponentType<EntityStore, ImpulseControllableComponent> impulseControllableComponentType;

    @Getter
    private ComponentType<EntityStore, GeneratedVisualProxyComponent> generatedVisualProxyComponentType;

    @Getter
    private ResourceType<EntityStore, PhysicsWorldResource> physicsWorldResourceType;

    @Getter
    private ResourceType<EntityStore, PhysicsDebugResource> physicsDebugResourceType;

    @Getter
    private ResourceType<EntityStore, PhysicsRuntimeProfilingResource> physicsRuntimeProfilingResourceType;

    @Getter
    private ResourceType<EntityStore, PhysicsOwnerResource> physicsOwnerResourceType;

    @Getter
    private ResourceType<EntityStore, WorldCollisionProfilingResource> worldCollisionProfilingResourceType;

    @Getter
    private ResourceType<EntityStore, ? extends PhysicsPersistenceResource> persistentPhysicsWorldResourceType;

    @Getter
    private WorldEventType<EntityStore, PhysicsEventFramePublishedEvent> physicsEventFramePublishedEventType;

    @Getter
    private SystemGroup<EntityStore> persistenceRestoreGroup;

    @Nullable
    private BackendId defaultBackendId;

    private PhysicsOwnerLaneScheduler physicsOwnerLaneScheduler;
    private PhysicsStepSystem physicsStepSystem;
    private PhysicsOwnerLifecycleSystem physicsOwnerLifecycleSystem;

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
        ImpulseSubPluginRegistration.register(this);
        discoverBackends();

        registerComponents();
        registerSystems();
        registerCommands();
    }

    @Override
    protected void start() {
        registerCrucibleSuites();
    }

    @Override
    protected void shutdown() {
        if (physicsStepSystem != null) {
            physicsStepSystem.close();
            physicsStepSystem = null;
        }
        if (physicsOwnerLifecycleSystem != null) {
            physicsOwnerLifecycleSystem.close();
            physicsOwnerLifecycleSystem = null;
        }
        if (physicsOwnerLaneScheduler != null) {
            physicsOwnerLaneScheduler.close();
            physicsOwnerLaneScheduler = null;
        }
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

    static int configuredPositiveInt(@Nonnull String property,
        int defaultValue) {
        if (defaultValue < 1) {
            throw new IllegalArgumentException("defaultValue must be positive");
        }
        String configured = System.getProperty(property);
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(configured.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
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

    private void registerComponents() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        physicsBodyAttachmentComponentType = entityRegistry.registerComponent(
            PhysicsBodyAttachmentComponent.class,
            "PhysicsBodyAttachment",
            PhysicsBodyAttachmentComponent.CODEC);
        rigidBodyComponentType = entityRegistry.registerComponent(
            RigidBodyComponent.class,
            "RigidBody",
            RigidBodyComponent.CODEC);
        rigidBodyKinematicTargetComponentType = entityRegistry.registerComponent(
            RigidBodyKinematicTargetComponent.class,
            "RigidBodyKinematicTarget",
            RigidBodyKinematicTargetComponent.CODEC);
        rigidBodyLifecycleComponentType = entityRegistry.registerComponent(
            RigidBodyLifecycleComponent.class,
            "RigidBodyLifecycle",
            RigidBodyLifecycleComponent.CODEC);
        impulseControllableComponentType = entityRegistry.registerComponent(
            ImpulseControllableComponent.class,
            "ImpulseControllable",
            ImpulseControllableComponent.CODEC);
        PhysicsControlSessionComponent.setComponentType(entityRegistry.registerComponent(
            PhysicsControlSessionComponent.class, PhysicsControlSessionComponent::new));
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
        physicsOwnerLaneScheduler = new PhysicsOwnerLaneScheduler(
            configuredPositiveInt(OWNER_POOL_SIZE_PROPERTY,
                PhysicsOwnerLaneScheduler.DEFAULT_POOL_SIZE),
            PhysicsOwnerLaneScheduler.DEFAULT_QUEUE_CAPACITY,
            PhysicsOwnerLaneScheduler.DEFAULT_CLOSE_TIMEOUT);
        physicsOwnerResourceType = entityRegistry.registerResource(
            PhysicsOwnerResource.class,
            physicsOwnerLaneScheduler::createLane);
        worldCollisionProfilingResourceType = entityRegistry.registerResource(
            WorldCollisionProfilingResource.class,
            WorldCollisionProfilingResource::new);
        persistentPhysicsWorldResourceType = entityRegistry.registerResource(
            PersistentPhysicsWorldResource.class,
            "PersistentPhysicsWorld",
            PersistentPhysicsWorldResource.CODEC);
        physicsEventFramePublishedEventType =
            entityRegistry.registerWorldEventType(PhysicsEventFramePublishedEvent.class);
    }

    private void registerSystems() {
        ComponentRegistryProxy<ChunkStore> chunkRegistry = getChunkStoreRegistry();
        physicsStepSystem = new PhysicsStepSystem();
        chunkRegistry.registerSystem(physicsStepSystem);
        chunkRegistry.registerSystem(new PhysicsDebugSystem());

        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        physicsOwnerLifecycleSystem = new PhysicsOwnerLifecycleSystem();
        entityRegistry.registerSystem(physicsOwnerLifecycleSystem);
        persistenceRestoreGroup = entityRegistry.registerSystemGroup();
        entityRegistry.registerSystem(new PersistentPhysicsSpaceBootstrapSystem());
        entityRegistry.registerSystem(new PersistentPhysicsBodyHydrationSystem());
        entityRegistry.registerSystem(new PersistentPhysicsJointHydrationSystem());
        entityRegistry.registerSystem(new PhysicsRuntimeHolderSystem());
        entityRegistry.registerSystem(new RigidBodyLifecycleCleanupSystem());
        entityRegistry.registerSystem(new RigidBodyReconciliationSystem());
        entityRegistry.registerSystem(new PhysicsBodyAttachmentIndexSystem());
        entityRegistry.registerSystem(new PhysicsControlSessionCleanupSystem());
        entityRegistry.registerSystem(new PhysicsWorldCollisionStreamingSystem());
        entityRegistry.registerSystem(new PhysicsCollisionLodSystem());
        entityRegistry.registerSystem(new PhysicsSyncSystem());
        entityRegistry.registerSystem(new PhysicsDetachedVisualMaterializationSystem());
        entityRegistry.registerSystem(new PhysicsChunkBoundarySystem());
        entityRegistry.registerSystem(new PhysicsSnapshotPublicationSystem());
        entityRegistry.registerSystem(new PersistentPhysicsWorldSyncSystem());
        entityRegistry.registerSystem(new PhysicsKinematicControlSystem());
    }

    private void registerCommands() {
        CommandRegistry commandRegistry = getCommandRegistry();
        commandRegistry.registerCommand(new ImpulseCommand());
    }
}
