package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsStoreTerrainMutationCache.TargetRefreshDecision;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldVoxelCollisionCache.BuildStats;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.StreamingTargetDiagnostic;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionStats;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Shared EntityStore-side producer state for copied PhysicsStore terrain mutations.
 */
public final class PhysicsStoreWorldCollisionStreamingResource implements Resource<EntityStore> {

    @Nonnull
    private final PhysicsStoreTerrainMutationCache cache = new PhysicsStoreTerrainMutationCache();
    private long tick;

    @Nullable
    private static ResourceType<EntityStore, PhysicsStoreWorldCollisionStreamingResource> resourceType;

    public PhysicsStoreWorldCollisionStreamingResource() {
    }

    public static void setResourceType(
        @Nonnull ResourceType<EntityStore, PhysicsStoreWorldCollisionStreamingResource> type) {
        resourceType = Objects.requireNonNull(type, "type");
    }

    public static void clearResourceType() {
        resourceType = null;
    }

    @Nonnull
    public static ResourceType<EntityStore, PhysicsStoreWorldCollisionStreamingResource> getResourceType() {
        if (resourceType == null) {
            throw new IllegalStateException("PhysicsStore world-collision streaming resource is not registered");
        }
        return resourceType;
    }

    public synchronized long nextTick() {
        return ++tick;
    }

    public synchronized void retainSpaces(@Nonnull Set<UUID> retainedSpaces,
        @Nonnull PhysicsTerrainMutationQueueResource queue) {
        cache.retainSpaces(retainedSpaces, queue);
    }

    @Nonnull
    public synchronized WorldCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        LongSet visitedSections = new LongOpenHashSet();
        BuildStats total = BuildStats.empty();
        for (Vector3d center : centers) {
            total = total.plus(ensureAround(world,
                spaceUuid,
                queue,
                center,
                radius,
                tick,
                profiling,
                visitedSections,
                null,
                buildOptions));
        }
        return new WorldCollisionPrewarmStats(visitedSections.size(), worldCollisionStats(total));
    }

    @Nonnull
    public synchronized WorldCollisionBuildStats refreshAround(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        int removed = cache.clearSectionsAround(spaceUuid, queue, center, radius);
        BuildStats stats = ensureAround(world,
            spaceUuid,
            queue,
            center,
            radius,
            tick,
            profiling,
            null,
            null,
            buildOptions);
        return worldCollisionStats(withRemovedBodies(stats, stats.removedBodies() + removed));
    }

    @Nonnull
    public synchronized BuildStats ensureAround(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nullable LongSet visitedSections,
        @Nullable StreamingTargetDiagnostic targetDiagnostic,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        return cache.ensureAround(world,
            spaceUuid,
            queue,
            center,
            radius,
            tick,
            profiling,
            visitedSections,
            targetDiagnostic,
            buildOptions);
    }

    @Nonnull
    public synchronized TargetRefreshDecision shouldRefreshBodyTarget(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull WorldCollisionStreamingBounds bounds,
        boolean sleeping,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        return cache.shouldRefreshBodyTarget(spaceUuid,
            bodyUuid,
            bounds,
            sleeping,
            currentTick,
            ttlTicks,
            profiling);
    }

    public synchronized void recordBodyTargetRefresh(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull WorldCollisionStreamingBounds bounds,
        boolean sleeping,
        long currentTick) {
        cache.recordBodyTargetRefresh(spaceUuid, bodyUuid, bounds, sleeping, currentTick);
    }

    public synchronized int pruneBodyStreamingTargets(@Nonnull UUID spaceUuid,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        return cache.pruneBodyStreamingTargets(spaceUuid, currentTick, ttlTicks, profiling);
    }

    public synchronized int pruneUnloaded(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        @Nullable Snapshot profiling) {
        return cache.pruneUnloaded(world, spaceUuid, queue, profiling);
    }

    public synchronized int pruneUnused(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        return cache.pruneUnused(spaceUuid, queue, currentTick, ttlTicks, profiling);
    }

    public synchronized int clearSpace(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue) {
        return cache.clearSpace(spaceUuid, queue);
    }

    @Nonnull
    public synchronized WorldCollisionStats stats() {
        return new WorldCollisionStats(cache.spaceCount(),
            cache.sectionCount(),
            cache.bodyCount(),
            cache.shapeTemplateCount());
    }

    @Nonnull
    @Override
    public synchronized PhysicsStoreWorldCollisionStreamingResource clone() {
        PhysicsStoreWorldCollisionStreamingResource copy =
            new PhysicsStoreWorldCollisionStreamingResource();
        copy.tick = tick;
        return copy;
    }

    @Nonnull
    private static WorldCollisionBuildStats worldCollisionStats(@Nonnull BuildStats stats) {
        return new WorldCollisionBuildStats(stats.scannedBlocks(),
            stats.solidBlocks(),
            stats.culledInteriorBlocks(),
            stats.fullCubeRuns(),
            stats.detailBoxes(),
            stats.colliderBodies(),
            stats.removedBodies(),
            stats.sectionsBuilt(),
            stats.sectionsRebuilt(),
            stats.voxelBodies());
    }

    @Nonnull
    private static BuildStats withRemovedBodies(@Nonnull BuildStats stats, int removedBodies) {
        return new BuildStats(stats.scannedBlocks(),
            stats.solidBlocks(),
            stats.culledInteriorBlocks(),
            stats.fullCubeRuns(),
            stats.detailBoxes(),
            stats.colliderBodies(),
            removedBodies,
            stats.sectionsBuilt(),
            stats.sectionsRebuilt(),
            stats.voxelBodies());
    }
}
