package dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
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
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsStoreTerrainMutationCache.TargetRefreshDecision;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionStreamingBounds;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.StreamingTargetDiagnostic;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource.SpaceWorldCollisionSettings;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreSnapshotFrame;
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
 * Produces copied PhysicsStore terrain requests from EntityStore and ChunkStore state.
 */
public final class PhysicsStoreWorldCollisionProducerSystem extends TickingSystem<EntityStore>
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
        if (!WorldCollisionLifecycle.isEnabled()) {
            return;
        }

        WorldCollisionProfilingResource profiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        Snapshot snapshot = profiling.isEnabled() ? profiling.beginTick() : null;
        long tickStart = snapshot != null ? System.nanoTime() : 0L;
        try {
            World world = store.getExternalData().getWorld();
            PhysicsStore physicsStore = ((PhysicsStoreWorld) world).getPhysicsStore();
            Store<PhysicsStore> physics = physicsStore.getStore();
            PhysicsStoreThreading.requireWorldThread(physics,
                "produce PhysicsStore world-collision terrain mutations");
            PhysicsTerrainMutationQueueResource queue = physics.getResource(
                PhysicsTerrainMutationQueueResource.getResourceType());
            PhysicsWorldCollisionIndexResource worldCollisionIndex = physics.getResource(
                PhysicsWorldCollisionIndexResource.getResourceType());
            PhysicsSnapshotResource snapshotResource = physics.getResource(
                PhysicsSnapshotResource.getResourceType());
            PhysicsStoreWorldCollisionStreamingResource streaming = store.getResource(
                PhysicsStoreWorldCollisionStreamingResource.getResourceType());

            List<SpaceWorldCollisionSettings> spaces = worldCollisionIndex.streamingSpaces();
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
            for (SpaceWorldCollisionSettings settings : spaces) {
                retainedSpaces.add(settings.spaceUuid());
            }
            streaming.retainSpaces(retainedSpaces, queue);

            PhysicsStoreSnapshotFrame physicsFrame = snapshotResource.getLatestFrame();
            for (SpaceWorldCollisionSettings settings : spaces) {
                if (snapshot != null) {
                    snapshot.incrementStreamingSpaces();
                }
                processSpace(world,
                    streaming,
                    queue,
                    settings,
                    playerPositions,
                    physicsFrame,
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
        @Nonnull PhysicsStoreWorldCollisionStreamingResource streaming,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        @Nonnull SpaceWorldCollisionSettings settings,
        @Nonnull List<Vector3d> playerPositions,
        @Nonnull PhysicsStoreSnapshotFrame physicsFrame,
        long currentTick,
        @Nullable Snapshot snapshot) {
        LongSet visitedSections = new LongOpenHashSet();
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
                settings.buildOptions());
            if (snapshot != null) {
                snapshot.addPlayerSectionTargets(visitedSections.size() - sectionsBefore);
            }
        }

        for (BodyStreamingTarget target : collectDynamicBodyTargets(streaming,
            settings,
            physicsFrame,
            currentTick,
            snapshot)) {
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
                settings.buildOptions());
            for (BodyStreamingRefresh refresh : target.refreshes()) {
                streaming.recordBodyTargetRefresh(settings.spaceUuid(),
                    refresh.bodyUuid(),
                    target.bounds(),
                    refresh.sleeping(),
                    currentTick);
            }
            if (snapshot != null) {
                snapshot.addBodySectionTargets(visitedSections.size() - sectionsBefore);
            }
        }

        streaming.pruneUnloaded(world, settings.spaceUuid(), queue, snapshot);
        streaming.pruneUnused(settings.spaceUuid(), queue, currentTick, settings.ttlTicks(), snapshot);
        streaming.pruneBodyStreamingTargets(settings.spaceUuid(),
            currentTick,
            settings.ttlTicks(),
            snapshot);
    }

    @Nonnull
    private static List<BodyStreamingTarget> collectDynamicBodyTargets(
        @Nonnull PhysicsStoreWorldCollisionStreamingResource streaming,
        @Nonnull SpaceWorldCollisionSettings settings,
        @Nonnull PhysicsStoreSnapshotFrame physicsFrame,
        long currentTick,
        @Nullable Snapshot snapshot) {
        Map<WorldCollisionStreamingBounds, BodyStreamingTarget> uniqueTargets =
            new Object2ObjectOpenHashMap<>();
        int spatialCandidates = 0;
        int dynamicCandidates = 0;
        for (PhysicsStoreBodySnapshot body : physicsFrame.bodies()) {
            if (!body.spaceUuid().equals(settings.spaceUuid())) {
                continue;
            }
            spatialCandidates++;
            if (body.bodyType() != PhysicsBodyType.DYNAMIC) {
                continue;
            }
            dynamicCandidates++;
            Vector3f position = body.position();
            WorldCollisionStreamingBounds bounds = WorldCollisionStreamingBounds.from(position.x,
                position.y,
                position.z,
                settings.bodyRadius());
            TargetRefreshDecision decision = streaming.shouldRefreshBodyTarget(settings.spaceUuid(),
                body.bodyUuid(),
                bounds,
                body.sleeping(),
                currentTick,
                settings.ttlTicks(),
                snapshot);
            if (!decision.refresh()) {
                continue;
            }

            BodyStreamingTarget target = uniqueTargets.get(bounds);
            if (target == null) {
                target = new BodyStreamingTarget(new Vector3d(position.x, position.y, position.z),
                    bounds,
                    new ArrayList<>());
                uniqueTargets.put(bounds, target);
            } else if (snapshot != null) {
                snapshot.incrementBodyTargetDedupeSkips();
            }
            target.refreshes().add(new BodyStreamingRefresh(body.bodyUuid(), body.sleeping()));
        }

        if (snapshot != null) {
            snapshot.addBodySpatialIndexCandidates(spatialCandidates);
            snapshot.addBodyStreamingCandidates(dynamicCandidates);
            snapshot.addBodyStreamingTargets(uniqueTargets.size());
        }
        return new ArrayList<>(uniqueTargets.values());
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
        synchronized (PhysicsStoreWorldCollisionProducerSystem.class) {
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
        synchronized (PhysicsStoreWorldCollisionProducerSystem.class) {
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
        synchronized (PhysicsStoreWorldCollisionProducerSystem.class) {
            resolved = transformType;
            if (resolved == null) {
                resolved = TransformComponent.getComponentType();
                transformType = resolved;
            }
        }
        return resolved;
    }

    private record BodyStreamingTarget(@Nonnull Vector3d position,
                                       @Nonnull WorldCollisionStreamingBounds bounds,
                                       @Nonnull List<BodyStreamingRefresh> refreshes) {
    }

    private record BodyStreamingRefresh(@Nonnull UUID bodyUuid,
                                        boolean sleeping) {
    }

}
