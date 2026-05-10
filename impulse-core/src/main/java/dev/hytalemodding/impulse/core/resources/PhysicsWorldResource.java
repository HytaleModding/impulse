package dev.hytalemodding.impulse.core.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.ImpulseSpace;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;

/**
 * ECS resource that holds the physics space for a world.
 */
public class PhysicsWorldResource implements Resource<EntityStore> {

    private ImpulseSpace space;

    @Setter
    @Getter
    private boolean debugEnabled;

    public PhysicsWorldResource() {
    }

    @Nonnull
    public ImpulseSpace getSpace() {
        if (space == null) {
            space = ImpulsePlugin.createDefaultSpace();
        }
        return space;
    }

    public static ResourceType<EntityStore, PhysicsWorldResource> getResourceType()
    {
        return ImpulsePlugin.get().getPhysicsWorldResourceType();
    }

    @Nonnull
    @Override
    public PhysicsWorldResource clone() {
        PhysicsWorldResource copy = new PhysicsWorldResource();
        // FIXME: deepcopy?
        copy.space = space;
        return copy;
    }
}
