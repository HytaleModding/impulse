package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Copied chunk collision mutation emitted from PhysicsChunk collision code.
 */
public record ChunkCollisionMutation(@Nonnull UUID spaceUuid,
                                       @Nonnull String sourceKey,
                                       int chunkX,
                                       int sectionY,
                                       int chunkZ,
                                       @Nonnull String payloadResourceKey,
                                       @Nullable ChunkCollisionPayload payload,
                                       boolean remove,
                                       long lifecycleGeneration,
                                       long settingsGeneration) {

    private static final long UNSTAMPED_GENERATION = 0L;

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
            false,
            UNSTAMPED_GENERATION,
            UNSTAMPED_GENERATION);
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
            true,
            UNSTAMPED_GENERATION,
            UNSTAMPED_GENERATION);
    }

    @Nonnull
    public ChunkCollisionMutation stamped(long lifecycleGeneration,
        long settingsGeneration) {
        return new ChunkCollisionMutation(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadResourceKey,
            payload,
            remove,
            lifecycleGeneration,
            settingsGeneration);
    }

    @Nonnull
    public static UUID chunkCollisionUuid(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey) {
        String key = spaceUuid + "|" + sourceKey;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
