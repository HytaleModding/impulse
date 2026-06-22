package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkMutationCache.TargetRefreshDecision;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource.StreamingTargetDiagnostic;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionStats;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Shared EntityStore-side producer state for copied PhysicsStore chunk collision mutations.
 */
public final class PhysicsChunkCollisionStreamingResource implements Resource<EntityStore> {

    @Nonnull
    private final PhysicsChunkMutationCache cache;
    private long tick;

    @Nullable
    private static ResourceType<EntityStore, PhysicsChunkCollisionStreamingResource> resourceType;

    public PhysicsChunkCollisionStreamingResource() {
        this(new PhysicsChunkMutationCache(), 0L);
    }

    private PhysicsChunkCollisionStreamingResource(@Nonnull PhysicsChunkMutationCache cache,
        long tick) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.tick = tick;
    }

    public static void setResourceType(
        @Nonnull ResourceType<EntityStore, PhysicsChunkCollisionStreamingResource> type) {
        resourceType = Objects.requireNonNull(type, "type");
    }

    public static void clearResourceType() {
        resourceType = null;
    }

    @Nonnull
    public static ResourceType<EntityStore, PhysicsChunkCollisionStreamingResource> getResourceType() {
        if (resourceType == null) {
            throw new IllegalStateException("PhysicsStore PhysicsChunk collision streaming resource is not registered");
        }
        return resourceType;
    }

    public synchronized long nextTick() {
        return ++tick;
    }

    public synchronized void retainSpaces(@Nonnull Set<UUID> retainedSpaces,
        @Nonnull PhysicsChunkCollisionMutationQueueResource queue) {
        cache.retainSpaces(retainedSpaces, queue);
    }

    @Nonnull
    public synchronized PhysicsChunkCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsChunkCollisionMutationQueueResource queue,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nonnull PhysicsChunkBuildOptions buildOptions) {
        LongSet visitedSections = new LongOpenHashSet();
        PhysicsChunkBuildStats total = PhysicsChunkBuildStats.empty();
        PhysicsChunkSectionAccessCache accessCache = new PhysicsChunkSectionAccessCache();
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
                accessCache,
                buildOptions));
        }
        return new PhysicsChunkCollisionPrewarmStats(visitedSections.size(), collisionStats(total));
    }

    @Nonnull
    public synchronized PhysicsChunkCollisionBuildStats refreshAround(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsChunkCollisionMutationQueueResource queue,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nonnull PhysicsChunkBuildOptions buildOptions) {
        int removed = cache.clearSectionsAround(spaceUuid, queue, center, radius);
        PhysicsChunkSectionAccessCache accessCache = new PhysicsChunkSectionAccessCache();
        PhysicsChunkBuildStats stats = ensureAround(world,
            spaceUuid,
            queue,
            center,
            radius,
            tick,
            profiling,
            null,
            null,
            accessCache,
            buildOptions);
        return collisionStats(withRemovedBodies(stats, stats.removedBodies() + removed));
    }

    @Nonnull
    public synchronized PhysicsChunkBuildStats ensureAround(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsChunkCollisionMutationQueueResource queue,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nullable LongSet visitedSections,
        @Nullable StreamingTargetDiagnostic targetDiagnostic,
        @Nullable PhysicsChunkSectionAccessCache accessCache,
        @Nonnull PhysicsChunkBuildOptions buildOptions) {
        return cache.ensureAround(world,
            spaceUuid,
            queue,
            center,
            radius,
            tick,
            profiling,
            visitedSections,
            targetDiagnostic,
            accessCache,
            buildOptions);
    }

    @Nonnull
    public synchronized TargetRefreshDecision shouldRefreshBodyTarget(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull PhysicsChunkStreamingBounds bounds,
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

    @Nonnull
    public synchronized TargetRefreshDecision shouldRefreshBodyTarget(@Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsChunkStreamingBounds bounds,
        boolean sleeping,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        return cache.shouldRefreshBodyTarget(spaceUuid,
            bodyRef,
            bounds,
            sleeping,
            currentTick,
            ttlTicks,
            profiling);
    }

    public synchronized void recordBodyTargetRefresh(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull PhysicsChunkStreamingBounds bounds,
        boolean sleeping,
        long currentTick) {
        cache.recordBodyTargetRefresh(spaceUuid, bodyUuid, bounds, sleeping, currentTick);
    }

    public synchronized void recordBodyTargetRefresh(@Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsChunkStreamingBounds bounds,
        boolean sleeping,
        long currentTick) {
        cache.recordBodyTargetRefresh(spaceUuid, bodyRef, bounds, sleeping, currentTick);
    }

    public synchronized int pruneBodyStreamingTargets(@Nonnull UUID spaceUuid,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        return cache.pruneBodyStreamingTargets(spaceUuid, currentTick, ttlTicks, profiling);
    }

    public synchronized int pruneUnloaded(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsChunkCollisionMutationQueueResource queue,
        @Nullable Snapshot profiling) {
        return pruneUnloaded(world,
            spaceUuid,
            queue,
            profiling,
            new PhysicsChunkSectionAccessCache());
    }

    public synchronized int pruneUnloaded(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsChunkCollisionMutationQueueResource queue,
        @Nullable Snapshot profiling,
        @Nullable PhysicsChunkSectionAccessCache accessCache) {
        return cache.pruneUnloaded(world, spaceUuid, queue, profiling, accessCache);
    }

    public synchronized int pruneUnused(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsChunkCollisionMutationQueueResource queue,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        return cache.pruneUnused(spaceUuid, queue, currentTick, ttlTicks, profiling);
    }

    public synchronized int clearSpace(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsChunkCollisionMutationQueueResource queue) {
        return cache.clearSpace(spaceUuid, queue);
    }

    @Nonnull
    public synchronized PhysicsChunkCollisionStats stats() {
        return new PhysicsChunkCollisionStats(cache.spaceCount(),
            cache.sectionCount(),
            cache.bodyCount(),
            cache.shapeTemplateCount());
    }

    public synchronized int bodyCount(@Nonnull UUID spaceUuid) {
        return cache.bodyCount(spaceUuid);
    }

    @Nonnull
    @Override
    public synchronized PhysicsChunkCollisionStreamingResource clone() {
        return new PhysicsChunkCollisionStreamingResource(cache.copy(), tick);
    }

    @Nonnull
    private static PhysicsChunkCollisionBuildStats collisionStats(@Nonnull PhysicsChunkBuildStats stats) {
        return new PhysicsChunkCollisionBuildStats(stats.scannedBlocks(),
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
    private static PhysicsChunkBuildStats withRemovedBodies(@Nonnull PhysicsChunkBuildStats stats, int removedBodies) {
        return new PhysicsChunkBuildStats(stats.scannedBlocks(),
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
