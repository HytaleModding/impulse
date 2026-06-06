package dev.hytalemodding.impulse.examples.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.Test;

class ExampleBlockEntityVisualsTest {

    @Test
    void impulseOwnedBlockVisualsDoNotKeepHytaleVelocity() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ComponentType<EntityStore, DespawnComponent> despawnType =
            registry.registerComponent(DespawnComponent.class, DespawnComponent::new);
        ComponentType<EntityStore, Velocity> velocityType =
            registry.registerComponent(Velocity.class, Velocity::new);
        Holder<EntityStore> holder = registry.newHolder();
        holder.addComponent(despawnType, new DespawnComponent());
        holder.addComponent(velocityType, new Velocity());

        ExampleBlockEntityVisuals.stripHytaleRuntimeComponents(holder, despawnType, velocityType);

        assertFalse(holder.getArchetype().contains(despawnType));
        assertFalse(holder.getArchetype().contains(velocityType));
    }
}
