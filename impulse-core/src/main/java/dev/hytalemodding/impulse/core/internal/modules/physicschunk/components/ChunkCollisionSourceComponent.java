package dev.hytalemodding.impulse.core.internal.modules.physicschunk.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import lombok.Getter;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Internal PhysicsChunk source metadata for generated chunk collision body rows.
 */
public final class ChunkCollisionSourceComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<ChunkCollisionSourceComponent> CODEC = BuilderCodec.builder(
            ChunkCollisionSourceComponent.class,
            ChunkCollisionSourceComponent::new)
        .append(new KeyedCodec<>("SourceKey", Codec.STRING, false),
            (component, value) -> component.sourceKey = value != null ? value : "",
            ChunkCollisionSourceComponent::getSourceKey)
        .add()
        .append(new KeyedCodec<>("ChunkX", Codec.INTEGER, false),
            (component, value) -> component.chunkX = value != null ? value : 0,
            ChunkCollisionSourceComponent::getChunkX)
        .add()
        .append(new KeyedCodec<>("SectionY", Codec.INTEGER, false),
            (component, value) -> component.sectionY = value != null ? value : 0,
            ChunkCollisionSourceComponent::getSectionY)
        .add()
        .append(new KeyedCodec<>("ChunkZ", Codec.INTEGER, false),
            (component, value) -> component.chunkZ = value != null ? value : 0,
            ChunkCollisionSourceComponent::getChunkZ)
        .add()
        .append(new KeyedCodec<>("PayloadResourceKey", Codec.STRING, false),
            (component, value) -> component.payloadResourceKey = value != null ? value : "",
            ChunkCollisionSourceComponent::getPayloadResourceKey)
        .add()
        .append(new KeyedCodec<>("PartKind", new EnumCodec<>(PartKind.class), false),
            (component, value) -> component.partKind = value != null
                ? value
                : PartKind.BOX,
            ChunkCollisionSourceComponent::getPartKind)
        .add()
        .append(new KeyedCodec<>("PartIndex", Codec.INTEGER, false),
            (component, value) -> component.partIndex = value != null ? value : 0,
            ChunkCollisionSourceComponent::getPartIndex)
        .add()
        .build();

    @Nonnull
    private String sourceKey = "";
    @Getter
    private int chunkX;
    @Getter
    private int sectionY;
    @Getter
    private int chunkZ;
    @Nonnull
    private String payloadResourceKey = "";
    @Nonnull
    private PartKind partKind = PartKind.BOX;
    @Getter
    private int partIndex;

    public ChunkCollisionSourceComponent() {
    }

    public ChunkCollisionSourceComponent(@Nonnull String sourceKey,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nonnull String payloadResourceKey,
        @Nonnull PartKind partKind,
        int partIndex) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        this.chunkX = chunkX;
        this.sectionY = sectionY;
        this.chunkZ = chunkZ;
        this.payloadResourceKey = Objects.requireNonNull(payloadResourceKey,
            "payloadResourceKey");
        this.partKind = Objects.requireNonNull(partKind, "partKind");
        this.partIndex = partIndex;
    }

    @Nonnull
    public String getSourceKey() {
        return sourceKey;
    }

    @Nonnull
    public String getPayloadResourceKey() {
        return payloadResourceKey;
    }

    @Nonnull
    public PartKind getPartKind() {
        return partKind;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ChunkCollisionSourceComponent> getComponentType() {
        return PhysicsComponentTypeRegistry.chunkCollisionSourceComponentType();
    }

    @Nonnull
    @Override
    public ChunkCollisionSourceComponent clone() {
        return new ChunkCollisionSourceComponent(sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadResourceKey,
            partKind,
            partIndex);
    }

    public enum PartKind {
        BOX,
        DETAIL_BOX,
        NATIVE_VOXELS
    }
}
