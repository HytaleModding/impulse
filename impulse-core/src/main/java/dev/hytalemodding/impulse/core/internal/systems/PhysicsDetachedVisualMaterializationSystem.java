package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.TransformAuthority;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.resources.VisualOcclusionMode;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.ToIntFunction;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, DespawnComponent> DESPAWN_TYPE =
        DespawnComponent.getComponentType();
    private static final ComponentType<EntityStore, Velocity> VELOCITY_TYPE =
        Velocity.getComponentType();
    private static final ComponentType<ChunkStore, WorldChunk> WORLD_CHUNK_TYPE =
        WorldChunk.getComponentType();

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    /*
     * Approximate visibility policy for optional visual culling. Close bodies
     * bypass the cone so players do not lose nearby proxies while turning.
     */
    private static final float VIEW_CONE_DOT = 0.35f;
    private static final float VIEW_CONE_NEAR_RADIUS_SQUARED = 8.0f * 8.0f;

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
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        if (state.orphanVisualCleanupCooldown <= 0) {
            removeOrphanVisualFollowers(store, resource);
            state.orphanVisualCleanupCooldown = ORPHAN_VISUAL_CLEANUP_INTERVAL_TICKS;
        } else {
            state.orphanVisualCleanupCooldown--;
        }
        List<PhysicsWorldResource.VisualInterest> interests = currentVisualInterests(state,
            store,
            resource,
            collector);
        int materialized = currentMaterializedProxyCount(state,
            store,
            resource,
            interests,
            collector);
        refreshCachedMaterializationTargets(state, store, resource, interests, collector);
        materialized += spawnCachedMaterializationTargets(state,
            store,
            resource,
            materialized,
            collector);
        if (collector != null) {
            collector.setMaterialized(materialized);
        }
    }

    @Nonnull
    private MaterializationState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (statesByStore) {
            return statesByStore.computeIfAbsent(store, ignored -> new MaterializationState());
        }
    }

    @Nonnull
    private List<PhysicsWorldResource.VisualInterest> currentVisualInterests(
        @Nonnull MaterializationState state,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
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
        @Nonnull PhysicsWorldResource resource,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        int refreshInterval = resolveMaterializedVisibilityCheckInterval(resource);
        state.materializedVisibilityCheckCooldown = Math.min(state.materializedVisibilityCheckCooldown,
            refreshCooldown(refreshInterval));
        if (state.materializedVisibilityCheckCooldown <= 0) {
            state.materializedVisibilityCheckCooldown = refreshCooldown(refreshInterval);
            return processMaterializedProxies(store, resource, interests, collector);
        }

        state.materializedVisibilityCheckCooldown--;
        int materialized = resource.getGeneratedVisualProxyBodyIds().size();
        if (collector != null) {
            collector.addVisibilityCheckSkips(materialized);
        }
        return materialized;
    }

    private void refreshCachedMaterializationTargets(@Nonnull MaterializationState state,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
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
            RaycastBudget raycastBudget = new RaycastBudget();
            if (collector != null) {
                collector.incrementCandidateRefreshes();
            }
            collectMaterializationCandidates(store,
                resource,
                interests,
                raycastBudget,
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
        @Nonnull PhysicsWorldResource resource,
        int materialized,
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
                target);
            if (candidate == null) {
                state.cachedMaterializationTargets.remove(index);
                continue;
            }
            if (spawned >= candidate.settings().getDetachedVisualMaxSpawnsPerTick()
                || materialized >= candidate.settings().getDetachedVisualMaxMaterialized()) {
                index++;
                continue;
            }
            state.cachedMaterializationTargets.remove(index);
            Ref<EntityStore> proxy = spawnProxy(store,
                candidate.bodyId(),
                candidate.snapshot(),
                candidate.registration(),
                candidate.settings());
            if (proxy == null) {
                continue;
            }
            resource.setGeneratedVisualProxy(candidate.bodyId(), proxy);
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
        @Nonnull PhysicsWorldResource resource,
        @Nonnull CachedMaterializationTarget target) {
        if (resource.getGeneratedVisualProxy(target.bodyId()) != null) {
            return null;
        }

        PhysicsWorldResource.BodyRegistration registration = resource.getRegistration(target.bodyId());
        if (registration == null
            || registration.kind() != PhysicsBodyKind.BODY
            || !sameSpaceId(registration.spaceId(), target.spaceId())
            || !resource.getBodyAttachments(registration.id()).isEmpty()) {
            return null;
        }

        PhysicsSpaceSettings settings = resolveSettings(resource, registration);
        if (settings == null || !settings.isDetachedVisualMaterializationEnabled()) {
            return null;
        }

        PhysicsBodySnapshot snapshot = resource.getBodySnapshot(target.bodyId());
        if (!isBodyChunkLoaded(store, snapshot)) {
            return null;
        }
        return new MaterializationCandidate(target.bodyId(),
            snapshot,
            registration,
            settings,
            target.distanceSquared());
    }

    private static int refreshCooldown(int intervalTicks) {
        return Math.max(0, intervalTicks - 1);
    }

    private static int resolveVisualInterestRefreshInterval(
        @Nonnull PhysicsWorldResource resource) {
        return resolveMinimumDetachedVisualInterval(resource,
            PhysicsSpaceSettings::getDetachedVisualInterestRefreshIntervalTicks,
            PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS);
    }

    private static int resolveMaterializationCandidateRefreshInterval(
        @Nonnull PhysicsWorldResource resource) {
        return resolveMinimumDetachedVisualInterval(resource,
            PhysicsSpaceSettings::getDetachedVisualCandidateRefreshIntervalTicks,
            PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS);
    }

    private static int resolveMaterializedVisibilityCheckInterval(
        @Nonnull PhysicsWorldResource resource) {
        return resolveMinimumDetachedVisualInterval(resource,
            PhysicsSpaceSettings::getDetachedVisualVisibilityCheckIntervalTicks,
            PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS);
    }

    private static int resolveMinimumDetachedVisualInterval(
        @Nonnull PhysicsWorldResource resource,
        @Nonnull ToIntFunction<PhysicsSpaceSettings> intervalGetter,
        int defaultInterval) {
        int interval = Integer.MAX_VALUE;
        for (PhysicsSpace space : resource.iterateSpaces()) {
            PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
            if (settings.isDetachedVisualMaterializationEnabled()) {
                interval = Math.min(interval, intervalGetter.applyAsInt(settings));
            }
        }
        return interval == Integer.MAX_VALUE ? defaultInterval : interval;
    }

    private static int processMaterializedProxies(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        int count = 0;
        for (PhysicsBodyId bodyId : resource.getGeneratedVisualProxyBodyIds()) {
            if (collector != null) {
                collector.incrementVisibilityChecks();
            }
            PhysicsWorldResource.BodyRegistration registration = resource.getRegistration(bodyId);
            if (registration == null || registration.kind() != PhysicsBodyKind.BODY) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyId);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }
            Ref<EntityStore> proxy = resource.getGeneratedVisualProxy(bodyId);
            if (proxy == null || !isExpectedProxy(store, proxy, bodyId, registration.spaceId())) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyId);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }
            if (hasGameplayAttachment(store, resource, bodyId, proxy)) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyId);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }

            PhysicsSpaceSettings settings = resolveSettings(resource, registration);
            if (settings == null || !settings.isDetachedVisualMaterializationEnabled()) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyId);
                if (collector != null) {
                    collector.incrementDematerialized();
                }
                continue;
            }

            PhysicsBodySnapshot snapshot = resource.getBodySnapshot(bodyId);
            if (!isBodyChunkLoaded(store, snapshot)
                || shouldDematerialize(snapshot, settings, interests)) {
                GeneratedProxyLifecycle.removeProxy(store, resource, bodyId);
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
        @Nonnull PhysicsWorldResource resource,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        @Nonnull RaycastBudget raycastBudget,
        @Nonnull List<CachedMaterializationTarget> candidates,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        if (interests.isEmpty()) {
            return;
        }

        Set<PhysicsBodyId> seenBodies = new ObjectOpenHashSet<>();
        for (PhysicsSpace space : resource.iterateSpaces()) {
            PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
            if (!settings.isDetachedVisualMaterializationEnabled()) {
                continue;
            }

            for (PhysicsWorldResource.VisualInterest interest : interests) {
                if (collector != null) {
                    collector.incrementNearQueries();
                }
                int nearCandidates = resource.forEachBodySnapshotNear(space.getId(),
                    interest.position(),
                    settings.getDetachedVisualMaterializationRadius(),
                    entry -> {
                        PhysicsBodySnapshot snapshot = entry.snapshot();
                        PhysicsBodyId bodyId = entry.bodyId();
                        if (!seenBodies.add(bodyId) || resource.getGeneratedVisualProxy(bodyId) != null) {
                            return;
                        }
                        PhysicsWorldResource.BodyRegistration registration =
                            resolveBodyRegistration(resource, entry);
                        if (registration == null) {
                            return;
                        }
                        if (!isBodyChunkLoaded(store, snapshot)) {
                            return;
                        }
                        PhysicsSpaceSettings registrationSettings = resolveSettings(resource, registration);
                        if (registrationSettings == null
                            || !registrationSettings.isDetachedVisualMaterializationEnabled()) {
                            return;
                        }

                        PhysicsSpace resolvedSpace = resolveSpace(resource, registration);
                        InterestResult materializeInterest = resolveVisualInterest(resource,
                            bodyId,
                            resolvedSpace,
                            snapshot,
                            registrationSettings,
                            interests,
                            registrationSettings.getDetachedVisualMaterializationRadius(),
                            raycastBudget,
                            collector);
                        if (materializeInterest.shouldMaterialize()) {
                            candidates.add(new CachedMaterializationTarget(bodyId,
                                registration.spaceId(),
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
        @Nonnull PhysicsWorldResource resource) {
        Queue<OrphanVisualProxy> orphanProxies = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(ATTACHMENT_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                PhysicsBodyAttachmentComponent attachment = archetypeChunk.getComponent(index,
                    ATTACHMENT_TYPE);
                if (attachment == null
                    || attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                    return;
                }

                var ref = archetypeChunk.getReferenceTo(index);
                orphanProxies.add(new OrphanVisualProxy(attachment.getBodyId(),
                    attachment.getSpaceId(),
                    ref));
            });

        for (OrphanVisualProxy proxy : orphanProxies) {
            if (!hasLiveVisualTarget(resource, proxy.bodyId(), proxy.spaceId(), proxy.ref())) {
                GeneratedProxyLifecycle.removeProxy(store, resource, proxy.bodyId(), proxy.ref());
            }
        }
    }

    private static boolean hasLiveVisualTarget(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBodyId bodyId,
        @Nullable SpaceId spaceId,
        @Nonnull Ref<EntityStore> proxyRef) {
        PhysicsWorldResource.BodyRegistration registration = resource.getRegistration(bodyId);
        if (registration == null || !sameSpaceId(registration.spaceId(), spaceId)) {
            return false;
        }
        return resource.getSpace(registration.spaceId()) != null
            && resource.isGeneratedVisualProxy(bodyId, proxyRef);
    }

    private record OrphanVisualProxy(
        @Nonnull PhysicsBodyId bodyId,
        @Nullable SpaceId spaceId,
        @Nonnull Ref<EntityStore> ref
    ) {
    }

    @Nullable
    private static PhysicsWorldResource.BodyRegistration resolveBodyRegistration(
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsWorldResource.BodySnapshotEntry entry) {
        PhysicsWorldResource.BodyRegistration registration = entry.registration();
        if (registration.body() != entry.snapshot().body() || !registration.id()
            .equals(entry.bodyId())) {
            registration = resource.getRegistration(entry.bodyId());
        }
        if (registration == null
            || registration.kind() != PhysicsBodyKind.BODY
            || !resource.getBodyAttachments(registration.id()).isEmpty()) {
            return null;
        }
        SpaceId registrationSpaceId = registration.spaceId();
        if (!registrationSpaceId.equals(entry.spaceId())) {
            return null;
        }
        return registration;
    }

    private static boolean hasGameplayAttachment(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> proxy) {
        for (Ref<EntityStore> attachmentRef : resource.getBodyAttachments(bodyId)) {
            if (attachmentRef == proxy || attachmentRef.equals(proxy)) {
                continue;
            }
            PhysicsBodyAttachmentComponent attachment = store.getComponent(attachmentRef,
                ATTACHMENT_TYPE);
            if (attachment != null && attachment.getLifecycle() != AttachmentLifecycle.GENERATED_PROXY) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static PhysicsSpaceSettings resolveSettings(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsWorldResource.BodyRegistration registration) {
        if (resource.getSpace(registration.spaceId()) != null) {
            return resource.getSpaceSettings(registration.spaceId());
        }
        if (resource.getDefaultSpaceId() != null) {
            return resource.getSpaceSettings(resource.getDefaultSpaceId());
        }
        return null;
    }

    @Nullable
    private static PhysicsSpace resolveSpace(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsWorldResource.BodyRegistration registration) {
        PhysicsSpace space = resource.getSpace(registration.spaceId());
        if (space != null) {
            return space;
        }
        return resource.getDefaultSpace();
    }

    private static boolean shouldMaterialize(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests) {
        if (interests.isEmpty()) {
            return false;
        }
        return visibleDistanceSquared(snapshot,
            settings,
            interests,
            settings.getDetachedVisualMaterializationRadius()) != Float.POSITIVE_INFINITY;
    }

    private static boolean shouldDematerialize(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests) {
        if (interests.isEmpty()) {
            return true;
        }
        return visibleDistanceSquared(snapshot,
            settings,
            interests,
            settings.getDetachedVisualDematerializationRadius()) == Float.POSITIVE_INFINITY;
    }

    private static float visibleDistanceSquared(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        float radius) {
        float radiusSquared = radius * radius;
        Vector3f bodyPosition = snapshot.position();
        float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        for (PhysicsWorldResource.VisualInterest interest : interests) {
            float dx = bodyPosition.x - interest.position().x;
            float dy = bodyPosition.y - interest.position().y;
            float dz = bodyPosition.z - interest.position().z;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= radiusSquared
                && isInsideViewCone(settings, interest, dx, dy, dz, distanceSquared)) {
                nearestDistanceSquared = Math.min(nearestDistanceSquared, distanceSquared);
            }
        }
        return nearestDistanceSquared;
    }

    @Nonnull
    private static InterestResult resolveVisualInterest(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBodyId bodyId,
        @Nullable PhysicsSpace space,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        float radius,
        @Nonnull RaycastBudget raycastBudget,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        InterestProbe probe = probeNearestLikelyInterest(snapshot, settings, interests, radius);
        PhysicsWorldResource.BodyVisualInterestState state =
            resource.getOrCreateBodyVisualInterestState(bodyId);
        if (!probe.inRange()) {
            state.recordInterest(Float.POSITIVE_INFINITY, false, false, false);
            return InterestResult.notVisible();
        }

        VisualOcclusionMode occlusionMode = settings.getVisualOcclusionMode();
        if (occlusionMode == VisualOcclusionMode.OFF || space == null) {
            state.recordInterest(probe.distanceSquared(), true, true, false);
            return InterestResult.visible(probe.distanceSquared(), probe.distanceSquared());
        }

        boolean raycastKnown = state.hasFreshRaycast(settings.getVisualOcclusionCacheTicks());
        boolean raycastVisible = raycastKnown && state.isRaycastVisible();
        boolean raycastEvaluated = false;
        if (raycastKnown && collector != null) {
            collector.incrementRaycastCacheHits();
        }
        if (!raycastKnown && raycastBudget.tryUse(settings)) {
            assert probe.interest() != null;
            raycastVisible = raycastVisible(space, probe.interest(), snapshot);
            raycastKnown = true;
            raycastEvaluated = true;
            if (collector != null) {
                collector.incrementRaycasts();
            }
        }

        state.recordInterest(probe.distanceSquared(), true, raycastVisible, raycastEvaluated);
        if (occlusionMode == VisualOcclusionMode.CULL && raycastKnown && !raycastVisible) {
            return InterestResult.notVisible();
        }

        /*
         * PRIORITY only biases spawn order: visible bodies move forward in the
         * queue and occluded bodies move back. CULL above is the mode that skips
         * occluded candidates entirely.
         */
        float priorityDistanceSquared = probe.distanceSquared();
        if (occlusionMode == VisualOcclusionMode.PRIORITY && raycastKnown) {
            priorityDistanceSquared = raycastVisible
                ? probe.distanceSquared() * 0.25f
                : probe.distanceSquared() + radius * radius;
        }
        return InterestResult.visible(probe.distanceSquared(), priorityDistanceSquared);
    }

    @Nonnull
    private static InterestProbe probeNearestLikelyInterest(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        float radius) {
        float radiusSquared = radius * radius;
        Vector3f bodyPosition = snapshot.position();
        float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        PhysicsWorldResource.VisualInterest nearestInterest = null;
        for (PhysicsWorldResource.VisualInterest interest : interests) {
            float dx = bodyPosition.x - interest.position().x;
            float dy = bodyPosition.y - interest.position().y;
            float dz = bodyPosition.z - interest.position().z;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= radiusSquared
                && distanceSquared < nearestDistanceSquared
                && isInsideViewCone(settings, interest, dx, dy, dz, distanceSquared)) {
                nearestDistanceSquared = distanceSquared;
                nearestInterest = interest;
            }
        }
        return nearestInterest == null
            ? InterestProbe.notVisible()
            : new InterestProbe(nearestInterest, nearestDistanceSquared);
    }

    private static boolean raycastVisible(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsWorldResource.VisualInterest interest,
        @Nonnull PhysicsBodySnapshot snapshot) {
        Optional<PhysicsRayHit> hit = space.raycastClosest(interest.position(), snapshot.position());
        return hit.isPresent() && hit.get().body() == snapshot.body();
    }

    private static boolean isInsideViewCone(@Nonnull PhysicsSpaceSettings settings,
        @Nonnull PhysicsWorldResource.VisualInterest interest,
        float dx,
        float dy,
        float dz,
        float distanceSquared) {
        if (!settings.isVisualVisibilityCullingEnabled()
            || interest.direction() == null
            || distanceSquared <= VIEW_CONE_NEAR_RADIUS_SQUARED) {
            return true;
        }

        float length = (float) Math.sqrt(distanceSquared);
        if (length <= 0.0f) {
            return true;
        }
        Vector3f direction = interest.direction();
        float dot = (dx * direction.x + dy * direction.y + dz * direction.z) / length;
        return dot >= VIEW_CONE_DOT;
    }

    private static boolean isBodyChunkLoaded(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsBodySnapshot snapshot) {
        Vector3f position = snapshot.position();
        ChunkStore chunkStore = store.getExternalData().getWorld().getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunk(
            ChunkUtil.chunkCoordinate(position.x),
            ChunkUtil.chunkCoordinate(position.z)));
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        WorldChunk worldChunk = chunkComponentStore.getComponentConcurrent(chunkRef,
            WORLD_CHUNK_TYPE);
        return worldChunk != null;
    }

    private static boolean isExpectedProxy(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> proxy,
        @Nonnull PhysicsBodyId bodyId,
        @Nullable SpaceId spaceId) {
        if (!proxy.isValid()) {
            return false;
        }
        PhysicsBodyAttachmentComponent attachment = store.getComponent(proxy, ATTACHMENT_TYPE);
        return attachment != null
            && attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY
            && attachment.getBodyId().equals(bodyId)
            && sameSpaceId(attachment.getSpaceId(), spaceId);
    }

    private static boolean sameSpaceId(@Nullable SpaceId first, @Nullable SpaceId second) {
        return Objects.equals(first, second);
    }

    @Nullable
    private static Ref<EntityStore> spawnProxy(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsWorldResource.BodyRegistration registration,
        @Nonnull PhysicsSpaceSettings settings) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Vector3f position = snapshot.position();
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            settings.getDetachedVisualBlockType(),
            new Vector3d(position.x,
                position.y - snapshot.centerOfMassOffsetY(),
                position.z));
        holder.removeComponent(DESPAWN_TYPE);
        holder.removeComponent(VELOCITY_TYPE);
        holder.addComponent(store.getRegistry().getNonSerializedComponentType(), NonSerialized.get());
        holder.addComponent(ATTACHMENT_TYPE,
            new PhysicsBodyAttachmentComponent(bodyId,
                registration.spaceId(),
                TransformAuthority.BODY,
                AttachmentLifecycle.GENERATED_PROXY));
        return store.addEntity(holder, AddReason.SPAWN);
    }

    private record MaterializationCandidate(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsWorldResource.BodyRegistration registration,
        @Nonnull PhysicsSpaceSettings settings,
        float distanceSquared) {
    }

    private record CachedMaterializationTarget(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        float distanceSquared) {
    }

    private static final class MaterializationState {

        private int orphanVisualCleanupCooldown;
        private int visualInterestRefreshCooldown;
        private int materializationCandidateRefreshCooldown;
        private int materializedVisibilityCheckCooldown;
        @Nonnull
        private List<PhysicsWorldResource.VisualInterest> cachedInterests = List.of();
        @Nonnull
        private final List<CachedMaterializationTarget> cachedMaterializationTargets =
            new ArrayList<>();
    }

    private record InterestProbe(@Nullable PhysicsWorldResource.VisualInterest interest,
        float distanceSquared) {

        static InterestProbe notVisible() {
            return new InterestProbe(null, Float.POSITIVE_INFINITY);
        }

        boolean inRange() {
            return interest != null;
        }
    }

    private record InterestResult(boolean shouldMaterialize,
        float distanceSquared,
        float priorityDistanceSquared) {

        static InterestResult notVisible() {
            return new InterestResult(false, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        }

        static InterestResult visible(float distanceSquared, float priorityDistanceSquared) {
            return new InterestResult(true, distanceSquared, priorityDistanceSquared);
        }
    }

    private static final class RaycastBudget {

        private int used;

        boolean tryUse(@Nonnull PhysicsSpaceSettings settings) {
            if (used >= settings.getVisualOcclusionRaycastsPerTick()) {
                return false;
            }
            used++;
            return true;
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
