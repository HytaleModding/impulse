package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreSnapshotFrame;
import javax.annotation.Nonnull;

/**
 * Latest copied PhysicsStore snapshot frame for projection, debug, and queries.
 */
public final class PhysicsSnapshotResource implements Resource<PhysicsStore> {

    @Nonnull
    private PhysicsStoreSnapshotFrame latestFrame = PhysicsStoreSnapshotFrame.EMPTY;

    public PhysicsSnapshotResource() {
    }

    @Nonnull
    public PhysicsStoreSnapshotFrame getLatestFrame() {
        return latestFrame;
    }

    public void publish(@Nonnull PhysicsStoreSnapshotFrame frame) {
        latestFrame = frame;
    }

    public void clear() {
        latestFrame = PhysicsStoreSnapshotFrame.EMPTY;
    }

    @Nonnull
    @Override
    public PhysicsSnapshotResource clone() {
        PhysicsSnapshotResource copy = new PhysicsSnapshotResource();
        copy.latestFrame = latestFrame;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSnapshotResource> getResourceType() {
        return PhysicsStoreTypes.snapshotResourceType();
    }
}
