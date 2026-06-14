package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Holder/component helpers for authoritative PhysicsStore aggregate entities.
 */
public final class PhysicsStoreEntities {

    private PhysicsStoreEntities() {
    }

    @Nonnull
    public static Holder<PhysicsStore> bodyHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyComponent body,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target,
        @Nonnull ColliderComponent collider,
        @Nonnull ShapeComponent shape,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter) {
        Holder<PhysicsStore> holder = store.getRegistry().newHolder();
        holder.addComponent(UuidComponent.getComponentType(),
            new UuidComponent(Objects.requireNonNull(bodyUuid, "bodyUuid")));
        addBodyComponents(holder, body, dynamics, target, collider, shape, material, filter);
        return holder;
    }

    public static void addBodyComponents(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull BodyComponent body,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target,
        @Nonnull ColliderComponent collider,
        @Nonnull ShapeComponent shape,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter) {
        Objects.requireNonNull(holder, "holder")
            .addComponent(BodyComponent.getComponentType(),
                Objects.requireNonNull(body, "body").clone());
        holder.addComponent(DynamicsComponent.getComponentType(),
            Objects.requireNonNull(dynamics, "dynamics").clone());
        holder.addComponent(ColliderComponent.getComponentType(),
            Objects.requireNonNull(collider, "collider").clone());
        holder.addComponent(ShapeComponent.getComponentType(),
            Objects.requireNonNull(shape, "shape").clone());
        holder.addComponent(MaterialComponent.getComponentType(),
            Objects.requireNonNull(material, "material").clone());
        holder.addComponent(CollisionFilterComponent.getComponentType(),
            Objects.requireNonNull(filter, "filter").clone());
        if (target != null) {
            holder.addComponent(TargetComponent.getComponentType(), target.clone());
        }
    }

    public static void putBodyComponents(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull BodyComponent body,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target,
        @Nonnull ColliderComponent collider,
        @Nonnull ShapeComponent shape,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(ref, "ref");
        store.putComponent(ref,
            BodyComponent.getComponentType(),
            Objects.requireNonNull(body, "body").clone());
        store.putComponent(ref,
            DynamicsComponent.getComponentType(),
            Objects.requireNonNull(dynamics, "dynamics").clone());
        store.putComponent(ref,
            ColliderComponent.getComponentType(),
            Objects.requireNonNull(collider, "collider").clone());
        store.putComponent(ref,
            ShapeComponent.getComponentType(),
            Objects.requireNonNull(shape, "shape").clone());
        store.putComponent(ref,
            MaterialComponent.getComponentType(),
            Objects.requireNonNull(material, "material").clone());
        store.putComponent(ref,
            CollisionFilterComponent.getComponentType(),
            Objects.requireNonNull(filter, "filter").clone());
        if (target != null) {
            store.putComponent(ref, TargetComponent.getComponentType(), target.clone());
        } else {
            store.removeComponent(ref, TargetComponent.getComponentType());
        }
    }
}
