package dev.hytalemodding.impulse.core;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.ImpulseBackendRegistry;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommandTreeRegistry;
import dev.hytalemodding.impulse.core.internal.modules.ImpulseSubPluginRegistration;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.PhysicsStoreRegistration;
import dev.hytalemodding.impulse.core.internal.PhysicsStoreEarlyPluginProbe;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ImpulsePlugin extends JavaPlugin {

    private static ImpulsePlugin instance;
    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

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
        PhysicsComponentTypeRegistry.registerComponentTypes(physicsStoreRegistry);
        PhysicsStoreRegistration.register(physicsStoreRegistry);
        ImpulseSubPluginRegistration.register(this);
        discoverBackends();

        registerCommands();
    }

    @Override
    protected void shutdown() {
        ImpulseCommandTreeRegistry.unregister();
    }

    private void discoverBackends() {
        for (PhysicsBackendRuntimeProvider provider : BackendDiscovery.discoverRuntimeProviders(
            backendSearchRoots(),
            getClassLoader())) {
            ImpulseBackendRegistry.registerRuntimeProvider(provider);
        }

        for (PhysicsBackendRuntimeProvider provider : ImpulseBackendRegistry.getRuntimeProviders()) {
            LOGGER.at(Level.INFO).log("Registered physics backend runtime %s", provider.getId());
        }

        if (ImpulseBackendRegistry.getRuntimeProviders().isEmpty()) {
            throw new IllegalStateException("No physics backends discovered");
        }

        defaultBackendId = selectDefaultRuntimeProviderId(ImpulseBackendRegistry.getRuntimeProviders());
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
        for (PhysicsBackendRuntimeProvider backend : ImpulseBackendRegistry.getRuntimeProviders()) {
            if (!ids.isEmpty()) {
                ids.append(", ");
            }
            ids.append(backend.getId().value());
        }
        return ids.toString();
    }

    private void registerCommands() {
        CommandRegistry commandRegistry = getCommandRegistry();
        ImpulseCommandTreeRegistry.register(commandRegistry);
    }

}
