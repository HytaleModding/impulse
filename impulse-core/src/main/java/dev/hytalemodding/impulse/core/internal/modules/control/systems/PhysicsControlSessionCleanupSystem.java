package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PhysicsControlSessionCleanupSystem
    extends RefChangeSystem<EntityStore, PhysicsControlSessionComponent> {

    @Nonnull
    private final ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType;
    @Nonnull
    private final Query<EntityStore> query;

    public PhysicsControlSessionCleanupSystem() {
        this(PhysicsControlSessionComponent.getComponentType());
    }

    PhysicsControlSessionCleanupSystem(
        @Nonnull ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        this.sessionType = Objects.requireNonNull(sessionType, "sessionType");
        this.query = sessionType;
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsControlSessionComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!ControlLifecycle.isEnabled()) {
            PhysicsControlSessionCleanup.cleanup(store, component);
            commandBuffer.removeComponent(ref, sessionType);
            return;
        }
        ControlLifecycle.registerStore(store);
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
        @Nullable PhysicsControlSessionComponent oldComponent,
        @Nonnull PhysicsControlSessionComponent newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!ControlLifecycle.isEnabled()) {
            assert oldComponent != null;
            PhysicsControlSessionCleanup.cleanup(store, oldComponent);
            PhysicsControlSessionCleanup.cleanup(store, newComponent);
            commandBuffer.removeComponent(ref, sessionType);
            return;
        }
        ControlLifecycle.registerStore(store);
        assert oldComponent != null;
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

    @Nonnull
    @Override
    public ComponentType<EntityStore, PhysicsControlSessionComponent> componentType() {
        return sessionType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    private static boolean sameSessionOwner(@Nonnull PhysicsControlSessionComponent first,
        @Nonnull PhysicsControlSessionComponent second) {
        return Objects.equals(first.getBodyUuid(), second.getBodyUuid())
            && Objects.equals(first.getAnchorBodyUuid(), second.getAnchorBodyUuid())
            && Objects.equals(first.getControlJointKey(), second.getControlJointKey());
    }
}
