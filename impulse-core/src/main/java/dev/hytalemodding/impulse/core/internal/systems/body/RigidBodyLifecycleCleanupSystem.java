package dev.hytalemodding.impulse.core.internal.systems.body;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyLifecycleComponent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies explicit backend destruction when ECS ownership is removed.
 */
public class RigidBodyLifecycleCleanupSystem
    extends RefChangeSystem<EntityStore, RigidBodyLifecycleComponent> {

    private static final ComponentType<EntityStore, RigidBodyLifecycleComponent> LIFECYCLE_TYPE =
        RigidBodyLifecycleComponent.getComponentType();
    private static final Query<EntityStore> QUERY = LIFECYCLE_TYPE;

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull RigidBodyLifecycleComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
        @Nullable RigidBodyLifecycleComponent oldComponent,
        @Nonnull RigidBodyLifecycleComponent newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (oldComponent != null
            && shouldDestroy(oldComponent)
            && (!sameBody(oldComponent, newComponent)
                || oldComponent.getOwnership() != newComponent.getOwnership())) {
            destroy(store, oldComponent);
        }
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
        @Nonnull RigidBodyLifecycleComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (shouldDestroy(component)) {
            destroy(store, component);
        }
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, RigidBodyLifecycleComponent> componentType() {
        return LIFECYCLE_TYPE;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    private static boolean shouldDestroy(@Nonnull RigidBodyLifecycleComponent component) {
        return component.getBodyKey() != null
            && component.getOwnership() == RigidBodyComponent.Ownership.ENTITY_OWNED
            && component.getState() != RigidBodyLifecycleComponent.State.FAILED
            && component.getState() != RigidBodyLifecycleComponent.State.DESTROYED;
    }

    private static boolean sameBody(@Nonnull RigidBodyLifecycleComponent first,
        @Nonnull RigidBodyLifecycleComponent second) {
        RigidBodyKey firstKey = first.getBodyKey();
        return firstKey != null && firstKey.equals(second.getBodyKey());
    }

    private static void destroy(@Nonnull Store<EntityStore> store,
        @Nonnull RigidBodyLifecycleComponent component) {
        RigidBodyKey bodyKey = component.getBodyKey();
        if (bodyKey != null) {
            PhysicsWorldRuntimeResource.require(store).destroyBody(bodyKey);
        }
    }
}
