package dev.hytalemodding.impulse.core.internal.systems.visual;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent.AttachmentLifecycle;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class GameplayAttachmentSnapshot {

    @Nonnull
    private final BodyKeySource source;
    @Nullable
    private Set<RigidBodyKey> bodyKeys;

    private GameplayAttachmentSnapshot(@Nonnull BodyKeySource source) {
        this.source = source;
    }

    @Nonnull
    static GameplayAttachmentSnapshot forStore(@Nonnull Store<EntityStore> store) {
        return fromSource(() -> collectGameplayAttachmentBodyKeys(store));
    }

    @Nonnull
    static GameplayAttachmentSnapshot fromSource(@Nonnull BodyKeySource source) {
        return new GameplayAttachmentSnapshot(source);
    }

    boolean hasKnownGameplayAttachment(boolean runtimeIndexHasAttachment,
        @Nonnull RigidBodyKey bodyKey) {
        if (runtimeIndexHasAttachment) {
            return true;
        }
        return hasGameplayAttachment(bodyKey);
    }

    boolean hasGameplayAttachment(@Nonnull RigidBodyKey bodyKey) {
        return bodyKeys().contains(bodyKey);
    }

    @Nonnull
    private Set<RigidBodyKey> bodyKeys() {
        if (bodyKeys == null) {
            bodyKeys = source.bodyKeys();
        }
        return bodyKeys;
    }

    @Nonnull
    private static Set<RigidBodyKey> collectGameplayAttachmentBodyKeys(
        @Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        Queue<RigidBodyKey> bodyKeys = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(attachmentType,
            (index, archetypeChunk, _) -> {
                BodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    attachmentType);
                if (attachment != null
                    && attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                    bodyKeys.add(RigidBodyKey.of(attachment.getBodyUuid()));
                }
            });
        Set<RigidBodyKey> uniqueBodyKeys = new ObjectOpenHashSet<>();
        uniqueBodyKeys.addAll(bodyKeys);
        return uniqueBodyKeys;
    }

    @FunctionalInterface
    interface BodyKeySource {

        @Nonnull
        Set<RigidBodyKey> bodyKeys();
    }
}
