package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkBuildOptions;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.TerrainColliderMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only copied PhysicsChunk terrain settings indexed by PhysicsStore space UUID.
 */
public final class PhysicsChunkSettingsIndexResource implements Resource<PhysicsStore> {

    @Nullable
    private static ResourceType<PhysicsStore, PhysicsChunkSettingsIndexResource> resourceType;
    @Nonnull
    private final Map<UUID, PhysicsChunkSpaceSettings> settingsBySpaceUuid =
        new Object2ObjectOpenHashMap<>();

    public PhysicsChunkSettingsIndexResource() {
    }

    public synchronized void replaceAll(@Nonnull Map<UUID, PhysicsChunkSpaceSettings> settings) {
        settingsBySpaceUuid.clear();
        settingsBySpaceUuid.putAll(settings);
    }

    @Nonnull
    public synchronized List<PhysicsChunkSpaceSettings> streamingSpaces() {
        return settingsBySpaceUuid.values().stream()
            .filter(settings -> settings.mode() == PhysicsChunkTerrainMode.STREAMING)
            .toList();
    }

    @Nullable
    public synchronized PhysicsChunkSpaceSettings settings(@Nonnull UUID spaceUuid) {
        return settingsBySpaceUuid.get(spaceUuid);
    }

    public synchronized void clear() {
        settingsBySpaceUuid.clear();
    }

    @Nonnull
    @Override
    public synchronized PhysicsChunkSettingsIndexResource clone() {
        PhysicsChunkSettingsIndexResource copy = new PhysicsChunkSettingsIndexResource();
        copy.settingsBySpaceUuid.putAll(settingsBySpaceUuid);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsChunkSettingsIndexResource> getResourceType() {
        return resourceType;
    }

    public static void setResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsChunkSettingsIndexResource> type) {
        resourceType = type;
    }

    public record PhysicsChunkSpaceSettings(@Nonnull UUID spaceUuid,
                                              @Nonnull PhysicsChunkTerrainMode mode,
                                              @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
                                              boolean nativeVoxelTerrainEnabled,
                                              int radius,
                                              int bodyRadius,
                                              int ttlTicks,
                                              float terrainFriction,
                                              float terrainRestitution) {

        @Nonnull
        public PhysicsChunkBuildOptions buildOptions() {
            return new PhysicsChunkBuildOptions(
                TerrainColliderMode.fromNativeVoxelTerrainEnabled(nativeVoxelTerrainEnabled),
                terrainFriction,
                terrainRestitution);
        }
    }
}
