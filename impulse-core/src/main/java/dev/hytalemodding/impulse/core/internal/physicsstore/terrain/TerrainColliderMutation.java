package dev.hytalemodding.impulse.core.internal.physicsstore.terrain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Copied terrain collider mutation emitted from chunk/world-collision code.
 */
public record TerrainColliderMutation(@Nonnull UUID spaceUuid,
                                       @Nonnull String sourceKey,
                                       int chunkX,
                                       int sectionY,
                                       int chunkZ,
                                       @Nonnull String payloadResourceKey,
                                       @Nullable TerrainColliderPayload payload,
                                       boolean remove) {

    public TerrainColliderMutation {
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(payloadResourceKey, "payloadResourceKey");
    }

    @Nonnull
    public UUID terrainColliderUuid() {
        return terrainColliderUuid(spaceUuid, sourceKey);
    }

    @Nonnull
    public static TerrainColliderMutation upsert(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nonnull String payloadResourceKey,
        @Nonnull TerrainColliderPayload payload) {
        return new TerrainColliderMutation(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadResourceKey,
            payload,
            false);
    }

    @Nonnull
    public static TerrainColliderMutation remove(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        int chunkX,
        int sectionY,
        int chunkZ) {
        return new TerrainColliderMutation(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            "",
            null,
            true);
    }

    @Nonnull
    public static UUID terrainColliderUuid(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey) {
        String key = spaceUuid + "|" + sourceKey;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
