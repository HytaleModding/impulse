package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import javax.annotation.Nonnull;

/**
 * Copied request queue drained by PhysicsStore.tick().
 */
public final class PhysicsRequestQueueResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Queue<PhysicsStoreRequest> requests = new ArrayDeque<>();

    public PhysicsRequestQueueResource() {
    }

    public synchronized void enqueue(@Nonnull PhysicsStoreRequest request) {
        requests.add(Objects.requireNonNull(request, "request"));
    }

    public synchronized void enqueueAll(@Nonnull Iterable<? extends PhysicsStoreRequest> batch) {
        Objects.requireNonNull(batch, "batch");
        for (PhysicsStoreRequest request : batch) {
            requests.add(Objects.requireNonNull(request, "request"));
        }
    }

    @Nonnull
    public synchronized List<PhysicsStoreRequest> drain() {
        List<PhysicsStoreRequest> drained = new ArrayList<>(requests.size());
        PhysicsStoreRequest request;
        while ((request = requests.poll()) != null) {
            drained.add(request);
        }
        return drained;
    }

    public synchronized int size() {
        return requests.size();
    }

    public synchronized void clear() {
        requests.clear();
    }

    @Nonnull
    @Override
    public synchronized PhysicsRequestQueueResource clone() {
        PhysicsRequestQueueResource copy = new PhysicsRequestQueueResource();
        copy.requests.addAll(requests);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRequestQueueResource> getResourceType() {
        return PhysicsStoreTypes.requestQueueResourceType();
    }
}
