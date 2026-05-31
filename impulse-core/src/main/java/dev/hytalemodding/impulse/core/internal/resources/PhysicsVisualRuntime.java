package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastHitView;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
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
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<RigidBodyKey, Ref<EntityStore>> generatedVisualProxies =
        new Object2ObjectLinkedOpenHashMap<>();
    private final List<VisualInterest> syntheticVisualInterests = new ArrayList<>();
    private final Map<RigidBodyKey, BodyVisualInterestState> bodyVisualInterestStates =
        new Object2ObjectLinkedOpenHashMap<>();

    public PhysicsVisualRuntime(@Nonnull Consumer<Ref<EntityStore>> syncStateCleaner) {
        this.syncStateCleaner = syncStateCleaner;
    }

    public void registerAttachment(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> attachment) {
        bodyAttachments.computeIfAbsent(bodyKey, ignored -> new ObjectOpenHashSet<>())
            .add(attachment);
    }

    public void unregisterAttachment(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> attachment) {
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
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyKey);
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        List<Ref<EntityStore>> liveAttachments = new ArrayList<>();
        List<Ref<EntityStore>> staleAttachments = new ArrayList<>();
        for (Ref<EntityStore> attachment : attachments) {
            if (attachment != null && attachment.isValid()) {
                liveAttachments.add(attachment);
            } else {
                staleAttachments.add(attachment);
            }
        }
        staleAttachments.forEach(attachments::remove);
        for (Ref<EntityStore> staleAttachment : staleAttachments) {
            if (staleAttachment != null) {
                syncStateCleaner.accept(staleAttachment);
            }
        }
        if (attachments.isEmpty()) {
            bodyAttachments.remove(bodyKey);
        }
        return liveAttachments;
    }

    public boolean hasAttachments(@Nonnull RigidBodyKey bodyKey) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyKey);
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
                if (attachment != null) {
                    syncStateCleaner.accept(attachment);
                }
            }
        }
        if (attachments.isEmpty()) {
            bodyAttachments.remove(bodyKey);
        }
        return hasLiveAttachment;
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey) {
        Ref<EntityStore> proxy = generatedVisualProxies.get(bodyKey);
        if (proxy != null && !proxy.isValid()) {
            generatedVisualProxies.remove(bodyKey);
            syncStateCleaner.accept(proxy);
            return null;
        }
        return proxy;
    }

    @Nonnull
    public Collection<RigidBodyKey> getGeneratedVisualProxyBodyKeys() {
        List<RigidBodyKey> bodyKeys = new ArrayList<>();
        List<RigidBodyKey> staleBodyKeys = new ArrayList<>();
        for (Map.Entry<RigidBodyKey, Ref<EntityStore>> entry : generatedVisualProxies.entrySet()) {
            Ref<EntityStore> proxy = entry.getValue();
            if (proxy != null && proxy.isValid()) {
                bodyKeys.add(entry.getKey());
            } else {
                staleBodyKeys.add(entry.getKey());
                if (proxy != null) {
                    syncStateCleaner.accept(proxy);
                }
            }
        }
        for (RigidBodyKey bodyKey : staleBodyKeys) {
            generatedVisualProxies.remove(bodyKey);
        }
        return bodyKeys;
    }

    public int generatedVisualProxyCount() {
        int count = 0;
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
                    syncStateCleaner.accept(proxy);
                }
            }
        }
        return count;
    }

    public void setGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> proxy) {
        Ref<EntityStore> previousProxy = generatedVisualProxies.put(bodyKey, proxy);
        if (previousProxy != null && previousProxy != proxy) {
            syncStateCleaner.accept(previousProxy);
        }
    }

    public void clearGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey) {
        Ref<EntityStore> proxy = generatedVisualProxies.remove(bodyKey);
        if (proxy != null) {
            syncStateCleaner.accept(proxy);
        }
    }

    public boolean clearGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> expectedProxy) {
        Ref<EntityStore> proxy = generatedVisualProxies.get(bodyKey);
        if (proxy == null || !sameRef(proxy, expectedProxy)) {
            return false;
        }

        generatedVisualProxies.remove(bodyKey);
        syncStateCleaner.accept(proxy);
        return true;
    }

    public boolean isGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> proxy) {
        Ref<EntityStore> registeredProxy = generatedVisualProxies.get(bodyKey);
        return registeredProxy != null && sameRef(registeredProxy, proxy);
    }

    public void setSyntheticVisualInterests(
        @Nonnull Collection<VisualInterest> interests) {
        syntheticVisualInterests.clear();
        syntheticVisualInterests.addAll(interests);
    }

    @Nonnull
    public List<VisualInterest> getSyntheticVisualInterests() {
        return new ArrayList<>(syntheticVisualInterests);
    }

    public void clearSyntheticVisualInterests() {
        syntheticVisualInterests.clear();
    }

    @Nonnull
    public BodyVisualInterestState getOrCreateBodyVisualInterestState(
        @Nonnull RigidBodyKey bodyKey) {
        return bodyVisualInterestStates.computeIfAbsent(bodyKey,
            ignored -> new BodyVisualInterestState());
    }

    @Nullable
    public BodyVisualInterestState getBodyVisualInterestState(
        @Nonnull RigidBodyKey bodyKey) {
        return bodyVisualInterestStates.get(bodyKey);
    }

    public void clearBodyRuntimeState(@Nonnull RigidBodyKey bodyKey) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.remove(bodyKey);
        if (attachments != null) {
            for (Ref<EntityStore> attachment : attachments) {
                if (attachment != null) {
                    syncStateCleaner.accept(attachment);
                }
            }
        }
        bodyVisualInterestStates.remove(bodyKey);
        clearGeneratedVisualProxy(bodyKey);
    }

    public void clear() {
        for (Ref<EntityStore> proxy : generatedVisualProxies.values()) {
            if (proxy != null) {
                syncStateCleaner.accept(proxy);
            }
        }
        for (Set<Ref<EntityStore>> attachments : bodyAttachments.values()) {
            for (Ref<EntityStore> attachment : attachments) {
                if (attachment != null) {
                    syncStateCleaner.accept(attachment);
                }
            }
        }
        bodyAttachments.clear();
        generatedVisualProxies.clear();
        syntheticVisualInterests.clear();
        bodyVisualInterestStates.clear();
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
     * worker query is still incomplete.</p>
     */
    public static final class BodyVisualInterestState {

        @Getter
        private float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        @Getter
        private boolean inRange;
        @Getter
        private boolean likelyVisible;
        @Getter
        private boolean raycastVisible;
        private long currentTick;
        private long lastRaycastTick = Long.MIN_VALUE;
        @Nullable
        private CompletableFuture<Optional<RaycastHitView>> pendingRaycast;

        public void recordInterest(float nearestDistanceSquared,
            boolean likelyVisible,
            boolean raycastVisible,
            boolean raycastEvaluated) {
            recordInterest(nearestDistanceSquared,
                likelyVisible,
                raycastVisible,
                raycastEvaluated,
                currentTick);
        }

        public void recordInterest(float nearestDistanceSquared,
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

        public boolean hasFreshRaycast(int cacheTicks) {
            return hasFreshRaycast(cacheTicks, currentTick);
        }

        public boolean hasFreshRaycast(int cacheTicks, long currentTick) {
            long resolvedTick = advanceVisualInterestTick(currentTick);
            return lastRaycastTick != Long.MIN_VALUE
                && resolvedTick - lastRaycastTick <= cacheTicks;
        }

        public boolean hasRaycastResult() {
            return lastRaycastTick != Long.MIN_VALUE;
        }

        public synchronized boolean hasPendingRaycast() {
            CompletableFuture<Optional<RaycastHitView>> current = pendingRaycast;
            return current != null && !current.isDone();
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

        @Nullable
        public synchronized Optional<RaycastHitView> pollCompletedRaycast() {
            CompletableFuture<Optional<RaycastHitView>> current = pendingRaycast;
            if (current == null || !current.isDone()) {
                return null;
            }
            pendingRaycast = null;
            try {
                return current.getNow(Optional.empty());
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }

        public long advanceVisualInterestTick(long currentTick) {
            this.currentTick = Math.max(this.currentTick, currentTick);
            return this.currentTick;
        }

    }

    public record VisualInterest(@Nonnull Vector3f position, @Nullable Vector3f direction) {
    }
}
