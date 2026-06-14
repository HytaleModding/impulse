package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreSnapshotFrame;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
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

    public void publish(@Nonnull PhysicsStoreSnapshotFrame frame) {
        Map<UUID, PhysicsStoreBodySnapshot> bodiesByUuid = new Object2ObjectOpenHashMap<>();
        for (PhysicsStoreBodySnapshot body : frame.bodies()) {
            bodiesByUuid.put(body.bodyUuid(), body);
        }
        snapshot = new PublishedSnapshot(frame, Map.copyOf(bodiesByUuid));
    }

    public void clear() {
        snapshot = PublishedSnapshot.EMPTY;
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
        @Nonnull Map<UUID, PhysicsStoreBodySnapshot> bodiesByUuid) {

        private static final PublishedSnapshot EMPTY =
            new PublishedSnapshot(PhysicsStoreSnapshotFrame.EMPTY, Map.of());
    }
}
