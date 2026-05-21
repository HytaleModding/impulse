package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings.ExecutionMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.internal.systems.PersistentPhysicsSpaceBootstrapSystem;
import dev.hytalemodding.impulse.core.plugin.voxel.WorldCollisionMode;
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
        .add()
        .append(new KeyedCodec<>("BackendId", Codec.STRING),
            (state, value) -> state.backendId = value,
            PersistentPhysicsSpaceState::getBackendId)
        .add()
        .append(new KeyedCodec<>("Gravity", Vector3fUtil.CODEC),
            (state, value) -> state.gravity.set(value),
            PersistentPhysicsSpaceState::getGravity)
        .add()
        .append(new KeyedCodec<>("WorldCollisionMode", new EnumCodec<>(WorldCollisionMode.class), false),
            (state, value) -> state.worldCollisionMode = value,
            PersistentPhysicsSpaceState::getWorldCollisionMode)
        .add()
        .append(new KeyedCodec<>("EntityChunkBoundaryMode",
                new EnumCodec<>(EntityChunkBoundaryMode.class), false),
            (state, value) -> state.entityChunkBoundaryMode = value,
            PersistentPhysicsSpaceState::getEntityChunkBoundaryMode)
        .add()
        .append(new KeyedCodec<>("WorldCollisionRadius", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setWorldCollisionRadius,
            PersistentPhysicsSpaceState::getWorldCollisionRadius)
        .add()
        .append(new KeyedCodec<>("WorldCollisionBodyRadius", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setWorldCollisionBodyRadius,
            PersistentPhysicsSpaceState::getWorldCollisionBodyRadius)
        .add()
        .append(new KeyedCodec<>("WorldCollisionTtlTicks", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setWorldCollisionTtlTicks,
            PersistentPhysicsSpaceState::getWorldCollisionTtlTicks)
        .add()
        .append(new KeyedCodec<>("VisualFullSyncRadius", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setVisualFullSyncRadius,
            PersistentPhysicsSpaceState::getVisualFullSyncRadius)
        .add()
        .append(new KeyedCodec<>("VisualMaxSyncRadius", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setVisualMaxSyncRadius,
            PersistentPhysicsSpaceState::getVisualMaxSyncRadius)
        .add()
        .append(new KeyedCodec<>("VisualFarSyncCutoffEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.visualFarSyncCutoffEnabled = value,
            PersistentPhysicsSpaceState::isVisualFarSyncCutoffEnabled)
        .add()
        .append(new KeyedCodec<>("VisualMidSyncIntervalTicks", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setVisualMidSyncIntervalTicks,
            PersistentPhysicsSpaceState::getVisualMidSyncIntervalTicks)
        .add()
        .append(new KeyedCodec<>("VisualFarSyncIntervalTicks", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setVisualFarSyncIntervalTicks,
            PersistentPhysicsSpaceState::getVisualFarSyncIntervalTicks)
        .add()
        .append(new KeyedCodec<>("VisualOcclusionMode", new EnumCodec<>(VisualOcclusionMode.class), false),
            (state, value) -> state.visualOcclusionMode = value,
            PersistentPhysicsSpaceState::getVisualOcclusionMode)
        .add()
        .append(new KeyedCodec<>("VisualOcclusionRaycastsPerTick", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setVisualOcclusionRaycastsPerTick,
            PersistentPhysicsSpaceState::getVisualOcclusionRaycastsPerTick)
        .add()
        .append(new KeyedCodec<>("VisualOcclusionCacheTicks", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setVisualOcclusionCacheTicks,
            PersistentPhysicsSpaceState::getVisualOcclusionCacheTicks)
        .add()
        .append(new KeyedCodec<>("SolverIterations", Codec.INTEGER, false),
            (state, value) -> state.solverIterations = value,
            PersistentPhysicsSpaceState::getSolverIterations)
        .add()
        .append(new KeyedCodec<>("InternalPgsIterations", Codec.INTEGER, false),
            (state, value) -> state.internalPgsIterations = value,
            PersistentPhysicsSpaceState::getInternalPgsIterations)
        .add()
        .append(new KeyedCodec<>("StabilizationIterations", Codec.INTEGER, false),
            (state, value) -> state.stabilizationIterations = value,
            PersistentPhysicsSpaceState::getStabilizationIterations)
        .add()
        .append(new KeyedCodec<>("MinIslandSize", Codec.INTEGER, false),
            (state, value) -> state.minIslandSize = value,
            PersistentPhysicsSpaceState::getMinIslandSize)
        .add()
        .append(new KeyedCodec<>("DynamicSleepLinearThreshold", Codec.FLOAT, false),
            (state, value) -> state.dynamicSleepLinearThreshold = value,
            PersistentPhysicsSpaceState::getDynamicSleepLinearThreshold)
        .add()
        .append(new KeyedCodec<>("DynamicSleepAngularThreshold", Codec.FLOAT, false),
            (state, value) -> state.dynamicSleepAngularThreshold = value,
            PersistentPhysicsSpaceState::getDynamicSleepAngularThreshold)
        .add()
        .append(new KeyedCodec<>("DynamicSleepTimeUntilSleep", Codec.FLOAT, false),
            (state, value) -> state.dynamicSleepTimeUntilSleep = value,
            PersistentPhysicsSpaceState::getDynamicSleepTimeUntilSleep)
        .add()
        .append(new KeyedCodec<>("ExecutionMode", new EnumCodec<>(ExecutionMode.class), false),
            PersistentPhysicsSpaceState::setExecutionMode,
            PersistentPhysicsSpaceState::getExecutionMode)
        .add()
        .append(new KeyedCodec<>("EntityVisualSyncCullingEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.entityVisualSyncCullingEnabled = value,
            PersistentPhysicsSpaceState::isEntityVisualSyncCullingEnabled)
        .add()
        .append(new KeyedCodec<>("VisualVisibilityCullingEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.visualVisibilityCullingEnabled = value,
            PersistentPhysicsSpaceState::isVisualVisibilityCullingEnabled)
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaterializationEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.detachedVisualMaterializationEnabled = value,
            PersistentPhysicsSpaceState::isDetachedVisualMaterializationEnabled)
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaterializationRadius", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setDetachedVisualMaterializationRadius,
            PersistentPhysicsSpaceState::getDetachedVisualMaterializationRadius)
        .add()
        .append(new KeyedCodec<>("DetachedVisualDematerializationRadius", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setDetachedVisualDematerializationRadius,
            PersistentPhysicsSpaceState::getDetachedVisualDematerializationRadius)
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaxSpawnsPerTick", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setDetachedVisualMaxSpawnsPerTick,
            PersistentPhysicsSpaceState::getDetachedVisualMaxSpawnsPerTick)
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaxMaterialized", Codec.INTEGER, false),
            PersistentPhysicsSpaceState::setDetachedVisualMaxMaterialized,
            PersistentPhysicsSpaceState::getDetachedVisualMaxMaterialized)
        .add()
        .append(new KeyedCodec<>("DetachedVisualBlockType", Codec.STRING, false),
            (state, value) -> state.detachedVisualBlockType = value,
            PersistentPhysicsSpaceState::getDetachedVisualBlockType)
        .add()
        .build();

    @Setter
    private int spaceId;
    @Nonnull
    @Setter
    private String backendId = "";
    @Nonnull
    private final Vector3f gravity = new Vector3f(0.0f, -9.81f, 0.0f);
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
        settings.setDetachedVisualBlockType(detachedVisualBlockType);
        return settings;
    }

    public void setWorldCollisionRadius(int worldCollisionRadius) {
        this.worldCollisionRadius = requirePositiveAtMost(
            "World collision radius",
            worldCollisionRadius,
            PhysicsSpaceSettings.MAX_WORLD_COLLISION_RADIUS);
    }

    public void setWorldCollisionBodyRadius(int worldCollisionBodyRadius) {
        this.worldCollisionBodyRadius = requirePositiveAtMost(
            "World collision body radius",
            worldCollisionBodyRadius,
            PhysicsSpaceSettings.MAX_WORLD_COLLISION_BODY_RADIUS);
    }

    public void setWorldCollisionTtlTicks(int worldCollisionTtlTicks) {
        this.worldCollisionTtlTicks = requirePositiveAtMost(
            "World collision TTL",
            worldCollisionTtlTicks,
            PhysicsSpaceSettings.MAX_WORLD_COLLISION_TTL_TICKS);
    }

    public void setVisualFullSyncRadius(int visualFullSyncRadius) {
        this.visualFullSyncRadius = requirePositiveAtMost(
            "Visual full sync radius",
            visualFullSyncRadius,
            PhysicsSpaceSettings.MAX_VISUAL_FULL_SYNC_RADIUS);
    }

    public void setVisualMaxSyncRadius(int visualMaxSyncRadius) {
        this.visualMaxSyncRadius = requirePositiveAtMost(
            "Visual max sync radius",
            visualMaxSyncRadius,
            PhysicsSpaceSettings.MAX_VISUAL_MAX_SYNC_RADIUS);
    }

    public void setVisualMidSyncIntervalTicks(int visualMidSyncIntervalTicks) {
        this.visualMidSyncIntervalTicks = requirePositiveAtMost(
            "Visual mid sync interval",
            visualMidSyncIntervalTicks,
            PhysicsSpaceSettings.MAX_VISUAL_MID_SYNC_INTERVAL_TICKS);
    }

    public void setVisualFarSyncIntervalTicks(int visualFarSyncIntervalTicks) {
        this.visualFarSyncIntervalTicks = requirePositiveAtMost(
            "Visual far sync interval",
            visualFarSyncIntervalTicks,
            PhysicsSpaceSettings.MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS);
    }

    public void setVisualOcclusionRaycastsPerTick(int visualOcclusionRaycastsPerTick) {
        this.visualOcclusionRaycastsPerTick = requirePositiveAtMost(
            "Visual occlusion raycasts per tick",
            visualOcclusionRaycastsPerTick,
            PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK);
    }

    public void setVisualOcclusionCacheTicks(int visualOcclusionCacheTicks) {
        this.visualOcclusionCacheTicks = requirePositiveAtMost(
            "Visual occlusion cache ticks",
            visualOcclusionCacheTicks,
            PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_CACHE_TICKS);
    }

    public void setDetachedVisualMaterializationRadius(int detachedVisualMaterializationRadius) {
        this.detachedVisualMaterializationRadius = requirePositiveAtMost(
            "Detached visual materialization radius",
            detachedVisualMaterializationRadius,
            PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS);
    }

    public void setDetachedVisualDematerializationRadius(int detachedVisualDematerializationRadius) {
        this.detachedVisualDematerializationRadius = requirePositiveAtMost(
            "Detached visual dematerialization radius",
            detachedVisualDematerializationRadius,
            PhysicsSpaceSettings.MAX_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS);
    }

    public void setDetachedVisualMaxSpawnsPerTick(int detachedVisualMaxSpawnsPerTick) {
        this.detachedVisualMaxSpawnsPerTick = requirePositiveAtMost(
            "Detached visual max spawns per tick",
            detachedVisualMaxSpawnsPerTick,
            PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK);
    }

    public void setDetachedVisualMaxMaterialized(int detachedVisualMaxMaterialized) {
        this.detachedVisualMaxMaterialized = requirePositiveAtMost(
            "Detached visual max materialized",
            detachedVisualMaxMaterialized,
            PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_MATERIALIZED);
    }

    public void setExecutionMode(@Nonnull ExecutionMode executionMode) {
        if (executionMode != ExecutionMode.INLINE) {
            throw new IllegalArgumentException(
                "Worker physics execution is not available yet; use inline execution");
        }
        this.executionMode = executionMode;
    }

    private static int requirePositiveAtMost(@Nonnull String label, int value, int maxValue) {
        if (value < 1 || value > maxValue) {
            throw new IllegalArgumentException(label + " must be between 1 and " + maxValue);
        }
        return value;
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
        copy.detachedVisualBlockType = detachedVisualBlockType;
        return copy;
    }
}
