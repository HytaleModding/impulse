package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
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
    private final Int2ObjectOpenHashMap<BodyAttachmentRefs> bodyAttachmentsByRowIndex =
        new Int2ObjectOpenHashMap<>();
    private final Map<UUID, Ref<EntityStore>> generatedVisualProxies =
        new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<GeneratedVisualProxyRef> generatedVisualProxiesByRowIndex =
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
            bodyAttachmentRefs(bodyRef)
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
        return liveAttachments(bodyRef);
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
        return hasLiveAttachments(bodyRef);
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
        return liveGeneratedVisualProxy(bodyRef);
    }

    @Nonnull
    public Collection<RigidBodyKey> getGeneratedVisualProxyBodyKeys() {
        List<RigidBodyKey> bodyKeys = new ArrayList<>();
        synchronized (this) {
            for (Iterator<Map.Entry<UUID, Ref<EntityStore>>> iterator =
                 generatedVisualProxies.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<UUID, Ref<EntityStore>> entry = iterator.next();
                Ref<EntityStore> proxy = entry.getValue();
                if (proxy != null && proxy.isValid()) {
                    bodyKeys.add(RigidBodyKey.of(entry.getKey()));
                } else {
                    iterator.remove();
                }
            }
        }
        return bodyKeys;
    }

    public int generatedVisualProxyCount() {
        int count = 0;
        synchronized (this) {
            for (Iterator<Map.Entry<UUID, Ref<EntityStore>>> iterator =
                 generatedVisualProxies.entrySet().iterator(); iterator.hasNext();) {
                Ref<EntityStore> proxy = iterator.next().getValue();
                if (proxy != null && proxy.isValid()) {
                    count++;
                } else {
                    iterator.remove();
                }
            }
        }
        return count;
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
                generatedVisualProxiesByRowIndex.put(bodyRef.getIndex(),
                    new GeneratedVisualProxyRef(bodyRef, proxy));
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
                bodyAttachmentRefs(newBodyRef)
                    .add(attachment);
                if (generatedProxy) {
                    generatedVisualProxiesByRowIndex.put(newBodyRef.getIndex(),
                        new GeneratedVisualProxyRef(newBodyRef, attachment));
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
                BodyAttachmentRefs refs = entry.getValue();
                copy.bodyAttachmentsByRowIndex.put(entry.getIntKey(),
                    new BodyAttachmentRefs(refs.bodyRef(),
                        new ObjectOpenHashSet<>(refs.attachments())));
            }
            copy.generatedVisualProxies.putAll(generatedVisualProxies);
            for (var entry : generatedVisualProxiesByRowIndex.int2ObjectEntrySet()) {
                GeneratedVisualProxyRef proxy = entry.getValue();
                copy.generatedVisualProxiesByRowIndex.put(entry.getIntKey(),
                    new GeneratedVisualProxyRef(proxy.bodyRef(), proxy.proxy()));
            }
        }
        return copy;
    }

    public static ResourceType<EntityStore, PhysicsProjectionIndexResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsProjectionIndexResourceType();
    }

    private void unregisterAttachmentRef(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> attachment) {
        int rowIndex = bodyRef.getIndex();
        BodyAttachmentRefs row = bodyAttachmentsByRowIndex.get(rowIndex);
        if (row == null || !sameRef(row.bodyRef(), bodyRef)) {
            if (row != null) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
            }
            return;
        }
        Set<Ref<EntityStore>> attachments = row.attachments();
        attachments.remove(attachment);
        if (attachments.isEmpty()) {
            bodyAttachmentsByRowIndex.remove(rowIndex);
        }
    }

    private void clearGeneratedVisualProxyRef(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> expectedProxy) {
        int rowIndex = bodyRef.getIndex();
        GeneratedVisualProxyRef row = generatedVisualProxiesByRowIndex.get(rowIndex);
        if (row != null
            && (!sameRef(row.bodyRef(), bodyRef)
                || sameRef(row.proxy(), expectedProxy))) {
            generatedVisualProxiesByRowIndex.remove(rowIndex);
        }
    }

    @Nonnull
    private Set<Ref<EntityStore>> bodyAttachmentRefs(@Nonnull Ref<PhysicsStore> bodyRef) {
        int rowIndex = bodyRef.getIndex();
        BodyAttachmentRefs row = bodyAttachmentsByRowIndex.get(rowIndex);
        if (row == null || !sameRef(row.bodyRef(), bodyRef)) {
            row = new BodyAttachmentRefs(bodyRef, new ObjectOpenHashSet<>());
            bodyAttachmentsByRowIndex.put(rowIndex, row);
        }
        return row.attachments();
    }

    @Nonnull
    private Collection<Ref<EntityStore>> liveAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        List<Ref<EntityStore>> liveAttachments = new ArrayList<>();
        synchronized (this) {
            int rowIndex = bodyRef.getIndex();
            BodyAttachmentRefs row = bodyAttachmentsByRowIndex.get(rowIndex);
            if (row == null) {
                return List.of();
            }
            if (!sameRef(row.bodyRef(), bodyRef)) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
                return List.of();
            }
            Set<Ref<EntityStore>> attachments = row.attachments();
            if (attachments.isEmpty()) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
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
                bodyAttachmentsByRowIndex.remove(rowIndex);
            }
        }
        return liveAttachments;
    }

    private boolean hasLiveAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        synchronized (this) {
            int rowIndex = bodyRef.getIndex();
            BodyAttachmentRefs row = bodyAttachmentsByRowIndex.get(rowIndex);
            if (row == null) {
                return false;
            }
            if (!sameRef(row.bodyRef(), bodyRef)) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
                return false;
            }
            Set<Ref<EntityStore>> attachments = row.attachments();
            if (attachments.isEmpty()) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
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
                bodyAttachmentsByRowIndex.remove(rowIndex);
            }
            return hasLiveAttachment;
        }
    }

    @Nullable
    private Ref<EntityStore> liveGeneratedVisualProxy(@Nonnull Ref<PhysicsStore> bodyRef) {
        synchronized (this) {
            int rowIndex = bodyRef.getIndex();
            GeneratedVisualProxyRef row = generatedVisualProxiesByRowIndex.get(rowIndex);
            if (row == null) {
                return null;
            }
            if (!sameRef(row.bodyRef(), bodyRef)) {
                generatedVisualProxiesByRowIndex.remove(rowIndex);
                return null;
            }
            Ref<EntityStore> proxy = row.proxy();
            if (proxy != null && proxy.isValid()) {
                return proxy;
            }
            generatedVisualProxiesByRowIndex.remove(rowIndex);
            return null;
        }
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

    private record BodyAttachmentRefs(@Nonnull Ref<PhysicsStore> bodyRef,
                                      @Nonnull Set<Ref<EntityStore>> attachments) {
    }

    private record GeneratedVisualProxyRef(@Nonnull Ref<PhysicsStore> bodyRef,
                                           @Nonnull Ref<EntityStore> proxy) {
    }
}
