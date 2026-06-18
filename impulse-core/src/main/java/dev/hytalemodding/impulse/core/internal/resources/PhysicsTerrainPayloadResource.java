package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.terrain.TerrainColliderPayload;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only copied PhysicsChunk collision payloads keyed by ChunkCollisionSourceComponent payload keys.
 */
public final class PhysicsTerrainPayloadResource implements Resource<PhysicsStore> {

    @Nullable
    private static ResourceType<PhysicsStore, PhysicsTerrainPayloadResource> resourceType;
    @Nonnull
    private final Map<String, TerrainColliderPayload> payloadsByKey =
        new Object2ObjectOpenHashMap<>();

    public PhysicsTerrainPayloadResource() {
    }

    public void put(@Nonnull String key, @Nonnull TerrainColliderPayload payload) {
        payloadsByKey.put(key, payload);
    }

    @Nullable
    public TerrainColliderPayload get(@Nonnull String key) {
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
    public PhysicsTerrainPayloadResource clone() {
        PhysicsTerrainPayloadResource copy = new PhysicsTerrainPayloadResource();
        copy.payloadsByKey.putAll(payloadsByKey);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsTerrainPayloadResource> getResourceType() {
        return resourceType;
    }

    public static void setResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsTerrainPayloadResource> type) {
        resourceType = type;
    }
}
