package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Copied chunk collision mutation emitted from PhysicsChunk terrain code.
 */
public record ChunkCollisionMutation(@Nonnull UUID spaceUuid,
                                       @Nonnull String sourceKey,
                                       int chunkX,
                                       int sectionY,
                                       int chunkZ,
                                       @Nonnull String payloadResourceKey,
                                       @Nullable ChunkCollisionPayload payload,
                                       boolean remove) {

    public ChunkCollisionMutation {
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(payloadResourceKey, "payloadResourceKey");
    }

    @Nonnull
    public UUID chunkCollisionUuid() {
        return chunkCollisionUuid(spaceUuid, sourceKey);
    }

    @Nonnull
    public static ChunkCollisionMutation upsert(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nonnull String payloadResourceKey,
        @Nonnull ChunkCollisionPayload payload) {
        return new ChunkCollisionMutation(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadResourceKey,
            payload,
            false);
    }

    @Nonnull
    public static ChunkCollisionMutation remove(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        int chunkX,
        int sectionY,
        int chunkZ) {
        return new ChunkCollisionMutation(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            "",
            null,
            true);
    }

    @Nonnull
    public static UUID chunkCollisionUuid(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey) {
        String key = spaceUuid + "|" + sourceKey;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
