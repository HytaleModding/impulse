package dev.hytalemodding.impulse.examples.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.examples.events.PhysicsEventTracker;
import javax.annotation.Nonnull;

public final class PhysicsEventTrackingSystem
    extends WorldEventSystem<EntityStore, PhysicsEventFramePublishedEvent> {

    public PhysicsEventTrackingSystem() {
        super(PhysicsEventFramePublishedEvent.class);
    }

    @Override
    public void handle(@Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsEventFramePublishedEvent event) {
        PhysicsEventTracker.record(event.frame());
    }
}
