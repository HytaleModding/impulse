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
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsChunkTerrainSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Authored PhysicsChunk terrain streaming settings for one PhysicsStore space entity.
 */
public class PhysicsChunkTerrainComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<PhysicsChunkTerrainComponent> CODEC = BuilderCodec.builder(
            PhysicsChunkTerrainComponent.class,
            PhysicsChunkTerrainComponent::new)
        .append(new KeyedCodec<>("Mode", new EnumCodec<>(PhysicsChunkTerrainMode.class), false),
            (component, value) -> component.terrainMode = value != null
                ? value
                : PhysicsChunkTerrainMode.NONE,
            PhysicsChunkTerrainComponent::getTerrainMode)
        .add()
        .append(new KeyedCodec<>("NativeVoxelTerrain", Codec.BOOLEAN, false),
            (component, value) -> component.nativeVoxelTerrainEnabled = value != null && value,
            PhysicsChunkTerrainComponent::isNativeVoxelTerrainEnabled)
        .add()
        .append(new KeyedCodec<>("EntityChunkBoundaryMode",
                new EnumCodec<>(EntityChunkBoundaryMode.class),
                false),
            (component, value) -> component.entityChunkBoundaryMode = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            PhysicsChunkTerrainComponent::getEntityChunkBoundaryMode)
        .add()
        .append(new KeyedCodec<>("Radius", Codec.INTEGER, false),
            (component, value) -> component.radius = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RADIUS,
            PhysicsChunkTerrainComponent::getRadius)
        .add()
        .append(new KeyedCodec<>("BodyRadius", Codec.INTEGER, false),
            (component, value) -> component.bodyRadius = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_BODY_TERRAIN_RADIUS,
            PhysicsChunkTerrainComponent::getBodyRadius)
        .add()
        .append(new KeyedCodec<>("TtlTicks", Codec.INTEGER, false),
            (component, value) -> component.ttlTicks = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_TTL_TICKS,
            PhysicsChunkTerrainComponent::getTtlTicks)
        .add()
        .append(new KeyedCodec<>("TerrainFriction", Codec.FLOAT, false),
            (component, value) -> component.terrainFriction = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_FRICTION,
            PhysicsChunkTerrainComponent::getTerrainFriction)
        .add()
        .append(new KeyedCodec<>("TerrainRestitution", Codec.FLOAT, false),
            (component, value) -> component.terrainRestitution = value != null
                ? value
                : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RESTITUTION,
            PhysicsChunkTerrainComponent::getTerrainRestitution)
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
    private float terrainFriction = PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_FRICTION;
    private float terrainRestitution = PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RESTITUTION;

    public PhysicsChunkTerrainComponent() {
    }

    public PhysicsChunkTerrainComponent(@Nonnull PhysicsChunkTerrainSettings settings) {
        this(settings.getTerrainMode(),
            settings.getEntityChunkBoundaryMode(),
            settings.isNativeVoxelTerrainEnabled(),
            settings.getTerrainRadius(),
            settings.getBodyTerrainRadius(),
            settings.getTerrainTtlTicks(),
            settings.getTerrainFriction(),
            settings.getTerrainRestitution());
    }

    /**
     * @deprecated Use {@link #PhysicsChunkTerrainComponent(PhysicsChunkTerrainSettings)}.
     */
    @Deprecated(forRemoval = false)
    public PhysicsChunkTerrainComponent(@Nonnull PhysicsWorldCollisionSettings settings) {
        this((PhysicsChunkTerrainSettings) settings);
    }

    public PhysicsChunkTerrainComponent(@Nonnull PhysicsChunkTerrainMode terrainMode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks,
        float terrainFriction,
        float terrainRestitution) {
        this(terrainMode,
            PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks,
            terrainFriction,
            terrainRestitution);
    }

    public PhysicsChunkTerrainComponent(@Nonnull PhysicsChunkTerrainMode terrainMode,
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks,
        float terrainFriction,
        float terrainRestitution) {
        this.terrainMode = Objects.requireNonNull(terrainMode, "terrainMode");
        this.entityChunkBoundaryMode = Objects.requireNonNull(entityChunkBoundaryMode,
            "entityChunkBoundaryMode");
        this.nativeVoxelTerrainEnabled = nativeVoxelTerrainEnabled;
        this.radius = radius;
        this.bodyRadius = bodyRadius;
        this.ttlTicks = ttlTicks;
        this.terrainFriction = terrainFriction;
        this.terrainRestitution = terrainRestitution;
    }

    /**
     * @deprecated Use {@link #PhysicsChunkTerrainComponent(PhysicsChunkTerrainMode, boolean, int, int, int, float, float)}.
     */
    @Deprecated(forRemoval = false)
    public PhysicsChunkTerrainComponent(@Nonnull WorldCollisionMode mode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks,
        float terrainFriction,
        float terrainRestitution) {
        this(mode.toPhysicsChunkTerrainMode(),
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks,
            terrainFriction,
            terrainRestitution);
    }

    /**
     * @deprecated Use {@link #PhysicsChunkTerrainComponent(PhysicsChunkTerrainMode, EntityChunkBoundaryMode, boolean, int, int, int, float, float)}.
     */
    @Deprecated(forRemoval = false)
    public PhysicsChunkTerrainComponent(@Nonnull WorldCollisionMode mode,
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks,
        float terrainFriction,
        float terrainRestitution) {
        this(mode.toPhysicsChunkTerrainMode(),
            entityChunkBoundaryMode,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks,
            terrainFriction,
            terrainRestitution);
    }

    @Nonnull
    public PhysicsChunkTerrainMode getTerrainMode() {
        return terrainMode;
    }

    public void setTerrainMode(@Nonnull PhysicsChunkTerrainMode terrainMode) {
        this.terrainMode = Objects.requireNonNull(terrainMode, "terrainMode");
    }

    /**
     * @deprecated Use {@link #getTerrainMode()}.
     */
    @Deprecated(forRemoval = false)
    @Nonnull
    public WorldCollisionMode getMode() {
        return terrainMode.toWorldCollisionMode();
    }

    /**
     * @deprecated Use {@link #setTerrainMode(PhysicsChunkTerrainMode)}.
     */
    @Deprecated(forRemoval = false)
    public void setMode(@Nonnull WorldCollisionMode mode) {
        setTerrainMode(mode.toPhysicsChunkTerrainMode());
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

    public float getTerrainFriction() {
        return terrainFriction;
    }

    public void setTerrainFriction(float terrainFriction) {
        this.terrainFriction = terrainFriction;
    }

    public float getTerrainRestitution() {
        return terrainRestitution;
    }

    public void setTerrainRestitution(float terrainRestitution) {
        this.terrainRestitution = terrainRestitution;
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
        settings.setTerrainMaterial(terrainFriction, terrainRestitution);
    }

    /**
     * @deprecated Use {@link #copyTo(PhysicsChunkTerrainSettings)}.
     */
    @Deprecated(forRemoval = false)
    public void copyTo(@Nonnull PhysicsWorldCollisionSettings settings) {
        copyTo((PhysicsChunkTerrainSettings) settings);
    }

    @Nonnull
    public static ComponentType<PhysicsStore, PhysicsChunkTerrainComponent> getComponentType() {
        return PhysicsComponentTypes.physicsChunkTerrainComponentType();
    }

    @Nonnull
    @Override
    public PhysicsChunkTerrainComponent clone() {
        return new PhysicsChunkTerrainComponent(terrainMode,
            entityChunkBoundaryMode,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks,
            terrainFriction,
            terrainRestitution);
    }
}
