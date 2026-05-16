package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsControlSessionComponent;
import javax.annotation.Nonnull;

/**
 * Strips runtime-only physics components from holders when entities cross
 * unload/load boundaries, and marks persisted bodies for later rehydration.
 */
public class PhysicsRuntimeHolderSystem extends HolderSystem<EntityStore> {

    private static final ComponentType<EntityStore, PersistentPhysicsBodyComponent>
        PERSISTENT_BODY_TYPE = PersistentPhysicsBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyComponent> PHYSICS_BODY_TYPE =
        PhysicsBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, ImpulseControllableComponent>
        IMPULSE_CONTROLLABLE_TYPE = ImpulseControllableComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsControlSessionComponent>
        PHYSICS_CONTROL_SESSION_TYPE = PhysicsControlSessionComponent.getComponentType();
    private static final Query<EntityStore> QUERY = Query.or(
        PERSISTENT_BODY_TYPE,
        PHYSICS_BODY_TYPE,
        IMPULSE_CONTROLLABLE_TYPE,
        PHYSICS_CONTROL_SESSION_TYPE);

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store) {
        if (reason == AddReason.LOAD) {
            cleanupHolder(holder, false);
        }
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store) {
        cleanupHolder(holder, reason == RemoveReason.UNLOAD);
    }

    private static void cleanupHolder(@Nonnull Holder<EntityStore> holder, boolean markForRebuild) {
        boolean removedRuntimeBody = holder.tryRemoveComponent(PHYSICS_BODY_TYPE);
        holder.tryRemoveComponent(IMPULSE_CONTROLLABLE_TYPE);
        holder.tryRemoveComponent(PHYSICS_CONTROL_SESSION_TYPE);

        PersistentPhysicsBodyComponent persistent = holder.getComponent(PERSISTENT_BODY_TYPE);
        if (persistent != null && (markForRebuild || removedRuntimeBody)) {
            persistent.markForBodyRebuild();
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }
}
