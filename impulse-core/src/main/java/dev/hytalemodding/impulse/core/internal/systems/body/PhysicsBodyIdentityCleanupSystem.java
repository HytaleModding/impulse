package dev.hytalemodding.impulse.core.internal.systems.body;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyIdentityComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Destroys entity-authored backend bodies when their durable ECS identity leaves the entity.
 */
public final class PhysicsBodyIdentityCleanupSystem
    extends RefChangeSystem<EntityStore, PhysicsBodyIdentityComponent> {

    @Nonnull
    private final ComponentType<EntityStore, PhysicsBodyIdentityComponent> identityType;
    @Nonnull
    private final ResourceType<EntityStore, ? extends PhysicsWorldResource> physicsWorldType;
    @Nonnull
    private final Query<EntityStore> query;

    public PhysicsBodyIdentityCleanupSystem() {
        this(PhysicsBodyIdentityComponent.getComponentType(), PhysicsWorldResource.getResourceType());
    }

    PhysicsBodyIdentityCleanupSystem(
        @Nonnull ComponentType<EntityStore, PhysicsBodyIdentityComponent> identityType,
        @Nonnull ResourceType<EntityStore, ? extends PhysicsWorldResource> physicsWorldType) {
        this.identityType = Objects.requireNonNull(identityType, "identityType");
        this.physicsWorldType = Objects.requireNonNull(physicsWorldType, "physicsWorldType");
        this.query = identityType;
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyIdentityComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
        @Nullable PhysicsBodyIdentityComponent oldComponent,
        @Nonnull PhysicsBodyIdentityComponent newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (oldComponent != null && !sameIdentity(oldComponent, newComponent)) {
            PhysicsWorldRuntimeResource.require(store.getResource(physicsWorldType))
                .destroyBody(oldComponent.getBodyKey());
        }
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyIdentityComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsWorldRuntimeResource.require(store.getResource(physicsWorldType))
            .destroyBody(component.getBodyKey());
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, PhysicsBodyIdentityComponent> componentType() {
        return identityType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    private static boolean sameIdentity(@Nonnull PhysicsBodyIdentityComponent first,
        @Nonnull PhysicsBodyIdentityComponent second) {
        return first.getBodyKey().equals(second.getBodyKey())
            && Objects.equals(first.getSpaceId(), second.getSpaceId())
            && first.getPersistenceMode() == second.getPersistenceMode();
    }
}
