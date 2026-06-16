package dev.hytalemodding.impulse.core.internal.systems.visual;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent.AttachmentLifecycle;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class GameplayAttachmentSnapshot {

    @Nonnull
    private final AttachmentSource source;
    @Nullable
    private AttachmentBodies bodies;

    private GameplayAttachmentSnapshot(@Nonnull AttachmentSource source) {
        this.source = source;
    }

    @Nonnull
    static GameplayAttachmentSnapshot forStore(@Nonnull Store<EntityStore> store) {
        return fromAttachmentSource(() -> collectGameplayAttachments(store));
    }

    @Nonnull
    static GameplayAttachmentSnapshot fromSource(@Nonnull BodyKeySource source) {
        return fromAttachmentSource(() -> {
            Set<UUID> bodyUuids = new ObjectOpenHashSet<>();
            for (RigidBodyKey bodyKey : source.bodyKeys()) {
                bodyUuids.add(bodyKey.value());
            }
            return new AttachmentBodies(bodyUuids, new Int2ObjectOpenHashMap<>());
        });
    }

    @Nonnull
    private static GameplayAttachmentSnapshot fromAttachmentSource(@Nonnull AttachmentSource source) {
        return new GameplayAttachmentSnapshot(source);
    }

    boolean hasKnownGameplayAttachment(boolean runtimeIndexHasAttachment,
        @Nonnull RigidBodyKey bodyKey) {
        return hasKnownGameplayAttachment(runtimeIndexHasAttachment, null, bodyKey.value());
    }

    boolean hasKnownGameplayAttachment(boolean runtimeIndexHasAttachment,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull RigidBodyKey bodyKey) {
        return hasKnownGameplayAttachment(runtimeIndexHasAttachment, bodyRef, bodyKey.value());
    }

    boolean hasKnownGameplayAttachment(boolean runtimeIndexHasAttachment,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid) {
        if (runtimeIndexHasAttachment) {
            return true;
        }
        return hasGameplayAttachment(bodyRef, bodyUuid);
    }

    boolean hasGameplayAttachment(@Nonnull RigidBodyKey bodyKey) {
        return hasGameplayAttachment(null, bodyKey.value());
    }

    boolean hasGameplayAttachment(@Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull RigidBodyKey bodyKey) {
        return hasGameplayAttachment(bodyRef, bodyKey.value());
    }

    boolean hasGameplayAttachment(@Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid) {
        return hasGameplayAttachment(bodyRef) || bodies().bodyUuids().contains(bodyUuid);
    }

    private boolean hasGameplayAttachment(@Nullable Ref<PhysicsStore> bodyRef) {
        if (bodyRef == null || !bodyRef.isValid()) {
            return false;
        }
        Ref<PhysicsStore> indexed = bodies().bodyRefsByRowIndex().get(bodyRef.getIndex());
        return sameRef(indexed, bodyRef);
    }

    @Nonnull
    private AttachmentBodies bodies() {
        if (bodies == null) {
            bodies = source.attachments();
        }
        return bodies;
    }

    @Nonnull
    private static AttachmentBodies collectGameplayAttachments(
        @Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        Queue<UUID> bodyUuids = new ConcurrentLinkedQueue<>();
        Queue<Ref<PhysicsStore>> bodyRefs = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(attachmentType,
            (index, archetypeChunk, _) -> {
                BodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    attachmentType);
                if (attachment != null
                    && attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                    bodyUuids.add(attachment.getBodyUuid());
                    Ref<PhysicsStore> bodyRef = attachment.getBodyRef();
                    if (bodyRef != null && bodyRef.isValid()) {
                        bodyRefs.add(bodyRef);
                    }
                }
            });
        Set<UUID> uniqueBodyUuids = new ObjectOpenHashSet<>();
        uniqueBodyUuids.addAll(bodyUuids);
        Int2ObjectOpenHashMap<Ref<PhysicsStore>> bodyRefsByRowIndex =
            new Int2ObjectOpenHashMap<>();
        for (Ref<PhysicsStore> bodyRef : bodyRefs) {
            int rowIndex = bodyRef.getIndex();
            Ref<PhysicsStore> existing = bodyRefsByRowIndex.get(rowIndex);
            if (existing == null || sameRef(existing, bodyRef)) {
                bodyRefsByRowIndex.put(rowIndex, bodyRef);
            }
        }
        return new AttachmentBodies(uniqueBodyUuids, bodyRefsByRowIndex);
    }

    private static boolean sameRef(@Nullable Ref<?> first,
        @Nullable Ref<?> second) {
        return first == second
            || (first != null
                && second != null
                && first.getStore() != null
                && first.getStore() == second.getStore()
                && first.getIndex() == second.getIndex());
    }

    @FunctionalInterface
    interface BodyKeySource {

        @Nonnull
        Set<RigidBodyKey> bodyKeys();
    }

    @FunctionalInterface
    private interface AttachmentSource {

        @Nonnull
        AttachmentBodies attachments();
    }

    private record AttachmentBodies(
        @Nonnull Set<UUID> bodyUuids,
        @Nonnull Int2ObjectOpenHashMap<Ref<PhysicsStore>> bodyRefsByRowIndex
    ) {
    }
}
