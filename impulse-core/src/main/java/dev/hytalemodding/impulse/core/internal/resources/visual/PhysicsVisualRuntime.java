package dev.hytalemodding.impulse.core.internal.resources.visual;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Visual attachment, generated-proxy, and visual-interest state for one physics world.
 */
public final class PhysicsVisualRuntime {

    @Nonnull
    private final Consumer<Ref<EntityStore>> syncStateCleaner;
    private final Map<PhysicsBodyId, Set<Ref<EntityStore>>> bodyAttachments =
        new Object2ObjectLinkedOpenHashMap<>();
    private final Map<PhysicsBodyId, Ref<EntityStore>> generatedVisualProxies =
        new Object2ObjectLinkedOpenHashMap<>();
    private final List<VisualInterest> syntheticVisualInterests = new ArrayList<>();
    private final Map<PhysicsBodyId, BodyVisualInterestState> bodyVisualInterestStates =
        new Object2ObjectLinkedOpenHashMap<>();

    public PhysicsVisualRuntime(@Nonnull Consumer<Ref<EntityStore>> syncStateCleaner) {
        this.syncStateCleaner = syncStateCleaner;
    }

    public void registerAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        bodyAttachments.computeIfAbsent(bodyId, ignored -> new ObjectOpenHashSet<>())
            .add(attachment);
    }

    public void unregisterAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyId);
        if (attachments == null) {
            return;
        }

        attachments.remove(attachment);
        if (attachments.isEmpty()) {
            bodyAttachments.remove(bodyId);
        }
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getAttachments(@Nonnull PhysicsBodyId bodyId) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.get(bodyId);
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
            bodyAttachments.remove(bodyId);
        }
        return liveAttachments;
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        Ref<EntityStore> proxy = generatedVisualProxies.get(bodyId);
        if (proxy != null && !proxy.isValid()) {
            generatedVisualProxies.remove(bodyId);
            syncStateCleaner.accept(proxy);
            return null;
        }
        return proxy;
    }

    @Nonnull
    public Collection<PhysicsBodyId> getGeneratedVisualProxyBodyIds() {
        List<PhysicsBodyId> bodyIds = new ArrayList<>();
        List<PhysicsBodyId> staleBodyIds = new ArrayList<>();
        for (Map.Entry<PhysicsBodyId, Ref<EntityStore>> entry : generatedVisualProxies.entrySet()) {
            Ref<EntityStore> proxy = entry.getValue();
            if (proxy != null && proxy.isValid()) {
                bodyIds.add(entry.getKey());
            } else {
                staleBodyIds.add(entry.getKey());
                if (proxy != null) {
                    syncStateCleaner.accept(proxy);
                }
            }
        }
        for (PhysicsBodyId bodyId : staleBodyIds) {
            generatedVisualProxies.remove(bodyId);
        }
        return bodyIds;
    }

    public void setGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> proxy) {
        Ref<EntityStore> previousProxy = generatedVisualProxies.put(bodyId, proxy);
        if (previousProxy != null && previousProxy != proxy) {
            syncStateCleaner.accept(previousProxy);
        }
    }

    public void clearGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        Ref<EntityStore> proxy = generatedVisualProxies.remove(bodyId);
        if (proxy != null) {
            syncStateCleaner.accept(proxy);
        }
    }

    public boolean clearGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> expectedProxy) {
        Ref<EntityStore> proxy = generatedVisualProxies.get(bodyId);
        if (proxy == null || !sameRef(proxy, expectedProxy)) {
            return false;
        }

        generatedVisualProxies.remove(bodyId);
        syncStateCleaner.accept(proxy);
        return true;
    }

    public boolean isGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> proxy) {
        Ref<EntityStore> registeredProxy = generatedVisualProxies.get(bodyId);
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
        @Nonnull PhysicsBodyId bodyId) {
        return bodyVisualInterestStates.computeIfAbsent(bodyId,
            ignored -> new BodyVisualInterestState());
    }

    @Nullable
    public BodyVisualInterestState getBodyVisualInterestState(
        @Nonnull PhysicsBodyId bodyId) {
        return bodyVisualInterestStates.get(bodyId);
    }

    public void clearBodyRuntimeState(@Nonnull PhysicsBodyId bodyId) {
        Set<Ref<EntityStore>> attachments = bodyAttachments.remove(bodyId);
        if (attachments != null) {
            for (Ref<EntityStore> attachment : attachments) {
                if (attachment != null) {
                    syncStateCleaner.accept(attachment);
                }
            }
        }
        bodyVisualInterestStates.remove(bodyId);
        clearGeneratedVisualProxy(bodyId);
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
     * share one occlusion decision window instead of spending duplicate raycasts.</p>
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

        public long advanceVisualInterestTick(long currentTick) {
            this.currentTick = Math.max(this.currentTick, currentTick);
            return this.currentTick;
        }

    }

    public record VisualInterest(@Nonnull Vector3f position, @Nullable Vector3f direction) {
    }
}
