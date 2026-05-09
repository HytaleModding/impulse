package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;

import javax.annotation.Nonnull;

/**
 * Steps the physics world each tick.
 */
public class PhysicsStepSystem extends TickingSystem<ChunkStore>
{
    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store)
    {
        var world = store.getExternalData().getWorld();
        var entityStore = world.getEntityStore().getStore();
        PhysicsWorldResource resource = entityStore.getResource(PhysicsWorldResource.getResourceType());

        resource.getSpace().step(dt);
    }
}
