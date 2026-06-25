package dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkBuildOptions;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.EntityChunkBoundaryMode;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only copied PhysicsChunk settings indexed by PhysicsStore space UUID.
 */
public final class PhysicsChunkSettingsIndexResource implements Resource<PhysicsStore> {

    public static final long INITIAL_GENERATION = 1L;

    @Nullable
    private static ResourceType<PhysicsStore, PhysicsChunkSettingsIndexResource> resourceType;
    @Nonnull
    private final Map<UUID, PhysicsChunkSpaceSettings> settingsBySpaceUuid =
        new Object2ObjectOpenHashMap<>();
    private long generation = INITIAL_GENERATION;

    public PhysicsChunkSettingsIndexResource() {
    }

    public synchronized void replaceAll(@Nonnull Map<UUID, PhysicsChunkSpaceSettings> settings) {
        if (settingsBySpaceUuid.equals(settings)) {
            return;
        }
        settingsBySpaceUuid.clear();
        settingsBySpaceUuid.putAll(settings);
        generation++;
    }

    @Nonnull
    public synchronized List<PhysicsChunkSpaceSettings> streamingSpaces() {
        return settingsBySpaceUuid.values().stream()
            .filter(settings -> settings.mode() == PhysicsChunkCollisionMode.STREAMING)
            .toList();
    }

    @Nullable
    public synchronized PhysicsChunkSpaceSettings settings(@Nonnull UUID spaceUuid) {
        return settingsBySpaceUuid.get(spaceUuid);
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized void clear() {
        if (!settingsBySpaceUuid.isEmpty()) {
            generation++;
        }
        settingsBySpaceUuid.clear();
    }

    @Nonnull
    @Override
    public synchronized PhysicsChunkSettingsIndexResource clone() {
        PhysicsChunkSettingsIndexResource copy = new PhysicsChunkSettingsIndexResource();
        copy.settingsBySpaceUuid.putAll(settingsBySpaceUuid);
        copy.generation = generation;
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

    public static void clearResourceType() {
        resourceType = null;
    }

    public record PhysicsChunkSpaceSettings(@Nonnull UUID spaceUuid,
                                              @Nonnull PhysicsChunkCollisionMode mode,
                                              @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
                                              boolean nativeVoxelCollisionEnabled,
                                              int radius,
                                              int bodyRadius,
                                              int ttlTicks) {

        @Nonnull
        public PhysicsChunkBuildOptions buildOptions() {
            return new PhysicsChunkBuildOptions(
                ChunkCollisionMode.fromNativeVoxelCollisionEnabled(nativeVoxelCollisionEnabled));
        }
    }
}
