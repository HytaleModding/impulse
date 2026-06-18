package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.PhysicsChunkTerrainComponent;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

@SuppressWarnings("deprecation")
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
                (dto, value) -> dto.terrainMode = value != null
                    ? value.toPhysicsChunkTerrainMode()
                    : PhysicsChunkTerrainMode.NONE,
                PersistentSpaceDto::getPersistedWorldCollisionMode)
            .add()
            .append(new KeyedCodec<>("EntityChunkBoundaryMode",
                    new EnumCodec<>(EntityChunkBoundaryMode.class),
                    false),
                (dto, value) -> dto.entityChunkBoundaryMode = value != null
                    ? value
                    : PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
                PersistentSpaceDto::getEntityChunkBoundaryMode)
            .add()
            .append(new KeyedCodec<>("NativeVoxelTerrain", Codec.BOOLEAN, false),
                (dto, value) -> dto.nativeVoxelTerrainEnabled = value != null && value,
                PersistentSpaceDto::isNativeVoxelTerrainEnabled)
            .add()
            .append(new KeyedCodec<>("WorldCollisionRadius", Codec.INTEGER, false),
                (dto, value) -> dto.terrainRadius = value != null
                    ? value
                    : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RADIUS,
                PersistentSpaceDto::getTerrainRadius)
            .add()
            .append(new KeyedCodec<>("WorldCollisionBodyRadius", Codec.INTEGER, false),
                (dto, value) -> dto.bodyTerrainRadius = value != null
                    ? value
                    : PhysicsChunkTerrainSettings.DEFAULT_BODY_TERRAIN_RADIUS,
                PersistentSpaceDto::getBodyTerrainRadius)
            .add()
            .append(new KeyedCodec<>("WorldCollisionTtlTicks", Codec.INTEGER, false),
                (dto, value) -> dto.terrainTtlTicks = value != null
                    ? value
                    : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_TTL_TICKS,
                PersistentSpaceDto::getTerrainTtlTicks)
            .add()
            .append(new KeyedCodec<>("TerrainFriction", Codec.FLOAT, false),
                (dto, value) -> dto.terrainFriction = value != null
                    ? value
                    : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_FRICTION,
                PersistentSpaceDto::getTerrainFriction)
            .add()
            .append(new KeyedCodec<>("TerrainRestitution", Codec.FLOAT, false),
                (dto, value) -> dto.terrainRestitution = value != null
                    ? value
                    : PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RESTITUTION,
                PersistentSpaceDto::getTerrainRestitution)
            .add()
            .append(new KeyedCodec<>("SolverSettings", SolverSettingsComponent.CODEC, false),
                (dto, value) -> dto.solverSettings = value != null
                    ? value.clone()
                    : new SolverSettingsComponent(),
                PersistentSpaceDto::getSolverSettings)
            .add()
            .append(new KeyedCodec<>("VisualSyncSettings", VisualSyncSettingsComponent.CODEC, false),
                (dto, value) -> dto.visualSyncSettings = value != null
                    ? value.clone()
                    : new VisualSyncSettingsComponent(),
                PersistentSpaceDto::getVisualSyncSettings)
            .add()
            .append(new KeyedCodec<>("VisualMaterializationSettings",
                    VisualMaterializationSettingsComponent.CODEC,
                    false),
                (dto, value) -> dto.visualMaterializationSettings = value != null
                    ? value.clone()
                    : new VisualMaterializationSettingsComponent(),
                PersistentSpaceDto::getVisualMaterializationSettings)
            .add()
            .append(new KeyedCodec<>("CollisionLodSettings",
                    CollisionLodSettingsComponent.CODEC,
                    false),
                (dto, value) -> dto.collisionLodSettings = value != null
                    ? value.clone()
                    : new CollisionLodSettingsComponent(),
                PersistentSpaceDto::getCollisionLodSettings)
            .add()
            .append(new KeyedCodec<>("ExtensionSettings",
                    ExtensionSettingsComponent.CODEC,
                    false),
                (dto, value) -> dto.extensionSettings = value != null
                    ? value.clone()
                    : new ExtensionSettingsComponent(),
                PersistentSpaceDto::getExtensionSettings)
            .add()
            .build();

    @Nonnull
    private UUID spaceUuid = new UUID(0L, 0L);
    @Nonnull
    private String backendId = "";
    @Nonnull
    private final Vector3f gravity = new Vector3f(0.0f, -9.81f, 0.0f);
    @Nonnull
    private PhysicsChunkTerrainMode terrainMode = PhysicsChunkTerrainMode.NONE;
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode =
        PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    private boolean nativeVoxelTerrainEnabled =
        PhysicsChunkTerrainSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED;
    private int terrainRadius =
        PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RADIUS;
    private int bodyTerrainRadius =
        PhysicsChunkTerrainSettings.DEFAULT_BODY_TERRAIN_RADIUS;
    private int terrainTtlTicks =
        PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_TTL_TICKS;
    private float terrainFriction = PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_FRICTION;
    private float terrainRestitution = PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RESTITUTION;
    @Nonnull
    private SolverSettingsComponent solverSettings = new SolverSettingsComponent();
    @Nonnull
    private VisualSyncSettingsComponent visualSyncSettings = new VisualSyncSettingsComponent();
    @Nonnull
    private VisualMaterializationSettingsComponent visualMaterializationSettings =
        new VisualMaterializationSettingsComponent();
    @Nonnull
    private CollisionLodSettingsComponent collisionLodSettings =
        new CollisionLodSettingsComponent();
    @Nonnull
    private ExtensionSettingsComponent extensionSettings = new ExtensionSettingsComponent();

    public PersistentSpaceDto() {
    }

    public PersistentSpaceDto(@Nonnull UUID spaceUuid,
        @Nonnull String backendId,
        @Nonnull Vector3f gravity) {
        this(spaceUuid,
            backendId,
            gravity,
            PhysicsChunkTerrainMode.NONE,
            PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            PhysicsChunkTerrainSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED,
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RADIUS,
            PhysicsChunkTerrainSettings.DEFAULT_BODY_TERRAIN_RADIUS,
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_TTL_TICKS,
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_FRICTION,
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RESTITUTION,
            new SolverSettingsComponent(),
            new VisualSyncSettingsComponent(),
            new VisualMaterializationSettingsComponent(),
            new CollisionLodSettingsComponent(),
            new ExtensionSettingsComponent());
    }

    public PersistentSpaceDto(@Nonnull UUID spaceUuid,
        @Nonnull String backendId,
        @Nonnull Vector3f gravity,
        @Nonnull PhysicsChunkTerrainMode terrainMode,
        boolean nativeVoxelTerrainEnabled,
        int terrainRadius,
        int bodyTerrainRadius,
        int terrainTtlTicks,
        float terrainFriction,
        float terrainRestitution) {
        this(spaceUuid,
            backendId,
            gravity,
            terrainMode,
            PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            nativeVoxelTerrainEnabled,
            terrainRadius,
            bodyTerrainRadius,
            terrainTtlTicks,
            terrainFriction,
            terrainRestitution,
            new SolverSettingsComponent(),
            new VisualSyncSettingsComponent(),
            new VisualMaterializationSettingsComponent(),
            new CollisionLodSettingsComponent(),
            new ExtensionSettingsComponent());
    }

    public PersistentSpaceDto(@Nonnull UUID spaceUuid,
        @Nonnull String backendId,
        @Nonnull Vector3f gravity,
        @Nonnull PhysicsChunkTerrainMode terrainMode,
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
        boolean nativeVoxelTerrainEnabled,
        int terrainRadius,
        int bodyTerrainRadius,
        int terrainTtlTicks,
        float terrainFriction,
        float terrainRestitution,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.gravity.set(Objects.requireNonNull(gravity, "gravity"));
        this.terrainMode = Objects.requireNonNull(terrainMode, "terrainMode");
        this.entityChunkBoundaryMode = Objects.requireNonNull(entityChunkBoundaryMode,
            "entityChunkBoundaryMode");
        this.nativeVoxelTerrainEnabled = nativeVoxelTerrainEnabled;
        this.terrainRadius = terrainRadius;
        this.bodyTerrainRadius = bodyTerrainRadius;
        this.terrainTtlTicks = terrainTtlTicks;
        this.terrainFriction = terrainFriction;
        this.terrainRestitution = terrainRestitution;
        this.solverSettings = Objects.requireNonNull(solverSettings, "solverSettings").clone();
        this.visualSyncSettings = Objects.requireNonNull(visualSyncSettings,
            "visualSyncSettings").clone();
        this.visualMaterializationSettings = Objects.requireNonNull(visualMaterializationSettings,
            "visualMaterializationSettings").clone();
        this.collisionLodSettings = Objects.requireNonNull(collisionLodSettings,
            "collisionLodSettings").clone();
        this.extensionSettings = Objects.requireNonNull(extensionSettings,
            "extensionSettings").clone();
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
    public PhysicsChunkTerrainMode getTerrainMode() {
        return terrainMode;
    }

    @Nonnull
    public EntityChunkBoundaryMode getEntityChunkBoundaryMode() {
        return entityChunkBoundaryMode;
    }

    public boolean isNativeVoxelTerrainEnabled() {
        return nativeVoxelTerrainEnabled;
    }

    public int getTerrainRadius() {
        return terrainRadius;
    }

    public int getBodyTerrainRadius() {
        return bodyTerrainRadius;
    }

    public int getTerrainTtlTicks() {
        return terrainTtlTicks;
    }

    public float getTerrainFriction() {
        return terrainFriction;
    }

    public float getTerrainRestitution() {
        return terrainRestitution;
    }

    @Nonnull
    public PhysicsChunkTerrainComponent getPhysicsChunkTerrain() {
        return new PhysicsChunkTerrainComponent(getTerrainMode(),
            entityChunkBoundaryMode,
            nativeVoxelTerrainEnabled,
            terrainRadius,
            bodyTerrainRadius,
            terrainTtlTicks,
            terrainFriction,
            terrainRestitution);
    }

    @Nonnull
    public SolverSettingsComponent getSolverSettings() {
        return solverSettings.clone();
    }

    @Nonnull
    public VisualSyncSettingsComponent getVisualSyncSettings() {
        return visualSyncSettings.clone();
    }

    @Nonnull
    public VisualMaterializationSettingsComponent getVisualMaterializationSettings() {
        return visualMaterializationSettings.clone();
    }

    @Nonnull
    public CollisionLodSettingsComponent getCollisionLodSettings() {
        return collisionLodSettings.clone();
    }

    @Nonnull
    public ExtensionSettingsComponent getExtensionSettings() {
        return extensionSettings.clone();
    }

    @Nonnull
    public PhysicsSpaceSettings toSettings() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        getPhysicsChunkTerrain().copyTo(settings);
        solverSettings.copyTo(settings);
        visualSyncSettings.copyTo(settings);
        visualMaterializationSettings.copyTo(settings);
        collisionLodSettings.copyTo(settings);
        extensionSettings.copyTo(settings);
        return settings;
    }

    @Nonnull
    public PersistentSpaceDto copy() {
        return new PersistentSpaceDto(spaceUuid,
            backendId,
            gravity,
            terrainMode,
            entityChunkBoundaryMode,
            nativeVoxelTerrainEnabled,
            terrainRadius,
            bodyTerrainRadius,
            terrainTtlTicks,
            terrainFriction,
            terrainRestitution,
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
    }

    @Nonnull
    private WorldCollisionMode getPersistedWorldCollisionMode() {
        return terrainMode.toWorldCollisionMode();
    }
}
