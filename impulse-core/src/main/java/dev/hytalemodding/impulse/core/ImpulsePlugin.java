package dev.hytalemodding.impulse.core;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.systems.PhysicsCleanupSystem;
import dev.hytalemodding.impulse.core.systems.PhysicsStepSystem;
import dev.hytalemodding.impulse.core.systems.PhysicsSyncSystem;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import lombok.Getter;

public final class ImpulsePlugin extends JavaPlugin {

    private static ImpulsePlugin instance;
    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    @Getter
    private ComponentType<EntityStore, PhysicsBodyComponent> physicsBodyComponentType;

    @Getter
    private ResourceType<EntityStore, PhysicsWorldResource> physicsWorldResourceType;

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
                .filter(backend -> configuredBackendId.get().equals(backend.getId().value()))
                .map(PhysicsBackend::getId)
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
        physicsBodyComponentType = entityRegistry.registerComponent(PhysicsBodyComponent.class,
            PhysicsBodyComponent::new);
        physicsWorldResourceType = entityRegistry.registerResource(PhysicsWorldResource.class,
            PhysicsWorldResource::new);
    }

    private void registerSystems() {
        ComponentRegistryProxy<ChunkStore> chunkRegistry = getChunkStoreRegistry();
        chunkRegistry.registerSystem(new PhysicsStepSystem());
        chunkRegistry.registerSystem(new PhysicsDebugSystem());

        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        entityRegistry.registerSystem(new PhysicsSyncSystem());
        entityRegistry.registerSystem(new PhysicsCleanupSystem());
    }
}
