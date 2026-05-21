package dev.hytalemodding.impulse.core;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
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
import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommand;
import dev.hytalemodding.impulse.core.plugin.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.systems.PersistentPhysicsBodyHydrationSystem;
import dev.hytalemodding.impulse.core.internal.systems.PersistentPhysicsJointHydrationSystem;
import dev.hytalemodding.impulse.core.internal.systems.PersistentPhysicsSpaceBootstrapSystem;
import dev.hytalemodding.impulse.core.internal.systems.PersistentPhysicsWorldSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsBodyAttachmentSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsChunkBoundarySystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsDebugSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsDetachedVisualMaterializationSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsKinematicControlSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsRuntimeHolderSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsStepSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsWorldCollisionStreamingSystem;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import lombok.Getter;

public final class ImpulsePlugin extends JavaPlugin {

    private static ImpulsePlugin instance;
    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    @Getter
    private ComponentType<EntityStore, PhysicsBodyAttachmentComponent> physicsBodyAttachmentComponentType;

    @Getter
    private ComponentType<EntityStore, ImpulseControllableComponent> impulseControllableComponentType;

    @Getter
    private ComponentType<EntityStore, PhysicsControlSessionComponent> physicsControlSessionComponentType;

    @Getter
    private ResourceType<EntityStore, PhysicsWorldResource> physicsWorldResourceType;

    @Getter
    private ResourceType<EntityStore, PhysicsDebugResource> physicsDebugResourceType;

    @Getter
    private ResourceType<EntityStore, PhysicsRuntimeProfilingResource> physicsRuntimeProfilingResourceType;

    @Getter
    private ResourceType<EntityStore, WorldCollisionProfilingResource> worldCollisionProfilingResourceType;

    @Getter
    private ResourceType<EntityStore, PersistentPhysicsWorldResource> persistentPhysicsWorldResourceType;

    @Getter
    private SystemGroup<EntityStore> persistenceRestoreGroup;

    @Getter
    private BackendId defaultBackendId;

    public ImpulsePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static ImpulsePlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        discoverBackends();

        registerComponents();
        registerSystems();
        registerCommands();
    }

    @Override
    protected void start() {
        registerCrucibleSuites();
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

    private void discoverBackends() {
        ServiceLoader<PhysicsBackend> loader = ServiceLoader.load(PhysicsBackend.class);
        for (PhysicsBackend backend : loader) {
            Impulse.registerBackend(backend);
            backend.setDataDirectory(getDataDirectory());
            LOGGER.at(Level.INFO).log("Registered physics backend %s", backend.getId());
        }

        if (Impulse.getBackends().isEmpty()) {
            throw new IllegalStateException("No physics backends discovered");
        }

        Optional<String> configuredBackendId = getConfiguredBackendId();
        if (configuredBackendId.isPresent()) {
            defaultBackendId = Impulse.getBackends().stream()
                .map(PhysicsBackend::getId)
                .filter(id -> configuredBackendId.get().equals(id.value()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "Configured physics backend " + configuredBackendId.get()
                        + " was not discovered. Available backends: " + getAvailableBackendIds()));
            LOGGER.at(Level.INFO).log("Using configured physics backend %s", defaultBackendId);
            return;
        }

        Optional<PhysicsBackend> bullet = Impulse.getBackends().stream()
            .filter(backend -> "impulse:bullet".equals(backend.getId().value()))
            .findFirst();

        defaultBackendId = bullet
            .map(PhysicsBackend::getId)
            .orElseGet(() -> Impulse.getBackends().iterator().next().getId());
        LOGGER.at(Level.INFO).log("Using default physics backend %s", defaultBackendId);
    }

    @Nonnull
    private Optional<String> getConfiguredBackendId() {
        String systemProperty = System.getProperty("impulse.backend");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return Optional.of(systemProperty.trim());
        }

        String environmentVariable = System.getenv("IMPULSE_BACKEND");
        if (environmentVariable != null && !environmentVariable.isBlank()) {
            return Optional.of(environmentVariable.trim());
        }

        return Optional.empty();
    }

    @Nonnull
    private String getAvailableBackendIds() {
        StringBuilder ids = new StringBuilder();
        for (PhysicsBackend backend : Impulse.getBackends()) {
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
        impulseControllableComponentType = entityRegistry.registerComponent(
            ImpulseControllableComponent.class,
            "ImpulseControllable",
            ImpulseControllableComponent.CODEC);
        physicsControlSessionComponentType = entityRegistry.registerComponent(
            PhysicsControlSessionComponent.class, PhysicsControlSessionComponent::new);
        physicsWorldResourceType = entityRegistry.registerResource(PhysicsWorldResource.class,
            PhysicsWorldResource::new);
        physicsDebugResourceType = entityRegistry.registerResource(PhysicsDebugResource.class,
            PhysicsDebugResource::new);
        physicsRuntimeProfilingResourceType = entityRegistry.registerResource(
            PhysicsRuntimeProfilingResource.class,
            PhysicsRuntimeProfilingResource::new);
        worldCollisionProfilingResourceType = entityRegistry.registerResource(
            WorldCollisionProfilingResource.class,
            WorldCollisionProfilingResource::new);
        persistentPhysicsWorldResourceType = entityRegistry.registerResource(
            PersistentPhysicsWorldResource.class,
            "PersistentPhysicsWorld",
            PersistentPhysicsWorldResource.CODEC);
    }

    private void registerSystems() {
        ComponentRegistryProxy<ChunkStore> chunkRegistry = getChunkStoreRegistry();
        chunkRegistry.registerSystem(new PhysicsStepSystem());
        chunkRegistry.registerSystem(new PhysicsDebugSystem());

        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        persistenceRestoreGroup = entityRegistry.registerSystemGroup();
        entityRegistry.registerSystem(new PersistentPhysicsSpaceBootstrapSystem());
        entityRegistry.registerSystem(new PersistentPhysicsBodyHydrationSystem());
        entityRegistry.registerSystem(new PersistentPhysicsJointHydrationSystem());
        entityRegistry.registerSystem(new PhysicsRuntimeHolderSystem());
        entityRegistry.registerSystem(new PhysicsBodyAttachmentSystem());
        entityRegistry.registerSystem(new PhysicsWorldCollisionStreamingSystem());
        entityRegistry.registerSystem(new PhysicsSyncSystem());
        entityRegistry.registerSystem(new PhysicsDetachedVisualMaterializationSystem());
        entityRegistry.registerSystem(new PhysicsChunkBoundarySystem());
        entityRegistry.registerSystem(new PersistentPhysicsWorldSyncSystem());
        entityRegistry.registerSystem(new PhysicsKinematicControlSystem());
    }

    private void registerCommands() {
        CommandRegistry commandRegistry = getCommandRegistry();
        commandRegistry.registerCommand(new ImpulseCommand());
    }
}
