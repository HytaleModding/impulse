package dev.hytalemodding.impulse.core.internal.systems.visual;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Cleans EntityStore projections whose authoritative PhysicsStore row is gone.
 */
public class PhysicsProjectionCleanupSystem extends TickingSystem<EntityStore> {

    private static final int CLEANUP_INTERVAL_TICKS = 40;

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, PhysicsEntityTypes.persistenceRestoreGroup())
    );
    @Nonnull
    private final Map<Store<EntityStore>, Integer> cleanupCooldowns =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        clearDestroyedBodyAttachments(store);
        if (shouldSkipCleanup(store)) {
            return;
        }
        removeOrphanGeneratedVisualProxyMarkers(store);
    }

    private boolean shouldSkipCleanup(@Nonnull Store<EntityStore> store) {
        synchronized (cleanupCooldowns) {
            int cooldown = cleanupCooldowns.getOrDefault(store, 0);
            if (cooldown > 0) {
                cleanupCooldowns.put(store, cooldown - 1);
                return true;
            }
            cleanupCooldowns.put(store, CLEANUP_INTERVAL_TICKS);
            return false;
        }
    }

    private static void clearDestroyedBodyAttachments(@Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        Store<PhysicsStore> physicsStore = PhysicsThreading.storeOrNull(
            store.getExternalData().getWorld());
        store.forEachEntityParallel(attachmentType,
            (index, archetypeChunk, commandBuffer) -> {
                BodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    attachmentType);
                if (attachment == null || !hasMissingBody(resource, physicsStore, attachment)) {
                    return;
                }
                GeneratedProxyLifecycle.clearMissingAttachment(archetypeChunk.getReferenceTo(index),
                    attachment,
                    resource,
                    commandBuffer);
            });
    }

    private static boolean hasDestroyedBodyRef(@Nonnull BodyAttachmentComponent attachment) {
        Ref<PhysicsStore> bodyRef = attachment.getBodyRef();
        return bodyRef != null && !bodyRef.isValid();
    }

    private static boolean hasMissingBody(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nullable Store<PhysicsStore> store,
        @Nonnull BodyAttachmentComponent attachment) {
        if (hasDestroyedBodyRef(attachment)) {
            return true;
        }
        if (attachment.getBodyRef() != null
            || attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
            return false;
        }
        return store != null
            ? !PhysicsBodies.isRegistered(store, attachment.getBodyUuid())
            : !resource.hasPublishedBodyRegistration(attachment.getBodyUuid());
    }

    private static void removeOrphanGeneratedVisualProxyMarkers(@Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        ComponentType<EntityStore, GeneratedVisualProxyComponent> generatedProxyType =
            GeneratedVisualProxyComponent.getComponentType();
        store.forEachEntityParallel(generatedProxyType,
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, attachmentType) != null) {
                    return;
                }
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }
}
