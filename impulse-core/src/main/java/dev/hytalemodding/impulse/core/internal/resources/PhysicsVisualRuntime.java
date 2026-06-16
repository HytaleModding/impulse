package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * Visual attachment, generated-proxy, and visual-interest state for one physics world.
 */
public final class PhysicsVisualRuntime {

    @Nonnull
    private final Consumer<Ref<EntityStore>> syncStateCleaner;
    private final Map<UUID, Set<Ref<EntityStore>>> bodyAttachments =
        new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<BodyAttachmentRefs> bodyAttachmentsByRowIndex =
        new Int2ObjectOpenHashMap<>();
    private final Map<UUID, Ref<EntityStore>> generatedVisualProxies =
        new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<GeneratedVisualProxyRef> generatedVisualProxiesByRowIndex =
        new Int2ObjectOpenHashMap<>();
    private final List<VisualInterest> syntheticVisualInterests = new ArrayList<>();
    private final Map<UUID, BodyVisualInterestState> bodyVisualInterestStates =
        new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<BodyVisualInterestRefState> bodyVisualInterestStatesByRowIndex =
        new Int2ObjectOpenHashMap<>();

    public PhysicsVisualRuntime(@Nonnull Consumer<Ref<EntityStore>> syncStateCleaner) {
        this.syncStateCleaner = syncStateCleaner;
    }

    public synchronized void registerAttachment(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> attachment) {
        bodyAttachments.computeIfAbsent(bodyUuid, _ -> new ObjectOpenHashSet<>())
            .add(attachment);
        if (isValidRef(bodyRef)) {
            bodyAttachmentRefs(bodyRef).add(attachment);
        }
    }

