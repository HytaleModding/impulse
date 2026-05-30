package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import javax.annotation.Nonnull;

/**
 * Tracks body keys reserved by async body registrations until publication catches up.
 */
public final class PhysicsBodyCreationTracker {

    private final Object2IntOpenHashMap<RigidBodyKey> pendingBodyCreations =
        new Object2IntOpenHashMap<>();

    public void markPending(@Nonnull RigidBodyKey bodyKey) {
        synchronized (pendingBodyCreations) {
            pendingBodyCreations.addTo(bodyKey, 1);
        }
    }

    public void clearPending(@Nonnull RigidBodyKey bodyKey) {
        synchronized (pendingBodyCreations) {
            clearPendingDirect(bodyKey);
        }
    }

    public boolean isPending(@Nonnull RigidBodyKey bodyKey) {
        synchronized (pendingBodyCreations) {
            return pendingBodyCreations.containsKey(bodyKey);
        }
    }

    public void clear() {
        synchronized (pendingBodyCreations) {
            pendingBodyCreations.clear();
        }
    }

    private void clearPendingDirect(RigidBodyKey bodyKey) {
        int count = pendingBodyCreations.getInt(bodyKey);
        if (count <= 1) {
            pendingBodyCreations.removeInt(bodyKey);
        } else {
            pendingBodyCreations.put(bodyKey, count - 1);
        }
    }
}
