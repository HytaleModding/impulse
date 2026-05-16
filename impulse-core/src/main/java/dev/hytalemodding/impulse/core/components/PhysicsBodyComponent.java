package dev.hytalemodding.impulse.core.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

/**
 * ECS component that links a Hytale entity to an Impulse PhysicsBody.
 */
public class PhysicsBodyComponent implements Component<EntityStore> {

    @Setter
    @Getter(onMethod_ = @__(@Nonnull))
    private PhysicsBody body;

    @Setter
    @Getter
    @Nullable
    private SpaceId spaceId;

    public PhysicsBodyComponent() {
    }

    public PhysicsBodyComponent(@Nonnull PhysicsBody body, @Nullable SpaceId spaceId) {
        this.body = body;
        this.spaceId = spaceId;
    }

    public static ComponentType<EntityStore, PhysicsBodyComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyComponentType();
    }

    @Nonnull
    @Override
    public PhysicsBodyComponent clone() {
        return new PhysicsBodyComponent(body, spaceId);
    }
}
