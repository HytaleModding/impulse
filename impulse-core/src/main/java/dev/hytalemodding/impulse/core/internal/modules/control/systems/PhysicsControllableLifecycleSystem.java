package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tracks stores that contain control-module marker components so unload cleanup can strip them.
 */
public final class PhysicsControllableLifecycleSystem
    extends RefChangeSystem<EntityStore, ImpulseControllableComponent> {

    @Nonnull
    private final ComponentType<EntityStore, ImpulseControllableComponent> controllableType;
    @Nonnull
    private final Query<EntityStore> query;

    public PhysicsControllableLifecycleSystem() {
        this(ImpulseControllableComponent.getComponentType());
    }

    PhysicsControllableLifecycleSystem(
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> controllableType) {
        this.controllableType = Objects.requireNonNull(controllableType, "controllableType");
        this.query = controllableType;
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull ImpulseControllableComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!ControlLifecycle.isEnabled()) {
            commandBuffer.removeComponent(ref, controllableType);
            return;
        }
        ControlLifecycle.registerStore(store);
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
        @Nullable ImpulseControllableComponent oldComponent,
        @Nonnull ImpulseControllableComponent newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!ControlLifecycle.isEnabled()) {
            commandBuffer.removeComponent(ref, controllableType);
            return;
        }
        ControlLifecycle.registerStore(store);
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
        @Nonnull ImpulseControllableComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, ImpulseControllableComponent> componentType() {
        return controllableType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }
}
