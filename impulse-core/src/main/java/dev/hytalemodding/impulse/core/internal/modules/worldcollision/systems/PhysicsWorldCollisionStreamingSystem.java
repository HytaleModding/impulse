package dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.StreamingTargetDiagnostic;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionBuildOptions;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionStreamingBounds;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldVoxelCollisionCache.SectionAccessCache;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldVoxelCollisionCache.TargetRefreshDecision;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Streams world voxel collision around online players and dynamic physics bodies
 * once per entity-store tick.
 *
 * <p>Players stream at the configured world collision radius.
 * Dynamic physics bodies stream at a smaller radius so they do not
 * aggressively pull collision into unpopulated areas, but still
 * have terrain to interact with after rolling away from players.</p>
 */
public class PhysicsWorldCollisionStreamingSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    @Nullable
    private static volatile ComponentType<EntityStore, Player> playerType;
    @Nullable
    private static volatile ComponentType<EntityStore, TransformComponent> transformType;
    @Nullable
    private static volatile Query<EntityStore> query;

    /**
     * Radius (in blocks) used for streaming around dynamic physics bodies.
     * Smaller than the player radius because bodies should not pull collision
     * as far as players do, but they still need terrain to land on.
     */
    public static final int DEFAULT_BODY_STREAMING_RADIUS = 4;

    @Nonnull
    private final Map<Store<EntityStore>, StreamingState> statesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (!WorldCollisionLifecycle.isEnabled()) {
            return;
        }
        WorldCollisionProfilingResource profiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        Snapshot snapshot = null;
        long tickStart = 0L;
        try {
            World world = store.getExternalData().getWorld();
            PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
            WorldVoxelCollisionCache cache = resource.worldCollisionCache();
            if (cache.isStreamingApplyPending()) {
                recordSkippedTerrainApply(profiling);
                return;
            }

            snapshot = profiling.isEnabled() ? profiling.beginTick() : null;
            tickStart = snapshot != null ? System.nanoTime() : 0L;
            List<Vector3d> playerPositions = new ArrayList<>();
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> collector =
                (chunk, commandBuffer) -> collectPlayerPositions(chunk, playerPositions);
            store.forEachChunk(systemIndex, collector);
            List<Vector3d> streamingPlayerPositions = List.copyOf(playerPositions);

            if (snapshot != null) {
                snapshot.setPlayerStreamingTargets(streamingPlayerPositions.size());
            }

            long currentTick = stateFor(store).nextTick();
            List<SpaceStreamingPlan> plans = collectStreamingPlans(resource,
                cache,
                streamingPlayerPositions,
                currentTick,
                snapshot);
            if (plans.isEmpty()) {
                return;
            }
            if (!cache.tryBeginStreamingApply()) {
                if (snapshot != null) {
                    snapshot.incrementTerrainApplySkippedPending();
                }
                return;
            }
            Snapshot applySnapshot = snapshot;
            if (applySnapshot != null) {
                applySnapshot.incrementTerrainApplyQueued();
            }
            resource.enqueueOwnerMutation("stream world collision terrain apply", () -> {
                long applyStart = applySnapshot != null ? System.nanoTime() : 0L;
                try {
                    SectionAccessCache sectionAccessCache = cache.newSectionAccessCache();
                    for (SpaceStreamingPlan plan : plans) {
                        PhysicsSpaceBinding space = resource.getSpaceBinding(plan.spaceId());
                        if (space == null) {
                            continue;
                        }
                        PhysicsSpaceSettings settings = resource.getLiveSpaceSettings(plan.spaceId());
                        PhysicsWorldCollisionSettings collisionSettings =
                            settings.getWorldCollisionSettings();
                        if (!WorldCollisionLifecycle.isEnabled()
                            || WorldCollisionLifecycle.generation() != plan.lifecycleGeneration()
                            || collisionSettings.getWorldCollisionMode() != WorldCollisionMode.STREAMING
                            || resource.worldCollisionStreamingRevision(plan.spaceId()) != plan.settingsRevision()) {
                            continue;
                        }
                        applySpaceCollision(world,
                            cache,
                            sectionAccessCache,
                            space,
                            plan,
                            collisionSettings,
                            currentTick,
                            applySnapshot);
                    }
                } finally {
                    if (applySnapshot != null) {
                        applySnapshot.setTickNanos(System.nanoTime() - applyStart);
                        profiling.finishTick(applySnapshot);
                    }
                    cache.finishStreamingApply();
                }
            }).completion().whenComplete((ignored, failure) -> {
                if (failure != null) {
                    cache.finishStreamingApply();
                }
            });
            snapshot = null;
        } finally {
            if (snapshot != null) {
                snapshot.setTickNanos(System.nanoTime() - tickStart);
                profiling.finishTick(snapshot);
            }
        }
    }

    private static void recordSkippedTerrainApply(
        @Nonnull WorldCollisionProfilingResource profiling) {
        if (!profiling.isEnabled()) {
            return;
        }
        Snapshot snapshot = profiling.beginTick();
        snapshot.incrementTerrainApplySkippedPending();
        profiling.finishTick(snapshot);
    }

    @Nonnull
    private List<SpaceStreamingPlan> collectStreamingPlans(
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull List<Vector3d> playerPositions,
        long currentTick,
        @Nullable Snapshot snapshot) {
        List<SpaceStreamingPlan> plans = new ArrayList<>();
        if (!WorldCollisionLifecycle.isEnabled()) {
            return plans;
        }
        long lifecycleGeneration = WorldCollisionLifecycle.generation();
        for (SpaceId spaceId : resource.getSpaceIds()) {
            PhysicsSpaceSettings settings = resource.getLiveSpaceSettings(spaceId);
            if (settings.getWorldCollisionSettings().getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
                continue;
            }

            if (snapshot != null) {
                snapshot.incrementStreamingSpaces();
            }

            int bodyRadius = settings.getWorldCollisionSettings().getWorldCollisionBodyRadius();
            plans.add(new SpaceStreamingPlan(spaceId,
                resource.worldCollisionStreamingRevision(spaceId),
                lifecycleGeneration,
                playerPositions,
                collectDynamicBodyTargets(resource,
                    cache,
                    spaceId,
                    bodyRadius,
                    currentTick,
                    settings.getWorldCollisionSettings().getWorldCollisionTtlTicks(),
                    snapshot)));
        }
        return plans;
    }

    private void applySpaceCollision(@Nonnull World world,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull SectionAccessCache sectionAccessCache,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull SpaceStreamingPlan plan,
        @Nonnull PhysicsWorldCollisionSettings settings,
        long currentTick,
        @Nullable Snapshot snapshot) {
        LongSet visitedSections = new LongOpenHashSet();
        WorldCollisionBuildOptions buildOptions = WorldCollisionBuildOptions.fromSettings(settings);
        for (Vector3d position : plan.playerPositions()) {
            int sectionsBefore = visitedSections.size();
            cache.ensureAround(world,
                space,
                position,
                settings.getWorldCollisionRadius(),
                currentTick,
                snapshot,
                visitedSections,
                snapshot != null ? StreamingTargetDiagnostic.player(position) : null,
                sectionAccessCache,
                buildOptions);
            if (snapshot != null) {
                snapshot.addPlayerSectionTargets(visitedSections.size() - sectionsBefore);
            }
        }
        for (BodyStreamingTarget target : plan.bodyTargets()) {
            int sectionsBefore = visitedSections.size();
            cache.ensureAround(world,
                space,
                target.position(),
                settings.getWorldCollisionBodyRadius(),
                currentTick,
                snapshot,
                visitedSections,
                target.diagnostic(),
                sectionAccessCache,
                buildOptions);
            for (BodyStreamingRefresh refresh : target.refreshes()) {
                cache.recordBodyTargetRefresh(space.spaceId(),
                    refresh.bodyKey(),
                    target.bounds(),
                    refresh.sleeping(),
                    currentTick);
            }
            if (snapshot != null) {
                snapshot.addBodySectionTargets(visitedSections.size() - sectionsBefore);
            }
        }
        cache.pruneUnloaded(world, space.spaceId(), space, snapshot, sectionAccessCache);
        cache.pruneUnused(space.spaceId(),
            space,
            currentTick,
            settings.getWorldCollisionTtlTicks(),
            snapshot);
    }

    private void collectPlayerPositions(@Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull List<Vector3d> positions) {
        for (int index = 0; index < chunk.size(); index++) {
            TransformComponent transform = chunk.getComponent(index, transformType());
            if (transform != null) {
                positions.add(new Vector3d(transform.getPosition()));
            }
        }
    }

    /**
     * Collects unique dynamic-body streaming targets for one physics space.
     *
     * <p>Bodies are deduplicated by the exact section bounds that the configured
     * radius would touch. This removes a large amount of repeated `ensureAround`
     * work for big piles where many bodies share the same neighborhood.</p>
     */
    @Nonnull
    private List<BodyStreamingTarget> collectDynamicBodyTargets(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull SpaceId spaceId,
        int radius,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot snapshot) {
        Map<WorldCollisionStreamingBounds, BodyStreamingTarget> uniqueTargets =
            new Object2ObjectLinkedOpenHashMap<>();
        int[] spatialIndexCandidateCount = {0};
        int[] candidateCount = {0};
        resource.forEachIndexedBodySnapshot(spaceId, (bodyKey, bodySnapshot, bodySpaceId, kind, persistenceMode) -> {
            spatialIndexCandidateCount[0]++;
            if (!bodySnapshot.isDynamic()) {
                return;
            }

            candidateCount[0]++;
            float positionX = bodySnapshot.positionX();
            float positionY = bodySnapshot.positionY();
            float positionZ = bodySnapshot.positionZ();
            WorldCollisionStreamingBounds bounds = WorldCollisionStreamingBounds.from(positionX,
                positionY,
                positionZ,
                radius);
            TargetRefreshDecision refreshDecision = cache.shouldRefreshBodyTarget(spaceId,
                bodyKey,
                bounds,
                bodySnapshot.sleeping(),
                currentTick,
                ttlTicks,
                snapshot);
            if (!refreshDecision.refresh()) {
                return;
            }
            BodyStreamingTarget previous = uniqueTargets.get(bounds);
            if (previous == null) {
                BodyStreamingTarget target = new BodyStreamingTarget(
                    new Vector3d(positionX, positionY, positionZ),
                    bounds,
                    new ArrayList<>(),
                    diagnosticFor(snapshot, bodyKey, positionX, positionY, positionZ));
                target.refreshes().add(new BodyStreamingRefresh(bodyKey, bodySnapshot.sleeping()));
                uniqueTargets.put(bounds, target);
            }
            if (previous != null) {
                previous.refreshes().add(new BodyStreamingRefresh(bodyKey, bodySnapshot.sleeping()));
                if (snapshot != null) {
                    snapshot.incrementBodyTargetDedupeSkips();
                }
            }
        });
        cache.pruneBodyStreamingTargets(spaceId, currentTick, ttlTicks, snapshot);
        if (snapshot != null) {
            snapshot.addBodyStreamingCandidates(candidateCount[0]);
            snapshot.addBodySpatialIndexCandidates(spatialIndexCandidateCount[0]);
            snapshot.addBodyStreamingTargets(uniqueTargets.size());
        }
        return new ArrayList<>(uniqueTargets.values());
    }

    @Nullable
    private static StreamingTargetDiagnostic diagnosticFor(@Nullable Snapshot snapshot,
        @Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ) {
        if (snapshot == null) {
            return null;
        }

        return StreamingTargetDiagnostic.body(bodyKey,
            positionX,
            positionY,
            positionZ,
            positionX,
            positionY,
            positionZ);
    }

    @Nonnull
    private StreamingState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (statesByStore) {
            return statesByStore.computeIfAbsent(store, ignored -> new StreamingState());
        }
    }

    private record BodyStreamingTarget(@Nonnull Vector3d position,
                                       @Nonnull WorldCollisionStreamingBounds bounds,
                                       @Nonnull List<BodyStreamingRefresh> refreshes,
                                       @Nullable StreamingTargetDiagnostic diagnostic) {
    }

    private record BodyStreamingRefresh(@Nonnull RigidBodyKey bodyKey,
                                        boolean sleeping) {
    }

    private record SpaceStreamingPlan(@Nonnull SpaceId spaceId,
                                      long settingsRevision,
                                      long lifecycleGeneration,
                                      @Nonnull List<Vector3d> playerPositions,
                                      @Nonnull List<BodyStreamingTarget> bodyTargets) {
    }

    private static final class StreamingState {

        private long tick;

        private synchronized long nextTick() {
            return ++tick;
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query();
    }

    @Nonnull
    private static Query<EntityStore> query() {
        Query<EntityStore> resolved = query;
        if (resolved != null) {
            return resolved;
        }
        synchronized (PhysicsWorldCollisionStreamingSystem.class) {
            resolved = query;
            if (resolved == null) {
                resolved = Query.and(playerType(), transformType());
                query = resolved;
            }
        }
        return resolved;
    }

    @Nonnull
    private static ComponentType<EntityStore, Player> playerType() {
        ComponentType<EntityStore, Player> resolved = playerType;
        if (resolved != null) {
            return resolved;
        }
        synchronized (PhysicsWorldCollisionStreamingSystem.class) {
            resolved = playerType;
            if (resolved == null) {
                resolved = Player.getComponentType();
                playerType = resolved;
            }
        }
        return resolved;
    }

    @Nonnull
    private static ComponentType<EntityStore, TransformComponent> transformType() {
        ComponentType<EntityStore, TransformComponent> resolved = transformType;
        if (resolved != null) {
            return resolved;
        }
        synchronized (PhysicsWorldCollisionStreamingSystem.class) {
            resolved = transformType;
            if (resolved == null) {
                resolved = TransformComponent.getComponentType();
                transformType = resolved;
            }
        }
        return resolved;
    }

}
