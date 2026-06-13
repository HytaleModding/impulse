package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

public final class PersistentSpaceDto {

    @Nonnull
    public static final BuilderCodec<PersistentSpaceDto> CODEC =
        BuilderCodec.builder(PersistentSpaceDto.class, PersistentSpaceDto::new)
            .append(new KeyedCodec<>("SpaceUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.spaceUuid = value,
                PersistentSpaceDto::getSpaceUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("BackendId", Codec.STRING),
                (dto, value) -> dto.backendId = value,
                PersistentSpaceDto::getBackendId)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Gravity", Vector3fUtil.CODEC),
                (dto, value) -> dto.gravity.set(value),
                PersistentSpaceDto::getGravity)
            .addValidator(Validators.nonNull())
            .addValidator(PhysicsStorePersistenceValidation.finiteVector(
                "Persisted PhysicsStore space gravity must be finite"))
            .add()
            .append(new KeyedCodec<>("WorldCollisionMode",
                    new EnumCodec<>(WorldCollisionMode.class),
                    false),
                (dto, value) -> dto.worldCollisionMode = value != null
                    ? value
                    : WorldCollisionMode.NONE,
                PersistentSpaceDto::getWorldCollisionMode)
            .add()
            .append(new KeyedCodec<>("NativeVoxelTerrain", Codec.BOOLEAN, false),
                (dto, value) -> dto.nativeVoxelTerrainEnabled = value != null && value,
                PersistentSpaceDto::isNativeVoxelTerrainEnabled)
            .add()
            .append(new KeyedCodec<>("WorldCollisionRadius", Codec.INTEGER, false),
                (dto, value) -> dto.worldCollisionRadius = value != null
                    ? value
                    : PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_RADIUS,
                PersistentSpaceDto::getWorldCollisionRadius)
            .add()
            .append(new KeyedCodec<>("WorldCollisionBodyRadius", Codec.INTEGER, false),
                (dto, value) -> dto.worldCollisionBodyRadius = value != null
                    ? value
                    : PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS,
                PersistentSpaceDto::getWorldCollisionBodyRadius)
            .add()
            .append(new KeyedCodec<>("WorldCollisionTtlTicks", Codec.INTEGER, false),
                (dto, value) -> dto.worldCollisionTtlTicks = value != null
                    ? value
                    : PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_TTL_TICKS,
                PersistentSpaceDto::getWorldCollisionTtlTicks)
            .add()
            .append(new KeyedCodec<>("TerrainFriction", Codec.FLOAT, false),
                (dto, value) -> dto.terrainFriction = value != null
                    ? value
                    : PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_FRICTION,
                PersistentSpaceDto::getTerrainFriction)
            .add()
            .append(new KeyedCodec<>("TerrainRestitution", Codec.FLOAT, false),
                (dto, value) -> dto.terrainRestitution = value != null
                    ? value
                    : PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_RESTITUTION,
                PersistentSpaceDto::getTerrainRestitution)
            .add()
            .build();

    @Nonnull
    private UUID spaceUuid = new UUID(0L, 0L);
    @Nonnull
    private String backendId = "";
    @Nonnull
    private final Vector3f gravity = new Vector3f(0.0f, -9.81f, 0.0f);
    @Nonnull
    private WorldCollisionMode worldCollisionMode = WorldCollisionMode.NONE;
    private boolean nativeVoxelTerrainEnabled =
        PhysicsWorldCollisionSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED;
    private int worldCollisionRadius =
        PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_RADIUS;
    private int worldCollisionBodyRadius =
        PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS;
    private int worldCollisionTtlTicks =
        PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_TTL_TICKS;
    private float terrainFriction = PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_FRICTION;
    private float terrainRestitution = PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_RESTITUTION;

    public PersistentSpaceDto() {
    }

    public PersistentSpaceDto(@Nonnull UUID spaceUuid,
        @Nonnull String backendId,
        @Nonnull Vector3f gravity) {
        this(spaceUuid,
            backendId,
            gravity,
            WorldCollisionMode.NONE,
            PhysicsWorldCollisionSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED,
            PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_RADIUS,
            PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS,
            PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_TTL_TICKS,
            PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_FRICTION,
            PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_RESTITUTION);
    }

    public PersistentSpaceDto(@Nonnull UUID spaceUuid,
        @Nonnull String backendId,
        @Nonnull Vector3f gravity,
        @Nonnull WorldCollisionMode worldCollisionMode,
        boolean nativeVoxelTerrainEnabled,
        int worldCollisionRadius,
        int worldCollisionBodyRadius,
        int worldCollisionTtlTicks,
        float terrainFriction,
        float terrainRestitution) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.gravity.set(Objects.requireNonNull(gravity, "gravity"));
        this.worldCollisionMode = Objects.requireNonNull(worldCollisionMode,
            "worldCollisionMode");
        this.nativeVoxelTerrainEnabled = nativeVoxelTerrainEnabled;
        this.worldCollisionRadius = worldCollisionRadius;
        this.worldCollisionBodyRadius = worldCollisionBodyRadius;
        this.worldCollisionTtlTicks = worldCollisionTtlTicks;
        this.terrainFriction = terrainFriction;
        this.terrainRestitution = terrainRestitution;
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    @Nonnull
    public String getBackendId() {
        return backendId;
    }

    @Nonnull
    public Vector3f getGravity() {
        return new Vector3f(gravity);
    }

    @Nonnull
    public WorldCollisionMode getWorldCollisionMode() {
        return worldCollisionMode;
    }

    public boolean isNativeVoxelTerrainEnabled() {
        return nativeVoxelTerrainEnabled;
    }

    public int getWorldCollisionRadius() {
        return worldCollisionRadius;
    }

    public int getWorldCollisionBodyRadius() {
        return worldCollisionBodyRadius;
    }

    public int getWorldCollisionTtlTicks() {
        return worldCollisionTtlTicks;
    }

    public float getTerrainFriction() {
        return terrainFriction;
    }

    public float getTerrainRestitution() {
        return terrainRestitution;
    }

    @Nonnull
    public PersistentSpaceDto copy() {
        return new PersistentSpaceDto(spaceUuid,
            backendId,
            gravity,
            worldCollisionMode,
            nativeVoxelTerrainEnabled,
            worldCollisionRadius,
            worldCollisionBodyRadius,
            worldCollisionTtlTicks,
            terrainFriction,
            terrainRestitution);
    }
}
