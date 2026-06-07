package dev.hytalemodding.impulse.core.internal.systems.persistence;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import javax.annotation.Nonnull;

/**
 * Strips runtime-only core physics components from holders when entities cross unload/load
 * boundaries.
 */
public class PhysicsRuntimeHolderSystem extends HolderSystem<EntityStore> {

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final Query<EntityStore> QUERY = ATTACHMENT_TYPE;

    @Override
    public void onEntityAdd(@Nonnull Holder<EntityStore> holder,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store) {
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

    private static void cleanupHolder(@Nonnull Holder<EntityStore> holder,
        @Nonnull Store<EntityStore> store) {
        PhysicsBodyAttachmentComponent attachment = holder.getComponent(ATTACHMENT_TYPE);
        if (attachment == null
            || attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY) {
            holder.tryRemoveComponent(ATTACHMENT_TYPE);
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }
}
