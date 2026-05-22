package dev.hytalemodding.impulse.core.internal.systems.collision;

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
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource.StreamingTargetDiagnostic;
import dev.hytalemodding.impulse.core.plugin.voxel.WorldCollisionMode;
import dev.hytalemodding.impulse.core.internal.voxel.WorldCollisionStreamingBounds;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache.SectionAccessCache;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache.TargetRefreshDecision;
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
import org.joml.Vector3f;

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

    private static final ComponentType<EntityStore, Player> PLAYER_TYPE = Player.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(PLAYER_TYPE, TRANSFORM_TYPE);

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
        WorldCollisionProfilingResource profiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        Snapshot snapshot = null;
        long tickStart = 0L;
        try {
            World world = store.getExternalData().getWorld();
            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            WorldVoxelCollisionCache cache = resource.internalWorldCollisionState();
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
            resource.enqueuePhysicsMutation("stream world collision terrain apply", () -> {
                long applyStart = applySnapshot != null ? System.nanoTime() : 0L;
                try {
                    SectionAccessCache sectionAccessCache = cache.newSectionAccessCache();
                    for (SpaceStreamingPlan plan : plans) {
                        PhysicsSpace space = resource.getSpace(plan.spaceId());
                        if (space == null) {
                            continue;
                        }
                        PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
                        if (settings.getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
                            continue;
                        }
                        applySpaceCollision(world,
                            cache,
                            sectionAccessCache,
                            space,
                            plan,
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
        @Nonnull PhysicsWorldResource resource,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull List<Vector3d> playerPositions,
        long currentTick,
        @Nullable Snapshot snapshot) {
        List<SpaceStreamingPlan> plans = new ArrayList<>();
        for (PhysicsSpace space : resource.getSpaces()) {
            PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
            if (settings.getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
                continue;
            }

            if (snapshot != null) {
                snapshot.incrementStreamingSpaces();
            }

            int bodyRadius = settings.getWorldCollisionBodyRadius();
            plans.add(new SpaceStreamingPlan(space.getId(),
                settings.getWorldCollisionRadius(),
                bodyRadius,
                settings.getWorldCollisionTtlTicks(),
                playerPositions,
                collectDynamicBodyTargets(resource,
                    cache,
                    space.getId(),
                    bodyRadius,
                    currentTick,
                    settings.getWorldCollisionTtlTicks(),
                    snapshot)));
        }
        return plans;
    }

    private void applySpaceCollision(@Nonnull World world,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull SectionAccessCache sectionAccessCache,
        @Nonnull PhysicsSpace space,
        @Nonnull SpaceStreamingPlan plan,
        long currentTick,
        @Nullable Snapshot snapshot) {
        LongSet visitedSections = new LongOpenHashSet();
        for (Vector3d position : plan.playerPositions()) {
            int sectionsBefore = visitedSections.size();
            cache.ensureAround(world,
                space,
                position,
                plan.playerRadius(),
                currentTick,
                snapshot,
                visitedSections,
                snapshot != null ? StreamingTargetDiagnostic.player(position) : null,
                sectionAccessCache);
            if (snapshot != null) {
                snapshot.addPlayerSectionTargets(visitedSections.size() - sectionsBefore);
            }
        }
        for (BodyStreamingTarget target : plan.bodyTargets()) {
            int sectionsBefore = visitedSections.size();
            cache.ensureAround(world,
                space,
                target.position(),
                plan.bodyRadius(),
                currentTick,
                snapshot,
                visitedSections,
                target.diagnostic(),
                sectionAccessCache);
            if (snapshot != null) {
                snapshot.addBodySectionTargets(visitedSections.size() - sectionsBefore);
            }
        }
        cache.pruneUnloaded(world, space.getId(), space, snapshot, sectionAccessCache);
        cache.pruneUnused(space.getId(),
            space,
            currentTick,
            plan.ttlTicks(),
            snapshot);
    }

    private void collectPlayerPositions(@Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull List<Vector3d> positions) {
        for (int index = 0; index < chunk.size(); index++) {
            TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
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
    private List<BodyStreamingTarget> collectDynamicBodyTargets(@Nonnull PhysicsWorldResource resource,
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
        resource.forEachBodySnapshot(spaceId, entry -> {
            spatialIndexCandidateCount[0]++;
            var bodySnapshot = entry.snapshot();
            if (!bodySnapshot.isDynamic()) {
                return;
            }

            candidateCount[0]++;
            Vector3f position = bodySnapshot.position();
            WorldCollisionStreamingBounds bounds = WorldCollisionStreamingBounds.from(position,
                radius);
            TargetRefreshDecision refreshDecision = cache.shouldRefreshBodyTarget(spaceId,
                entry.bodyId(),
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
                uniqueTargets.put(bounds, new BodyStreamingTarget(
                    new Vector3d(position.x, position.y, position.z),
                    diagnosticFor(snapshot, entry, position)));
            }
            if (previous != null && snapshot != null) {
                snapshot.incrementBodyTargetDedupeSkips();
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
        @Nonnull PhysicsWorldResource.BodySnapshotEntry entry,
        @Nonnull Vector3f snapshotPosition) {
        if (snapshot == null) {
            return null;
        }

        return StreamingTargetDiagnostic.body(entry.bodyId(), snapshotPosition, snapshotPosition);
    }

    @Nonnull
    private StreamingState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (statesByStore) {
            return statesByStore.computeIfAbsent(store, ignored -> new StreamingState());
        }
    }

    private record BodyStreamingTarget(@Nonnull Vector3d position,
                                       @Nullable StreamingTargetDiagnostic diagnostic) {
    }

    private record SpaceStreamingPlan(@Nonnull SpaceId spaceId,
                                      int playerRadius,
                                      int bodyRadius,
                                      int ttlTicks,
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
        return QUERY;
    }

}
