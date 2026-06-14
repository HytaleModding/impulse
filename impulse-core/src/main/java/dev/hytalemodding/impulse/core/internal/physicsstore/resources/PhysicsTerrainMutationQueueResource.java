package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.terrain.TerrainColliderMutation;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Predicate;
import javax.annotation.Nonnull;

/**
 * Copied terrain mutation queue drained by PhysicsStore.tick().
 */
public final class PhysicsTerrainMutationQueueResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Queue<TerrainColliderMutation> mutations = new ArrayDeque<>();

    public PhysicsTerrainMutationQueueResource() {
    }

    public synchronized void enqueue(@Nonnull TerrainColliderMutation mutation) {
        mutations.add(Objects.requireNonNull(mutation, "mutation"));
    }

    @Nonnull
    public synchronized List<TerrainColliderMutation> drain() {
        List<TerrainColliderMutation> drained = new ArrayList<>(mutations.size());
        TerrainColliderMutation mutation;
        while ((mutation = mutations.poll()) != null) {
            drained.add(mutation);
        }
        return drained;
    }

    public synchronized int size() {
        return mutations.size();
    }

    public synchronized int removeIf(@Nonnull Predicate<TerrainColliderMutation> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        int before = mutations.size();
        mutations.removeIf(predicate);
        return before - mutations.size();
    }

    public synchronized void clear() {
        mutations.clear();
    }

    @Nonnull
    @Override
    public synchronized PhysicsTerrainMutationQueueResource clone() {
        PhysicsTerrainMutationQueueResource copy = new PhysicsTerrainMutationQueueResource();
        copy.mutations.addAll(mutations);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsTerrainMutationQueueResource> getResourceType() {
        return PhysicsStoreTypes.terrainMutationQueueResourceType();
    }
}
