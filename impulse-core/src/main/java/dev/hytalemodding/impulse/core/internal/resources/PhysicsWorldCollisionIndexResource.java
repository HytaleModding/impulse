package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.WorldCollisionBuildOptions;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.TerrainColliderMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only copied world-collision settings indexed by PhysicsStore space UUID.
 */
public final class PhysicsWorldCollisionIndexResource implements Resource<PhysicsStore> {

    @Nullable
    private static ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource> resourceType;
    @Nonnull
    private final Map<UUID, SpaceWorldCollisionSettings> settingsBySpaceUuid =
        new Object2ObjectOpenHashMap<>();

    public PhysicsWorldCollisionIndexResource() {
    }

    public synchronized void replaceAll(@Nonnull Map<UUID, SpaceWorldCollisionSettings> settings) {
        settingsBySpaceUuid.clear();
        settingsBySpaceUuid.putAll(settings);
    }

    @Nonnull
    public synchronized List<SpaceWorldCollisionSettings> streamingSpaces() {
        return settingsBySpaceUuid.values().stream()
            .filter(settings -> settings.mode() == WorldCollisionMode.STREAMING)
            .toList();
    }

    @Nullable
    public synchronized SpaceWorldCollisionSettings settings(@Nonnull UUID spaceUuid) {
        return settingsBySpaceUuid.get(spaceUuid);
    }

    public synchronized void clear() {
        settingsBySpaceUuid.clear();
    }

    @Nonnull
    @Override
    public synchronized PhysicsWorldCollisionIndexResource clone() {
        PhysicsWorldCollisionIndexResource copy = new PhysicsWorldCollisionIndexResource();
        copy.settingsBySpaceUuid.putAll(settingsBySpaceUuid);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource> getResourceType() {
        return resourceType;
    }

    public static void setResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource> type) {
        resourceType = type;
    }

    public record SpaceWorldCollisionSettings(@Nonnull UUID spaceUuid,
                                              @Nonnull WorldCollisionMode mode,
                                              @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
                                              boolean nativeVoxelTerrainEnabled,
                                              int radius,
                                              int bodyRadius,
                                              int ttlTicks,
                                              float terrainFriction,
                                              float terrainRestitution) {

        @Nonnull
        public WorldCollisionBuildOptions buildOptions() {
            return new WorldCollisionBuildOptions(
                TerrainColliderMode.fromNativeVoxelTerrainEnabled(nativeVoxelTerrainEnabled),
                terrainFriction,
                terrainRestitution);
        }
    }
}