    public synchronized void unregisterAttachment(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> attachment) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyUuid);
        if (attachments == null) {
            if (isValidRef(bodyRef)) {
                unregisterAttachmentRef(bodyRef, attachment);
            }
            return;
        }
        attachments.remove(attachment);
        if (attachments.isEmpty()) {
            bodyAttachments.remove(bodyUuid);
        }
        if (isValidRef(bodyRef)) {
            unregisterAttachmentRef(bodyRef, attachment);
        }
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        if (!bodyRef.isValid()) {
            return List.of();
        }
        return liveAttachments(bodyRef);
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getAttachments(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (isValidRef(bodyRef)) {
            Collection<Ref<EntityStore>> attachments = liveAttachments(bodyRef);
            if (!attachments.isEmpty()) {
                return attachments;
            }
        }
        return liveAttachments(bodyAttachments, bodyUuid);
    }

    @Nonnull
    private <K> Collection<Ref<EntityStore>> liveAttachments(
        @Nonnull Map<K, Set<Ref<EntityStore>>> attachmentsByKey,
        @Nonnull K key) {
        List<Ref<EntityStore>> liveAttachments = new ArrayList<>();
        List<Ref<EntityStore>> staleAttachments = new ArrayList<>();
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = attachmentsByKey.get(key);
            if (attachments == null || attachments.isEmpty()) {
                return List.of();
            }

            for (Ref<EntityStore> attachment : attachments) {
                if (attachment != null && attachment.isValid()) {
                    liveAttachments.add(attachment);
                } else {
                    staleAttachments.add(attachment);
                }
            }
            staleAttachments.forEach(attachments::remove);
            if (attachments.isEmpty()) {
                attachmentsByKey.remove(key);
            }
        }
        cleanSyncStates(staleAttachments);
        return liveAttachments;
    }

    public boolean hasAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        return bodyRef.isValid() && hasLiveAttachments(bodyRef);
    }

    public boolean hasAttachments(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        return isValidRef(bodyRef) && hasLiveAttachments(bodyRef)
            || hasLiveAttachments(bodyAttachments, bodyUuid);
    }

    private <K> boolean hasLiveAttachments(@Nonnull Map<K, Set<Ref<EntityStore>>> attachmentsByKey,
        @Nonnull K key) {
        boolean hasLiveAttachment = false;
        List<Ref<EntityStore>> staleAttachments = new ArrayList<>();
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = attachmentsByKey.get(key);
            if (attachments == null || attachments.isEmpty()) {
                return false;
            }

            for (Iterator<Ref<EntityStore>> iterator = attachments.iterator(); iterator.hasNext();) {
                Ref<EntityStore> attachment = iterator.next();
                if (attachment != null && attachment.isValid()) {
                    hasLiveAttachment = true;
                } else {
                    iterator.remove();
                    if (attachment != null) {
                        staleAttachments.add(attachment);
                    }
                }
            }
            if (attachments.isEmpty()) {
                attachmentsByKey.remove(key);
            }
        }
        cleanSyncStates(staleAttachments);
        return hasLiveAttachment;
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull Ref<PhysicsStore> bodyRef) {
        if (!bodyRef.isValid()) {
            return null;
        }
        return liveGeneratedVisualProxy(bodyRef);
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (isValidRef(bodyRef)) {
            Ref<EntityStore> proxy = liveGeneratedVisualProxy(bodyRef);
            if (proxy != null) {
                return proxy;
            }
        }
        return liveGeneratedVisualProxy(generatedVisualProxies, bodyUuid);
    }

    @Nullable
    private <K> Ref<EntityStore> liveGeneratedVisualProxy(
        @Nonnull Map<K, Ref<EntityStore>> proxiesByKey,
        @Nonnull K key) {
        Ref<EntityStore> staleProxy = null;
        Ref<EntityStore> proxy;
        synchronized (this) {
            proxy = proxiesByKey.get(key);
            if (proxy != null && !proxy.isValid()) {
                proxiesByKey.remove(key);
                staleProxy = proxy;
                proxy = null;
            }
        }
        cleanSyncState(staleProxy);
        return proxy;
    }

    public int generatedVisualProxyCount() {
        int count = 0;
        List<Ref<EntityStore>> staleProxies = new ArrayList<>();
        synchronized (this) {
            Iterator<Map.Entry<UUID, Ref<EntityStore>>> iterator =
                generatedVisualProxies.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Ref<EntityStore>> entry = iterator.next();
                Ref<EntityStore> proxy = entry.getValue();
                if (proxy != null && proxy.isValid()) {
                    count++;
                } else {
                    iterator.remove();
                    if (proxy != null) {
                        staleProxies.add(proxy);
                    }
                }
            }
        }
        cleanSyncStates(staleProxies);
        return count;
    }

    public void setGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> proxy) {
        Ref<EntityStore> previousProxy;
        Ref<EntityStore> previousRefProxy = null;
        synchronized (this) {
            previousProxy = generatedVisualProxies.put(bodyUuid, proxy);
            if (isValidRef(bodyRef)) {
                GeneratedVisualProxyRef previous = generatedVisualProxiesByRowIndex.put(bodyRef.getIndex(),
                    new GeneratedVisualProxyRef(bodyUuid, bodyRef, proxy));
                if (previous != null) {
                    previousRefProxy = previous.proxy();
                }
            }
        }
        if (!sameRef(previousProxy, proxy)) {
            cleanSyncState(previousProxy);
        }
        if (!sameRef(previousRefProxy, proxy) && !sameRef(previousRefProxy, previousProxy)) {
            cleanSyncState(previousRefProxy);
        }
    }

    public void clearGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        Ref<EntityStore> proxy;
        Ref<EntityStore> refProxy = null;
        synchronized (this) {
            proxy = generatedVisualProxies.remove(bodyUuid);
            if (isValidRef(bodyRef)) {
                GeneratedVisualProxyRef removed = removeGeneratedVisualProxyRef(bodyRef);
                if (removed != null) {
                    refProxy = removed.proxy();
                }
            }
        }
        cleanSyncState(proxy);
        if (!sameRef(refProxy, proxy)) {
            cleanSyncState(refProxy);
        }
    }

    public boolean clearGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> expectedProxy) {
        Ref<EntityStore> proxy = null;
        Ref<EntityStore> refProxy = null;
        boolean matched = false;
        synchronized (this) {
            Ref<EntityStore> uuidProxy = generatedVisualProxies.get(bodyUuid);
            if (sameRef(uuidProxy, expectedProxy)) {
                generatedVisualProxies.remove(bodyUuid);
                proxy = uuidProxy;
                matched = true;
            }
            if (isValidRef(bodyRef)) {
                GeneratedVisualProxyRef removed = removeGeneratedVisualProxyRef(bodyRef, expectedProxy);
                if (removed != null) {
                    refProxy = removed.proxy();
                    matched = true;
                }
            }
            if (!matched) {
                return false;
            }
        }
        cleanSyncState(proxy);
        if (!sameRef(refProxy, proxy)) {
            cleanSyncState(refProxy);
        }
        return true;
    }

    public synchronized boolean isGeneratedVisualProxy(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> proxy) {
        if (isValidRef(bodyRef) && sameRef(liveGeneratedVisualProxy(bodyRef), proxy)) {
            return true;
        }
        Ref<EntityStore> registeredProxy = generatedVisualProxies.get(bodyUuid);
        return registeredProxy != null && sameRef(registeredProxy, proxy);
    }

    public synchronized boolean isGeneratedVisualProxy(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> proxy) {
        return bodyRef.isValid() && sameRef(liveGeneratedVisualProxy(bodyRef), proxy);
    }

    public synchronized void setSyntheticVisualInterests(
        @Nonnull Collection<VisualInterest> interests) {
        syntheticVisualInterests.clear();
        syntheticVisualInterests.addAll(interests);
    }

    @Nonnull
    public synchronized List<VisualInterest> getSyntheticVisualInterests() {
        return new ArrayList<>(syntheticVisualInterests);
    }

    public synchronized void clearSyntheticVisualInterests() {
        syntheticVisualInterests.clear();
    }

    @Nonnull
    public synchronized BodyVisualInterestState getOrCreateBodyVisualInterestState(
        @Nonnull RigidBodyKey bodyKey) {
        return bodyVisualInterestStates.computeIfAbsent(bodyKey.value(),
            _ -> new BodyVisualInterestState());
    }

    @Nonnull
    public synchronized BodyVisualInterestState getOrCreateBodyVisualInterestState(
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (bodyRef != null && bodyRef.isValid()) {
            return getOrCreateBodyVisualInterestState(bodyRef);
        }
        return bodyVisualInterestStates.computeIfAbsent(bodyUuid,
            _ -> new BodyVisualInterestState());
    }

    @Nullable
    public synchronized BodyVisualInterestState getBodyVisualInterestState(
        @Nonnull RigidBodyKey bodyKey) {
        return bodyVisualInterestStates.get(bodyKey.value());
    }

    @Nullable
    public synchronized BodyVisualInterestState getBodyVisualInterestState(
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (bodyRef != null && bodyRef.isValid()) {
            int rowIndex = bodyRef.getIndex();
            BodyVisualInterestRefState row = bodyVisualInterestStatesByRowIndex.get(rowIndex);
            if (row == null) {
                return null;
            }
            if (!isMatchingLiveRef(row, bodyRef)) {
                bodyVisualInterestStatesByRowIndex.remove(rowIndex);
                return null;
            }
            return row.state();
        }
        return bodyVisualInterestStates.get(bodyUuid);
    }

    public synchronized void clearBodyVisualInterestState(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        bodyVisualInterestStates.remove(bodyUuid);
        if (!isValidRef(bodyRef)) {
            return;
        }
        int rowIndex = bodyRef.getIndex();
        BodyVisualInterestRefState row = bodyVisualInterestStatesByRowIndex.get(rowIndex);
        if (row != null && (!row.bodyRef().isValid() || sameRef(row.bodyRef(), bodyRef))) {
            bodyVisualInterestStatesByRowIndex.remove(rowIndex);
        }
    }

    public void clearBodyRuntimeState(@Nonnull RigidBodyKey bodyKey) {
        clearBodyRuntimeState(bodyKey.value(), null);
    }

    public void clearBodyRuntimeState(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        List<Ref<EntityStore>> staleRefs = new ArrayList<>();
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = bodyAttachments.remove(bodyUuid);
            if (attachments != null) {
                staleRefs.addAll(attachments);
            }
            if (isValidRef(bodyRef)) {
                BodyAttachmentRefs attachmentRefs = removeAttachmentRefs(bodyRef);
                if (attachmentRefs != null) {
                    staleRefs.addAll(attachmentRefs.attachments());
                }
            }
            Ref<EntityStore> proxy = generatedVisualProxies.remove(bodyUuid);
            if (proxy != null) {
                staleRefs.add(proxy);
            }
            if (isValidRef(bodyRef)) {
                GeneratedVisualProxyRef proxyRef = removeGeneratedVisualProxyRef(bodyRef);
                if (proxyRef != null && !sameRef(proxyRef.proxy(), proxy)) {
                    staleRefs.add(proxyRef.proxy());
                }
            }
            bodyVisualInterestStates.remove(bodyUuid);
            if (isValidRef(bodyRef)) {
                int rowIndex = bodyRef.getIndex();
                BodyVisualInterestRefState row = bodyVisualInterestStatesByRowIndex.get(rowIndex);
                if (row != null && (!row.bodyRef().isValid() || sameRef(row.bodyRef(), bodyRef))) {
                    bodyVisualInterestStatesByRowIndex.remove(rowIndex);
                }
            }
        }
        cleanSyncStates(staleRefs);
    }

    public void clear() {
        List<Ref<EntityStore>> staleRefs = new ArrayList<>();
        synchronized (this) {
            staleRefs.addAll(generatedVisualProxies.values());
            for (Set<Ref<EntityStore>> attachments : bodyAttachments.values()) {
                for (Ref<EntityStore> attachment : attachments) {
                    if (attachment != null) {
                        staleRefs.add(attachment);
                    }
                }
            }
            for (var entry : bodyAttachmentsByRowIndex.int2ObjectEntrySet()) {
                staleRefs.addAll(entry.getValue().attachments());
            }
            for (var entry : generatedVisualProxiesByRowIndex.int2ObjectEntrySet()) {
                Ref<EntityStore> proxy = entry.getValue().proxy();
                if (proxy != null) {
                    staleRefs.add(proxy);
                }
            }
            bodyAttachments.clear();
            bodyAttachmentsByRowIndex.clear();
            generatedVisualProxies.clear();
            generatedVisualProxiesByRowIndex.clear();
            syntheticVisualInterests.clear();
            bodyVisualInterestStates.clear();
            bodyVisualInterestStatesByRowIndex.clear();
        }
        cleanSyncStates(staleRefs);
    }

    @Nonnull
    private BodyVisualInterestState getOrCreateBodyVisualInterestState(
        @Nonnull Ref<PhysicsStore> bodyRef) {
        int rowIndex = bodyRef.getIndex();
        BodyVisualInterestRefState row = bodyVisualInterestStatesByRowIndex.get(rowIndex);
        if (row == null || !isMatchingLiveRef(row, bodyRef)) {
            row = new BodyVisualInterestRefState(bodyRef, new BodyVisualInterestState());
            bodyVisualInterestStatesByRowIndex.put(rowIndex, row);
        }
        return row.state();
    }

    private static boolean isMatchingLiveRef(@Nonnull BodyVisualInterestRefState row,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return row.bodyRef().isValid() && sameRef(row.bodyRef(), bodyRef);
    }

    private static boolean isMatchingLiveRef(@Nonnull BodyAttachmentRefs row,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return row.bodyRef().isValid() && sameRef(row.bodyRef(), bodyRef);
    }

    private static boolean isMatchingLiveRef(@Nonnull GeneratedVisualProxyRef row,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return row.bodyRef().isValid() && sameRef(row.bodyRef(), bodyRef);
    }

    @Nonnull
    private Set<Ref<EntityStore>> bodyAttachmentRefs(@Nonnull Ref<PhysicsStore> bodyRef) {
        int rowIndex = bodyRef.getIndex();
        BodyAttachmentRefs row = bodyAttachmentsByRowIndex.get(rowIndex);
        if (row == null || !isMatchingLiveRef(row, bodyRef)) {
            row = new BodyAttachmentRefs(bodyRef, new ObjectOpenHashSet<>());
            bodyAttachmentsByRowIndex.put(rowIndex, row);
        }
        return row.attachments();
    }

    private void unregisterAttachmentRef(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> attachment) {
        int rowIndex = bodyRef.getIndex();
        BodyAttachmentRefs row = bodyAttachmentsByRowIndex.get(rowIndex);
        if (row == null) {
            return;
        }
        if (!isMatchingLiveRef(row, bodyRef)) {
            bodyAttachmentsByRowIndex.remove(rowIndex);
            return;
        }
        Set<Ref<EntityStore>> attachments = row.attachments();
        attachments.remove(attachment);
        if (attachments.isEmpty()) {
            bodyAttachmentsByRowIndex.remove(rowIndex);
        }
    }

    @Nullable
    private BodyAttachmentRefs removeAttachmentRefs(@Nonnull Ref<PhysicsStore> bodyRef) {
        int rowIndex = bodyRef.getIndex();
        BodyAttachmentRefs row = bodyAttachmentsByRowIndex.get(rowIndex);
        if (row == null || !isMatchingLiveRef(row, bodyRef)) {
            if (row != null) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
            }
            return null;
        }
        bodyAttachmentsByRowIndex.remove(rowIndex);
        return row;
    }

    @Nonnull
    private Collection<Ref<EntityStore>> liveAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        List<Ref<EntityStore>> liveAttachments = new ArrayList<>();
        List<Ref<EntityStore>> staleAttachments = new ArrayList<>();
        synchronized (this) {
            int rowIndex = bodyRef.getIndex();
            BodyAttachmentRefs row = bodyAttachmentsByRowIndex.get(rowIndex);
            if (row == null) {
                return List.of();
            }
            if (!isMatchingLiveRef(row, bodyRef)) {
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
                    if (attachment != null) {
                        staleAttachments.add(attachment);
                    }
                }
            }
            if (attachments.isEmpty()) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
            }
        }
        cleanSyncStates(staleAttachments);
        return liveAttachments;
    }

    private boolean hasLiveAttachments(@Nonnull Ref<PhysicsStore> bodyRef) {
        boolean hasLiveAttachment = false;
        List<Ref<EntityStore>> staleAttachments = new ArrayList<>();
        synchronized (this) {
            int rowIndex = bodyRef.getIndex();
            BodyAttachmentRefs row = bodyAttachmentsByRowIndex.get(rowIndex);
            if (row == null) {
                return false;
            }
            if (!isMatchingLiveRef(row, bodyRef)) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
                return false;
            }
            Set<Ref<EntityStore>> attachments = row.attachments();
            if (attachments.isEmpty()) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
                return false;
            }
            for (Iterator<Ref<EntityStore>> iterator = attachments.iterator(); iterator.hasNext();) {
                Ref<EntityStore> attachment = iterator.next();
                if (attachment != null && attachment.isValid()) {
                    hasLiveAttachment = true;
                } else {
                    iterator.remove();
                    if (attachment != null) {
                        staleAttachments.add(attachment);
                    }
                }
            }
            if (attachments.isEmpty()) {
                bodyAttachmentsByRowIndex.remove(rowIndex);
            }
        }
        cleanSyncStates(staleAttachments);
        return hasLiveAttachment;
    }

    @Nullable
    private Ref<EntityStore> liveGeneratedVisualProxy(@Nonnull Ref<PhysicsStore> bodyRef) {
        Ref<EntityStore> staleProxy = null;
        synchronized (this) {
            int rowIndex = bodyRef.getIndex();
            GeneratedVisualProxyRef row = generatedVisualProxiesByRowIndex.get(rowIndex);
            if (row == null) {
                return null;
            }
            if (!isMatchingLiveRef(row, bodyRef)) {
                generatedVisualProxiesByRowIndex.remove(rowIndex);
                return null;
            }
            Ref<EntityStore> proxy = row.proxy();
            if (proxy != null && proxy.isValid()) {
                return proxy;
            }
            generatedVisualProxiesByRowIndex.remove(rowIndex);
            staleProxy = proxy;
        }
        cleanSyncState(staleProxy);
        return null;
    }

    @Nullable
    private GeneratedVisualProxyRef removeGeneratedVisualProxyRef(@Nonnull Ref<PhysicsStore> bodyRef) {
        int rowIndex = bodyRef.getIndex();
        GeneratedVisualProxyRef row = generatedVisualProxiesByRowIndex.get(rowIndex);
        if (row == null || !isMatchingLiveRef(row, bodyRef)) {
            if (row != null) {
                generatedVisualProxiesByRowIndex.remove(rowIndex);
            }
            return null;
        }
        generatedVisualProxiesByRowIndex.remove(rowIndex);
        return row;
    }

    @Nullable
    private GeneratedVisualProxyRef removeGeneratedVisualProxyRef(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<EntityStore> expectedProxy) {
        int rowIndex = bodyRef.getIndex();
        GeneratedVisualProxyRef row = generatedVisualProxiesByRowIndex.get(rowIndex);
        if (row == null || !isMatchingLiveRef(row, bodyRef)) {
            if (row != null) {
                generatedVisualProxiesByRowIndex.remove(rowIndex);
            }
            return null;
        }
        if (!sameRef(row.proxy(), expectedProxy)) {
            return null;
        }
        generatedVisualProxiesByRowIndex.remove(rowIndex);
        return row;
    }

    private static boolean isValidRef(@Nullable Ref<?> ref) {
        return ref != null && ref.isValid();
    }

    private void cleanSyncState(@Nullable Ref<EntityStore> ref) {
        if (ref != null) {
            syncStateCleaner.accept(ref);
        }
    }

    private void cleanSyncStates(@Nonnull Collection<Ref<EntityStore>> refs) {
        for (Ref<EntityStore> ref : refs) {
            cleanSyncState(ref);
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

    private record GeneratedVisualProxyRef(@Nonnull UUID bodyUuid,
                                           @Nonnull Ref<PhysicsStore> bodyRef,
                                           @Nonnull Ref<EntityStore> proxy) {
    }

    private record BodyVisualInterestRefState(@Nonnull Ref<PhysicsStore> bodyRef,
                                              @Nonnull BodyVisualInterestState state) {
    }

    /**
     * Per-body visual-interest cache produced by detached visual materialization.
     *
     * <p>Physics sync can reuse fresh raycast results from this state when
     * {@code VisualOcclusionMode.CULL} is enabled, so materialization and sync
     * share one occlusion decision window instead of spending duplicate raycasts. Raycasts are
     * submitted asynchronously; callers poll this state and use the last-known visibility while a
     * owner query is still incomplete.</p>
     */
    public static final class BodyVisualInterestState {

        @Getter
        private volatile float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        @Getter
        private volatile boolean inRange;
        @Getter
        private volatile boolean likelyVisible;
        @Getter
        private volatile boolean raycastVisible;
        private volatile long currentTick;
        private volatile long lastRaycastTick = Long.MIN_VALUE;
        @Nullable
        private CompletableFuture<Optional<RaycastHitView>> pendingRaycast;

        public synchronized void recordInterest(float nearestDistanceSquared,
            boolean likelyVisible,
            boolean raycastVisible,
            boolean raycastEvaluated) {
            recordInterest(nearestDistanceSquared,
                likelyVisible,
                raycastVisible,
                raycastEvaluated,
                currentTick);
        }

        public synchronized void recordInterest(float nearestDistanceSquared,
            boolean likelyVisible,
            boolean raycastVisible,
            boolean raycastEvaluated,
            long currentTick) {
            long resolvedTick = advanceVisualInterestTick(currentTick);
            this.nearestDistanceSquared = nearestDistanceSquared;
            inRange = nearestDistanceSquared != Float.POSITIVE_INFINITY;
            this.likelyVisible = likelyVisible;
            if (raycastEvaluated) {
                this.raycastVisible = raycastVisible;
                lastRaycastTick = resolvedTick;
            }
        }

        public synchronized boolean hasFreshRaycast(int cacheTicks) {
            return hasFreshRaycast(cacheTicks, currentTick);
        }

        public synchronized boolean hasFreshRaycast(int cacheTicks, long currentTick) {
            long resolvedTick = advanceVisualInterestTick(currentTick);
            return lastRaycastTick != Long.MIN_VALUE
                && resolvedTick - lastRaycastTick <= cacheTicks;
        }

        public synchronized boolean hasRaycastResult() {
            return lastRaycastTick != Long.MIN_VALUE;
        }

        public synchronized boolean hasPendingRaycast() {
            CompletableFuture<Optional<RaycastHitView>> current = pendingRaycast;
            return current != null && !current.isDone();
        }

        public synchronized boolean hasCompletedRaycast() {
            CompletableFuture<Optional<RaycastHitView>> current = pendingRaycast;
            return current != null && current.isDone();
        }

        public synchronized boolean startPendingRaycast(
            @Nonnull CompletionStage<Optional<RaycastHitView>> completion) {
            if (pendingRaycast != null) {
                return false;
            }
            pendingRaycast = completion.toCompletableFuture();
            return true;
        }

        public synchronized void clearPendingRaycast() {
            pendingRaycast = null;
        }

        @Nonnull
        public synchronized Optional<RaycastHitView> pollCompletedRaycast() {
            CompletableFuture<Optional<RaycastHitView>> current = pendingRaycast;
            if (current == null || !current.isDone()) {
                return Optional.empty();
            }
            pendingRaycast = null;
            try {
                return current.getNow(Optional.empty());
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }

        public synchronized long advanceVisualInterestTick(long currentTick) {
            this.currentTick = Math.max(this.currentTick, currentTick);
            return this.currentTick;
        }

    }

    public record VisualInterest(@Nonnull Vector3f position, @Nullable Vector3f direction) {
    }
}
