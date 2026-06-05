package dev.hytalemodding.impulse.examples;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.examples.commands.ImpulseCommand;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockComponent;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveFuseComponent;
import dev.hytalemodding.impulse.examples.systems.BenchmarkEntityRemovalDiagnosticsSystem;
import dev.hytalemodding.impulse.examples.systems.ExplosiveFuseContactSystem;
import dev.hytalemodding.impulse.examples.systems.ExplosiveFuseTickSystem;
import dev.hytalemodding.impulse.examples.systems.PhysicsEventTrackingSystem;
import javax.annotation.Nonnull;

public final class ImpulseExamplesPlugin extends JavaPlugin {

    private static ImpulseExamplesPlugin instance;

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    public ImpulseExamplesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        registerComponents();
        registerSystems();
        registerCommands();
    }

    private void registerComponents() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        ExplosiveBlockComponent.setComponentType(entityRegistry.registerComponent(
            ExplosiveBlockComponent.class,
            "ExplosiveBlock",
            ExplosiveBlockComponent.CODEC));
        ExplosiveFuseComponent.setComponentType(entityRegistry.registerComponent(
            ExplosiveFuseComponent.class,
            "ExplosiveFuse",
            ExplosiveFuseComponent.CODEC));
    }

    private void registerSystems() {
        this.getEntityStoreRegistry()
            .registerSystem(new BenchmarkEntityRemovalDiagnosticsSystem());
        this.getEntityStoreRegistry()
            .registerSystem(new ExplosiveFuseContactSystem());
        this.getEntityStoreRegistry()
            .registerSystem(new ExplosiveFuseTickSystem());
        this.getEntityStoreRegistry()
            .registerSystem(new PhysicsEventTrackingSystem());
    }

    private void registerCommands() {
        CommandRegistry commandRegistry = this.getCommandRegistry();
        commandRegistry.registerCommand(new ImpulseCommand());
    }

    public static ImpulseExamplesPlugin get() {
        return instance;
    }
}
