package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.simulation.query.BenchmarkSpaceStatsQuery;
import dev.hytalemodding.impulse.core.internal.simulation.view.BenchmarkSpaceStatsView;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Crucible-only copied diagnostics sourced from authoritative PhysicsStore rows.
 */
final class PhysicsStoreBenchmarkQueries {

    private PhysicsStoreBenchmarkQueries() {
    }

    @Nonnull
    static BenchmarkSpaceStatsView benchmarkSpaceStats(@Nonnull Store<PhysicsStore> store,
        @Nullable PhysicsStoreWorldCollisionStreamingResource streaming,
        @Nonnull BenchmarkSpaceStatsQuery query) {
        PhysicsStoreThreading.requireWorldThread(store, "read Crucible PhysicsStore benchmark stats");
        UUID spaceUuid = PhysicsStoreSpaceMutations.requireSpaceUuid(store, query.spaceId());
        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        BenchmarkSpaceStatsAccumulator stats = new BenchmarkSpaceStatsAccumulator();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectBodyRows(chunk, snapshots, spaceUuid, query, stats);
        store.forEachChunk(BodyComponent.getComponentType(), collector);
        int worldCollisionBodies = streaming != null ? streaming.bodyCount(spaceUuid) : 0;
        stats.bodies += worldCollisionBodies;
        stats.worldCollisionBodies += worldCollisionBodies;
        return stats.toView();
    }

    private static void collectBodyRows(@Nonnull ArchetypeChunk<PhysicsStore> chunk,
        @Nonnull PhysicsSnapshotResource snapshots,
        @Nonnull UUID spaceUuid,
        @Nonnull BenchmarkSpaceStatsQuery query,
        @Nonnull BenchmarkSpaceStatsAccumulator stats) {
        for (int index = 0; index < chunk.size(); index++) {
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body == null || !spaceUuid.equals(body.getSpaceUuid())) {
                continue;
            }
            UuidComponent uuid = chunk.getComponent(index, UuidComponent.getComponentType());
            if (uuid == null) {
                continue;
            }
            PhysicsStoreBodySnapshot snapshot = snapshots.getBody(uuid.getUuid());
            if (snapshot == null) {
                continue;
            }
            ShapeComponent shape = chunk.getComponent(index, ShapeComponent.getComponentType());
            classifyBody(stats, body, shape, snapshot, query);
        }
    }

    private static void classifyBody(@Nonnull BenchmarkSpaceStatsAccumulator stats,
        @Nonnull BodyComponent body,
        @Nullable ShapeComponent shape,
        @Nonnull PhysicsStoreBodySnapshot snapshot,
        @Nonnull BenchmarkSpaceStatsQuery query) {
        stats.bodies++;
        if (snapshot.bodyType() == PhysicsBodyType.DYNAMIC) {
            stats.dynamicBodies++;
            Vector3f position = snapshot.position();
            stats.minDynamicBodyY = Math.min(stats.minDynamicBodyY, position.y);
            stats.maxDynamicBodyY = Math.max(stats.maxDynamicBodyY, position.y);
            if (position.y < query.groundY() - query.belowPlaneTolerance()) {
                stats.belowPlaneBodies++;
            }
            if (query.includeTerrainProbe()) {
                stats.missingTerrainBaselineBodies++;
            }
            if (position.y < query.bodyWorldMinY()) {
                stats.belowWorldMinBodies++;
            }
            if (position.y < query.bodyVoidY()) {
                stats.belowVoidBodies++;
            }
            if (snapshot.sleeping()) {
                stats.sleepingDynamicBodies++;
            } else {
                stats.awakeDynamicBodies++;
            }
        }

        if (body.getKind() == PhysicsBodyKind.BODY) {
            stats.detachedBodies++;
            return;
        }
        if (shape != null && shape.getShapeType() == ShapeType.PLANE) {
            return;
        }
        if (body.getKind() == PhysicsBodyKind.WORLD_COLLISION) {
            stats.worldCollisionBodies++;
            return;
        }
        stats.rawBodies++;
    }

    private static final class BenchmarkSpaceStatsAccumulator {

        private int bodies;
        private int dynamicBodies;
        private int awakeDynamicBodies;
        private int sleepingDynamicBodies;
        private int detachedBodies;
        private int rawBodies;
        private int worldCollisionBodies;
        private int belowPlaneBodies;
        private int belowTerrainBodies;
        private int belowWorldMinBodies;
        private int belowVoidBodies;
        private int terrainBaselineBodies;
        private int missingTerrainBaselineBodies;
        private double minTerrainBottomClearance = Double.POSITIVE_INFINITY;
        private double minDynamicBodyY = Double.POSITIVE_INFINITY;
        private double maxDynamicBodyY = Double.NEGATIVE_INFINITY;

        @Nonnull
        private BenchmarkSpaceStatsView toView() {
            return new BenchmarkSpaceStatsView(bodies,
                dynamicBodies,
                awakeDynamicBodies,
                sleepingDynamicBodies,
                detachedBodies,
                rawBodies,
                worldCollisionBodies,
                belowPlaneBodies,
                belowTerrainBodies,
                belowWorldMinBodies,
                belowVoidBodies,
                terrainBaselineBodies,
                missingTerrainBaselineBodies,
                Double.isFinite(minTerrainBottomClearance) ? (float) minTerrainBottomClearance : Float.NaN,
                Double.isFinite(minDynamicBodyY) ? (float) minDynamicBodyY : Float.NaN,
                Double.isFinite(maxDynamicBodyY) ? (float) maxDynamicBodyY : Float.NaN);
        }
    }
}
