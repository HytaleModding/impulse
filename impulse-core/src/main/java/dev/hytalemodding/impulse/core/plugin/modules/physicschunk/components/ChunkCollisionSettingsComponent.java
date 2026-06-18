package dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsComponentTypes;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
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
        .append(new KeyedCodec<>("Mode", new EnumCodec<>(PhysicsChunkTerrainMode.class), false),
            (component, value) -> component.terrainMode = value != null
                ? value
                : PhysicsChunkTerrainMode.NONE,
            ChunkCollisionSettingsComponent::getTerrainMode)
        .add()
        .append(new KeyedCodec<>("NativeVoxelTerrain", Codec.BOOLEAN, false),
            (component, value) -> component.nativeVoxelTerrainEnabled = value != null && value,
            ChunkCollisionSettingsComponent::isNativeVoxelTerrainEnabled)
        .add()
        .append(new KeyedCodec<>("EntityChunkBoundaryMode",
                new EnumCodec<>(EntityChunkBoundaryMode.class),
                false),
            (component, value) -> component.entityChunkBoundaryMode = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            ChunkCollisionSettingsComponent::getEntityChunkBoundaryMode)
        .add()
        .append(new KeyedCodec<>("Radius", Codec.INTEGER, false),
            (component, value) -> component.radius = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RADIUS,
            ChunkCollisionSettingsComponent::getRadius)
        .add()
        .append(new KeyedCodec<>("BodyRadius", Codec.INTEGER, false),
            (component, value) -> component.bodyRadius = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_BODY_TERRAIN_RADIUS,
            ChunkCollisionSettingsComponent::getBodyRadius)
        .add()
        .append(new KeyedCodec<>("TtlTicks", Codec.INTEGER, false),
            (component, value) -> component.ttlTicks = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_TTL_TICKS,
            ChunkCollisionSettingsComponent::getTtlTicks)
        .add()
        .build();

    @Nonnull
    private PhysicsChunkTerrainMode terrainMode = PhysicsChunkTerrainMode.NONE;
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode =
        PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    private boolean nativeVoxelTerrainEnabled =
        PhysicsChunkTerrainSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED;
    private int radius = PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RADIUS;
    private int bodyRadius = PhysicsChunkTerrainSettings.DEFAULT_BODY_TERRAIN_RADIUS;
    private int ttlTicks = PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_TTL_TICKS;

    public ChunkCollisionSettingsComponent() {
    }

    public ChunkCollisionSettingsComponent(@Nonnull PhysicsChunkTerrainSettings settings) {
        this(settings.getTerrainMode(),
            settings.getEntityChunkBoundaryMode(),
            settings.isNativeVoxelTerrainEnabled(),
            settings.getTerrainRadius(),
            settings.getBodyTerrainRadius(),
            settings.getTerrainTtlTicks());
    }

    public ChunkCollisionSettingsComponent(@Nonnull PhysicsChunkTerrainMode terrainMode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks) {
        this(terrainMode,
            PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks);
    }

    public ChunkCollisionSettingsComponent(@Nonnull PhysicsChunkTerrainMode terrainMode,
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks) {
        this.terrainMode = Objects.requireNonNull(terrainMode, "terrainMode");
        this.entityChunkBoundaryMode = Objects.requireNonNull(entityChunkBoundaryMode,
            "entityChunkBoundaryMode");
        this.nativeVoxelTerrainEnabled = nativeVoxelTerrainEnabled;
        this.radius = radius;
        this.bodyRadius = bodyRadius;
        this.ttlTicks = ttlTicks;
    }

    @Nonnull
    public PhysicsChunkTerrainMode getTerrainMode() {
        return terrainMode;
    }

    public void setTerrainMode(@Nonnull PhysicsChunkTerrainMode terrainMode) {
        this.terrainMode = Objects.requireNonNull(terrainMode, "terrainMode");
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

    public boolean isNativeVoxelTerrainEnabled() {
        return nativeVoxelTerrainEnabled;
    }

    public void setNativeVoxelTerrainEnabled(boolean nativeVoxelTerrainEnabled) {
        this.nativeVoxelTerrainEnabled = nativeVoxelTerrainEnabled;
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

    public void copyTo(@Nonnull PhysicsSpaceSettings settings) {
        copyTo(settings.getPhysicsChunkTerrainSettings());
    }

    public void copyTo(@Nonnull PhysicsChunkTerrainSettings settings) {
        settings.setTerrainMode(terrainMode);
        settings.setEntityChunkBoundaryMode(entityChunkBoundaryMode);
        settings.setNativeVoxelTerrainEnabled(nativeVoxelTerrainEnabled);
        settings.setTerrainRadius(radius);
        settings.setBodyTerrainRadius(bodyRadius);
        settings.setTerrainTtlTicks(ttlTicks);
    }

    public boolean isDefault() {
        return terrainMode == PhysicsChunkTerrainMode.NONE
            && entityChunkBoundaryMode
                == PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE
            && nativeVoxelTerrainEnabled
                == PhysicsChunkTerrainSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED
            && radius == PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RADIUS
            && bodyRadius == PhysicsChunkTerrainSettings.DEFAULT_BODY_TERRAIN_RADIUS
            && ttlTicks == PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_TTL_TICKS;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ChunkCollisionSettingsComponent> getComponentType() {
        return PhysicsComponentTypes.chunkCollisionSettingsComponentType();
    }

    @Nonnull
    @Override
    public ChunkCollisionSettingsComponent clone() {
        return new ChunkCollisionSettingsComponent(terrainMode,
            entityChunkBoundaryMode,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks);
    }
}
