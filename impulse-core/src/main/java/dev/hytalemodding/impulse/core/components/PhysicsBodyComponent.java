package dev.hytalemodding.impulse.core.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.ImpulseBody;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;

/**
 * ECS component that links a Hytale entity to a Impulse Rigid body.
 */
public class PhysicsBodyComponent implements Component<EntityStore>
{
    private ImpulseBody body;

    public PhysicsBodyComponent()
    {
    }

    public PhysicsBodyComponent(@Nonnull ImpulseBody body)
    {
        this.body = body;
    }

    @Nonnull
    public ImpulseBody getBody()
    {
        return body;
    }

    public void setBody(@Nonnull ImpulseBody body)
    {
        this.body = body;
    }

    public static ComponentType<EntityStore, PhysicsBodyComponent> getComponentType()
    {
        return ImpulsePlugin.get().getPhysicsBodyComponentType();
    }

    @Nonnull
    @Override
    public PhysicsBodyComponent clone()
    {
        return new PhysicsBodyComponent(body);
    }
}
