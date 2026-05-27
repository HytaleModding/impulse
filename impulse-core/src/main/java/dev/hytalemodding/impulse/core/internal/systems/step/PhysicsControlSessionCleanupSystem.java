package dev.hytalemodding.impulse.core.internal.systems.step;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsControlSessionComponent;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class PhysicsControlSessionCleanupSystem
    extends RefChangeSystem<EntityStore, PhysicsControlSessionComponent> {

    private static final ComponentType<EntityStore, PhysicsControlSessionComponent> SESSION_TYPE =
        PhysicsControlSessionComponent.getComponentType();
    private static final Query<EntityStore> QUERY = SESSION_TYPE;

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsControlSessionComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsControlSessionComponent oldComponent,
        @Nonnull PhysicsControlSessionComponent newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!sameSessionOwner(oldComponent, newComponent)) {
            PhysicsControlSessionCleanup.cleanup(store, oldComponent);
        }
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsControlSessionComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsControlSessionCleanup.cleanup(store, component);
    }

    @Override
    public ComponentType<EntityStore, PhysicsControlSessionComponent> componentType() {
        return SESSION_TYPE;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    private static boolean sameSessionOwner(@Nonnull PhysicsControlSessionComponent first,
        @Nonnull PhysicsControlSessionComponent second) {
        return Objects.equals(first.getBodyId(), second.getBodyId())
            && Objects.equals(first.getAnchorBodyId(), second.getAnchorBodyId())
            && Objects.equals(first.getSpaceId(), second.getSpaceId());
    }
}
