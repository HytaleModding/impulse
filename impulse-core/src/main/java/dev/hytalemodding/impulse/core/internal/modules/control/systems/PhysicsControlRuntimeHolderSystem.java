package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Strips runtime-only control sessions from holders across entity load/unload.
 */
public final class PhysicsControlRuntimeHolderSystem extends HolderSystem<EntityStore> {

    @Nonnull
    private final ComponentType<EntityStore, ImpulseControllableComponent> controllableType;
    @Nonnull
    private final ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType;
    @Nonnull
    private final Query<EntityStore> query;

    public PhysicsControlRuntimeHolderSystem() {
        this(ImpulseControllableComponent.getComponentType(),
            PhysicsControlSessionComponent.getComponentType());
    }

    PhysicsControlRuntimeHolderSystem(
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nonnull ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        this.controllableType = Objects.requireNonNull(controllableType, "controllableType");
        this.sessionType = Objects.requireNonNull(sessionType, "sessionType");
        this.query = Query.or(controllableType, sessionType);
    }

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store) {
        ControlLifecycle.registerStore(store);
        if (reason == AddReason.LOAD) {
            cleanupHolder(holder, store);
        }
    }

    @Override
    public void onEntityRemoved(@Nonnull Holder<EntityStore> holder,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store) {
        cleanupHolder(holder, store);
    }

    private void cleanupHolder(@Nonnull Holder<EntityStore> holder,
        @Nonnull Store<EntityStore> store) {
        PhysicsControlSessionComponent session = holder.getComponent(sessionType);
        if (session != null) {
            PhysicsControlSessionCleanup.cleanup(store, session);
        }
        holder.tryRemoveComponent(sessionType);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }
}
