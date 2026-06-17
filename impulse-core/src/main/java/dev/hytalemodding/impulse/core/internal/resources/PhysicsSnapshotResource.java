package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
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
    public PhysicsSnapshotFrame getLatestFrame() {
        return snapshot.frame();
    }

    @Nullable
    public PhysicsBodySnapshot getBody(@Nonnull UUID bodyUuid) {
        return snapshot.bodiesByUuid().get(bodyUuid);
    }

    public boolean containsBody(@Nonnull UUID bodyUuid) {
        return snapshot.bodiesByUuid().containsKey(Objects.requireNonNull(bodyUuid, "bodyUuid"));
    }

    @Nullable
    public PhysicsBodySnapshot getBody(@Nonnull Ref<PhysicsStore> bodyRef) {
        PhysicsBodySnapshot body = snapshot.bodiesByRowIndex()
            .get(Objects.requireNonNull(bodyRef, "bodyRef").getIndex());
        return body != null && sameRef(body.bodyRef(), bodyRef) ? body : null;
    }

    public void publish(@Nonnull PhysicsSnapshotFrame frame) {
        int bodyCount = frame.bodies().size();
        Map<UUID, PhysicsBodySnapshot> bodiesByUuid = new Object2ObjectOpenHashMap<>(bodyCount);
        Int2ObjectOpenHashMap<PhysicsBodySnapshot> bodiesByRowIndex =
            new Int2ObjectOpenHashMap<>(bodyCount);
        for (PhysicsBodySnapshot body : frame.bodies()) {
            bodiesByUuid.put(body.bodyUuid(), body);
            Ref<PhysicsStore> bodyRef = body.bodyRef();
            if (bodyRef != null) {
                bodiesByRowIndex.put(bodyRef.getIndex(), body);
            }
        }
        snapshot = new PublishedSnapshot(frame,
            bodiesByUuid,
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
        int bodyCount = Math.max(0, current.frame().bodies().size() - 1);
        List<PhysicsBodySnapshot> bodies = new ArrayList<>(bodyCount);
        Map<UUID, PhysicsBodySnapshot> bodiesByUuid = new Object2ObjectOpenHashMap<>(bodyCount);
        Int2ObjectOpenHashMap<PhysicsBodySnapshot> bodiesByRowIndex =
            new Int2ObjectOpenHashMap<>(bodyCount);
        for (PhysicsBodySnapshot body : current.frame().bodies()) {
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
            new PhysicsSnapshotFrame(current.frame().sequence(),
                current.frame().dt(),
                bodies),
            bodiesByUuid,
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
        return PhysicsResourceTypes.snapshotResourceType();
    }

    private record PublishedSnapshot(
        @Nonnull PhysicsSnapshotFrame frame,
        @Nonnull Map<UUID, PhysicsBodySnapshot> bodiesByUuid,
        @Nonnull Int2ObjectOpenHashMap<PhysicsBodySnapshot> bodiesByRowIndex) {

        private static final PublishedSnapshot EMPTY =
            new PublishedSnapshot(PhysicsSnapshotFrame.EMPTY,
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
