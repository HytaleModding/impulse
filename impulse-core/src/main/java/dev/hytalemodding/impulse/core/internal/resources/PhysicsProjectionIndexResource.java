package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
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
    private final Map<UUID, Ref<EntityStore>> generatedVisualProxies =
        new Object2ObjectOpenHashMap<>();

    public synchronized void registerAttachment(@Nonnull UUID bodyUuid,
        @Nonnull Ref<EntityStore> attachment) {
        bodyAttachments.computeIfAbsent(bodyUuid, _ -> new ObjectOpenHashSet<>())
            .add(attachment);
    }

    public synchronized void unregisterAttachment(@Nonnull UUID bodyUuid,
        @Nonnull Ref<EntityStore> attachment) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyUuid);
        if (attachments == null) {
            return;
        }
        attachments.remove(attachment);
        if (attachments.isEmpty()) {
            bodyAttachments.remove(bodyUuid);
        }
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getAttachments(@Nonnull UUID bodyUuid) {
        List<Ref<EntityStore>> liveAttachments = new ArrayList<>();
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyUuid);
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
                bodyAttachments.remove(bodyUuid);
            }
        }
        return liveAttachments;
    }

    public boolean hasAttachments(@Nonnull UUID bodyUuid) {
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyUuid);
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
                bodyAttachments.remove(bodyUuid);
            }
            return hasLiveAttachment;
        }
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull UUID bodyUuid) {
        synchronized (this) {
            Ref<EntityStore> proxy = generatedVisualProxies.get(bodyUuid);
            if (proxy != null && proxy.isValid()) {
                return proxy;
            }
            generatedVisualProxies.remove(bodyUuid);
            return null;
        }
    }

    public void setGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nonnull Ref<EntityStore> proxy) {
        synchronized (this) {
            generatedVisualProxies.put(bodyUuid, proxy);
        }
    }

    public void clearGeneratedVisualProxy(@Nonnull UUID bodyUuid) {
        synchronized (this) {
            generatedVisualProxies.remove(bodyUuid);
        }
    }

    public void clearGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nonnull Ref<EntityStore> expectedProxy) {
        synchronized (this) {
            Ref<EntityStore> proxy = generatedVisualProxies.get(bodyUuid);
            if (sameRef(proxy, expectedProxy)) {
                generatedVisualProxies.remove(bodyUuid);
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
            copy.generatedVisualProxies.putAll(generatedVisualProxies);
        }
        return copy;
    }

    public static ResourceType<EntityStore, PhysicsProjectionIndexResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsProjectionIndexResourceType();
    }

    private static boolean sameRef(@Nullable Ref<EntityStore> first,
        @Nonnull Ref<EntityStore> second) {
        return first != null && (first == second || first.equals(second));
    }
}
