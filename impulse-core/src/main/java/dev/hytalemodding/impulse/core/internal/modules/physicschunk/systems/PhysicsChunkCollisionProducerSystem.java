package dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkMutationCache.TargetRefreshDecision;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkSectionAccessCache;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStreamingBounds;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource.StreamingTargetDiagnostic;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource.BodyCursor;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkSettingsIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkSettingsIndexResource.PhysicsChunkSpaceSettings;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Produces copied PhysicsStore chunk collision mutations from EntityStore and ChunkStore state.
 */
public final class PhysicsChunkCollisionProducerSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    @Nullable
    private static volatile ComponentType<EntityStore, Player> playerType;
    @Nullable
    private static volatile ComponentType<EntityStore, TransformComponent> transformType;
    @Nullable
    private static volatile Query<EntityStore> query;

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PhysicsSyncSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (!PhysicsChunkLifecycle.isEnabled()) {
            return;
        }

        PhysicsChunkProfilingResource profiling = store.getResource(
            PhysicsChunkProfilingResource.getResourceType());
        Snapshot snapshot = profiling.isEnabled() ? profiling.beginTick() : null;
        long tickStart = snapshot != null ? System.nanoTime() : 0L;
        try {
            World world = store.getExternalData().getWorld();
            Store<PhysicsStore> physics = PhysicsThreading.store(world);
            PhysicsThreading.requireWorldThread(physics,
                "produce PhysicsStore PhysicsChunk chunk collision mutations");
            PhysicsChunkCollisionMutationQueueResource queue = physics.getResource(
                PhysicsChunkCollisionMutationQueueResource.getResourceType());
            PhysicsChunkSettingsIndexResource chunkCollisionSettingsIndex = physics.getResource(
                PhysicsChunkSettingsIndexResource.getResourceType());
            queue.updateStamp(PhysicsChunkLifecycle.generation(),
                chunkCollisionSettingsIndex.generation());
            PhysicsSnapshotResource snapshotResource = physics.getResource(
                PhysicsSnapshotResource.getResourceType());
            PhysicsChunkCollisionStreamingResource streaming = store.getResource(
                PhysicsChunkCollisionStreamingResource.getResourceType());

            List<PhysicsChunkSpaceSettings> spaces =
                chunkCollisionSettingsIndex.streamingSpaces();
            if (spaces.isEmpty()) {
                streaming.retainSpaces(Set.of(), queue);
                return;
            }

            List<Vector3d> playerPositions = collectPlayerPositions(store, systemIndex);
            if (snapshot != null) {
                snapshot.setPlayerStreamingTargets(playerPositions.size());
            }
            long currentTick = streaming.nextTick();
            Set<UUID> retainedSpaces = new ObjectOpenHashSet<>();
            for (PhysicsChunkSpaceSettings settings : spaces) {
                retainedSpaces.add(settings.spaceUuid());
            }
            streaming.retainSpaces(retainedSpaces, queue);

            Map<UUID, List<BodyStreamingTarget>> bodyTargetsBySpace =
                collectDynamicBodyTargetsBySpace(streaming,
                    spaces,
                    snapshotResource,
                    currentTick,
                    snapshot);
            for (PhysicsChunkSpaceSettings settings : spaces) {
                if (snapshot != null) {
                    snapshot.incrementStreamingSpaces();
                }
                processSpace(world,
                    streaming,
                    queue,
                    settings,
                    playerPositions,
                    bodyTargetsBySpace.getOrDefault(settings.spaceUuid(), List.of()),
                    currentTick,
                    snapshot);
            }
        } finally {
            if (snapshot != null) {
                snapshot.setTickNanos(System.nanoTime() - tickStart);
                profiling.finishTick(snapshot);
            }
        }
    }

    private static void processSpace(@Nonnull World world,
        @Nonnull PhysicsChunkCollisionStreamingResource streaming,
        @Nonnull PhysicsChunkCollisionMutationQueueResource queue,
        @Nonnull PhysicsChunkSpaceSettings settings,
        @Nonnull List<Vector3d> playerPositions,
        @Nonnull List<BodyStreamingTarget> bodyTargets,
        long currentTick,
        @Nullable Snapshot snapshot) {
        LongSet visitedSections = new LongOpenHashSet();
        PhysicsChunkSectionAccessCache accessCache = new PhysicsChunkSectionAccessCache();
        for (Vector3d position : playerPositions) {
            int sectionsBefore = visitedSections.size();
            streaming.ensureAround(world,
                settings.spaceUuid(),
                queue,
                position,
                settings.radius(),
                currentTick,
                snapshot,
                visitedSections,
                snapshot != null ? StreamingTargetDiagnostic.player(position) : null,
                accessCache,
                settings.buildOptions());
            if (snapshot != null) {
                snapshot.addPlayerSectionTargets(visitedSections.size() - sectionsBefore);
            }
        }

        for (BodyStreamingTarget target : bodyTargets) {
            int sectionsBefore = visitedSections.size();
            streaming.ensureAround(world,
                settings.spaceUuid(),
                queue,
                target.position(),
                settings.bodyRadius(),
                currentTick,
                snapshot,
                visitedSections,
                null,
                accessCache,
                settings.buildOptions());
            for (BodyStreamingRefresh refresh : target.refreshes()) {
                streaming.recordBodyTargetRefresh(settings.spaceUuid(),
                    refresh.bodyRef(),
                    target.bounds(),
                    refresh.sleeping(),
                    currentTick);
            }
            if (snapshot != null) {
                snapshot.addBodySectionTargets(visitedSections.size() - sectionsBefore);
            }
        }

        streaming.pruneUnloaded(world, settings.spaceUuid(), queue, snapshot, accessCache);
        streaming.pruneUnused(settings.spaceUuid(), queue, currentTick, settings.ttlTicks(), snapshot);
        streaming.pruneBodyStreamingTargets(settings.spaceUuid(),
            currentTick,
            settings.ttlTicks(),
            snapshot);
    }

    @Nonnull
    private static Map<UUID, List<BodyStreamingTarget>> collectDynamicBodyTargetsBySpace(
        @Nonnull PhysicsChunkCollisionStreamingResource streaming,
        @Nonnull List<PhysicsChunkSpaceSettings> spaces,
        @Nonnull PhysicsSnapshotResource snapshotResource,
        long currentTick,
        @Nullable Snapshot snapshot) {
        Map<UUID, PhysicsChunkSpaceSettings> settingsBySpace = new Object2ObjectOpenHashMap<>();
        Map<UUID, BodyTargetAccumulator> accumulators = new Object2ObjectOpenHashMap<>();
        for (PhysicsChunkSpaceSettings settings : spaces) {
            settingsBySpace.put(settings.spaceUuid(), settings);
            accumulators.put(settings.spaceUuid(), new BodyTargetAccumulator());
        }
        snapshotResource.forEachBodyCursor(body -> collectDynamicBodyTarget(streaming,
            settingsBySpace,
            accumulators,
            body,
            currentTick,
            snapshot));

        Map<UUID, List<BodyStreamingTarget>> targetsBySpace = new Object2ObjectOpenHashMap<>();
        if (snapshot != null) {
            for (BodyTargetAccumulator accumulator : accumulators.values()) {
                snapshot.addBodySpatialIndexCandidates(accumulator.spatialCandidates);
                snapshot.addBodyStreamingCandidates(accumulator.dynamicCandidates);
                snapshot.addBodyStreamingTargets(accumulator.targets.size());
            }
        }
        for (Map.Entry<UUID, BodyTargetAccumulator> entry : accumulators.entrySet()) {
            targetsBySpace.put(entry.getKey(), new ArrayList<>(entry.getValue().targets.values()));
        }
        return targetsBySpace;
    }

    private static void collectDynamicBodyTarget(
        @Nonnull PhysicsChunkCollisionStreamingResource streaming,
        @Nonnull Map<UUID, PhysicsChunkSpaceSettings> settingsBySpace,
        @Nonnull Map<UUID, BodyTargetAccumulator> accumulators,
        @Nonnull BodyCursor body,
        long currentTick,
        @Nullable Snapshot snapshot) {
        PhysicsChunkSpaceSettings settings = settingsBySpace.get(body.spaceUuid());
        if (settings == null) {
            return;
        }
        BodyTargetAccumulator accumulator = accumulators.get(settings.spaceUuid());
        accumulator.spatialCandidates++;
        if (body.bodyType() != PhysicsBodyType.DYNAMIC) {
            return;
        }
        Ref<PhysicsStore> bodyRef = body.bodyRef();
        if (bodyRef == null || !bodyRef.isValid()) {
            return;
        }
        accumulator.dynamicCandidates++;
        PhysicsChunkStreamingBounds bounds = PhysicsChunkStreamingBounds.from(body.positionX(),
            body.positionY(),
            body.positionZ(),
            settings.bodyRadius());
        TargetRefreshDecision decision = streaming.shouldRefreshBodyTarget(settings.spaceUuid(),
            bodyRef,
            bounds,
            body.sleeping(),
            currentTick,
            settings.ttlTicks(),
            snapshot);
        if (!decision.refresh()) {
            return;
        }

        BodyStreamingTarget target = accumulator.targets.get(bounds);
        if (target == null) {
            target = new BodyStreamingTarget(new Vector3d(body.positionX(),
                body.positionY(),
                body.positionZ()),
                bounds,
                new ArrayList<>());
            accumulator.targets.put(bounds, target);
        } else if (snapshot != null) {
            snapshot.incrementBodyTargetDedupeSkips();
        }
        target.refreshes().add(new BodyStreamingRefresh(bodyRef, body.sleeping()));
    }

    @Nonnull
    private static List<Vector3d> collectPlayerPositions(@Nonnull Store<EntityStore> store,
        int systemIndex) {
        List<Vector3d> playerPositions = new ArrayList<>();
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> collector =
            (chunk, _) -> collectPlayerPositions(chunk, playerPositions);
        store.forEachChunk(systemIndex, collector);
        return List.copyOf(playerPositions);
    }

    private static void collectPlayerPositions(@Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull List<Vector3d> positions) {
        for (int index = 0; index < chunk.size(); index++) {
            TransformComponent transform = chunk.getComponent(index, transformType());
            if (transform != null) {
                positions.add(new Vector3d(transform.getPosition()));
            }
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Nonnull
    private static Query<EntityStore> query() {
        Query<EntityStore> resolved = query;
        if (resolved != null) {
            return resolved;
        }
        synchronized (PhysicsChunkCollisionProducerSystem.class) {
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
        synchronized (PhysicsChunkCollisionProducerSystem.class) {
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
        synchronized (PhysicsChunkCollisionProducerSystem.class) {
            resolved = transformType;
            if (resolved == null) {
                resolved = TransformComponent.getComponentType();
                transformType = resolved;
            }
        }
        return resolved;
    }

    private record BodyStreamingTarget(@Nonnull Vector3d position,
                                       @Nonnull PhysicsChunkStreamingBounds bounds,
                                       @Nonnull List<BodyStreamingRefresh> refreshes) {
    }

    private record BodyStreamingRefresh(@Nonnull Ref<PhysicsStore> bodyRef,
                                        boolean sleeping) {
    }

    private static final class BodyTargetAccumulator {

        private final Map<PhysicsChunkStreamingBounds, BodyStreamingTarget> targets =
            new Object2ObjectOpenHashMap<>();
        private int spatialCandidates;
        private int dynamicCandidates;
    }

}
