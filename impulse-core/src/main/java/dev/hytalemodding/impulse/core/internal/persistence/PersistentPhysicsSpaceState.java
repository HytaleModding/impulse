package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.validation.Validator;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsSpaceBootstrapSystem;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings.ExecutionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Codec-backed definition of one physics space for the persistence layer.
 *
 * <p>Captures the space identity, backend choice, gravity, and world-collision
 * settings so that {@link PersistentPhysicsSpaceBootstrapSystem} can recreate
 * the runtime {@link dev.hytalemodding.impulse.api.PhysicsSpace} after a
 * world load or manual snapshot restore.</p>
 */
@Getter
public class PersistentPhysicsSpaceState {

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
        .addValidator(finiteVector("Persisted space gravity must be finite"))
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
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_WORLD_COLLISION_RADIUS))
        .add()
        .append(new KeyedCodec<>("WorldCollisionBodyRadius", Codec.INTEGER, false),
            (state, value) -> state.worldCollisionBodyRadius = value,
            PersistentPhysicsSpaceState::getWorldCollisionBodyRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_WORLD_COLLISION_BODY_RADIUS))
        .add()
        .append(new KeyedCodec<>("WorldCollisionTtlTicks", Codec.INTEGER, false),
            (state, value) -> state.worldCollisionTtlTicks = value,
            PersistentPhysicsSpaceState::getWorldCollisionTtlTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_WORLD_COLLISION_TTL_TICKS))
        .add()
        .append(new KeyedCodec<>("VisualFullSyncRadius", Codec.INTEGER, false),
            (state, value) -> state.visualFullSyncRadius = value,
            PersistentPhysicsSpaceState::getVisualFullSyncRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_VISUAL_FULL_SYNC_RADIUS))
        .add()
        .append(new KeyedCodec<>("VisualMaxSyncRadius", Codec.INTEGER, false),
            (state, value) -> state.visualMaxSyncRadius = value,
            PersistentPhysicsSpaceState::getVisualMaxSyncRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_VISUAL_MAX_SYNC_RADIUS))
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
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_VISUAL_MID_SYNC_INTERVAL_TICKS))
        .add()
        .append(new KeyedCodec<>("VisualFarSyncIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.visualFarSyncIntervalTicks = value,
            PersistentPhysicsSpaceState::getVisualFarSyncIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS))
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
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK))
        .add()
        .append(new KeyedCodec<>("VisualOcclusionCacheTicks", Codec.INTEGER, false),
            (state, value) -> state.visualOcclusionCacheTicks = value,
            PersistentPhysicsSpaceState::getVisualOcclusionCacheTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_CACHE_TICKS))
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
        .addValidator(Validators.range(0.0f, PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS))
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
        .addValidator(Validators.max(PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE))
        .add()
        .append(new KeyedCodec<>("SolverIterations", Codec.INTEGER, false),
            (state, value) -> state.solverIterations = value,
            PersistentPhysicsSpaceState::getSolverIterations)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.min(1))
        .add()
        .append(new KeyedCodec<>("InternalPgsIterations", Codec.INTEGER, false),
            (state, value) -> state.internalPgsIterations = value,
            PersistentPhysicsSpaceState::getInternalPgsIterations)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.min(1))
        .add()
        .append(new KeyedCodec<>("StabilizationIterations", Codec.INTEGER, false),
            (state, value) -> state.stabilizationIterations = value,
            PersistentPhysicsSpaceState::getStabilizationIterations)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.min(0))
        .add()
        .append(new KeyedCodec<>("MinIslandSize", Codec.INTEGER, false),
            (state, value) -> state.minIslandSize = value,
            PersistentPhysicsSpaceState::getMinIslandSize)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.min(1))
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
        .append(new KeyedCodec<>("ExecutionMode", new EnumCodec<>(ExecutionMode.class), false),
            (state, value) -> state.executionMode = value,
            PersistentPhysicsSpaceState::getExecutionMode)
        .addValidator(Validators.nonNull())
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
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS))
        .add()
        .append(new KeyedCodec<>("DetachedVisualDematerializationRadius", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualDematerializationRadius = value,
            PersistentPhysicsSpaceState::getDetachedVisualDematerializationRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS))
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaxSpawnsPerTick", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualMaxSpawnsPerTick = value,
            PersistentPhysicsSpaceState::getDetachedVisualMaxSpawnsPerTick)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK))
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaxMaterialized", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualMaxMaterialized = value,
            PersistentPhysicsSpaceState::getDetachedVisualMaxMaterialized)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_MATERIALIZED))
        .add()
        .append(new KeyedCodec<>("DetachedVisualInterestRefreshIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualInterestRefreshIntervalTicks = value,
            PersistentPhysicsSpaceState::getDetachedVisualInterestRefreshIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS))
        .add()
        .append(new KeyedCodec<>("DetachedVisualCandidateRefreshIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualCandidateRefreshIntervalTicks = value,
            PersistentPhysicsSpaceState::getDetachedVisualCandidateRefreshIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS))
        .add()
        .append(new KeyedCodec<>("DetachedVisualVisibilityCheckIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualVisibilityCheckIntervalTicks = value,
            PersistentPhysicsSpaceState::getDetachedVisualVisibilityCheckIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS))
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
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_COLLISION_LOD_RADIUS))
        .add()
        .append(new KeyedCodec<>("CollisionLodMidRadius", Codec.INTEGER, false),
            (state, value) -> state.collisionLodMidRadius = value,
            PersistentPhysicsSpaceState::getCollisionLodMidRadius)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_COLLISION_LOD_RADIUS))
        .add()
        .append(new KeyedCodec<>("CollisionLodHysteresis", Codec.INTEGER, false),
            (state, value) -> state.collisionLodHysteresis = value,
            PersistentPhysicsSpaceState::getCollisionLodHysteresis)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(0, PhysicsSpaceSettings.MAX_COLLISION_LOD_HYSTERESIS))
        .add()
        .append(new KeyedCodec<>("CollisionLodRefreshIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.collisionLodRefreshIntervalTicks = value,
            PersistentPhysicsSpaceState::getCollisionLodRefreshIntervalTicks)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, PhysicsSpaceSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS))
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
        .addValidator(nonBlankString("Persisted detached visual block type cannot be blank"))
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
        PhysicsSpaceSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    private int worldCollisionRadius = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_RADIUS;
    private int worldCollisionBodyRadius = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS;
    private int worldCollisionTtlTicks = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_TTL_TICKS;
    private int visualFullSyncRadius = PhysicsSpaceSettings.DEFAULT_VISUAL_FULL_SYNC_RADIUS;
    private int visualMaxSyncRadius = PhysicsSpaceSettings.DEFAULT_VISUAL_MAX_SYNC_RADIUS;
    @Setter
    private boolean visualFarSyncCutoffEnabled =
        PhysicsSpaceSettings.DEFAULT_VISUAL_FAR_SYNC_CUTOFF_ENABLED;
    private int visualMidSyncIntervalTicks =
        PhysicsSpaceSettings.DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS;
    private int visualFarSyncIntervalTicks =
        PhysicsSpaceSettings.DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS;
    @Nonnull
    @Setter
    private VisualOcclusionMode visualOcclusionMode =
        PhysicsSpaceSettings.DEFAULT_VISUAL_OCCLUSION_MODE;
    private int visualOcclusionRaycastsPerTick =
        PhysicsSpaceSettings.DEFAULT_VISUAL_OCCLUSION_RAYCASTS_PER_TICK;
    private int visualOcclusionCacheTicks =
        PhysicsSpaceSettings.DEFAULT_VISUAL_OCCLUSION_CACHE_TICKS;
    @Setter
    private boolean visualSnapshotPredictionEnabled =
        PhysicsSpaceSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_ENABLED;
    private float visualSnapshotPredictionMaxSeconds =
        PhysicsSpaceSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS;
    @Setter
    private boolean visualSnapshotSmoothingEnabled =
        PhysicsSpaceSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_ENABLED;
    private float visualSnapshotSmoothingRate =
        PhysicsSpaceSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_RATE;
    @Setter
    private int solverIterations = PhysicsSpaceSettings.DEFAULT_SOLVER_ITERATIONS;
    @Setter
    private int internalPgsIterations = PhysicsSpaceSettings.DEFAULT_INTERNAL_PGS_ITERATIONS;
    @Setter
    private int stabilizationIterations = PhysicsSpaceSettings.DEFAULT_STABILIZATION_ITERATIONS;
    @Setter
    private int minIslandSize = PhysicsSpaceSettings.DEFAULT_MIN_ISLAND_SIZE;
    @Setter
    private float dynamicSleepLinearThreshold =
        PhysicsSpaceSettings.DEFAULT_DYNAMIC_SLEEP_LINEAR_THRESHOLD;
    @Setter
    private float dynamicSleepAngularThreshold =
        PhysicsSpaceSettings.DEFAULT_DYNAMIC_SLEEP_ANGULAR_THRESHOLD;
    @Setter
    private float dynamicSleepTimeUntilSleep =
        PhysicsSpaceSettings.DEFAULT_DYNAMIC_SLEEP_TIME_UNTIL_SLEEP;
    @Nonnull
    private ExecutionMode executionMode = PhysicsSpaceSettings.DEFAULT_EXECUTION_MODE;
    @Setter
    private boolean entityVisualSyncCullingEnabled =
        PhysicsSpaceSettings.DEFAULT_ENTITY_VISUAL_SYNC_CULLING_ENABLED;
    @Setter
    private boolean visualVisibilityCullingEnabled =
        PhysicsSpaceSettings.DEFAULT_VISUAL_VISIBILITY_CULLING_ENABLED;
    @Setter
    private boolean detachedVisualMaterializationEnabled =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_MATERIALIZATION_ENABLED;
    private int detachedVisualMaterializationRadius =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_MATERIALIZATION_RADIUS;
    private int detachedVisualDematerializationRadius =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS;
    private int detachedVisualMaxSpawnsPerTick =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK;
    private int detachedVisualMaxMaterialized =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_MAX_MATERIALIZED;
    private int detachedVisualInterestRefreshIntervalTicks =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS;
    private int detachedVisualCandidateRefreshIntervalTicks =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS;
    private int detachedVisualVisibilityCheckIntervalTicks =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS;
    @Setter
    private boolean collisionLodEnabled =
        PhysicsSpaceSettings.DEFAULT_COLLISION_LOD_ENABLED;
    private int collisionLodNearRadius =
        PhysicsSpaceSettings.DEFAULT_COLLISION_LOD_NEAR_RADIUS;
    private int collisionLodMidRadius =
        PhysicsSpaceSettings.DEFAULT_COLLISION_LOD_MID_RADIUS;
    private int collisionLodHysteresis =
        PhysicsSpaceSettings.DEFAULT_COLLISION_LOD_HYSTERESIS;
    private int collisionLodRefreshIntervalTicks =
        PhysicsSpaceSettings.DEFAULT_COLLISION_LOD_REFRESH_INTERVAL_TICKS;
    @Setter
    private boolean collisionLodFarSleepEnabled =
        PhysicsSpaceSettings.DEFAULT_COLLISION_LOD_FAR_SLEEP_ENABLED;
    @Nonnull
    @Setter
    private String detachedVisualBlockType =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE;

    public PersistentPhysicsSpaceState() {
    }

    @Nonnull
    public static PersistentPhysicsSpaceState from(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        PersistentPhysicsSpaceState state = new PersistentPhysicsSpaceState();
        state.spaceId = space.getId().value();
        state.backendId = space.getBackendId().value();
        state.gravity.set(space.getGravity());
        state.worldCollisionMode = settings.getWorldCollisionMode();
        state.entityChunkBoundaryMode = settings.getEntityChunkBoundaryMode();
        state.worldCollisionRadius = settings.getWorldCollisionRadius();
        state.worldCollisionBodyRadius = settings.getWorldCollisionBodyRadius();
        state.worldCollisionTtlTicks = settings.getWorldCollisionTtlTicks();
        state.visualFullSyncRadius = settings.getVisualFullSyncRadius();
        state.visualMaxSyncRadius = settings.getVisualMaxSyncRadius();
        state.visualFarSyncCutoffEnabled = settings.isVisualFarSyncCutoffEnabled();
        state.visualMidSyncIntervalTicks = settings.getVisualMidSyncIntervalTicks();
        state.visualFarSyncIntervalTicks = settings.getVisualFarSyncIntervalTicks();
        state.visualOcclusionMode = settings.getVisualOcclusionMode();
        state.visualOcclusionRaycastsPerTick = settings.getVisualOcclusionRaycastsPerTick();
        state.visualOcclusionCacheTicks = settings.getVisualOcclusionCacheTicks();
        state.visualSnapshotPredictionEnabled = settings.isVisualSnapshotPredictionEnabled();
        state.visualSnapshotPredictionMaxSeconds = settings.getVisualSnapshotPredictionMaxSeconds();
        state.visualSnapshotSmoothingEnabled = settings.isVisualSnapshotSmoothingEnabled();
        state.visualSnapshotSmoothingRate = settings.getVisualSnapshotSmoothingRate();
        state.solverIterations = settings.getSolverIterations();
        state.internalPgsIterations = settings.getInternalPgsIterations();
        state.stabilizationIterations = settings.getStabilizationIterations();
        state.minIslandSize = settings.getMinIslandSize();
        state.dynamicSleepLinearThreshold = settings.getDynamicSleepLinearThreshold();
        state.dynamicSleepAngularThreshold = settings.getDynamicSleepAngularThreshold();
        state.dynamicSleepTimeUntilSleep = settings.getDynamicSleepTimeUntilSleep();
        state.executionMode = settings.getExecutionMode();
        state.entityVisualSyncCullingEnabled = settings.isEntityVisualSyncCullingEnabled();
        state.visualVisibilityCullingEnabled = settings.isVisualVisibilityCullingEnabled();
        state.detachedVisualMaterializationEnabled = settings.isDetachedVisualMaterializationEnabled();
        state.detachedVisualMaterializationRadius = settings.getDetachedVisualMaterializationRadius();
        state.detachedVisualDematerializationRadius = settings.getDetachedVisualDematerializationRadius();
        state.detachedVisualMaxSpawnsPerTick = settings.getDetachedVisualMaxSpawnsPerTick();
        state.detachedVisualMaxMaterialized = settings.getDetachedVisualMaxMaterialized();
        state.detachedVisualInterestRefreshIntervalTicks =
            settings.getDetachedVisualInterestRefreshIntervalTicks();
        state.detachedVisualCandidateRefreshIntervalTicks =
            settings.getDetachedVisualCandidateRefreshIntervalTicks();
        state.detachedVisualVisibilityCheckIntervalTicks =
            settings.getDetachedVisualVisibilityCheckIntervalTicks();
        state.collisionLodEnabled = settings.isCollisionLodEnabled();
        state.collisionLodNearRadius = settings.getCollisionLodNearRadius();
        state.collisionLodMidRadius = settings.getCollisionLodMidRadius();
        state.collisionLodHysteresis = settings.getCollisionLodHysteresis();
        state.collisionLodRefreshIntervalTicks = settings.getCollisionLodRefreshIntervalTicks();
        state.collisionLodFarSleepEnabled = settings.isCollisionLodFarSleepEnabled();
        state.detachedVisualBlockType = settings.getDetachedVisualBlockType();
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
        settings.setWorldCollisionMode(worldCollisionMode);
        settings.setEntityChunkBoundaryMode(entityChunkBoundaryMode);
        settings.setWorldCollisionRadius(worldCollisionRadius);
        settings.setWorldCollisionBodyRadius(worldCollisionBodyRadius);
        settings.setWorldCollisionTtlTicks(worldCollisionTtlTicks);
        settings.setVisualSyncRadii(visualFullSyncRadius, visualMaxSyncRadius);
        settings.setVisualFarSyncCutoffEnabled(visualFarSyncCutoffEnabled);
        settings.setVisualMidSyncIntervalTicks(visualMidSyncIntervalTicks);
        settings.setVisualFarSyncIntervalTicks(visualFarSyncIntervalTicks);
        settings.setVisualOcclusionMode(visualOcclusionMode);
        settings.setVisualOcclusionRaycastsPerTick(visualOcclusionRaycastsPerTick);
        settings.setVisualOcclusionCacheTicks(visualOcclusionCacheTicks);
        settings.setVisualSnapshotPredictionEnabled(visualSnapshotPredictionEnabled);
        settings.setVisualSnapshotPredictionMaxSeconds(visualSnapshotPredictionMaxSeconds);
        settings.setVisualSnapshotSmoothingEnabled(visualSnapshotSmoothingEnabled);
        settings.setVisualSnapshotSmoothingRate(visualSnapshotSmoothingRate);
        settings.setSolverIterations(solverIterations);
        settings.setInternalPgsIterations(internalPgsIterations);
        settings.setStabilizationIterations(stabilizationIterations);
        settings.setMinIslandSize(minIslandSize);
        settings.setDynamicSleepTuning(dynamicSleepLinearThreshold,
            dynamicSleepAngularThreshold,
            dynamicSleepTimeUntilSleep);
        settings.setExecutionMode(executionMode);
        settings.setEntityVisualSyncCullingEnabled(entityVisualSyncCullingEnabled);
        settings.setVisualVisibilityCullingEnabled(visualVisibilityCullingEnabled);
        settings.setDetachedVisualMaterializationEnabled(detachedVisualMaterializationEnabled);
        settings.setDetachedVisualRadii(
            detachedVisualMaterializationRadius,
            detachedVisualDematerializationRadius);
        settings.setDetachedVisualMaxSpawnsPerTick(detachedVisualMaxSpawnsPerTick);
        settings.setDetachedVisualMaxMaterialized(detachedVisualMaxMaterialized);
        settings.setDetachedVisualInterestRefreshIntervalTicks(
            detachedVisualInterestRefreshIntervalTicks);
        settings.setDetachedVisualCandidateRefreshIntervalTicks(
            detachedVisualCandidateRefreshIntervalTicks);
        settings.setDetachedVisualVisibilityCheckIntervalTicks(
            detachedVisualVisibilityCheckIntervalTicks);
        settings.setCollisionLodEnabled(collisionLodEnabled);
        settings.setCollisionLodRadii(collisionLodNearRadius, collisionLodMidRadius);
        settings.setCollisionLodHysteresis(collisionLodHysteresis);
        settings.setCollisionLodRefreshIntervalTicks(collisionLodRefreshIntervalTicks);
        settings.setCollisionLodFarSleepEnabled(collisionLodFarSleepEnabled);
        settings.setDetachedVisualBlockType(detachedVisualBlockType);
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
    private static Validator<Vector3f> finiteVector(@Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(Vector3f value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                if (!Float.isFinite(value.x) || !Float.isFinite(value.y) || !Float.isFinite(value.z)) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
    }

    @Nonnull
    private static Validator<String> nonBlankString(@Nonnull String message) {
        return new Validator<>() {
            @Override
            public void accept(String value, ValidationResults results) {
                if (value == null) {
                    return;
                }
                if (value.isBlank()) {
                    results.fail(message);
                }
            }

            @Override
            public void updateSchema(SchemaContext context, Schema schema) {
            }
        };
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
        copy.internalPgsIterations = internalPgsIterations;
        copy.stabilizationIterations = stabilizationIterations;
        copy.minIslandSize = minIslandSize;
        copy.dynamicSleepLinearThreshold = dynamicSleepLinearThreshold;
        copy.dynamicSleepAngularThreshold = dynamicSleepAngularThreshold;
        copy.dynamicSleepTimeUntilSleep = dynamicSleepTimeUntilSleep;
        copy.executionMode = executionMode;
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
}
