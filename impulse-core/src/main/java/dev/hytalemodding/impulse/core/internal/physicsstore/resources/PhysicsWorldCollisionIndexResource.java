package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionBuildOptions;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.TerrainColliderMode;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Runtime-only copied world-collision settings indexed by PhysicsStore space UUID.
 */
public final class PhysicsWorldCollisionIndexResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Map<UUID, SpaceWorldCollisionSettings> settingsBySpaceUuid =
        new Object2ObjectOpenHashMap<>();

    public PhysicsWorldCollisionIndexResource() {
    }

    public void replaceAll(@Nonnull Map<UUID, SpaceWorldCollisionSettings> settings) {
        settingsBySpaceUuid.clear();
        settingsBySpaceUuid.putAll(settings);
    }

    @Nonnull
    public List<SpaceWorldCollisionSettings> streamingSpaces() {
        return settingsBySpaceUuid.values().stream()
            .filter(settings -> settings.mode() == WorldCollisionMode.STREAMING)
            .toList();
    }

    public void clear() {
        settingsBySpaceUuid.clear();
    }

    @Nonnull
    @Override
    public PhysicsWorldCollisionIndexResource clone() {
        PhysicsWorldCollisionIndexResource copy = new PhysicsWorldCollisionIndexResource();
        copy.settingsBySpaceUuid.putAll(settingsBySpaceUuid);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource> getResourceType() {
        return PhysicsStoreTypes.worldCollisionIndexResourceType();
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
