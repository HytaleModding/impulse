package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import javax.annotation.Nonnull;

/**
 * Tracks body ids reserved by async body registrations until worker completion is observed.
 */
public final class PhysicsBodyCreationTracker {

    private final Object2IntOpenHashMap<PhysicsBodyId> pendingBodyCreations =
        new Object2IntOpenHashMap<>();

    public void markPending(@Nonnull PhysicsBodyId bodyId) {
        synchronized (pendingBodyCreations) {
            pendingBodyCreations.addTo(bodyId, 1);
        }
    }

    public void clearPending(@Nonnull PhysicsBodyId bodyId) {
        synchronized (pendingBodyCreations) {
            int count = pendingBodyCreations.getInt(bodyId);
            if (count <= 1) {
                pendingBodyCreations.removeInt(bodyId);
            } else {
                pendingBodyCreations.put(bodyId, count - 1);
            }
        }
    }

    public boolean isPending(@Nonnull PhysicsBodyId bodyId) {
        synchronized (pendingBodyCreations) {
            return pendingBodyCreations.containsKey(bodyId);
        }
    }
}
