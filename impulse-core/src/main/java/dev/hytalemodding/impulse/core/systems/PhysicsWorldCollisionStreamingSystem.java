package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.voxel.WorldCollisionMode;
import dev.hytalemodding.impulse.core.voxel.WorldVoxelCollisionCache;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /**
     * Radius (in blocks) used for streaming around dynamic physics bodies.
     * Smaller than the player radius because bodies should not pull collision
     * as far as players do, but they still need terrain to land on.
     */
    public static final int DEFAULT_BODY_STREAMING_RADIUS = 4;

    private static final ComponentType<EntityStore, Player> PLAYER_TYPE = Player.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(PLAYER_TYPE, TRANSFORM_TYPE);

    private long tick;
    private final Vector3f bodyPositionScratch = new Vector3f();

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

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
                List<Vector3d> bodyTargets = collectDynamicBodyTargets(space, bodyRadius, snapshot);
                LongSet visitedSections = new LongOpenHashSet();
                for (Vector3d position : playerPositions) {
                    cache.ensureAround(world,
                        space,
                        position,
                        playerRadius,
                        currentTick,
                        snapshot,
                        visitedSections);
                }
                for (Vector3d position : bodyTargets) {
                    cache.ensureAround(world,
                        space,
                        position,
                        bodyRadius,
                        currentTick,
                        snapshot,
                        visitedSections);
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
    private List<Vector3d> collectDynamicBodyTargets(@Nonnull PhysicsSpace space,
        int radius,
        @Nullable Snapshot snapshot) {
        Map<StreamingBounds, Vector3d> uniqueTargets = new LinkedHashMap<>();
        int[] candidateCount = {0};
        space.forEachBody(body -> {
            if (!body.isDynamic() || body.isSleeping()) {
                return;
            }

            candidateCount[0]++;
            body.getPosition(bodyPositionScratch);
            StreamingBounds bounds = StreamingBounds.from(bodyPositionScratch, radius);
            Vector3d previous = uniqueTargets.putIfAbsent(bounds,
                new Vector3d(bodyPositionScratch.x, bodyPositionScratch.y, bodyPositionScratch.z));
            if (previous != null && snapshot != null) {
                snapshot.incrementBodyTargetDedupeSkips();
            }
        });
        if (snapshot != null) {
            snapshot.addBodyStreamingCandidates(candidateCount[0]);
            snapshot.addBodyStreamingTargets(uniqueTargets.size());
        }
        return new ArrayList<>(uniqueTargets.values());
    }

    private record StreamingBounds(int minChunkX,
                                   int maxChunkX,
                                   int minSectionY,
                                   int maxSectionY,
                                   int minChunkZ,
                                   int maxChunkZ) {

        @Nonnull
        private static StreamingBounds from(@Nonnull Vector3f center, int radius) {
            int minX = (int) Math.floor(center.x) - radius;
            int maxX = (int) Math.floor(center.x) + radius;
            int minY = Math.max(0, (int) Math.floor(center.y) - radius);
            int maxY = Math.min(ChunkUtil.HEIGHT_MINUS_1, (int) Math.floor(center.y) + radius);
            int minZ = (int) Math.floor(center.z) - radius;
            int maxZ = (int) Math.floor(center.z) + radius;
            return new StreamingBounds(
                ChunkUtil.chunkCoordinate(minX),
                ChunkUtil.chunkCoordinate(maxX),
                ChunkUtil.indexSection(minY),
                ChunkUtil.indexSection(maxY),
                ChunkUtil.chunkCoordinate(minZ),
                ChunkUtil.chunkCoordinate(maxZ));
        }
    }
}
