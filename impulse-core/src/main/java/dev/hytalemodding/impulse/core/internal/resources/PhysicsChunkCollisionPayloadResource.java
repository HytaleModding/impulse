package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only copied PhysicsChunk collision payloads keyed by ChunkCollisionSourceComponent payload keys.
 */
public final class PhysicsChunkCollisionPayloadResource implements Resource<PhysicsStore> {

    @Nullable
    private static ResourceType<PhysicsStore, PhysicsChunkCollisionPayloadResource> resourceType;
    @Nonnull
    private final Map<String, ChunkCollisionPayload> payloadsByKey =
        new Object2ObjectOpenHashMap<>();

    public PhysicsChunkCollisionPayloadResource() {
    }

    public void put(@Nonnull String key, @Nonnull ChunkCollisionPayload payload) {
        payloadsByKey.put(key, payload);
    }

    @Nullable
    public ChunkCollisionPayload get(@Nonnull String key) {
        return payloadsByKey.get(key);
    }

    public void remove(@Nonnull String key) {
        payloadsByKey.remove(key);
    }

    public void clear() {
        payloadsByKey.clear();
    }

    @Nonnull
    @Override
    public PhysicsChunkCollisionPayloadResource clone() {
        PhysicsChunkCollisionPayloadResource copy = new PhysicsChunkCollisionPayloadResource();
        copy.payloadsByKey.putAll(payloadsByKey);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsChunkCollisionPayloadResource> getResourceType() {
        return resourceType;
    }

    public static void setResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsChunkCollisionPayloadResource> type) {
        resourceType = type;
    }
}
