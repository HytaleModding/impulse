package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Snapshot-side spatial hash for detached physics bodies.
 *
 * <p>Stores the latest published {@link PhysicsBodySnapshot} for each body UUID
 * and groups those snapshots into fixed-size world cells.
 * Cell membership is updated whenever a body publishes a snapshot in a new
 * position.</p>
 *
 * <p>Callers use it for area queries that need body identity and pose data, such
 * as visual materialization, PhysicsChunk collision streaming hints, diagnostics, and
 * other nearby-body discovery. Query freshness follows the snapshot publishing
 * policy for each body.</p>
 */
final class PhysicsBodySpatialIndex {

    private static final float CELL_SIZE = 16.0f;
    private static final int AXIS_MASK = 0x1F_FFFF;

    private final Map<UUID, IndexedBody> entries = new Object2ObjectOpenHashMap<>();
    private final Long2ObjectMap<List<IndexedBody>> cells = new Long2ObjectOpenHashMap<>();
    private final Int2IntOpenHashMap spaceBodyCounts = new Int2IntOpenHashMap();

    void update(@Nonnull UUID bodyUuid,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId) {
        long cellKey = cellKey(snapshot.positionX(), snapshot.positionY(), snapshot.positionZ());
        IndexedBody indexed = entries.get(bodyUuid);
        if (indexed == null) {
            indexed = new IndexedBody(bodyUuid, snapshot, spaceId, cellKey);
            entries.put(bodyUuid, indexed);
            addToCell(indexed, cellKey);
            spaceBodyCounts.addTo(spaceId.value(), 1);
            return;
        }

        if (!indexed.spaceId.equals(spaceId)) {
            spaceBodyCounts.addTo(indexed.spaceId.value(), -1);
            spaceBodyCounts.addTo(spaceId.value(), 1);
        }
        if (indexed.cellKey != cellKey) {
            removeFromCell(indexed);
            addToCell(indexed, cellKey);
        }
        indexed.snapshot = snapshot;
        indexed.spaceId = spaceId;
    }

    void remove(@Nonnull UUID bodyUuid) {
        IndexedBody indexed = entries.remove(bodyUuid);
        if (indexed != null) {
            removeFromCell(indexed);
            spaceBodyCounts.addTo(indexed.spaceId.value(), -1);
        }
    }

    void clear() {
        entries.clear();
        cells.clear();
        spaceBodyCounts.clear();
    }

    int bodyCount() {
        return entries.size();
    }

    int bodyCount(@Nonnull SpaceId spaceId) {
        return Math.max(0, spaceBodyCounts.get(spaceId.value()));
    }

    int cellCount() {
        return cells.size();
    }

    void forEach(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        for (IndexedBody indexed : entries.values()) {
            if (spaceId.equals(indexed.spaceId)) {
                consumer.accept(indexed.entry());
            }
        }
    }

    void forEachIndexed(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        for (IndexedBody indexed : entries.values()) {
            if (spaceId.equals(indexed.spaceId)) {
                indexed.visit(visitor);
            }
        }
    }

