package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionDefaults;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
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
            .append(new KeyedCodec<>("PhysicsChunkTerrainMode",
                    new EnumCodec<>(PhysicsChunkCollisionMode.class),
                    false),
                (dto, value) -> dto.terrainMode = value != null
                    ? value
                    : PhysicsChunkCollisionMode.NONE,
                PersistentSpaceDto::getMode)
            .add()
            .append(new KeyedCodec<>("EntityChunkBoundaryMode",
                    new EnumCodec<>(EntityChunkBoundaryMode.class),
                    false),
                (dto, value) -> dto.entityChunkBoundaryMode = value != null
                    ? value
                    : PhysicsChunkCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
                PersistentSpaceDto::getEntityChunkBoundaryMode)
            .add()
            .append(new KeyedCodec<>("NativeVoxelCollision", Codec.BOOLEAN, false),
                (dto, value) -> dto.nativeVoxelCollisionEnabled = value != null && value,
                PersistentSpaceDto::isNativeVoxelCollisionEnabled)
            .add()
            .append(new KeyedCodec<>("ChunkCollisionRadius", Codec.INTEGER, false),
                (dto, value) -> dto.terrainRadius = value != null
                    ? value
                    : PhysicsChunkCollisionSettings.DEFAULT_RADIUS,
                PersistentSpaceDto::getRadius)
            .add()
            .append(new KeyedCodec<>("BodyChunkCollisionRadius", Codec.INTEGER, false),
                (dto, value) -> dto.bodyTerrainRadius = value != null
                    ? value
                    : PhysicsChunkCollisionSettings.DEFAULT_BODY_RADIUS,
                PersistentSpaceDto::getBodyRadius)
            .add()
            .append(new KeyedCodec<>("ChunkCollisionTtlTicks", Codec.INTEGER, false),
                (dto, value) -> dto.terrainTtlTicks = value != null
                    ? value
                    : PhysicsChunkCollisionSettings.DEFAULT_TTL_TICKS,
                PersistentSpaceDto::getTtlTicks)
            .add()
            .append(new KeyedCodec<>("ChunkCollisionFriction", Codec.FLOAT, false),
                (dto, value) -> dto.chunkCollisionFriction = value != null
                    ? value
                    : PhysicsChunkCollisionDefaults.FRICTION,
                PersistentSpaceDto::getChunkCollisionFriction)
            .add()
            .append(new KeyedCodec<>("ChunkCollisionRestitution", Codec.FLOAT, false),
                (dto, value) -> dto.chunkCollisionRestitution = value != null
                    ? value
                    : PhysicsChunkCollisionDefaults.RESTITUTION,
                PersistentSpaceDto::getChunkCollisionRestitution)
            .add()
            .append(new KeyedCodec<>("ChunkCollisionFilter",
                    CollisionFilterComponent.CODEC,
                    false),
                (dto, value) -> dto.chunkCollisionFilter = value != null
                    ? value.clone()
                    : defaultChunkCollisionFilter(),
                PersistentSpaceDto::getChunkCollisionFilter)
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
    private PhysicsChunkCollisionMode terrainMode = PhysicsChunkCollisionMode.NONE;
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode =
        PhysicsChunkCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    private boolean nativeVoxelCollisionEnabled =
        PhysicsChunkCollisionSettings.DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED;
    private int terrainRadius =
        PhysicsChunkCollisionSettings.DEFAULT_RADIUS;
    private int bodyTerrainRadius =
        PhysicsChunkCollisionSettings.DEFAULT_BODY_RADIUS;
    private int terrainTtlTicks =
        PhysicsChunkCollisionSettings.DEFAULT_TTL_TICKS;
    private float chunkCollisionFriction = PhysicsChunkCollisionDefaults.FRICTION;
    private float chunkCollisionRestitution =
        PhysicsChunkCollisionDefaults.RESTITUTION;
    @Nonnull
    private CollisionFilterComponent chunkCollisionFilter = defaultChunkCollisionFilter();
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
            PhysicsChunkCollisionMode.NONE,
            PhysicsChunkCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            PhysicsChunkCollisionSettings.DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED,
            PhysicsChunkCollisionSettings.DEFAULT_RADIUS,
            PhysicsChunkCollisionSettings.DEFAULT_BODY_RADIUS,
            PhysicsChunkCollisionSettings.DEFAULT_TTL_TICKS,
            PhysicsChunkCollisionDefaults.FRICTION,
            PhysicsChunkCollisionDefaults.RESTITUTION,
            PhysicsChunkCollisionDefaults.COLLISION_GROUP,
            PhysicsChunkCollisionDefaults.COLLISION_MASK,
            new SolverSettingsComponent(),
            new VisualSyncSettingsComponent(),
            new VisualMaterializationSettingsComponent(),
            new CollisionLodSettingsComponent(),
            new ExtensionSettingsComponent());
    }

    public PersistentSpaceDto(@Nonnull UUID spaceUuid,
        @Nonnull String backendId,
        @Nonnull Vector3f gravity,
        @Nonnull PhysicsChunkCollisionMode terrainMode,
        boolean nativeVoxelCollisionEnabled,
        int terrainRadius,
        int bodyTerrainRadius,
        int terrainTtlTicks,
        float chunkCollisionFriction,
        float chunkCollisionRestitution) {
        this(spaceUuid,
            backendId,
            gravity,
            terrainMode,
            PhysicsChunkCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            nativeVoxelCollisionEnabled,
            terrainRadius,
            bodyTerrainRadius,
            terrainTtlTicks,
            chunkCollisionFriction,
            chunkCollisionRestitution,
            PhysicsChunkCollisionDefaults.COLLISION_GROUP,
            PhysicsChunkCollisionDefaults.COLLISION_MASK,
            new SolverSettingsComponent(),
            new VisualSyncSettingsComponent(),
            new VisualMaterializationSettingsComponent(),
            new CollisionLodSettingsComponent(),
            new ExtensionSettingsComponent());
    }

    public PersistentSpaceDto(@Nonnull UUID spaceUuid,
        @Nonnull String backendId,
        @Nonnull Vector3f gravity,
        @Nonnull PhysicsChunkCollisionMode terrainMode,
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
        boolean nativeVoxelCollisionEnabled,
        int terrainRadius,
        int bodyTerrainRadius,
        int terrainTtlTicks,
        float chunkCollisionFriction,
        float chunkCollisionRestitution,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        this(spaceUuid,
            backendId,
            gravity,
            terrainMode,
            entityChunkBoundaryMode,
            nativeVoxelCollisionEnabled,
            terrainRadius,
            bodyTerrainRadius,
            terrainTtlTicks,
            chunkCollisionFriction,
            chunkCollisionRestitution,
            PhysicsChunkCollisionDefaults.COLLISION_GROUP,
            PhysicsChunkCollisionDefaults.COLLISION_MASK,
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
    }

    public PersistentSpaceDto(@Nonnull UUID spaceUuid,
        @Nonnull String backendId,
        @Nonnull Vector3f gravity,
        @Nonnull PhysicsChunkCollisionMode terrainMode,
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
        boolean nativeVoxelCollisionEnabled,
        int terrainRadius,
        int bodyTerrainRadius,
        int terrainTtlTicks,
        float chunkCollisionFriction,
        float chunkCollisionRestitution,
        int chunkCollisionGroup,
        int chunkCollisionMask,
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
        this.nativeVoxelCollisionEnabled = nativeVoxelCollisionEnabled;
        this.terrainRadius = terrainRadius;
        this.bodyTerrainRadius = bodyTerrainRadius;
        this.terrainTtlTicks = terrainTtlTicks;
        this.chunkCollisionFriction = chunkCollisionFriction;
        this.chunkCollisionRestitution = chunkCollisionRestitution;
        this.chunkCollisionFilter = new CollisionFilterComponent(chunkCollisionGroup,
            chunkCollisionMask);
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
    public PhysicsChunkCollisionMode getMode() {
        return terrainMode;
    }

    @Nonnull
    public EntityChunkBoundaryMode getEntityChunkBoundaryMode() {
        return entityChunkBoundaryMode;
    }

    public boolean isNativeVoxelCollisionEnabled() {
        return nativeVoxelCollisionEnabled;
    }

    public int getRadius() {
        return terrainRadius;
    }

    public int getBodyRadius() {
        return bodyTerrainRadius;
    }

    public int getTtlTicks() {
        return terrainTtlTicks;
    }

    public float getChunkCollisionFriction() {
        return chunkCollisionFriction;
    }

    public float getChunkCollisionRestitution() {
        return chunkCollisionRestitution;
    }

    public int getChunkCollisionGroup() {
        return chunkCollisionFilter.getCollisionGroup();
    }

    public int getChunkCollisionMask() {
        return chunkCollisionFilter.getCollisionMask();
    }

    @Nonnull
    public ChunkCollisionSettingsComponent getChunkCollisionSettings() {
        return new ChunkCollisionSettingsComponent(getMode(),
            entityChunkBoundaryMode,
            nativeVoxelCollisionEnabled,
            terrainRadius,
            bodyTerrainRadius,
            terrainTtlTicks);
    }

    @Nonnull
    public MaterialComponent getChunkCollisionMaterial() {
        return new MaterialComponent(chunkCollisionFriction, chunkCollisionRestitution);
    }

    public boolean isDefaultChunkCollisionMaterial() {
        return Float.compare(chunkCollisionFriction,
            PhysicsChunkCollisionDefaults.FRICTION) == 0
            && Float.compare(chunkCollisionRestitution,
                PhysicsChunkCollisionDefaults.RESTITUTION) == 0;
    }

    @Nonnull
    public CollisionFilterComponent getChunkCollisionFilter() {
        return chunkCollisionFilter.clone();
    }

    public boolean isDefaultChunkCollisionFilter() {
        return getChunkCollisionGroup() == PhysicsChunkCollisionDefaults.COLLISION_GROUP
            && getChunkCollisionMask() == PhysicsChunkCollisionDefaults.COLLISION_MASK;
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
        getChunkCollisionSettings().copyTo(settings);
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
            nativeVoxelCollisionEnabled,
            terrainRadius,
            bodyTerrainRadius,
            terrainTtlTicks,
            chunkCollisionFriction,
            chunkCollisionRestitution,
            getChunkCollisionGroup(),
            getChunkCollisionMask(),
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
    }

    @Nonnull
    private static CollisionFilterComponent defaultChunkCollisionFilter() {
        return new CollisionFilterComponent(PhysicsChunkCollisionDefaults.COLLISION_GROUP,
            PhysicsChunkCollisionDefaults.COLLISION_MASK);
    }
}
