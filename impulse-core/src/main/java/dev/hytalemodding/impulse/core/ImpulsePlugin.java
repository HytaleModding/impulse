package dev.hytalemodding.impulse.core;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.ImpulseBody;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.ImpulseSpace;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.systems.PhysicsStepSystem;
import dev.hytalemodding.impulse.core.systems.PhysicsSyncSystem;
import javax.annotation.Nonnull;

import lombok.Getter;

public final class ImpulsePlugin extends JavaPlugin
{
    private static ImpulsePlugin INSTANCE;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Getter private ComponentType<EntityStore, PhysicsBodyComponent> physicsBodyComponentType;

    @Getter private ResourceType<EntityStore, PhysicsWorldResource> physicsWorldResourceType;

    public ImpulsePlugin(@Nonnull JavaPluginInit init)
    {
        super(init);
        INSTANCE = this;
    }


    @Override
    protected void setup()
    {
        Impulse.init();

        registerComponents();
        registerSystems();
    }

    private void registerComponents()
    {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        physicsBodyComponentType = entityRegistry.registerComponent(PhysicsBodyComponent.class, PhysicsBodyComponent::new);
        physicsWorldResourceType = entityRegistry.registerResource(PhysicsWorldResource.class, PhysicsWorldResource::new);
    }

    private void registerSystems()
    {
        ComponentRegistryProxy<ChunkStore> chunkRegistry = getChunkStoreRegistry();
        chunkRegistry.registerSystem(new PhysicsStepSystem());

        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        entityRegistry.registerSystem(new PhysicsSyncSystem());
    }

    public static ImpulseSpace createDefaultSpace()
    {
        ImpulseSpace space = Impulse.createSpace();
        // FIXME: ad hoc Y value for testing
        ImpulseBody ground = Impulse.createStaticPlane(122f);
        space.addBody(ground);
        return space;
    }

    public static ImpulsePlugin get() {
        return INSTANCE;
    }
}
