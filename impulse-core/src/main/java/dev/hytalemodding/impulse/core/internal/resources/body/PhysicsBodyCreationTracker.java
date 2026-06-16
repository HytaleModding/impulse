package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Tracks body UUIDs reserved by async body registrations until publication catches up.
 */
public final class PhysicsBodyCreationTracker {

    private final Object2IntOpenHashMap<UUID> pendingBodyCreations =
        new Object2IntOpenHashMap<>();

    public void markPending(@Nonnull RigidBodyKey bodyKey) {
        markPending(Objects.requireNonNull(bodyKey, "bodyKey").value());
    }

    public void markPending(@Nonnull UUID bodyUuid) {
        synchronized (pendingBodyCreations) {
            pendingBodyCreations.addTo(Objects.requireNonNull(bodyUuid, "bodyUuid"), 1);
        }
    }

    public void clearPending(@Nonnull RigidBodyKey bodyKey) {
        clearPending(Objects.requireNonNull(bodyKey, "bodyKey").value());
    }

    public void clearPending(@Nonnull UUID bodyUuid) {
        synchronized (pendingBodyCreations) {
            clearPendingDirect(Objects.requireNonNull(bodyUuid, "bodyUuid"));
        }
    }

    public boolean isPending(@Nonnull RigidBodyKey bodyKey) {
        return isPending(Objects.requireNonNull(bodyKey, "bodyKey").value());
    }

    public boolean isPending(@Nonnull UUID bodyUuid) {
        synchronized (pendingBodyCreations) {
            return pendingBodyCreations.containsKey(Objects.requireNonNull(bodyUuid, "bodyUuid"));
        }
    }

    public void clear() {
        synchronized (pendingBodyCreations) {
            pendingBodyCreations.clear();
        }
    }

    private void clearPendingDirect(UUID bodyUuid) {
        int count = pendingBodyCreations.getInt(bodyUuid);
        if (count <= 1) {
            pendingBodyCreations.removeInt(bodyUuid);
        } else {
            pendingBodyCreations.put(bodyUuid, count - 1);
        }
    }
}
