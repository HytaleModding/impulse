package dev.hytalemodding.impulse.core.plugin.modules.worldcollision;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.commands.WorldCollisionCommandContributions;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsChunkBoundarySystem;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsCollisionLodSystem;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsWorldCollisionStreamingSystem;
import javax.annotation.Nonnull;

/**
 * Subplugin that enables Impulse world collision.
 */
public final class ImpulseWorldCollisionPlugin extends JavaPlugin {

    public ImpulseWorldCollisionPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        WorldCollisionProfilingResource.setResourceType(entityRegistry.registerResource(
            WorldCollisionProfilingResource.class,
            WorldCollisionProfilingResource::new));
        entityRegistry.registerSystem(new PhysicsWorldCollisionStreamingSystem());
        entityRegistry.registerSystem(new PhysicsCollisionLodSystem());
        entityRegistry.registerSystem(new PhysicsChunkBoundarySystem());

        WorldCollisionCommandContributions.register();
        WorldCollisionLifecycle.enable();
    }

    @Override
    protected void shutdown() {
        WorldCollisionLifecycle.disable();
        WorldCollisionCommandContributions.unregister();
        WorldCollisionProfilingResource.clearResourceType();
    }
}
