package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreSnapshotFrame;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Latest copied PhysicsStore snapshot frame for projection, debug, and queries.
 */
public final class PhysicsSnapshotResource implements Resource<PhysicsStore> {

    @Nonnull
    private volatile PublishedSnapshot snapshot = PublishedSnapshot.EMPTY;

    public PhysicsSnapshotResource() {
    }

    @Nonnull
    public PhysicsStoreSnapshotFrame getLatestFrame() {
        return snapshot.frame();
    }

    @Nullable
    public PhysicsStoreBodySnapshot getBody(@Nonnull UUID bodyUuid) {
        return snapshot.bodiesByUuid().get(bodyUuid);
    }

    @Nullable
    public PhysicsStoreBodySnapshot getBody(@Nonnull Ref<PhysicsStore> bodyRef) {
        PhysicsStoreBodySnapshot body = snapshot.bodiesByRowIndex()
            .get(Objects.requireNonNull(bodyRef, "bodyRef").getIndex());
        return body != null && sameRef(body.bodyRef(), bodyRef) ? body : null;
    }

    public void publish(@Nonnull PhysicsStoreSnapshotFrame frame) {
        Map<UUID, PhysicsStoreBodySnapshot> bodiesByUuid = new Object2ObjectOpenHashMap<>();
        Int2ObjectOpenHashMap<PhysicsStoreBodySnapshot> bodiesByRowIndex =
            new Int2ObjectOpenHashMap<>();
        for (PhysicsStoreBodySnapshot body : frame.bodies()) {
            bodiesByUuid.put(body.bodyUuid(), body);
            Ref<PhysicsStore> bodyRef = body.bodyRef();
            if (bodyRef != null) {
                bodiesByRowIndex.put(bodyRef.getIndex(), body);
            }
        }
        snapshot = new PublishedSnapshot(frame,
            Map.copyOf(bodiesByUuid),
            bodiesByRowIndex);
    }

    public void removeBody(@Nonnull UUID bodyUuid) {
        PublishedSnapshot current = snapshot;
        if (!current.bodiesByUuid().containsKey(bodyUuid)) {
            return;
        }
        snapshot = withoutBody(current, bodyUuid);
    }

    public void clear() {
        snapshot = PublishedSnapshot.EMPTY;
    }

    @Nonnull
    private static PublishedSnapshot withoutBody(@Nonnull PublishedSnapshot current,
        @Nonnull UUID bodyUuid) {
        List<PhysicsStoreBodySnapshot> bodies = new ArrayList<>();
        Map<UUID, PhysicsStoreBodySnapshot> bodiesByUuid = new Object2ObjectOpenHashMap<>();
        Int2ObjectOpenHashMap<PhysicsStoreBodySnapshot> bodiesByRowIndex =
            new Int2ObjectOpenHashMap<>();
        for (PhysicsStoreBodySnapshot body : current.frame().bodies()) {
            if (bodyUuid.equals(body.bodyUuid())) {
                continue;
            }
            bodies.add(body);
            bodiesByUuid.put(body.bodyUuid(), body);
            Ref<PhysicsStore> bodyRef = body.bodyRef();
            if (bodyRef != null) {
                bodiesByRowIndex.put(bodyRef.getIndex(), body);
            }
        }
        return new PublishedSnapshot(
            new PhysicsStoreSnapshotFrame(current.frame().sequence(),
                current.frame().dt(),
                bodies),
            Map.copyOf(bodiesByUuid),
            bodiesByRowIndex);
    }

    @Nonnull
    @Override
    public PhysicsSnapshotResource clone() {
        PhysicsSnapshotResource copy = new PhysicsSnapshotResource();
        copy.snapshot = snapshot;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSnapshotResource> getResourceType() {
        return PhysicsStoreTypes.snapshotResourceType();
    }

    private record PublishedSnapshot(
        @Nonnull PhysicsStoreSnapshotFrame frame,
        @Nonnull Map<UUID, PhysicsStoreBodySnapshot> bodiesByUuid,
        @Nonnull Int2ObjectOpenHashMap<PhysicsStoreBodySnapshot> bodiesByRowIndex) {

        private static final PublishedSnapshot EMPTY =
            new PublishedSnapshot(PhysicsStoreSnapshotFrame.EMPTY,
                Map.of(),
                new Int2ObjectOpenHashMap<>());
    }

    private static boolean sameRef(@Nullable Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first != null
            && first.getIndex() == second.getIndex()
            && first.getStore() == second.getStore();
    }
}
