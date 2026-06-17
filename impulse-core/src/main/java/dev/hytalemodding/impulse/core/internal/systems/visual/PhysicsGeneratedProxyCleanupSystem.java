package dev.hytalemodding.impulse.core.internal.systems.visual;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;

/**
 * Removes serialized generated visual proxies left by the pre-PhysicsStore runtime model.
 */
public class PhysicsGeneratedProxyCleanupSystem extends TickingSystem<EntityStore> {

    private static final int CLEANUP_INTERVAL_TICKS = 40;

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, PhysicsEntityTypes.persistenceRestoreGroup())
    );
    @Nonnull
    private final Map<Store<EntityStore>, Integer> cleanupCooldowns =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (shouldSkipCleanup(store)) {
            return;
        }
        removeLegacyGeneratedVisualProxies(store);
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

    private static void removeLegacyGeneratedVisualProxies(@Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        store.forEachEntityParallel(attachmentType,
            (index, archetypeChunk, commandBuffer) -> {
                BodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    attachmentType);
                if (attachment == null
                    || attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                    return;
                }
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

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
