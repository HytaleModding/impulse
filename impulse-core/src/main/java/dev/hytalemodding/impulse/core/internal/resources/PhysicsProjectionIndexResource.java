package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only EntityStore projection index for authoritative PhysicsStore attachments.
 */
public final class PhysicsProjectionIndexResource implements Resource<EntityStore> {

    private final Map<UUID, Set<Ref<EntityStore>>> bodyAttachments =
        new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Set<Ref<EntityStore>>> bodyAttachmentsByRowIndex =
        new Int2ObjectOpenHashMap<>();
    private final Map<UUID, Ref<EntityStore>> generatedVisualProxies =
        new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Ref<EntityStore>> generatedVisualProxiesByRowIndex =
        new Int2ObjectOpenHashMap<>();

    public synchronized void registerAttachment(@Nonnull UUID bodyUuid,
        @Nonnull Ref<EntityStore> attachment) {
        registerAttachment(bodyUuid, null, attachment);
    }

    public synchronized void registerAttachment(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> attachment) {
        bodyAttachments.computeIfAbsent(bodyUuid, _ -> new ObjectOpenHashSet<>())
            .add(attachment);
        if (bodyRef != null) {
            bodyAttachmentsByRowIndex.computeIfAbsent(bodyRef.getIndex(),
                    _ -> new ObjectOpenHashSet<>())
                .add(attachment);
        }
    }

    public synchronized void unregisterAttachment(@Nonnull UUID bodyUuid,
        @Nonnull Ref<EntityStore> attachment) {
        unregisterAttachment(bodyUuid, null, attachment);
    }

