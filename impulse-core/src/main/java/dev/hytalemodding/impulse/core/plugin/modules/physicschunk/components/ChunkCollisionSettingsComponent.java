package dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsComponentTypes;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Authored PhysicsChunk collision streaming settings for one PhysicsStore space entity.
 */
public class ChunkCollisionSettingsComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<ChunkCollisionSettingsComponent> CODEC = BuilderCodec.builder(
            ChunkCollisionSettingsComponent.class,
            ChunkCollisionSettingsComponent::new)
        .append(new KeyedCodec<>("Mode", new EnumCodec<>(PhysicsChunkCollisionMode.class), false),
            (component, value) -> component.mode = value != null
                ? value
                : PhysicsChunkCollisionMode.NONE,
            ChunkCollisionSettingsComponent::getMode)
        .add()
        .append(new KeyedCodec<>("NativeVoxelCollision", Codec.BOOLEAN, false),
            (component, value) -> component.nativeVoxelCollisionEnabled = value != null && value,
            ChunkCollisionSettingsComponent::isNativeVoxelCollisionEnabled)
        .add()
        .append(new KeyedCodec<>("EntityChunkBoundaryMode",
                new EnumCodec<>(EntityChunkBoundaryMode.class),
                false),
            (component, value) -> component.entityChunkBoundaryMode = value != null
                ? value
                : PhysicsChunkCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            ChunkCollisionSettingsComponent::getEntityChunkBoundaryMode)
        .add()
        .append(new KeyedCodec<>("Radius", Codec.INTEGER, false),
            (component, value) -> component.radius = value != null
                ? value
                : PhysicsChunkCollisionSettings.DEFAULT_RADIUS,
            ChunkCollisionSettingsComponent::getRadius)
        .add()
        .append(new KeyedCodec<>("BodyRadius", Codec.INTEGER, false),
            (component, value) -> component.bodyRadius = value != null
                ? value
                : PhysicsChunkCollisionSettings.DEFAULT_BODY_RADIUS,
            ChunkCollisionSettingsComponent::getBodyRadius)
        .add()
        .append(new KeyedCodec<>("TtlTicks", Codec.INTEGER, false),
            (component, value) -> component.ttlTicks = value != null
                ? value
                : PhysicsChunkCollisionSettings.DEFAULT_TTL_TICKS,
            ChunkCollisionSettingsComponent::getTtlTicks)
        .add()
        .build();

    @Nonnull
    private PhysicsChunkCollisionMode mode = PhysicsChunkCollisionMode.NONE;
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode =
        PhysicsChunkCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    private boolean nativeVoxelCollisionEnabled =
        PhysicsChunkCollisionSettings.DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED;
    private int radius = PhysicsChunkCollisionSettings.DEFAULT_RADIUS;
    private int bodyRadius = PhysicsChunkCollisionSettings.DEFAULT_BODY_RADIUS;
    private int ttlTicks = PhysicsChunkCollisionSettings.DEFAULT_TTL_TICKS;

    public ChunkCollisionSettingsComponent() {
    }

    public ChunkCollisionSettingsComponent(@Nonnull PhysicsChunkCollisionSettings settings) {
        this(settings.getMode(),
            settings.getEntityChunkBoundaryMode(),
            settings.isNativeVoxelCollisionEnabled(),
            settings.getRadius(),
            settings.getBodyRadius(),
            settings.getTtlTicks());
    }

    public ChunkCollisionSettingsComponent(@Nonnull PhysicsChunkCollisionMode mode,
        boolean nativeVoxelCollisionEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks) {
        this(mode,
            PhysicsChunkCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            nativeVoxelCollisionEnabled,
            radius,
            bodyRadius,
            ttlTicks);
    }

    public ChunkCollisionSettingsComponent(@Nonnull PhysicsChunkCollisionMode mode,
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
        boolean nativeVoxelCollisionEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.entityChunkBoundaryMode = Objects.requireNonNull(entityChunkBoundaryMode,
            "entityChunkBoundaryMode");
        this.nativeVoxelCollisionEnabled = nativeVoxelCollisionEnabled;
        this.radius = radius;
        this.bodyRadius = bodyRadius;
        this.ttlTicks = ttlTicks;
    }

    @Nonnull
    public PhysicsChunkCollisionMode getMode() {
        return mode;
    }

    public void setMode(@Nonnull PhysicsChunkCollisionMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Nonnull
    public EntityChunkBoundaryMode getEntityChunkBoundaryMode() {
        return entityChunkBoundaryMode;
    }

    public void setEntityChunkBoundaryMode(
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode) {
        this.entityChunkBoundaryMode = Objects.requireNonNull(entityChunkBoundaryMode,
            "entityChunkBoundaryMode");
    }

    public boolean isNativeVoxelCollisionEnabled() {
        return nativeVoxelCollisionEnabled;
    }

    public void setNativeVoxelCollisionEnabled(boolean nativeVoxelCollisionEnabled) {
        this.nativeVoxelCollisionEnabled = nativeVoxelCollisionEnabled;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public int getBodyRadius() {
        return bodyRadius;
    }

    public void setBodyRadius(int bodyRadius) {
        this.bodyRadius = bodyRadius;
    }

    public int getTtlTicks() {
        return ttlTicks;
    }

    public void setTtlTicks(int ttlTicks) {
        this.ttlTicks = ttlTicks;
    }

    public void copyTo(@Nonnull PhysicsChunkCollisionSettings settings) {
        settings.setMode(mode);
        settings.setEntityChunkBoundaryMode(entityChunkBoundaryMode);
        settings.setNativeVoxelCollisionEnabled(nativeVoxelCollisionEnabled);
        settings.setRadius(radius);
        settings.setBodyRadius(bodyRadius);
        settings.setTtlTicks(ttlTicks);
    }

    public boolean isDefault() {
        return mode == PhysicsChunkCollisionMode.NONE
            && entityChunkBoundaryMode
                == PhysicsChunkCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE
            && nativeVoxelCollisionEnabled
                == PhysicsChunkCollisionSettings.DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED
            && radius == PhysicsChunkCollisionSettings.DEFAULT_RADIUS
            && bodyRadius == PhysicsChunkCollisionSettings.DEFAULT_BODY_RADIUS
            && ttlTicks == PhysicsChunkCollisionSettings.DEFAULT_TTL_TICKS;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ChunkCollisionSettingsComponent> getComponentType() {
        return PhysicsComponentTypes.chunkCollisionSettingsComponentType();
    }

    @Nonnull
    @Override
    public ChunkCollisionSettingsComponent clone() {
        return new ChunkCollisionSettingsComponent(mode,
            entityChunkBoundaryMode,
            nativeVoxelCollisionEnabled,
            radius,
            bodyRadius,
            ttlTicks);
    }
}
