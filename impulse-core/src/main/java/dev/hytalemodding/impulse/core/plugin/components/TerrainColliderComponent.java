package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import lombok.Getter;
import lombok.Setter;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Terrain collider entity mirrored from ChunkStore terrain source data.
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
    @Nullable
    private transient Ref<PhysicsStore> spaceRef;
    @Nonnull
    private String sourceKey = "";
    @Setter
    @Getter
    private int chunkX;
    @Setter
    @Getter
    private int sectionY;
    @Setter
    @Getter
    private int chunkZ;
    @Nonnull
    private String payloadResourceKey = "";
    @Setter
    @Getter
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
        this.spaceRef = null;
    }

    @Nullable
    public Ref<PhysicsStore> getSpaceRef() {
        return spaceRef;
    }

    public void setSpaceRef(@Nullable Ref<PhysicsStore> spaceRef) {
        this.spaceRef = spaceRef;
    }

    @Nonnull
    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(@Nonnull String sourceKey) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
    }

    @Nonnull
    public String getPayloadResourceKey() {
        return payloadResourceKey;
    }

    public void setPayloadResourceKey(@Nonnull String payloadResourceKey) {
        this.payloadResourceKey = Objects.requireNonNull(payloadResourceKey, "payloadResourceKey");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TerrainColliderComponent> getComponentType() {
        return PhysicsStoreTypes.terrainColliderComponentType();
    }

    @Nonnull
    @Override
    public TerrainColliderComponent clone() {
        TerrainColliderComponent copy = new TerrainColliderComponent(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadResourceKey,
            retained);
        copy.spaceRef = spaceRef;
        return copy;
    }
}
