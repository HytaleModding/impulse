package dev.hytalemodding.impulse.core.plugin.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import javax.annotation.Nonnull;

/**
 * Public resource type for the EntityStore-side physics runtime.
 *
 * <p>The concrete Impulse runtime lives in the internal package. Plugin code should use
 * {@code world.getPhysicsStore().getStore()} and {@code core.plugin.physicsstore} helpers for
 * PhysicsStore ECS reads and writes.</p>
 */
public abstract class PhysicsWorldResource implements Resource<EntityStore> {

    protected PhysicsWorldResource() {
    }

    public static ResourceType<EntityStore, PhysicsWorldResource> getResourceType() {
        return PhysicsEntityTypes.physicsWorldResourceType();
    }

    @Nonnull
    @Override
    public abstract PhysicsWorldResource clone();
}
