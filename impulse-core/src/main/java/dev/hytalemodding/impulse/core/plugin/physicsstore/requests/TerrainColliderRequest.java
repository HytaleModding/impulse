package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied terrain collider request emitted from chunk/world-collision code.
 */
public record TerrainColliderRequest(@Nonnull UUID requestUuid,
                                     @Nonnull UUID spaceUuid,
                                     @Nonnull String sourceKey,
                                     int chunkX,
                                     int sectionY,
                                     int chunkZ,
                                     @Nonnull String payloadResourceKey,
                                     boolean remove) implements PhysicsStoreRequest {

    public TerrainColliderRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(payloadResourceKey, "payloadResourceKey");
    }

    @Nonnull
    public static TerrainColliderRequest upsert(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nonnull String payloadResourceKey) {
        return new TerrainColliderRequest(UUID.randomUUID(),
            spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadResourceKey,
            false);
    }

    @Nonnull
    public static TerrainColliderRequest remove(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        int chunkX,
        int sectionY,
        int chunkZ) {
        return new TerrainColliderRequest(UUID.randomUUID(),
            spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            "",
            true);
    }
}
