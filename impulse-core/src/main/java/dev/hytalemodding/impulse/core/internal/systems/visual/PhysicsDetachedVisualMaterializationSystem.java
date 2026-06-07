package dev.hytalemodding.impulse.core.internal.systems.visual;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.internal.math.PhysicsVisualPoseMath;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.VisualInterest;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.TransformAuthority;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.ToIntFunction;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Materializes disposable Hytale visual followers for detached physics bodies near players.
 *
 * <p>Detached bodies stay physics-authoritative and are not persisted through these visual
 * proxies. Proxies are ordinary Hytale block entities with a generated
 * {@link PhysicsBodyAttachmentComponent}, so removing a proxy never removes the backend body.</p>
 */
public class PhysicsDetachedVisualMaterializationSystem extends TickingSystem<EntityStore> {

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    /**
     * Bounds generated-proxy orphan scans. Regular materialized-proxy
     * visibility checks are cached separately below.
     */
    private static final int ORPHAN_VISUAL_CLEANUP_INTERVAL_TICKS = 40;
    @Nonnull
    private final Map<Store<EntityStore>, MaterializationState> statesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsRuntimeProfilingResource profiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        PhysicsRuntimeProfilingResource.VisualCollector collector = profiling.isEnabled()
            ? profiling.beginVisualSample()
            : null;
        long tickStart = collector != null ? System.nanoTime() : 0L;
        try {
            tickMaterialization(store, collector);
        } finally {
            if (collector != null) {
                profiling.finishVisualSample(collector, System.nanoTime() - tickStart);
            }
        }
    }

    private void tickMaterialization(@Nonnull Store<EntityStore> store,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        MaterializationState state = stateFor(store);
        PersistentPhysicsWorldResource persistent = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        if (shouldPauseForRestore(state, persistent)) {
            return;
        }
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        if (state.orphanVisualCleanupCooldown <= 0) {
            removeOrphanVisualFollowers(store, resource);
            state.orphanVisualCleanupCooldown = ORPHAN_VISUAL_CLEANUP_INTERVAL_TICKS;
        } else {
            state.orphanVisualCleanupCooldown--;
        }
        long visualInterestTick = resource.advanceVisualInterestTick();
        GameplayAttachmentSnapshot gameplayAttachments = GameplayAttachmentSnapshot.forStore(store);
        List<VisualInterest> interests = currentVisualInterests(state,
            store,
            resource,
            collector);
        int materialized = currentMaterializedProxyCount(state,
            store,
            resource,
            interests,
            gameplayAttachments,
            collector);
        refreshCachedMaterializationTargets(state,
            store,
            resource,
            interests,
            visualInterestTick,
            gameplayAttachments,
            collector);
        materialized += spawnCachedMaterializationTargets(state,
            store,
            resource,
            materialized,
            gameplayAttachments,
            collector);
        if (collector != null) {
            collector.setMaterialized(materialized);
        }
    }

    private static boolean shouldPauseForRestore(@Nonnull MaterializationState state,
        @Nonnull PersistentPhysicsWorldResource persistent) {
        long restoreGeneration = persistent.runtimeRestoreGeneration();
        if (state.observedRestoreGeneration != restoreGeneration) {
            state.observedRestoreGeneration = restoreGeneration;
            state.cachedMaterializationTargets.clear();
            state.materializationCandidateRefreshCooldown = 0;
            state.materializedVisibilityCheckCooldown = 0;
            return true;
        }
        if (!persistent.isRuntimeRestorePending()) {
            return false;
        }
        state.cachedMaterializationTargets.clear();
        state.materializationCandidateRefreshCooldown = 0;
        state.materializedVisibilityCheckCooldown = 0;
        return true;
    }

    @Nonnull
    private MaterializationState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (statesByStore) {
            return statesByStore.computeIfAbsent(store, ignored -> new MaterializationState());
        }
    }

    @Nonnull
    private List<VisualInterest> currentVisualInterests(
        @Nonnull MaterializationState state,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        int refreshInterval = resolveVisualInterestRefreshInterval(resource);
        state.visualInterestRefreshCooldown = Math.min(state.visualInterestRefreshCooldown,
            refreshCooldown(refreshInterval));
        if (state.visualInterestRefreshCooldown <= 0) {
            state.cachedInterests = VisualInterestCollector.collectMaterializationInterests(store, resource);
            state.visualInterestRefreshCooldown = refreshCooldown(refreshInterval);
            state.materializationCandidateRefreshCooldown = 0;
        } else {
            state.visualInterestRefreshCooldown--;
        }
        if (collector != null) {
            collector.setInterests(state.cachedInterests.size());
        }
        return state.cachedInterests;
    }

    private int currentMaterializedProxyCount(@Nonnull MaterializationState state,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull List<VisualInterest> interests,
        @Nonnull GameplayAttachmentSnapshot gameplayAttachments,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        int refreshInterval = resolveMaterializedVisibilityCheckInterval(resource);
        state.materializedVisibilityCheckCooldown = Math.min(state.materializedVisibilityCheckCooldown,
            refreshCooldown(refreshInterval));
        if (state.materializedVisibilityCheckCooldown <= 0) {
            state.materializedVisibilityCheckCooldown = refreshCooldown(refreshInterval);
            return processMaterializedProxies(store, resource, interests, gameplayAttachments, collector);
        }

        state.materializedVisibilityCheckCooldown--;
        int materialized = resource.getGeneratedVisualProxyCount();
        if (collector != null) {
            collector.addVisibilityCheckSkips(materialized);
        }
        return materialized;
    }

    private void refreshCachedMaterializationTargets(@Nonnull MaterializationState state,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull List<VisualInterest> interests,
        long visualInterestTick,
        @Nonnull GameplayAttachmentSnapshot gameplayAttachments,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        if (interests.isEmpty()) {
            state.cachedMaterializationTargets.clear();
            state.materializationCandidateRefreshCooldown = 0;
            if (collector != null) {
                collector.setCandidates(0);
            }
            return;
        }

        int refreshInterval = resolveMaterializationCandidateRefreshInterval(resource);
        state.materializationCandidateRefreshCooldown = Math.min(
            state.materializationCandidateRefreshCooldown,
            refreshCooldown(refreshInterval));
        if (state.materializationCandidateRefreshCooldown <= 0) {
            List<CachedMaterializationTarget> refreshedTargets = new ArrayList<>();
            DetachedVisualOcclusion.RaycastBudget raycastBudget =
                new DetachedVisualOcclusion.RaycastBudget();
            if (collector != null) {
                collector.incrementCandidateRefreshes();
            }
            collectMaterializationCandidates(store,
                resource,
                interests,
                visualInterestTick,
                raycastBudget,
                gameplayAttachments,
                refreshedTargets,
                collector);
            refreshedTargets.removeIf(Objects::isNull);
            refreshedTargets.sort(Comparator.comparingDouble(
                CachedMaterializationTarget::distanceSquared));
            state.cachedMaterializationTargets.clear();
            state.cachedMaterializationTargets.addAll(refreshedTargets);
            state.materializationCandidateRefreshCooldown = refreshCooldown(refreshInterval);
        } else {
            state.materializationCandidateRefreshCooldown--;
            if (collector != null) {
                collector.incrementCandidateCacheUses();
            }
        }

        if (collector != null) {
            collector.setCandidates(state.cachedMaterializationTargets.size());
        }
    }

    private int spawnCachedMaterializationTargets(@Nonnull MaterializationState state,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        int materialized,
        @Nonnull GameplayAttachmentSnapshot gameplayAttachments,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        int spawned = 0;
        int index = 0;
        while (index < state.cachedMaterializationTargets.size()) {
            CachedMaterializationTarget target = state.cachedMaterializationTargets.get(index);
            if (target == null) {
                state.cachedMaterializationTargets.remove(index);
                continue;
            }
            MaterializationCandidate candidate = resolveCachedMaterializationCandidate(store,
                resource,
                target,
                gameplayAttachments);
            if (candidate == null) {
                state.cachedMaterializationTargets.remove(index);
                continue;
            }
            if (spawned >= candidate.settings().getVisualMaterializationSettings().getDetachedVisualMaxSpawnsPerTick()
                || materialized >= candidate.settings().getVisualMaterializationSettings().getDetachedVisualMaxMaterialized()) {
                index++;
                continue;
            }
            state.cachedMaterializationTargets.remove(index);
            Ref<EntityStore> proxy = spawnProxy(store,
                candidate.bodyKey(),
                candidate.snapshot(),
                candidate.registration(),
                candidate.settings());
            if (proxy == null) {
                continue;
            }
            resource.setGeneratedVisualProxy(candidate.bodyKey(), proxy);
            spawned++;
            materialized++;
            if (collector != null) {
                collector.incrementSpawned();
            }
        }
        return spawned;
    }

    @Nullable
    private static MaterializationCandidate resolveCachedMaterializationCandidate(
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull CachedMaterializationTarget target,
        @Nonnull GameplayAttachmentSnapshot gameplayAttachments) {
        if (resource.getGeneratedVisualProxy(target.bodyKey()) != null) {
            return null;
        }

        PhysicsBodyRegistrationView registration = resource.getBodyRegistrationView(target.bodyKey());
        if (registration == null
            || registration.kind() != PhysicsBodyKind.BODY
            || !sameSpaceId(registration.spaceId(), target.spaceId())
            || gameplayAttachments.hasKnownGameplayAttachment(
                resource.hasBodyAttachments(registration.bodyKey()),
                registration.bodyKey())) {
            return null;
        }

        PhysicsSpaceSettings settings = resolveSettings(resource, registration);
        if (settings == null || !settings.getVisualMaterializationSettings().isDetachedVisualMaterializationEnabled()) {
            return null;
        }

        PhysicsBodySnapshot snapshot = resource.getBodySnapshotIfRegistered(target.bodyKey());
        if (snapshot == null) {
            return null;
        }
        if (!isBodyChunkLoaded(store, snapshot)) {
            return null;
        }
        return new MaterializationCandidate(target.bodyKey(),
            snapshot,
            registration,
            settings,
            target.distanceSquared());
    }

    private static int refreshCooldown(int intervalTicks) {
        return Math.max(0, intervalTicks - 1);
    }

    private static int resolveVisualInterestRefreshInterval(
        @Nonnull PhysicsWorldRuntimeResource resource) {
        return resolveMinimumDetachedVisualInterval(resource,
            settings -> settings.getVisualMaterializationSettings()
                .getDetachedVisualInterestRefreshIntervalTicks(),
            PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS);
    }

    private static int resolveMaterializationCandidateRefreshInterval(
        @Nonnull PhysicsWorldRuntimeResource resource) {
        return resolveMinimumDetachedVisualInterval(resource,
            settings -> settings.getVisualMaterializationSettings()
                .getDetachedVisualCandidateRefreshIntervalTicks(),
            PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS);
    }

    private static int resolveMaterializedVisibilityCheckInterval(
        @Nonnull PhysicsWorldRuntimeResource resource) {
        return resolveMinimumDetachedVisualInterval(resource,
            settings -> settings.getVisualMaterializationSettings()
                .getDetachedVisualVisibilityCheckIntervalTicks(),
            PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS);
    }

    private static int resolveMinimumDetachedVisualInterval(
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull ToIntFunction<PhysicsSpaceSettings> intervalGetter,
        int defaultInterval) {
        int interval = Integer.MAX_VALUE;
        for (PhysicsSpaceBinding space : resource.iterateSpaceBindings()) {
            PhysicsSpaceSettings settings = resource.getLiveSpaceSettings(space.spaceId());
            if (settings.getVisualMaterializationSettings().isDetachedVisualMaterializationEnabled()) {
                interval = Math.min(interval, intervalGetter.applyAsInt(settings));
            }
        }
        return interval == Integer.MAX_VALUE ? defaultInterval : interval;
    }

    private static int processMaterializedProxies(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull List<VisualInterest> interests,
        @Nonnull GameplayAttachmentSnapshot gameplayAttachments,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        int count = 0;
        for (RigidBodyKey bodyKey : resource.getGeneratedVisualProxyBodyKeys()) {
            if (collector != null) {
                collector.incrementVisibilityChecks();
            }
            PhysicsBodyRegistrationView registration = resource.getBodyRegistrationView(bodyKey);
            if (registration == null || registration.kind() != PhysicsBodyKind.BODY) {
                if (registration == null && resource.isBodyCreationPending(bodyKey)) {
                    count++;
                    continue;
                }
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyKey);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }
            Ref<EntityStore> proxy = resource.getGeneratedVisualProxy(bodyKey);
            if (proxy == null || !isExpectedProxy(store, proxy, bodyKey, registration.spaceId())) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyKey);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }
            if (hasGameplayAttachment(store, resource, bodyKey, proxy, gameplayAttachments)) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyKey);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }

            PhysicsSpaceSettings settings = resolveSettings(resource, registration);
            if (settings == null || !settings.getVisualMaterializationSettings().isDetachedVisualMaterializationEnabled()) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyKey);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }

            PhysicsBodySnapshot snapshot = resource.getBodySnapshotIfRegistered(bodyKey);
            if (snapshot == null) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyKey);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }
            if (!isBodyChunkLoaded(store, snapshot)
                || shouldDematerialize(snapshot, settings, interests)) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyKey);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }
            count++;
        }
        return count;
    }

    private static void collectMaterializationCandidates(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull List<VisualInterest> interests,
        long visualInterestTick,
        @Nonnull DetachedVisualOcclusion.RaycastBudget raycastBudget,
        @Nonnull GameplayAttachmentSnapshot gameplayAttachments,
        @Nonnull List<CachedMaterializationTarget> candidates,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        if (interests.isEmpty()) {
            return;
        }

        Set<RigidBodyKey> seenBodies = new ObjectOpenHashSet<>();
        for (PhysicsSpaceBinding space : resource.iterateSpaceBindings()) {
            PhysicsSpaceSettings settings = resource.getLiveSpaceSettings(space.spaceId());
            if (!settings.getVisualMaterializationSettings().isDetachedVisualMaterializationEnabled()) {
                continue;
            }

            for (int interestIndex = 0; interestIndex < interests.size(); interestIndex++) {
                VisualInterest interest = interests.get(interestIndex);
                if (collector != null) {
                    collector.incrementNearQueries();
                }
                int nearCandidates = resource.forEachIndexedBodySnapshotNear(space.spaceId(),
                    interest.position(),
                    settings.getVisualMaterializationSettings().getDetachedVisualMaterializationRadius(),
                    (bodyKey, snapshot, bodySpaceId, kind, persistenceMode) -> {
                        if (!seenBodies.add(bodyKey) || resource.getGeneratedVisualProxy(bodyKey) != null) {
                            return;
                        }
                        if (kind != PhysicsBodyKind.BODY
                            || !bodySpaceId.equals(space.spaceId())
                            || gameplayAttachments.hasKnownGameplayAttachment(
                                resource.hasBodyAttachments(bodyKey),
                                bodyKey)) {
                            return;
                        }
                        if (!isBodyChunkLoaded(store, snapshot)) {
                            return;
                        }
                        DetachedVisualOcclusion.Result materializeInterest =
                            DetachedVisualOcclusion.resolve(resource,
                            bodyKey,
                            space,
                            snapshot,
                            settings,
                            interests,
                            settings.getVisualMaterializationSettings().getDetachedVisualMaterializationRadius(),
                            visualInterestTick,
                            raycastBudget,
                            collector);
                        if (materializeInterest.shouldMaterialize()) {
                            candidates.add(new CachedMaterializationTarget(bodyKey,
                                bodySpaceId,
                                materializeInterest.priorityDistanceSquared()));
                        }
                    });
                if (collector != null) {
                    collector.addNearQueryCandidates(nearCandidates);
                }
            }
        }
    }

    private static void removeOrphanVisualFollowers(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource) {
        Queue<OrphanVisualProxy> orphanProxies = new ConcurrentLinkedQueue<>();
        ComponentType<EntityStore, PhysicsBodyAttachmentComponent> attachmentType = attachmentType();
        store.forEachEntityParallel(attachmentType,
            (index, archetypeChunk, commandBuffer) -> {
                PhysicsBodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    attachmentType);
                if (attachment == null
                    || attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                    return;
                }

                var ref = archetypeChunk.getReferenceTo(index);
                orphanProxies.add(new OrphanVisualProxy(attachment.getBodyKey(),
                    attachment.getSpaceId(),
                    ref));
            });

        for (OrphanVisualProxy proxy : orphanProxies) {
            if (!hasLiveVisualTarget(resource, proxy.bodyKey(), proxy.spaceId(), proxy.ref())) {
                GeneratedProxyLifecycle.removeProxy(store, resource, proxy.bodyKey(), proxy.ref());
            }
        }
    }

    private static boolean hasLiveVisualTarget(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull Ref<EntityStore> proxyRef) {
        PhysicsBodyRegistrationView registration = resource.getBodyRegistrationView(bodyKey);
        if (registration == null) {
            return resource.isBodyCreationPending(bodyKey)
                && resource.isGeneratedVisualProxy(bodyKey, proxyRef);
        }
        if (!sameSpaceId(registration.spaceId(), spaceId)) {
            return false;
        }
        return resource.getSpaceBinding(registration.spaceId()) != null
            && resource.isGeneratedVisualProxy(bodyKey, proxyRef);
    }

    private record OrphanVisualProxy(
        @Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId,
        @Nonnull Ref<EntityStore> ref
    ) {
    }

    private static boolean hasGameplayAttachment(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> proxy,
        @Nonnull GameplayAttachmentSnapshot gameplayAttachments) {
        ComponentType<EntityStore, PhysicsBodyAttachmentComponent> attachmentType = attachmentType();
        for (Ref<EntityStore> attachmentRef : resource.getBodyAttachments(bodyKey)) {
            if (attachmentRef == proxy || attachmentRef.equals(proxy)) {
                continue;
            }
            PhysicsBodyAttachmentComponent attachment = store.getComponent(attachmentRef,
                attachmentType);
            if (attachment != null && attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                return true;
            }
        }
        return gameplayAttachments.hasGameplayAttachment(bodyKey);
    }

    @Nullable
    private static PhysicsSpaceSettings resolveSettings(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsBodyRegistrationView registration) {
        if (resource.getSpaceBinding(registration.spaceId()) != null) {
            return resource.getLiveSpaceSettings(registration.spaceId());
        }
        return null;
    }

    @Nullable
    private static PhysicsSpaceBinding resolveSpace(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsBodyRegistrationView registration) {
        return resource.getSpaceBinding(registration.spaceId());
    }

    private static boolean shouldMaterialize(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<VisualInterest> interests) {
        if (interests.isEmpty()) {
            return false;
        }
        return visibleDistanceSquared(snapshot,
            settings,
            interests,
            settings.getVisualMaterializationSettings().getDetachedVisualMaterializationRadius()) != Float.POSITIVE_INFINITY;
    }

    private static boolean shouldDematerialize(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<VisualInterest> interests) {
        if (interests.isEmpty()) {
            return true;
        }
        return visibleDistanceSquared(snapshot,
            settings,
            interests,
            settings.getVisualMaterializationSettings().getDetachedVisualDematerializationRadius()) == Float.POSITIVE_INFINITY;
    }

    private static float visibleDistanceSquared(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<VisualInterest> interests,
        float radius) {
        return DetachedVisualGeometry.visibleDistanceSquared(snapshot.positionX(),
            snapshot.positionY(),
            snapshot.positionZ(),
            settings,
            interests,
            radius);
    }

    private static boolean isBodyChunkLoaded(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsBodySnapshot snapshot) {
        ChunkStore chunkStore = store.getExternalData().getWorld().getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunk(
            ChunkUtil.chunkCoordinate(snapshot.positionX()),
            ChunkUtil.chunkCoordinate(snapshot.positionZ())));
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        WorldChunk worldChunk = chunkComponentStore.getComponentConcurrent(chunkRef,
            worldChunkType());
        return worldChunk != null;
    }

    private static boolean isExpectedProxy(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> proxy,
        @Nonnull RigidBodyKey bodyKey,
        @Nullable SpaceId spaceId) {
        if (!proxy.isValid()) {
            return false;
        }
        PhysicsBodyAttachmentComponent attachment = store.getComponent(proxy, attachmentType());
        return attachment != null
            && attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY
            && attachment.getBodyKey().equals(bodyKey)
            && sameSpaceId(attachment.getSpaceId(), spaceId);
    }

    private static boolean sameSpaceId(@Nullable SpaceId first, @Nullable SpaceId second) {
        return Objects.equals(first, second);
    }

    @Nullable
    private static Ref<EntityStore> spawnProxy(@Nonnull Store<EntityStore> store,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsBodyRegistrationView registration,
        @Nonnull PhysicsSpaceSettings settings) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Quaternionf rotation = new Quaternionf(snapshot.rotationX(),
            snapshot.rotationY(),
            snapshot.rotationZ(),
            snapshot.rotationW());
        Vector3f visualPosition = PhysicsVisualPoseMath.visualPositionFromBodyPose(new Vector3f(snapshot.positionX(),
                snapshot.positionY(),
                snapshot.positionZ()),
            rotation,
            snapshot.centerOfMassOffsetY(),
            new Vector3f(),
            new Vector3f());
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            settings.getVisualMaterializationSettings().getDetachedVisualBlockType(),
            new Vector3d(visualPosition.x, visualPosition.y, visualPosition.z));
        TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
        if (transform != null) {
            Vector3f euler = rotation.getEulerAnglesYXZ(new Vector3f());
            transform.getRotation().set(euler.x, euler.y, euler.z);
        }
        holder.removeComponent(despawnType());
        holder.removeComponent(velocityType());
        holder.addComponent(store.getRegistry().getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(GeneratedVisualProxyComponent.getComponentType(), new GeneratedVisualProxyComponent());
        holder.addComponent(attachmentType(),
            new PhysicsBodyAttachmentComponent(bodyKey,
                registration.spaceId(),
                TransformAuthority.BODY,
                AttachmentLifecycle.GENERATED_PROXY));
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    private static ComponentType<EntityStore, PhysicsBodyAttachmentComponent> attachmentType() {
        return PhysicsBodyAttachmentComponent.getComponentType();
    }

    @Nonnull
    private static ComponentType<EntityStore, DespawnComponent> despawnType() {
        return DespawnComponent.getComponentType();
    }

    @Nonnull
    private static ComponentType<EntityStore, Velocity> velocityType() {
        return Velocity.getComponentType();
    }

    @Nonnull
    private static ComponentType<ChunkStore, WorldChunk> worldChunkType() {
        return WorldChunk.getComponentType();
    }

    private record MaterializationCandidate(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsBodyRegistrationView registration,
        @Nonnull PhysicsSpaceSettings settings,
        float distanceSquared) {
    }

    private record CachedMaterializationTarget(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        float distanceSquared) {
    }

    private static final class MaterializationState {

        private int orphanVisualCleanupCooldown;
        private int visualInterestRefreshCooldown;
        private int materializationCandidateRefreshCooldown;
        private int materializedVisibilityCheckCooldown;
        private long observedRestoreGeneration;
        @Nonnull
        private List<VisualInterest> cachedInterests = List.of();
        @Nonnull
        private final List<CachedMaterializationTarget> cachedMaterializationTargets =
            new ArrayList<>();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }
}
