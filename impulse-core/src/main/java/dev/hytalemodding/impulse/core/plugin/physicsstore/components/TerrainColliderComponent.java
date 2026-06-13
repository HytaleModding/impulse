package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Terrain collider row mirrored from ChunkStore terrain source data.
 */
public final class TerrainColliderComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<TerrainColliderComponent> CODEC = BuilderCodec.builder(
            TerrainColliderComponent.class,
            TerrainColliderComponent::new)
        .append(new KeyedCodec<>("SpaceUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.spaceUuid = value,
            TerrainColliderComponent::getSpaceUuid)
        .add()
        .append(new KeyedCodec<>("SourceKey", Codec.STRING, false),
            (component, value) -> component.sourceKey = value != null ? value : "",
            TerrainColliderComponent::getSourceKey)
        .add()
        .append(new KeyedCodec<>("ChunkX", Codec.INTEGER, false),
            (component, value) -> component.chunkX = value != null ? value : 0,
            TerrainColliderComponent::getChunkX)
        .add()
        .append(new KeyedCodec<>("SectionY", Codec.INTEGER, false),
            (component, value) -> component.sectionY = value != null ? value : 0,
            TerrainColliderComponent::getSectionY)
        .add()
        .append(new KeyedCodec<>("ChunkZ", Codec.INTEGER, false),
            (component, value) -> component.chunkZ = value != null ? value : 0,
            TerrainColliderComponent::getChunkZ)
        .add()
        .append(new KeyedCodec<>("PayloadResourceKey", Codec.STRING, false),
            (component, value) -> component.payloadResourceKey = value != null ? value : "",
            TerrainColliderComponent::getPayloadResourceKey)
        .add()
        .append(new KeyedCodec<>("Retained", Codec.BOOLEAN, false),
            (component, value) -> component.retained = value == null || value,
            TerrainColliderComponent::isRetained)
        .add()
        .build();

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

    public TerrainColliderComponent() {
    }

    public TerrainColliderComponent(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nonnull String payloadResourceKey,
        boolean retained) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        this.chunkX = chunkX;
        this.sectionY = sectionY;
        this.chunkZ = chunkZ;
        this.payloadResourceKey = Objects.requireNonNull(payloadResourceKey, "payloadResourceKey");
        this.retained = retained;
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    public void setSpaceUuid(@Nonnull UUID spaceUuid) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
    }

    @Nonnull
    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(@Nonnull String sourceKey) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
    }

    public int getChunkX() {
        return chunkX;
    }

    public void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    public int getSectionY() {
        return sectionY;
    }

    public void setSectionY(int sectionY) {
        this.sectionY = sectionY;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public void setChunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
    }

    @Nonnull
    public String getPayloadResourceKey() {
        return payloadResourceKey;
    }

    public void setPayloadResourceKey(@Nonnull String payloadResourceKey) {
        this.payloadResourceKey = Objects.requireNonNull(payloadResourceKey, "payloadResourceKey");
    }

    public boolean isRetained() {
        return retained;
    }

    public void setRetained(boolean retained) {
        this.retained = retained;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TerrainColliderComponent> getComponentType() {
        return PhysicsStoreTypes.terrainColliderComponentType();
    }

    @Nonnull
    @Override
    public TerrainColliderComponent clone() {
        return new TerrainColliderComponent(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadResourceKey,
            retained);
    }
}
