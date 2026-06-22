package dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionMutation;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkLifecycle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Copied chunk collision mutation queue drained by PhysicsStore.tick().
 */
public final class PhysicsChunkCollisionMutationQueueResource implements Resource<PhysicsStore> {

    @Nullable
    private static ResourceType<PhysicsStore, PhysicsChunkCollisionMutationQueueResource> resourceType;
    @Nonnull
    private final LinkedHashMap<MutationKey, ChunkCollisionMutation> mutations =
        new LinkedHashMap<>();
    private long lifecycleGeneration = PhysicsChunkLifecycle.generation();
    private long settingsGeneration = PhysicsChunkSettingsIndexResource.INITIAL_GENERATION;

    public PhysicsChunkCollisionMutationQueueResource() {
    }

    public synchronized void enqueue(@Nonnull ChunkCollisionMutation mutation) {
        ChunkCollisionMutation stamped = Objects.requireNonNull(mutation, "mutation")
            .stamped(lifecycleGeneration, settingsGeneration);
        MutationKey key = new MutationKey(stamped.spaceUuid(), stamped.sourceKey());
        mutations.remove(key);
        mutations.put(key, stamped);
    }

    public synchronized void updateStamp(long lifecycleGeneration, long settingsGeneration) {
        this.lifecycleGeneration = lifecycleGeneration;
        this.settingsGeneration = settingsGeneration;
    }

    @Nonnull
    public synchronized List<ChunkCollisionMutation> drain() {
        List<ChunkCollisionMutation> drained = new ArrayList<>(mutations.values());
        mutations.clear();
        return drained;
    }

    public synchronized int size() {
        return mutations.size();
    }

    public synchronized int removeIf(@Nonnull Predicate<ChunkCollisionMutation> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        int before = mutations.size();
        mutations.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
        return before - mutations.size();
    }

    public synchronized void clear() {
        mutations.clear();
    }

    @Nonnull
    @Override
    public synchronized PhysicsChunkCollisionMutationQueueResource clone() {
        PhysicsChunkCollisionMutationQueueResource copy = new PhysicsChunkCollisionMutationQueueResource();
        copy.mutations.putAll(mutations);
        copy.lifecycleGeneration = lifecycleGeneration;
        copy.settingsGeneration = settingsGeneration;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsChunkCollisionMutationQueueResource> getResourceType() {
        return resourceType;
    }

    public static void setResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsChunkCollisionMutationQueueResource> type) {
        resourceType = type;
    }

    public static void clearResourceType() {
        resourceType = null;
    }

    private record MutationKey(@Nonnull java.util.UUID spaceUuid, @Nonnull String sourceKey) {
    }
}
