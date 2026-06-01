package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsSpaceBootstrapSystem;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Codec-backed definition of one physics space for the persistence layer.
 *
 * <p>Captures the space identity, backend choice, gravity, and world-collision
 * settings so that {@link PersistentPhysicsSpaceBootstrapSystem} can recreate
 * the runtime physics space after a
 * world load or manual snapshot restore.</p>
 */
@Getter
public class PersistentPhysicsSpaceState {

    private static final PersistentPhysicsExtensionSettingState[] EMPTY_EXTENSION_SETTINGS =
        new PersistentPhysicsExtensionSettingState[0];

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsSpaceState> CODEC = BuilderCodec.builder(
            PersistentPhysicsSpaceState.class,
            PersistentPhysicsSpaceState::new)
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER),
            (state, value) -> state.spaceId = value,
            PersistentPhysicsSpaceState::getSpaceId)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("BackendId", Codec.STRING),
            (state, value) -> state.backendId = value,
            PersistentPhysicsSpaceState::getBackendId)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("Gravity", Vector3fUtil.CODEC),
            (state, value) -> state.gravity.set(value),
            PersistentPhysicsSpaceState::getGravity)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.finiteVector(
            "Persisted space gravity must be finite"))
        .add()
        .append(new KeyedCodec<>("WorldCollisionMode", new EnumCodec<>(WorldCollisionMode.class), false),
            (state, value) -> state.worldCollisionMode = value,
            PersistentPhysicsSpaceState::getWorldCollisionMode)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("EntityChunkBoundaryMode",
                new EnumCodec<>(EntityChunkBoundaryMode.class), false),
            (state, value) -> state.entityChunkBoundaryMode = value,
            PersistentPhysicsSpaceState::getEntityChunkBoundaryMode)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("WorldCollisionRadius", Codec.INTEGER, false),
            (state, value) -> state.worldCollisionRadius = value,
            PersistentPhysicsSpaceState::getWorldCollisionRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsWorldCollisionSettings.MAX_WORLD_COLLISION_RADIUS))
        .add()
        .append(new KeyedCodec<>("WorldCollisionBodyRadius", Codec.INTEGER, false),
            (state, value) -> state.worldCollisionBodyRadius = value,
            PersistentPhysicsSpaceState::getWorldCollisionBodyRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsWorldCollisionSettings.MAX_WORLD_COLLISION_BODY_RADIUS))
        .add()
        .append(new KeyedCodec<>("WorldCollisionTtlTicks", Codec.INTEGER, false),
            (state, value) -> state.worldCollisionTtlTicks = value,
            PersistentPhysicsSpaceState::getWorldCollisionTtlTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsWorldCollisionSettings.MAX_WORLD_COLLISION_TTL_TICKS))
        .add()
        .append(new KeyedCodec<>("VisualFullSyncRadius", Codec.INTEGER, false),
            (state, value) -> state.visualFullSyncRadius = value,
            PersistentPhysicsSpaceState::getVisualFullSyncRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualSyncSettings.MAX_VISUAL_FULL_SYNC_RADIUS))
        .add()
        .append(new KeyedCodec<>("VisualMaxSyncRadius", Codec.INTEGER, false),
            (state, value) -> state.visualMaxSyncRadius = value,
            PersistentPhysicsSpaceState::getVisualMaxSyncRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualSyncSettings.MAX_VISUAL_MAX_SYNC_RADIUS))
        .add()
        .append(new KeyedCodec<>("VisualFarSyncCutoffEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.visualFarSyncCutoffEnabled = value,
            PersistentPhysicsSpaceState::isVisualFarSyncCutoffEnabled)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("VisualMidSyncIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.visualMidSyncIntervalTicks = value,
            PersistentPhysicsSpaceState::getVisualMidSyncIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualSyncSettings.MAX_VISUAL_MID_SYNC_INTERVAL_TICKS))
        .add()
        .append(new KeyedCodec<>("VisualFarSyncIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.visualFarSyncIntervalTicks = value,
            PersistentPhysicsSpaceState::getVisualFarSyncIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualSyncSettings.MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS))
        .add()
        .append(new KeyedCodec<>("VisualOcclusionMode", new EnumCodec<>(VisualOcclusionMode.class), false),
            (state, value) -> state.visualOcclusionMode = value,
            PersistentPhysicsSpaceState::getVisualOcclusionMode)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("VisualOcclusionRaycastsPerTick", Codec.INTEGER, false),
            (state, value) -> state.visualOcclusionRaycastsPerTick = value,
            PersistentPhysicsSpaceState::getVisualOcclusionRaycastsPerTick)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualSyncSettings.MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK))
        .add()
        .append(new KeyedCodec<>("VisualOcclusionCacheTicks", Codec.INTEGER, false),
            (state, value) -> state.visualOcclusionCacheTicks = value,
            PersistentPhysicsSpaceState::getVisualOcclusionCacheTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualSyncSettings.MAX_VISUAL_OCCLUSION_CACHE_TICKS))
        .add()
        .append(new KeyedCodec<>("VisualSnapshotPredictionEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.visualSnapshotPredictionEnabled = value,
            PersistentPhysicsSpaceState::isVisualSnapshotPredictionEnabled)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("VisualSnapshotPredictionMaxSeconds", Codec.FLOAT, false),
            (state, value) -> state.visualSnapshotPredictionMaxSeconds = value,
            PersistentPhysicsSpaceState::getVisualSnapshotPredictionMaxSeconds)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(0.0f, PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS))
        .add()
        .append(new KeyedCodec<>("VisualSnapshotSmoothingEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.visualSnapshotSmoothingEnabled = value,
            PersistentPhysicsSpaceState::isVisualSnapshotSmoothingEnabled)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("VisualSnapshotSmoothingRate", Codec.FLOAT, false),
            (state, value) -> state.visualSnapshotSmoothingRate = value,
            PersistentPhysicsSpaceState::getVisualSnapshotSmoothingRate)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.greaterThan(0.0f))
        .addValidator(Validators.max(PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE))
        .add()
        .append(new KeyedCodec<>("SolverIterations", Codec.INTEGER, false),
            (state, value) -> state.solverIterations = value,
            PersistentPhysicsSpaceState::getSolverIterations)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.min(1))
        .add()
        .append(new KeyedCodec<>("StabilizationIterations", Codec.INTEGER, false),
            (state, value) -> state.stabilizationIterations = value,
            PersistentPhysicsSpaceState::getStabilizationIterations)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.min(0))
        .add()
        .append(new KeyedCodec<>("DynamicSleepLinearThreshold", Codec.FLOAT, false),
            (state, value) -> state.dynamicSleepLinearThreshold = value,
            PersistentPhysicsSpaceState::getDynamicSleepLinearThreshold)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(0.0f, Float.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("DynamicSleepAngularThreshold", Codec.FLOAT, false),
            (state, value) -> state.dynamicSleepAngularThreshold = value,
            PersistentPhysicsSpaceState::getDynamicSleepAngularThreshold)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(0.0f, Float.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("DynamicSleepTimeUntilSleep", Codec.FLOAT, false),
            (state, value) -> state.dynamicSleepTimeUntilSleep = value,
            PersistentPhysicsSpaceState::getDynamicSleepTimeUntilSleep)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(0.0f, Float.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("ExtensionSettings",
                new ArrayCodec<>(PersistentPhysicsExtensionSettingState.CODEC,
                    PersistentPhysicsExtensionSettingState[]::new),
                false),
            (state, value) -> state.extensionSettings = copyExtensionSettings(value),
            PersistentPhysicsSpaceState::getExtensionSettings)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.nonNullArrayElements())
        .add()
        .append(new KeyedCodec<>("EntityVisualSyncCullingEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.entityVisualSyncCullingEnabled = value,
            PersistentPhysicsSpaceState::isEntityVisualSyncCullingEnabled)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("VisualVisibilityCullingEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.visualVisibilityCullingEnabled = value,
            PersistentPhysicsSpaceState::isVisualVisibilityCullingEnabled)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaterializationEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.detachedVisualMaterializationEnabled = value,
            PersistentPhysicsSpaceState::isDetachedVisualMaterializationEnabled)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaterializationRadius", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualMaterializationRadius = value,
            PersistentPhysicsSpaceState::getDetachedVisualMaterializationRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS))
        .add()
        .append(new KeyedCodec<>("DetachedVisualDematerializationRadius", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualDematerializationRadius = value,
            PersistentPhysicsSpaceState::getDetachedVisualDematerializationRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS))
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaxSpawnsPerTick", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualMaxSpawnsPerTick = value,
            PersistentPhysicsSpaceState::getDetachedVisualMaxSpawnsPerTick)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK))
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaxMaterialized", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualMaxMaterialized = value,
            PersistentPhysicsSpaceState::getDetachedVisualMaxMaterialized)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_MAX_MATERIALIZED))
        .add()
        .append(new KeyedCodec<>("DetachedVisualInterestRefreshIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualInterestRefreshIntervalTicks = value,
            PersistentPhysicsSpaceState::getDetachedVisualInterestRefreshIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS))
        .add()
        .append(new KeyedCodec<>("DetachedVisualCandidateRefreshIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualCandidateRefreshIntervalTicks = value,
            PersistentPhysicsSpaceState::getDetachedVisualCandidateRefreshIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS))
        .add()
        .append(new KeyedCodec<>("DetachedVisualVisibilityCheckIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualVisibilityCheckIntervalTicks = value,
            PersistentPhysicsSpaceState::getDetachedVisualVisibilityCheckIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS))
        .add()
        .append(new KeyedCodec<>("CollisionLodEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.collisionLodEnabled = value,
            PersistentPhysicsSpaceState::isCollisionLodEnabled)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("CollisionLodNearRadius", Codec.INTEGER, false),
            (state, value) -> state.collisionLodNearRadius = value,
            PersistentPhysicsSpaceState::getCollisionLodNearRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsCollisionLodSettings.MAX_COLLISION_LOD_RADIUS))
        .add()
        .append(new KeyedCodec<>("CollisionLodMidRadius", Codec.INTEGER, false),
            (state, value) -> state.collisionLodMidRadius = value,
            PersistentPhysicsSpaceState::getCollisionLodMidRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsCollisionLodSettings.MAX_COLLISION_LOD_RADIUS))
        .add()
        .append(new KeyedCodec<>("CollisionLodHysteresis", Codec.INTEGER, false),
            (state, value) -> state.collisionLodHysteresis = value,
            PersistentPhysicsSpaceState::getCollisionLodHysteresis)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(0, PhysicsCollisionLodSettings.MAX_COLLISION_LOD_HYSTERESIS))
        .add()
        .append(new KeyedCodec<>("CollisionLodRefreshIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.collisionLodRefreshIntervalTicks = value,
            PersistentPhysicsSpaceState::getCollisionLodRefreshIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsCollisionLodSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS))
        .add()
        .append(new KeyedCodec<>("CollisionLodFarSleepEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.collisionLodFarSleepEnabled = value,
            PersistentPhysicsSpaceState::isCollisionLodFarSleepEnabled)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("DetachedVisualBlockType", Codec.STRING, false),
            (state, value) -> state.detachedVisualBlockType = value,
            PersistentPhysicsSpaceState::getDetachedVisualBlockType)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.nonBlankString(
            "Persisted detached visual block type cannot be blank"))
        .add()
        .afterDecode(PersistentPhysicsSpaceState::validateAfterDecode)
        .build();

    @Setter
    private int spaceId;
    @Nonnull
    @Setter
    private String backendId = "";
    @Nonnull
    private final Vector3f gravity = new Vector3f(0.0f, -32f, 0.0f);// new Vector3f(0.0f, -9.81f, 0.0f);
    @Nonnull
    @Setter
    private WorldCollisionMode worldCollisionMode = WorldCollisionMode.NONE;
    @Nonnull
    @Setter
    private EntityChunkBoundaryMode entityChunkBoundaryMode =
        PhysicsWorldCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    private int worldCollisionRadius = PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_RADIUS;
    private int worldCollisionBodyRadius = PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS;
    private int worldCollisionTtlTicks = PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_TTL_TICKS;
    private int visualFullSyncRadius = PhysicsVisualSyncSettings.DEFAULT_VISUAL_FULL_SYNC_RADIUS;
    private int visualMaxSyncRadius = PhysicsVisualSyncSettings.DEFAULT_VISUAL_MAX_SYNC_RADIUS;
    @Setter
    private boolean visualFarSyncCutoffEnabled =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_FAR_SYNC_CUTOFF_ENABLED;
    private int visualMidSyncIntervalTicks =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS;
    private int visualFarSyncIntervalTicks =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS;
    @Nonnull
    @Setter
    private VisualOcclusionMode visualOcclusionMode =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_OCCLUSION_MODE;
    private int visualOcclusionRaycastsPerTick =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_OCCLUSION_RAYCASTS_PER_TICK;
    private int visualOcclusionCacheTicks =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_OCCLUSION_CACHE_TICKS;
    @Setter
    private boolean visualSnapshotPredictionEnabled =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_ENABLED;
    private float visualSnapshotPredictionMaxSeconds =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS;
    @Setter
    private boolean visualSnapshotSmoothingEnabled =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_ENABLED;
    private float visualSnapshotSmoothingRate =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_RATE;
    @Setter
    private int solverIterations = PhysicsSolverSettings.DEFAULT_SOLVER_ITERATIONS;
    @Setter
    private int stabilizationIterations = PhysicsSolverSettings.DEFAULT_STABILIZATION_ITERATIONS;
    @Setter
    private float dynamicSleepLinearThreshold =
        PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_LINEAR_THRESHOLD;
    @Setter
    private float dynamicSleepAngularThreshold =
        PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_ANGULAR_THRESHOLD;
    @Setter
    private float dynamicSleepTimeUntilSleep =
        PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_TIME_UNTIL_SLEEP;
    @Nonnull
    private PersistentPhysicsExtensionSettingState[] extensionSettings = EMPTY_EXTENSION_SETTINGS;
    @Setter
    private boolean entityVisualSyncCullingEnabled =
        PhysicsVisualSyncSettings.DEFAULT_ENTITY_VISUAL_SYNC_CULLING_ENABLED;
    @Setter
    private boolean visualVisibilityCullingEnabled =
        PhysicsVisualSyncSettings.DEFAULT_VISUAL_VISIBILITY_CULLING_ENABLED;
    @Setter
    private boolean detachedVisualMaterializationEnabled =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MATERIALIZATION_ENABLED;
    private int detachedVisualMaterializationRadius =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MATERIALIZATION_RADIUS;
    private int detachedVisualDematerializationRadius =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS;
    private int detachedVisualMaxSpawnsPerTick =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK;
    private int detachedVisualMaxMaterialized =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_MAX_MATERIALIZED;
    private int detachedVisualInterestRefreshIntervalTicks =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS;
    private int detachedVisualCandidateRefreshIntervalTicks =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS;
    private int detachedVisualVisibilityCheckIntervalTicks =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS;
    @Setter
    private boolean collisionLodEnabled =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_ENABLED;
    private int collisionLodNearRadius =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_NEAR_RADIUS;
    private int collisionLodMidRadius =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_MID_RADIUS;
    private int collisionLodHysteresis =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_HYSTERESIS;
    private int collisionLodRefreshIntervalTicks =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_REFRESH_INTERVAL_TICKS;
    @Setter
    private boolean collisionLodFarSleepEnabled =
        PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_FAR_SLEEP_ENABLED;
    @Nonnull
    @Setter
    private String detachedVisualBlockType =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE;

    public PersistentPhysicsSpaceState() {
    }

    @Nonnull
    public static PersistentPhysicsSpaceState from(@Nonnull PhysicsSpaceBinding space,
        @Nonnull PhysicsSpaceSettings settings) {
        PersistentPhysicsSpaceState state = new PersistentPhysicsSpaceState();
        state.spaceId = space.spaceId().value();
        state.backendId = space.backendId().value();
        space.runtime().getGravity(space.backendSpaceId(), state.gravity::set);
        state.worldCollisionMode = settings.getWorldCollisionSettings().getWorldCollisionMode();
        state.entityChunkBoundaryMode = settings.getWorldCollisionSettings().getEntityChunkBoundaryMode();
        state.worldCollisionRadius = settings.getWorldCollisionSettings().getWorldCollisionRadius();
        state.worldCollisionBodyRadius = settings.getWorldCollisionSettings().getWorldCollisionBodyRadius();
        state.worldCollisionTtlTicks = settings.getWorldCollisionSettings().getWorldCollisionTtlTicks();
        state.visualFullSyncRadius = settings.getVisualSyncSettings().getVisualFullSyncRadius();
        state.visualMaxSyncRadius = settings.getVisualSyncSettings().getVisualMaxSyncRadius();
        state.visualFarSyncCutoffEnabled = settings.getVisualSyncSettings().isVisualFarSyncCutoffEnabled();
        state.visualMidSyncIntervalTicks = settings.getVisualSyncSettings().getVisualMidSyncIntervalTicks();
        state.visualFarSyncIntervalTicks = settings.getVisualSyncSettings().getVisualFarSyncIntervalTicks();
        state.visualOcclusionMode = settings.getVisualSyncSettings().getVisualOcclusionMode();
        state.visualOcclusionRaycastsPerTick = settings.getVisualSyncSettings().getVisualOcclusionRaycastsPerTick();
        state.visualOcclusionCacheTicks = settings.getVisualSyncSettings().getVisualOcclusionCacheTicks();
        state.visualSnapshotPredictionEnabled = settings.getVisualSyncSettings().isVisualSnapshotPredictionEnabled();
        state.visualSnapshotPredictionMaxSeconds = settings.getVisualSyncSettings().getVisualSnapshotPredictionMaxSeconds();
        state.visualSnapshotSmoothingEnabled = settings.getVisualSyncSettings().isVisualSnapshotSmoothingEnabled();
        state.visualSnapshotSmoothingRate = settings.getVisualSyncSettings().getVisualSnapshotSmoothingRate();
        state.solverIterations = settings.getSolverSettings().getSolverIterations();
        state.stabilizationIterations = settings.getSolverSettings().getStabilizationIterations();
        state.dynamicSleepLinearThreshold = settings.getSolverSettings().getDynamicSleepLinearThreshold();
        state.dynamicSleepAngularThreshold = settings.getSolverSettings().getDynamicSleepAngularThreshold();
        state.dynamicSleepTimeUntilSleep = settings.getSolverSettings().getDynamicSleepTimeUntilSleep();
        state.extensionSettings = extensionSettingsFrom(settings);
        state.entityVisualSyncCullingEnabled = settings.getVisualSyncSettings().isEntityVisualSyncCullingEnabled();
        state.visualVisibilityCullingEnabled = settings.getVisualSyncSettings().isVisualVisibilityCullingEnabled();
        state.detachedVisualMaterializationEnabled = settings.getVisualMaterializationSettings().isDetachedVisualMaterializationEnabled();
        state.detachedVisualMaterializationRadius = settings.getVisualMaterializationSettings().getDetachedVisualMaterializationRadius();
        state.detachedVisualDematerializationRadius = settings.getVisualMaterializationSettings().getDetachedVisualDematerializationRadius();
        state.detachedVisualMaxSpawnsPerTick = settings.getVisualMaterializationSettings().getDetachedVisualMaxSpawnsPerTick();
        state.detachedVisualMaxMaterialized = settings.getVisualMaterializationSettings().getDetachedVisualMaxMaterialized();
        state.detachedVisualInterestRefreshIntervalTicks =
            settings.getVisualMaterializationSettings().getDetachedVisualInterestRefreshIntervalTicks();
        state.detachedVisualCandidateRefreshIntervalTicks =
            settings.getVisualMaterializationSettings().getDetachedVisualCandidateRefreshIntervalTicks();
        state.detachedVisualVisibilityCheckIntervalTicks =
            settings.getVisualMaterializationSettings().getDetachedVisualVisibilityCheckIntervalTicks();
        state.collisionLodEnabled = settings.getCollisionLodSettings().isCollisionLodEnabled();
        state.collisionLodNearRadius = settings.getCollisionLodSettings().getCollisionLodNearRadius();
        state.collisionLodMidRadius = settings.getCollisionLodSettings().getCollisionLodMidRadius();
        state.collisionLodHysteresis = settings.getCollisionLodSettings().getCollisionLodHysteresis();
        state.collisionLodRefreshIntervalTicks = settings.getCollisionLodSettings().getCollisionLodRefreshIntervalTicks();
        state.collisionLodFarSleepEnabled = settings.getCollisionLodSettings().isCollisionLodFarSleepEnabled();
        state.detachedVisualBlockType = settings.getVisualMaterializationSettings().getDetachedVisualBlockType();
        return state;
    }

    @Nonnull
    public SpaceId toSpaceId() {
        return new SpaceId(spaceId);
    }

    @Nonnull
    public BackendId toBackendId() {
        return new BackendId(backendId);
    }

    @Nonnull
    public PhysicsSpaceSettings toSettings() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getWorldCollisionSettings().setWorldCollisionMode(worldCollisionMode);
        settings.getWorldCollisionSettings().setEntityChunkBoundaryMode(entityChunkBoundaryMode);
        settings.getWorldCollisionSettings().setWorldCollisionRadius(worldCollisionRadius);
        settings.getWorldCollisionSettings().setWorldCollisionBodyRadius(worldCollisionBodyRadius);
        settings.getWorldCollisionSettings().setWorldCollisionTtlTicks(worldCollisionTtlTicks);
        settings.getVisualSyncSettings().setVisualSyncRadii(visualFullSyncRadius, visualMaxSyncRadius);
        settings.getVisualSyncSettings().setVisualFarSyncCutoffEnabled(visualFarSyncCutoffEnabled);
        settings.getVisualSyncSettings().setVisualMidSyncIntervalTicks(visualMidSyncIntervalTicks);
        settings.getVisualSyncSettings().setVisualFarSyncIntervalTicks(visualFarSyncIntervalTicks);
        settings.getVisualSyncSettings().setVisualOcclusionMode(visualOcclusionMode);
        settings.getVisualSyncSettings().setVisualOcclusionRaycastsPerTick(visualOcclusionRaycastsPerTick);
        settings.getVisualSyncSettings().setVisualOcclusionCacheTicks(visualOcclusionCacheTicks);
        settings.getVisualSyncSettings().setVisualSnapshotPredictionEnabled(visualSnapshotPredictionEnabled);
        settings.getVisualSyncSettings().setVisualSnapshotPredictionMaxSeconds(visualSnapshotPredictionMaxSeconds);
        settings.getVisualSyncSettings().setVisualSnapshotSmoothingEnabled(visualSnapshotSmoothingEnabled);
        settings.getVisualSyncSettings().setVisualSnapshotSmoothingRate(visualSnapshotSmoothingRate);
        settings.getSolverSettings().setSolverIterations(solverIterations);
        settings.getSolverSettings().setStabilizationIterations(stabilizationIterations);
        settings.getSolverSettings().setDynamicSleepTuning(dynamicSleepLinearThreshold,
            dynamicSleepAngularThreshold,
            dynamicSleepTimeUntilSleep);
        for (PersistentPhysicsExtensionSettingState extensionSetting : extensionSettings) {
            extensionSetting.applyTo(settings.getExtensionSettings());
        }
        settings.getVisualSyncSettings().setEntityVisualSyncCullingEnabled(entityVisualSyncCullingEnabled);
        settings.getVisualSyncSettings().setVisualVisibilityCullingEnabled(visualVisibilityCullingEnabled);
        settings.getVisualMaterializationSettings().setDetachedVisualMaterializationEnabled(detachedVisualMaterializationEnabled);
        settings.getVisualMaterializationSettings().setDetachedVisualRadii(
            detachedVisualMaterializationRadius,
            detachedVisualDematerializationRadius);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxSpawnsPerTick(detachedVisualMaxSpawnsPerTick);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxMaterialized(detachedVisualMaxMaterialized);
        settings.getVisualMaterializationSettings().setDetachedVisualInterestRefreshIntervalTicks(
            detachedVisualInterestRefreshIntervalTicks);
        settings.getVisualMaterializationSettings().setDetachedVisualCandidateRefreshIntervalTicks(
            detachedVisualCandidateRefreshIntervalTicks);
        settings.getVisualMaterializationSettings().setDetachedVisualVisibilityCheckIntervalTicks(
            detachedVisualVisibilityCheckIntervalTicks);
        settings.getCollisionLodSettings().setCollisionLodEnabled(collisionLodEnabled);
        settings.getCollisionLodSettings().setCollisionLodRadii(collisionLodNearRadius, collisionLodMidRadius);
        settings.getCollisionLodSettings().setCollisionLodHysteresis(collisionLodHysteresis);
        settings.getCollisionLodSettings().setCollisionLodRefreshIntervalTicks(collisionLodRefreshIntervalTicks);
        settings.getCollisionLodSettings().setCollisionLodFarSleepEnabled(collisionLodFarSleepEnabled);
        settings.getVisualMaterializationSettings().setDetachedVisualBlockType(detachedVisualBlockType);
        return settings;
    }

    private static void validate(@Nonnull PersistentPhysicsSpaceState state,
        @Nonnull ValidationResults results) {
        try {
            state.toSettings();
        } catch (RuntimeException exception) {
            results.fail("Invalid persisted space settings: " + exception.getMessage());
        }
    }

    private static void validateAfterDecode(@Nonnull PersistentPhysicsSpaceState state,
        @Nonnull ExtraInfo extraInfo) {
        validate(state, extraInfo.getValidationResults());
    }

    @Nonnull
    public PersistentPhysicsSpaceState copy() {
        PersistentPhysicsSpaceState copy = new PersistentPhysicsSpaceState();
        copy.spaceId = spaceId;
        copy.backendId = backendId;
        copy.gravity.set(gravity);
        copy.worldCollisionMode = worldCollisionMode;
        copy.entityChunkBoundaryMode = entityChunkBoundaryMode;
        copy.worldCollisionRadius = worldCollisionRadius;
        copy.worldCollisionBodyRadius = worldCollisionBodyRadius;
        copy.worldCollisionTtlTicks = worldCollisionTtlTicks;
        copy.visualFullSyncRadius = visualFullSyncRadius;
        copy.visualMaxSyncRadius = visualMaxSyncRadius;
        copy.visualFarSyncCutoffEnabled = visualFarSyncCutoffEnabled;
        copy.visualMidSyncIntervalTicks = visualMidSyncIntervalTicks;
        copy.visualFarSyncIntervalTicks = visualFarSyncIntervalTicks;
        copy.visualOcclusionMode = visualOcclusionMode;
        copy.visualOcclusionRaycastsPerTick = visualOcclusionRaycastsPerTick;
        copy.visualOcclusionCacheTicks = visualOcclusionCacheTicks;
        copy.visualSnapshotPredictionEnabled = visualSnapshotPredictionEnabled;
        copy.visualSnapshotPredictionMaxSeconds = visualSnapshotPredictionMaxSeconds;
        copy.visualSnapshotSmoothingEnabled = visualSnapshotSmoothingEnabled;
        copy.visualSnapshotSmoothingRate = visualSnapshotSmoothingRate;
        copy.solverIterations = solverIterations;
        copy.stabilizationIterations = stabilizationIterations;
        copy.dynamicSleepLinearThreshold = dynamicSleepLinearThreshold;
        copy.dynamicSleepAngularThreshold = dynamicSleepAngularThreshold;
        copy.dynamicSleepTimeUntilSleep = dynamicSleepTimeUntilSleep;
        copy.extensionSettings = copyExtensionSettings(extensionSettings);
        copy.entityVisualSyncCullingEnabled = entityVisualSyncCullingEnabled;
        copy.visualVisibilityCullingEnabled = visualVisibilityCullingEnabled;
        copy.detachedVisualMaterializationEnabled = detachedVisualMaterializationEnabled;
        copy.detachedVisualMaterializationRadius = detachedVisualMaterializationRadius;
        copy.detachedVisualDematerializationRadius = detachedVisualDematerializationRadius;
        copy.detachedVisualMaxSpawnsPerTick = detachedVisualMaxSpawnsPerTick;
        copy.detachedVisualMaxMaterialized = detachedVisualMaxMaterialized;
        copy.detachedVisualInterestRefreshIntervalTicks =
            detachedVisualInterestRefreshIntervalTicks;
        copy.detachedVisualCandidateRefreshIntervalTicks =
            detachedVisualCandidateRefreshIntervalTicks;
        copy.detachedVisualVisibilityCheckIntervalTicks =
            detachedVisualVisibilityCheckIntervalTicks;
        copy.collisionLodEnabled = collisionLodEnabled;
        copy.collisionLodNearRadius = collisionLodNearRadius;
        copy.collisionLodMidRadius = collisionLodMidRadius;
        copy.collisionLodHysteresis = collisionLodHysteresis;
        copy.collisionLodRefreshIntervalTicks = collisionLodRefreshIntervalTicks;
        copy.collisionLodFarSleepEnabled = collisionLodFarSleepEnabled;
        copy.detachedVisualBlockType = detachedVisualBlockType;
        return copy;
    }

    @Nonnull
    private static PersistentPhysicsExtensionSettingState[] extensionSettingsFrom(
        @Nonnull PhysicsSpaceSettings settings) {
        return settings.getExtensionSettings().asMap().entrySet().stream()
            .flatMap(entry -> entry.getValue().entrySet().stream()
                .map(setting -> PersistentPhysicsExtensionSettingState.from(entry.getKey(),
                    setting.getKey(),
                    setting.getValue())))
            .toArray(PersistentPhysicsExtensionSettingState[]::new);
    }

    @Nonnull
    private static PersistentPhysicsExtensionSettingState[] copyExtensionSettings(
        @Nonnull PersistentPhysicsExtensionSettingState[] settings) {
        if (settings.length == 0) {
            return EMPTY_EXTENSION_SETTINGS;
        }
        PersistentPhysicsExtensionSettingState[] copy =
            new PersistentPhysicsExtensionSettingState[settings.length];
        for (int i = 0; i < settings.length; i++) {
            copy[i] = settings[i].copy();
        }
        return copy;
    }
}
