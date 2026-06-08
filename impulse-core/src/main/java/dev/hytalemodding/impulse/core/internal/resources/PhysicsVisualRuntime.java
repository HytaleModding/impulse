package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final Map<RigidBodyKey, Set<Ref<EntityStore>>> bodyAttachments =
        new Object2ObjectOpenHashMap<>();
    private final Map<RigidBodyKey, Ref<EntityStore>> generatedVisualProxies =
        new Object2ObjectOpenHashMap<>();
    private final List<VisualInterest> syntheticVisualInterests = new ArrayList<>();
    private final Map<RigidBodyKey, BodyVisualInterestState> bodyVisualInterestStates =
        new Object2ObjectOpenHashMap<>();

    public PhysicsVisualRuntime(@Nonnull Consumer<Ref<EntityStore>> syncStateCleaner) {
        this.syncStateCleaner = syncStateCleaner;
    }

    public synchronized void registerAttachment(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> attachment) {
        bodyAttachments.computeIfAbsent(bodyKey, _ -> new ObjectOpenHashSet<>())
            .add(attachment);
    }

    public synchronized void unregisterAttachment(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> attachment) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyKey);
        if (attachments == null) {
            return;
        }

        attachments.remove(attachment);
        if (attachments.isEmpty()) {
            bodyAttachments.remove(bodyKey);
        }
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getAttachments(@Nonnull RigidBodyKey bodyKey) {
        List<Ref<EntityStore>> liveAttachments = new ArrayList<>();
        List<Ref<EntityStore>> staleAttachments = new ArrayList<>();
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyKey);
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
                bodyAttachments.remove(bodyKey);
            }
        }
        cleanSyncStates(staleAttachments);
        return liveAttachments;
    }

    public boolean hasAttachments(@Nonnull RigidBodyKey bodyKey) {
        boolean hasLiveAttachment = false;
        List<Ref<EntityStore>> staleAttachments = new ArrayList<>();
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyKey);
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
                bodyAttachments.remove(bodyKey);
            }
        }
        cleanSyncStates(staleAttachments);
        return hasLiveAttachment;
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey) {
        Ref<EntityStore> staleProxy = null;
        Ref<EntityStore> proxy;
        synchronized (this) {
            proxy = generatedVisualProxies.get(bodyKey);
            if (proxy != null && !proxy.isValid()) {
                generatedVisualProxies.remove(bodyKey);
                staleProxy = proxy;
                proxy = null;
            }
        }
        cleanSyncState(staleProxy);
        return proxy;
    }

    @Nonnull
    public Collection<RigidBodyKey> getGeneratedVisualProxyBodyKeys() {
        List<RigidBodyKey> bodyKeys = new ArrayList<>();
        List<RigidBodyKey> staleBodyKeys = new ArrayList<>();
        List<Ref<EntityStore>> staleProxies = new ArrayList<>();
        synchronized (this) {
            for (Map.Entry<RigidBodyKey, Ref<EntityStore>> entry : generatedVisualProxies.entrySet()) {
                Ref<EntityStore> proxy = entry.getValue();
                if (proxy != null && proxy.isValid()) {
                    bodyKeys.add(entry.getKey());
                } else {
                    staleBodyKeys.add(entry.getKey());
                    if (proxy != null) {
                        staleProxies.add(proxy);
                    }
                }
            }
            for (RigidBodyKey bodyKey : staleBodyKeys) {
                generatedVisualProxies.remove(bodyKey);
            }
        }
        cleanSyncStates(staleProxies);
        return bodyKeys;
    }

    public int generatedVisualProxyCount() {
        int count = 0;
        List<Ref<EntityStore>> staleProxies = new ArrayList<>();
        synchronized (this) {
            Iterator<Map.Entry<RigidBodyKey, Ref<EntityStore>>> iterator =
                generatedVisualProxies.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<RigidBodyKey, Ref<EntityStore>> entry = iterator.next();
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

    public void setGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> proxy) {
        Ref<EntityStore> previousProxy;
        synchronized (this) {
            previousProxy = generatedVisualProxies.put(bodyKey, proxy);
        }
        if (previousProxy != proxy) {
            cleanSyncState(previousProxy);
        }
    }

    public void clearGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey) {
        Ref<EntityStore> proxy;
        synchronized (this) {
            proxy = generatedVisualProxies.remove(bodyKey);
        }
        cleanSyncState(proxy);
    }

    public boolean clearGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> expectedProxy) {
        Ref<EntityStore> proxy;
        synchronized (this) {
            proxy = generatedVisualProxies.get(bodyKey);
            if (proxy == null || !sameRef(proxy, expectedProxy)) {
                return false;
            }

            generatedVisualProxies.remove(bodyKey);
        }
        cleanSyncState(proxy);
        return true;
    }

    public synchronized boolean isGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> proxy) {
        Ref<EntityStore> registeredProxy = generatedVisualProxies.get(bodyKey);
        return registeredProxy != null && sameRef(registeredProxy, proxy);
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
        return bodyVisualInterestStates.computeIfAbsent(bodyKey,
            _ -> new BodyVisualInterestState());
    }

    @Nullable
    public synchronized BodyVisualInterestState getBodyVisualInterestState(
        @Nonnull RigidBodyKey bodyKey) {
        return bodyVisualInterestStates.get(bodyKey);
    }

    public void clearBodyRuntimeState(@Nonnull RigidBodyKey bodyKey) {
        List<Ref<EntityStore>> staleRefs = new ArrayList<>();
        synchronized (this) {
            Set<Ref<EntityStore>> attachments = bodyAttachments.remove(bodyKey);
            if (attachments != null) {
                staleRefs.addAll(attachments);
            }
            Ref<EntityStore> proxy = generatedVisualProxies.remove(bodyKey);
            if (proxy != null) {
                staleRefs.add(proxy);
            }
            bodyVisualInterestStates.remove(bodyKey);
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
            bodyAttachments.clear();
            generatedVisualProxies.clear();
            syntheticVisualInterests.clear();
            bodyVisualInterestStates.clear();
        }
        cleanSyncStates(staleRefs);
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

    private static boolean sameRef(@Nonnull Ref<EntityStore> first,
        @Nonnull Ref<EntityStore> second) {
        return first == second || first.equals(second);
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
