package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PersistentTerrainColliderDto {

    @Nonnull
    public static final BuilderCodec<PersistentTerrainColliderDto> CODEC =
        BuilderCodec.builder(PersistentTerrainColliderDto.class, PersistentTerrainColliderDto::new)
            .append(new KeyedCodec<>("TerrainColliderUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.terrainColliderUuid = value,
                PersistentTerrainColliderDto::getTerrainColliderUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("SpaceUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.spaceUuid = value,
                PersistentTerrainColliderDto::getSpaceUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("SourceKey", Codec.STRING, false),
                (dto, value) -> dto.sourceKey = value != null ? value : "",
                PersistentTerrainColliderDto::getSourceKey)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("ChunkX", Codec.INTEGER, false),
                (dto, value) -> dto.chunkX = value != null ? value : 0,
                PersistentTerrainColliderDto::getChunkX)
            .add()
            .append(new KeyedCodec<>("SectionY", Codec.INTEGER, false),
                (dto, value) -> dto.sectionY = value != null ? value : 0,
                PersistentTerrainColliderDto::getSectionY)
            .add()
            .append(new KeyedCodec<>("ChunkZ", Codec.INTEGER, false),
                (dto, value) -> dto.chunkZ = value != null ? value : 0,
                PersistentTerrainColliderDto::getChunkZ)
            .add()
            .append(new KeyedCodec<>("PayloadResourceKey", Codec.STRING, false),
                (dto, value) -> dto.payloadResourceKey = value != null ? value : "",
                PersistentTerrainColliderDto::getPayloadResourceKey)
            .add()
            .append(new KeyedCodec<>("Retained", Codec.BOOLEAN, false),
                (dto, value) -> dto.retained = value == null || value,
                PersistentTerrainColliderDto::isRetained)
            .add()
            .build();

    @Nonnull
    private UUID terrainColliderUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID spaceUuid = new UUID(0L, 0L);
    @Nonnull
    private String sourceKey = "";
    private int chunkX;
    private int sectionY;
    private int chunkZ;
    @Nonnull
    private String payloadResourceKey = "";
    private boolean retained = true;

    public PersistentTerrainColliderDto() {
    }

    public PersistentTerrainColliderDto(@Nonnull UUID terrainColliderUuid,
        @Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nonnull String payloadResourceKey,
        boolean retained) {
        this.terrainColliderUuid = Objects.requireNonNull(terrainColliderUuid,
            "terrainColliderUuid");
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        this.chunkX = chunkX;
        this.sectionY = sectionY;
        this.chunkZ = chunkZ;
        this.payloadResourceKey = Objects.requireNonNull(payloadResourceKey,
            "payloadResourceKey");
        this.retained = retained;
    }

    @Nonnull
    public UUID getTerrainColliderUuid() {
        return terrainColliderUuid;
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    @Nonnull
    public String getSourceKey() {
        return sourceKey;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getSectionY() {
        return sectionY;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    @Nonnull
    public String getPayloadResourceKey() {
        return payloadResourceKey;
    }

    public boolean isRetained() {
        return retained;
    }

    @Nonnull
    public PersistentTerrainColliderDto copy() {
        return new PersistentTerrainColliderDto(terrainColliderUuid,
            spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadResourceKey,
            retained);
    }
}
