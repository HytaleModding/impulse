package dev.hytalemodding.impulse.core.internal.systems;

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
import dev.hytalemodding.impulse.core.internal.resources.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.plugin.voxel.WorldCollisionMode;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final int SLEEPING_BODY_STREAMING_INTERVAL_TICKS = 20;

    private long tick;

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        WorldCollisionProfilingResource profiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        Snapshot snapshot = profiling.isEnabled() ? profiling.beginTick() : null;
        long tickStart = snapshot != null ? System.nanoTime() : 0L;
        try {
            List<Vector3d> playerPositions = new ArrayList<>();
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> collector =
                (chunk, commandBuffer) -> collectPlayerPositions(chunk, playerPositions);
            store.forEachChunk(systemIndex, collector);

            World world = store.getExternalData().getWorld();
            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());

            if (snapshot != null) {
                snapshot.setPlayerStreamingTargets(playerPositions.size());
            }

            long currentTick = ++tick;
            WorldVoxelCollisionCache cache = resource.getWorldVoxelCollisionCache();
            for (PhysicsSpace space : resource.iterateSpaces()) {
                PhysicsSpaceSettings settings = resource.getSpaceSettings(space.getId());
                if (settings.getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
                    continue;
                }

                if (snapshot != null) {
                    snapshot.incrementStreamingSpaces();
                }

                int playerRadius = settings.getWorldCollisionRadius();
                int bodyRadius = settings.getWorldCollisionBodyRadius();
                List<BodyStreamingTarget> bodyTargets = collectDynamicBodyTargets(resource,
                    space.getId(),
                    bodyRadius,
                    currentTick,
                    settings.getWorldCollisionTtlTicks(),
                    snapshot);
                LongSet visitedSections = new LongOpenHashSet();
                for (Vector3d position : playerPositions) {
                    int sectionsBefore = visitedSections.size();
                    cache.ensureAround(world,
                        space,
                        position,
                        playerRadius,
                        currentTick,
                        snapshot,
                        visitedSections);
                    if (snapshot != null) {
                        snapshot.addPlayerSectionTargets(visitedSections.size() - sectionsBefore);
                    }
                }
                for (BodyStreamingTarget target : bodyTargets) {
                    int sectionsBefore = visitedSections.size();
                    if (target.buildSections()) {
                        cache.ensureAround(world,
                            space,
                            target.position(),
                            bodyRadius,
                            currentTick,
                            snapshot,
                            visitedSections);
                    } else {
                        cache.touchAround(space.getId(), target.position(), bodyRadius, currentTick);
                    }
                    if (snapshot != null) {
                        snapshot.addBodySectionTargets(visitedSections.size() - sectionsBefore);
                    }
                }
                cache.pruneUnloaded(world, space.getId(), space, snapshot);
                cache.pruneUnused(space.getId(),
                    space,
                    currentTick,
                    settings.getWorldCollisionTtlTicks(),
                    snapshot);
            }
        } finally {
            if (snapshot != null) {
                snapshot.setTickNanos(System.nanoTime() - tickStart);
                profiling.finishTick(snapshot);
            }
        }
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
            BodyStreamingTarget target = new BodyStreamingTarget(
                new Vector3d(position.x, position.y, position.z),
                !bodySnapshot.sleeping() || shouldRefreshSleepingBodyTarget(currentTick, ttlTicks));
            BodyStreamingTarget previous = uniqueTargets.putIfAbsent(bounds, target);
            if (previous != null && !previous.buildSections() && target.buildSections()) {
                uniqueTargets.put(bounds, target);
            }
            if (previous != null && snapshot != null) {
                snapshot.incrementBodyTargetDedupeSkips();
            }
        });
        if (snapshot != null) {
            snapshot.addBodyStreamingCandidates(candidateCount[0]);
            snapshot.addBodySpatialIndexCandidates(spatialIndexCandidateCount[0]);
            snapshot.addBodyStreamingTargets(uniqueTargets.size());
        }
        return new ArrayList<>(uniqueTargets.values());
    }

    private static boolean shouldRefreshSleepingBodyTarget(long currentTick, int ttlTicks) {
        int interval = Math.clamp(ttlTicks / 2, 1, SLEEPING_BODY_STREAMING_INTERVAL_TICKS);
        return currentTick == 1L || currentTick % interval == 0L;
    }

    private record BodyStreamingTarget(@Nonnull Vector3d position, boolean buildSections) {
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

}
