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
    private PhysicsStoreSnapshotFrame latestFrame = PhysicsStoreSnapshotFrame.EMPTY;
    @Nonnull
    private final Map<UUID, PhysicsStoreBodySnapshot> bodiesByUuid =
        new Object2ObjectOpenHashMap<>();

    public PhysicsSnapshotResource() {
    }

    @Nonnull
    public PhysicsStoreSnapshotFrame getLatestFrame() {
        return latestFrame;
    }

    @Nullable
    public PhysicsStoreBodySnapshot getBody(@Nonnull UUID bodyUuid) {
        return bodiesByUuid.get(bodyUuid);
    }

    public void publish(@Nonnull PhysicsStoreSnapshotFrame frame) {
        latestFrame = frame;
        bodiesByUuid.clear();
        for (PhysicsStoreBodySnapshot body : frame.bodies()) {
            bodiesByUuid.put(body.bodyUuid(), body);
        }
    }

    public void clear() {
        latestFrame = PhysicsStoreSnapshotFrame.EMPTY;
        bodiesByUuid.clear();
    }

    @Nonnull
    @Override
    public PhysicsSnapshotResource clone() {
        PhysicsSnapshotResource copy = new PhysicsSnapshotResource();
        copy.latestFrame = latestFrame;
        copy.bodiesByUuid.putAll(bodiesByUuid);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSnapshotResource> getResourceType() {
        return PhysicsStoreTypes.snapshotResourceType();
    }
}
