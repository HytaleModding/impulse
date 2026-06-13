package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Authored world-collision streaming settings for one PhysicsStore space row.
 */
public final class WorldCollisionComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<WorldCollisionComponent> CODEC = BuilderCodec.builder(
            WorldCollisionComponent.class,
            WorldCollisionComponent::new)
        .append(new KeyedCodec<>("Mode", new EnumCodec<>(WorldCollisionMode.class), false),
            (component, value) -> component.mode = value != null ? value : WorldCollisionMode.NONE,
            WorldCollisionComponent::getMode)
        .add()
        .append(new KeyedCodec<>("NativeVoxelTerrain", Codec.BOOLEAN, false),
            (component, value) -> component.nativeVoxelTerrainEnabled = value != null && value,
            WorldCollisionComponent::isNativeVoxelTerrainEnabled)
        .add()
        .append(new KeyedCodec<>("Radius", Codec.INTEGER, false),
            (component, value) -> component.radius = value != null
                ? value
                : PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_RADIUS,
            WorldCollisionComponent::getRadius)
        .add()
        .append(new KeyedCodec<>("BodyRadius", Codec.INTEGER, false),
            (component, value) -> component.bodyRadius = value != null
                ? value
                : PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS,
            WorldCollisionComponent::getBodyRadius)
        .add()
        .append(new KeyedCodec<>("TtlTicks", Codec.INTEGER, false),
            (component, value) -> component.ttlTicks = value != null
                ? value
                : PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_TTL_TICKS,
            WorldCollisionComponent::getTtlTicks)
        .add()
        .append(new KeyedCodec<>("TerrainFriction", Codec.FLOAT, false),
            (component, value) -> component.terrainFriction = value != null
                ? value
                : PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_FRICTION,
            WorldCollisionComponent::getTerrainFriction)
        .add()
        .append(new KeyedCodec<>("TerrainRestitution", Codec.FLOAT, false),
            (component, value) -> component.terrainRestitution = value != null
                ? value
                : PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_RESTITUTION,
            WorldCollisionComponent::getTerrainRestitution)
        .add()
        .build();

    @Nonnull
    private WorldCollisionMode mode = WorldCollisionMode.NONE;
    private boolean nativeVoxelTerrainEnabled =
        PhysicsWorldCollisionSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED;
    private int radius = PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_RADIUS;
    private int bodyRadius = PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS;
    private int ttlTicks = PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_TTL_TICKS;
    private float terrainFriction = PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_FRICTION;
    private float terrainRestitution = PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_RESTITUTION;

    public WorldCollisionComponent() {
    }

    public WorldCollisionComponent(@Nonnull WorldCollisionMode mode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks,
        float terrainFriction,
        float terrainRestitution) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.nativeVoxelTerrainEnabled = nativeVoxelTerrainEnabled;
        this.radius = radius;
        this.bodyRadius = bodyRadius;
        this.ttlTicks = ttlTicks;
        this.terrainFriction = terrainFriction;
        this.terrainRestitution = terrainRestitution;
    }

    @Nonnull
    public WorldCollisionMode getMode() {
        return mode;
    }

    public void setMode(@Nonnull WorldCollisionMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
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

    @Nonnull
    public static ComponentType<PhysicsStore, WorldCollisionComponent> getComponentType() {
        return PhysicsStoreTypes.worldCollisionComponentType();
    }

    @Nonnull
    @Override
    public WorldCollisionComponent clone() {
        return new WorldCollisionComponent(mode,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks,
            terrainFriction,
            terrainRestitution);
    }
}
