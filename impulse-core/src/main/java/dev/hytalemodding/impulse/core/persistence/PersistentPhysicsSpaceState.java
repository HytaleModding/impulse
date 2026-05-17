package dev.hytalemodding.impulse.core.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.resources.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.systems.PersistentPhysicsSpaceBootstrapSystem;
import dev.hytalemodding.impulse.core.voxel.WorldCollisionMode;
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
            (state, value) -> state.worldCollisionRadius = value,
            PersistentPhysicsSpaceState::getWorldCollisionRadius)
        .add()
        .append(new KeyedCodec<>("WorldCollisionBodyRadius", Codec.INTEGER, false),
            (state, value) -> state.worldCollisionBodyRadius = value,
            PersistentPhysicsSpaceState::getWorldCollisionBodyRadius)
        .add()
        .append(new KeyedCodec<>("WorldCollisionTtlTicks", Codec.INTEGER, false),
            (state, value) -> state.worldCollisionTtlTicks = value,
            PersistentPhysicsSpaceState::getWorldCollisionTtlTicks)
        .add()
        .append(new KeyedCodec<>("VisualFullSyncRadius", Codec.INTEGER, false),
            (state, value) -> state.visualFullSyncRadius = value,
            PersistentPhysicsSpaceState::getVisualFullSyncRadius)
        .add()
        .append(new KeyedCodec<>("VisualMaxSyncRadius", Codec.INTEGER, false),
            (state, value) -> state.visualMaxSyncRadius = value,
            PersistentPhysicsSpaceState::getVisualMaxSyncRadius)
        .add()
        .append(new KeyedCodec<>("VisualFarSyncCutoffEnabled", Codec.BOOLEAN, false),
            (state, value) -> state.visualFarSyncCutoffEnabled = value,
            PersistentPhysicsSpaceState::isVisualFarSyncCutoffEnabled)
        .add()
        .append(new KeyedCodec<>("VisualMidSyncIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.visualMidSyncIntervalTicks = value,
            PersistentPhysicsSpaceState::getVisualMidSyncIntervalTicks)
        .add()
        .append(new KeyedCodec<>("VisualFarSyncIntervalTicks", Codec.INTEGER, false),
            (state, value) -> state.visualFarSyncIntervalTicks = value,
            PersistentPhysicsSpaceState::getVisualFarSyncIntervalTicks)
        .add()
        .append(new KeyedCodec<>("VisualOcclusionMode", new EnumCodec<>(VisualOcclusionMode.class), false),
            (state, value) -> state.visualOcclusionMode = value,
            PersistentPhysicsSpaceState::getVisualOcclusionMode)
        .add()
        .append(new KeyedCodec<>("VisualOcclusionRaycastsPerTick", Codec.INTEGER, false),
            (state, value) -> state.visualOcclusionRaycastsPerTick = value,
            PersistentPhysicsSpaceState::getVisualOcclusionRaycastsPerTick)
        .add()
        .append(new KeyedCodec<>("VisualOcclusionCacheTicks", Codec.INTEGER, false),
            (state, value) -> state.visualOcclusionCacheTicks = value,
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
            (state, value) -> state.detachedVisualMaterializationRadius = value,
            PersistentPhysicsSpaceState::getDetachedVisualMaterializationRadius)
        .add()
        .append(new KeyedCodec<>("DetachedVisualDematerializationRadius", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualDematerializationRadius = value,
            PersistentPhysicsSpaceState::getDetachedVisualDematerializationRadius)
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaxSpawnsPerTick", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualMaxSpawnsPerTick = value,
            PersistentPhysicsSpaceState::getDetachedVisualMaxSpawnsPerTick)
        .add()
        .append(new KeyedCodec<>("DetachedVisualMaxMaterialized", Codec.INTEGER, false),
            (state, value) -> state.detachedVisualMaxMaterialized = value,
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
    @Setter
    private int worldCollisionRadius = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_RADIUS;
    @Setter
    private int worldCollisionBodyRadius = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_BODY_RADIUS;
    @Setter
    private int worldCollisionTtlTicks = PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_TTL_TICKS;
    @Setter
    private int visualFullSyncRadius = PhysicsSpaceSettings.DEFAULT_VISUAL_FULL_SYNC_RADIUS;
    @Setter
    private int visualMaxSyncRadius = PhysicsSpaceSettings.DEFAULT_VISUAL_MAX_SYNC_RADIUS;
    @Setter
    private boolean visualFarSyncCutoffEnabled =
        PhysicsSpaceSettings.DEFAULT_VISUAL_FAR_SYNC_CUTOFF_ENABLED;
    @Setter
    private int visualMidSyncIntervalTicks =
        PhysicsSpaceSettings.DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS;
    @Setter
    private int visualFarSyncIntervalTicks =
        PhysicsSpaceSettings.DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS;
    @Nonnull
    @Setter
    private VisualOcclusionMode visualOcclusionMode =
        PhysicsSpaceSettings.DEFAULT_VISUAL_OCCLUSION_MODE;
    @Setter
    private int visualOcclusionRaycastsPerTick =
        PhysicsSpaceSettings.DEFAULT_VISUAL_OCCLUSION_RAYCASTS_PER_TICK;
    @Setter
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
    private boolean entityVisualSyncCullingEnabled =
        PhysicsSpaceSettings.DEFAULT_ENTITY_VISUAL_SYNC_CULLING_ENABLED;
    @Setter
    private boolean visualVisibilityCullingEnabled =
        PhysicsSpaceSettings.DEFAULT_VISUAL_VISIBILITY_CULLING_ENABLED;
    @Setter
    private boolean detachedVisualMaterializationEnabled =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_MATERIALIZATION_ENABLED;
    @Setter
    private int detachedVisualMaterializationRadius =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_MATERIALIZATION_RADIUS;
    @Setter
    private int detachedVisualDematerializationRadius =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS;
    @Setter
    private int detachedVisualMaxSpawnsPerTick =
        PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK;
    @Setter
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
        applyVisualSyncRadii(settings, visualFullSyncRadius, visualMaxSyncRadius);
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
        settings.setEntityVisualSyncCullingEnabled(entityVisualSyncCullingEnabled);
        settings.setVisualVisibilityCullingEnabled(visualVisibilityCullingEnabled);
        settings.setDetachedVisualMaterializationEnabled(detachedVisualMaterializationEnabled);
        applyDetachedVisualRadii(settings,
            detachedVisualMaterializationRadius,
            detachedVisualDematerializationRadius);
        settings.setDetachedVisualMaxSpawnsPerTick(detachedVisualMaxSpawnsPerTick);
        settings.setDetachedVisualMaxMaterialized(detachedVisualMaxMaterialized);
        settings.setDetachedVisualBlockType(detachedVisualBlockType);
        return settings;
    }

    private static void applyVisualSyncRadii(@Nonnull PhysicsSpaceSettings settings,
        int fullRadius,
        int maxRadius) {
        if (maxRadius < fullRadius) {
            throw new IllegalArgumentException(
                "Visual max sync radius cannot be lower than visual full sync radius");
        }
        if (maxRadius < settings.getVisualFullSyncRadius()) {
            settings.setVisualFullSyncRadius(fullRadius);
            settings.setVisualMaxSyncRadius(maxRadius);
            return;
        }
        settings.setVisualMaxSyncRadius(maxRadius);
        settings.setVisualFullSyncRadius(fullRadius);
    }

    private static void applyDetachedVisualRadii(@Nonnull PhysicsSpaceSettings settings,
        int materializationRadius,
        int dematerializationRadius) {
        if (dematerializationRadius < materializationRadius) {
            throw new IllegalArgumentException(
                "Detached visual dematerialization radius cannot be lower than materialization radius");
        }
        if (materializationRadius > settings.getDetachedVisualDematerializationRadius()) {
            settings.setDetachedVisualDematerializationRadius(dematerializationRadius);
            settings.setDetachedVisualMaterializationRadius(materializationRadius);
            return;
        }
        settings.setDetachedVisualMaterializationRadius(materializationRadius);
        settings.setDetachedVisualDematerializationRadius(dematerializationRadius);
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