    public synchronized void unregisterAttachment(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> attachment) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyUuid);
        if (attachments != null) {
            attachments.remove(attachment);
            if (attachments.isEmpty()) {
                bodyAttachments.remove(bodyUuid);
            }
        }
        if (bodyRef != null) {
            unregisterAttachmentRef(bodyRef, attachment);
        }
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getAttachments(@Nonnull UUID bodyUuid) {
        return liveAttachments(bodyAttachments, bodyUuid);
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        return liveAttachments(bodyAttachmentsByRowIndex, bodyRef.getIndex());
    }

    @Nonnull
    private <K> Collection<Ref<EntityStore>> liveAttachments(
        @Nonnull Map<K, Set<Ref<EntityStore>>> attachmentsByKey,
        @Nonnull K key) {
        List<Ref<EntityStore>> liveAttachments = new ArrayList<>();
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = attachmentsByKey.get(key);
            if (attachments == null || attachments.isEmpty()) {
                return List.of();
            }
            for (Iterator<Ref<EntityStore>> iterator = attachments.iterator(); iterator.hasNext();) {
                Ref<EntityStore> attachment = iterator.next();
                if (attachment != null && attachment.isValid()) {
                    liveAttachments.add(attachment);
                } else {
                    iterator.remove();
                }
            }
            if (attachments.isEmpty()) {
                attachmentsByKey.remove(key);
            }
        }
        return liveAttachments;
    }

    public boolean hasAttachments(@Nonnull UUID bodyUuid) {
        return hasLiveAttachments(bodyAttachments, bodyUuid);
    }

    public boolean hasAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        return hasLiveAttachments(bodyAttachmentsByRowIndex, bodyRef.getIndex());
    }

    private <K> boolean hasLiveAttachments(@Nonnull Map<K, Set<Ref<EntityStore>>> attachmentsByKey,
        @Nonnull K key) {
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = attachmentsByKey.get(key);
            if (attachments == null || attachments.isEmpty()) {
                return false;
            }
            boolean hasLiveAttachment = false;
            for (Iterator<Ref<EntityStore>> iterator = attachments.iterator(); iterator.hasNext();) {
                Ref<EntityStore> attachment = iterator.next();
                if (attachment != null && attachment.isValid()) {
                    hasLiveAttachment = true;
                } else {
                    iterator.remove();
                }
            }
            if (attachments.isEmpty()) {
                attachmentsByKey.remove(key);
            }
            return hasLiveAttachment;
        }
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull UUID bodyUuid) {
        return liveGeneratedVisualProxy(generatedVisualProxies, bodyUuid);
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull Ref<PhysicsStore> bodyRef) {
        return liveGeneratedVisualProxy(generatedVisualProxiesByRowIndex, bodyRef.getIndex());
    }

    @Nullable
    private <K> Ref<EntityStore> liveGeneratedVisualProxy(
        @Nonnull Map<K, Ref<EntityStore>> proxiesByKey,
        @Nonnull K key) {
        synchronized (this) {
            Ref<EntityStore> proxy = proxiesByKey.get(key);
            if (proxy != null && proxy.isValid()) {
                return proxy;
            }
            proxiesByKey.remove(key);
            return null;
        }
    }

    public void setGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nonnull Ref<EntityStore> proxy) {
        setGeneratedVisualProxy(bodyUuid, null, proxy);
    }

    public void setGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> proxy) {
        synchronized (this) {
            generatedVisualProxies.put(bodyUuid, proxy);
            if (bodyRef != null) {
                generatedVisualProxiesByRowIndex.put(bodyRef.getIndex(), proxy);
            }
        }
    }

    public void clearGeneratedVisualProxy(@Nonnull UUID bodyUuid) {
        synchronized (this) {
            generatedVisualProxies.remove(bodyUuid);
        }
    }

    public void clearGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nonnull Ref<EntityStore> expectedProxy) {
        clearGeneratedVisualProxy(bodyUuid, null, expectedProxy);
    }

    public void clearGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> expectedProxy) {
        synchronized (this) {
            Ref<EntityStore> proxy = generatedVisualProxies.get(bodyUuid);
            if (sameRef(proxy, expectedProxy)) {
                generatedVisualProxies.remove(bodyUuid);
            }
            if (bodyRef != null) {
                clearGeneratedVisualProxyRef(bodyRef, expectedProxy);
            }
        }
    }

    public void updateAttachmentBodyRef(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> oldBodyRef,
        @Nullable Ref<PhysicsStore> newBodyRef,
        @Nonnull Ref<EntityStore> attachment,
        boolean generatedProxy) {
        synchronized (this) {
            if (sameRef(oldBodyRef, newBodyRef)) {
                return;
            }
            if (oldBodyRef != null) {
                unregisterAttachmentRef(oldBodyRef, attachment);
                if (generatedProxy) {
                    clearGeneratedVisualProxyRef(oldBodyRef, attachment);
                }
            }
            if (newBodyRef != null) {
                bodyAttachmentsByRowIndex.computeIfAbsent(newBodyRef.getIndex(),
                        _ -> new ObjectOpenHashSet<>())
                    .add(attachment);
                if (generatedProxy) {
                    generatedVisualProxiesByRowIndex.put(newBodyRef.getIndex(), attachment);
                }
            }
        }
    }

    @Nonnull
    @Override
    public PhysicsProjectionIndexResource clone() {
        PhysicsProjectionIndexResource copy = new PhysicsProjectionIndexResource();
        synchronized (this) {
            for (Map.Entry<UUID, Set<Ref<EntityStore>>> entry : bodyAttachments.entrySet()) {
                copy.bodyAttachments.put(entry.getKey(), new ObjectOpenHashSet<>(entry.getValue()));
            }
            for (var entry : bodyAttachmentsByRowIndex.int2ObjectEntrySet()) {
                copy.bodyAttachmentsByRowIndex.put(entry.getIntKey(),
                    new ObjectOpenHashSet<>(entry.getValue()));
            }
            copy.generatedVisualProxies.putAll(generatedVisualProxies);
            copy.generatedVisualProxiesByRowIndex.putAll(generatedVisualProxiesByRowIndex);
        }
        return copy;
    }

    public static ResourceType<EntityStore, PhysicsProjectionIndexResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsProjectionIndexResourceType();
    }

    private void unregisterAttachmentRef(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> attachment) {
        int rowIndex = bodyRef.getIndex();
        Set<Ref<EntityStore>> attachments = bodyAttachmentsByRowIndex.get(rowIndex);
        if (attachments == null) {
            return;
        }
        attachments.remove(attachment);
        if (attachments.isEmpty()) {
            bodyAttachmentsByRowIndex.remove(rowIndex);
        }
    }

    private void clearGeneratedVisualProxyRef(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> expectedProxy) {
        int rowIndex = bodyRef.getIndex();
        Ref<EntityStore> proxy = generatedVisualProxiesByRowIndex.get(rowIndex);
        if (sameRef(proxy, expectedProxy)) {
            generatedVisualProxiesByRowIndex.remove(rowIndex);
        }
    }

    private static boolean sameRef(@Nullable Ref<?> first,
        @Nullable Ref<?> second) {
        return first == second
            || (first != null
                && second != null
                && first.getStore() == second.getStore()
                && first.getIndex() == second.getIndex());
    }
}