    int forEachNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        int minX = cellCoordinate(center.x - radius);
        int maxX = cellCoordinate(center.x + radius);
        int minY = cellCoordinate(center.y - radius);
        int maxY = cellCoordinate(center.y + radius);
        int minZ = cellCoordinate(center.z - radius);
        int maxZ = cellCoordinate(center.z + radius);
        float radiusSquared = radius * radius;
        int candidates = 0;
        for (int cellX = minX; cellX <= maxX; cellX++) {
            for (int cellY = minY; cellY <= maxY; cellY++) {
                for (int cellZ = minZ; cellZ <= maxZ; cellZ++) {
                    List<IndexedBody> bucket = cells.get(packCell(cellX, cellY, cellZ));
                    if (bucket == null) {
                        continue;
                    }
                    for (IndexedBody indexed : bucket) {
                        if (!spaceId.equals(indexed.spaceId)) {
                            continue;
                        }
                        candidates++;
                        float dx = indexed.snapshot.positionX() - center.x;
                        float dy = indexed.snapshot.positionY() - center.y;
                        float dz = indexed.snapshot.positionZ() - center.z;
                        if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                            consumer.accept(indexed.entry());
                        }
                    }
                }
            }
        }
        return candidates;
    }

    int forEachIndexedNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        int minX = cellCoordinate(center.x - radius);
        int maxX = cellCoordinate(center.x + radius);
        int minY = cellCoordinate(center.y - radius);
        int maxY = cellCoordinate(center.y + radius);
        int minZ = cellCoordinate(center.z - radius);
        int maxZ = cellCoordinate(center.z + radius);
        float radiusSquared = radius * radius;
        int candidates = 0;
        for (int cellX = minX; cellX <= maxX; cellX++) {
            for (int cellY = minY; cellY <= maxY; cellY++) {
                for (int cellZ = minZ; cellZ <= maxZ; cellZ++) {
                    List<IndexedBody> bucket = cells.get(packCell(cellX, cellY, cellZ));
                    if (bucket == null) {
                        continue;
                    }
                    for (IndexedBody indexed : bucket) {
                        if (!spaceId.equals(indexed.spaceId)) {
                            continue;
                        }
                        candidates++;
                        float dx = indexed.snapshot.positionX() - center.x;
                        float dy = indexed.snapshot.positionY() - center.y;
                        float dz = indexed.snapshot.positionZ() - center.z;
                        if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                            indexed.visit(visitor);
                        }
                    }
                }
            }
        }
        return candidates;
    }

    private void removeFromCell(@Nonnull IndexedBody indexed) {
        List<IndexedBody> bucket = cells.get(indexed.cellKey);
        if (bucket == null) {
            return;
        }
        int index = indexed.cellIndex;
        int lastIndex = bucket.size() - 1;
        if (index >= 0 && index <= lastIndex && bucket.get(index) == indexed) {
            IndexedBody moved = bucket.get(lastIndex);
            bucket.set(index, moved);
            moved.cellIndex = index;
            bucket.remove(lastIndex);
        } else {
            bucket.remove(indexed);
        }
        indexed.cellIndex = -1;
        if (bucket.isEmpty()) {
            cells.remove(indexed.cellKey);
        }
    }

    private void addToCell(@Nonnull IndexedBody indexed, long cellKey) {
        List<IndexedBody> bucket = cells.computeIfAbsent(cellKey, ignored -> new ArrayList<>());
        indexed.cellKey = cellKey;
        indexed.cellIndex = bucket.size();
        bucket.add(indexed);
    }

    private static long cellKey(float positionX, float positionY, float positionZ) {
        return packCell(cellCoordinate(positionX),
            cellCoordinate(positionY),
            cellCoordinate(positionZ));
    }

    private static int cellCoordinate(float value) {
        return (int) Math.floor(value / CELL_SIZE);
    }

    private static long packCell(int x, int y, int z) {
        return ((long) x & AXIS_MASK) << 42
            | ((long) y & AXIS_MASK) << 21
            | ((long) z & AXIS_MASK);
    }

    private static final class IndexedBody {

        @Nonnull
        private final UUID bodyUuid;
        @Nonnull
        private PhysicsBodySnapshot snapshot;
        @Nonnull
        private SpaceId spaceId;
        private long cellKey;
        private int cellIndex = -1;

        private IndexedBody(@Nonnull UUID bodyUuid,
            @Nonnull PhysicsBodySnapshot snapshot,
            @Nonnull SpaceId spaceId,
            long cellKey) {
            this.bodyUuid = bodyUuid;
            this.snapshot = snapshot;
            this.spaceId = spaceId;
            this.cellKey = cellKey;
        }

        @Nonnull
        private PhysicsBodySnapshotEntry entry() {
            return new PhysicsBodySnapshotEntry(bodyUuid,
                snapshot,
                spaceId);
        }

        private void visit(@Nonnull PhysicsBodySnapshotVisitor visitor) {
            visitor.accept(bodyUuid,
                snapshot,
                spaceId);
        }
    }
}
