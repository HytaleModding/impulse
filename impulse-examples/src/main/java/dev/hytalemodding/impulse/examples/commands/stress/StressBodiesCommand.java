package dev.hytalemodding.impulse.examples.commands.stress;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public class StressBodiesCommand extends AbstractAsyncPlayerCommand {

    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_ENTITY_COUNT = 5000;
    private static final int MAX_DETACHED_COUNT = 10000;
    private static final double SPACING = 1.05;
    private static final int DETACHED_VISUAL_MATERIALIZATION_RADIUS = 64;
    private static final int DETACHED_VISUAL_DEMATERIALIZATION_RADIUS = 80;
    private static final int DETACHED_VISUAL_RADIUS_HYSTERESIS =
        DETACHED_VISUAL_DEMATERIALIZATION_RADIUS - DETACHED_VISUAL_MATERIALIZATION_RADIUS;
    private static final int DETACHED_VISUAL_MAX_MATERIALIZED = 10_000;
    private static final int DETACHED_VISUAL_MAX_SPAWNS_PER_TICK = 128;
    private static final int STRESS_BODY_WORLD_COLLISION_RADIUS = 8;
    private static final PhysicsBackendExtensionId RAPIER_SOLVER_EXTENSION_ID =
        new PhysicsBackendExtensionId("impulse:rapier_solver");
    private static final String RAPIER_INTERNAL_PGS_ITERATIONS = "internalPgsIterations";
    private final OptionalArg<Integer> countArg = this.withOptionalArg(
        "count",
        "Number of dynamic boxes to spawn",
        ArgTypes.INTEGER);
    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "Stress mode: detached-view (default), detached, or entity",
        ArgTypes.STRING);
    private final OptionalArg<String> visibilityArg = this.withOptionalArg(
        "visibility",
        "Detached-view visibility: range (default) or cone",
        ArgTypes.STRING);
    private final OptionalArg<String> collisionsArg = this.withOptionalArg(
        "collisions",
        "Detached collision policy: world (default) or body/full",
        ArgTypes.STRING);
    private final OptionalArg<Integer> visualRadiusArg = this.withOptionalArg(
        "visualRadius",
        "Detached-view visual materialization radius",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> visualDematerializeRadiusArg = this.withOptionalArg(
        "visualDematerializeRadius",
        "Detached-view visual dematerialization radius",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> visualSpawnRateArg = this.withOptionalArg(
        "visualSpawnRate",
        "Detached-view visual proxy spawns per tick",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> visualCapArg = this.withOptionalArg(
        "visualCap",
        "Detached-view maximum materialized visual proxies",
        ArgTypes.INTEGER);
    private final OptionalArg<String> visualPredictionArg = this.withOptionalArg(
        "visualPrediction",
        "Predict detached-view visual poses between snapshots: true or false",
        ArgTypes.STRING);
    private final OptionalArg<String> visualSmoothingArg = this.withOptionalArg(
        "visualSmoothing",
        "Smooth detached-view visual poses toward snapshots: true or false",
        ArgTypes.STRING);
    private final OptionalArg<String> collisionLodArg = this.withOptionalArg(
        "collisionLod",
        "Enable collision LOD for default dynamic filters: true or false",
        ArgTypes.STRING);
    private final OptionalArg<String> blockTypeArg = this.withOptionalArg(
        "blockType",
        "Hytale block type for entity-backed or detached-view visual proxies",
        ArgTypes.STRING);
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public StressBodiesCommand() {
        super("bodies", "Spawn many dynamic box bodies");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        Vector3d playerPos = ExamplePhysicsUtils.playerPosition(ctx, store, ref);
        if (playerPos == null) {
            return CompletableFuture.completedFuture(null);
        }

        StressMode mode = parseMode(ctx);
        if (mode == null) {
            return CompletableFuture.completedFuture(null);
        }
        StressVisibility visibility = parseVisibility(ctx);
        if (visibility == null) {
            return CompletableFuture.completedFuture(null);
        }
        StressCollisionPolicy collisionPolicy = parseCollisionPolicy(ctx, mode);
        if (collisionPolicy == null) {
            return CompletableFuture.completedFuture(null);
        }
        Boolean collisionLod = parseCollisionLod(ctx);
        if (collisionLodArg.provided(ctx) && collisionLod == null) {
            return CompletableFuture.completedFuture(null);
        }

        int count = ExamplePhysicsUtils.optionalInt(ctx, countArg, DEFAULT_COUNT, 1, mode.maxCount());
        StressVisualSettings visualSettings = parseVisualSettings(ctx);
        if (visualSettings == null) {
            return CompletableFuture.completedFuture(null);
        }
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        PhysicsSpaceSettings settings = configureStressRuntime(resource,
            spaceId,
            mode,
            visibility,
            visualSettings,
            collisionLod);
        TimeResource time = store.getResource(TimeResource.getResourceType());

        StressLayout layout = StressLayout.forCount(count, playerPos);
        long prewarmStartNanos = System.nanoTime();
        int prewarmedSections = prewarmStressWorldCollision(world,
            resource,
            spaceId,
            settings,
            mode,
            layout,
            count);
        long prewarmNanos = System.nanoTime() - prewarmStartNanos;

        long serverTick = Math.max(0L, world.getTick());
        StressSpawnTiming timing;
        if (mode == StressMode.ENTITY) {
            PhysicsShapeSpec box = PhysicsShapeSpec.box(0.48f, 0.48f, 0.48f);
            RigidBodySpawnSettings spawnSettings = RigidBodySpawnSettings.material(0.65f, 0.15f);
            ExamplePhysicsUtils.BlockBodyBatchTiming batchTiming = ExamplePhysicsUtils.spawnBlockBodiesMeasured(store,
                time,
                resource,
                serverTick,
                spaceId,
                count,
                visualSettings.blockType(),
                box,
                1.0f,
                spawnSettings,
                bodies -> {
                    for (int i = 0; i < count; i++) {
                        bodies.addBody(layout.positionX(i),
                            layout.positionY(i),
                            layout.positionZ(i));
                    }
                });
            timing = new StressSpawnTiming(batchTiming.commandApplyNanos() + batchTiming.entityAttachNanos(),
                batchTiming.commandApplyNanos(),
                batchTiming.entityAttachNanos());
        } else {
            PhysicsShapeSpec box = PhysicsShapeSpec.box(0.48f, 0.48f, 0.48f);
            RigidBodySpawnSettings spawnSettings = detachedSpawnSettings(collisionPolicy);
            ExamplePhysicsUtils.BodyRequestBatchTiming batchTiming =
                ExamplePhysicsUtils.enqueueDynamicBodyBatchMeasured(world,
                    spaceId,
                    count,
                    box,
                    1.0f,
                    spawnSettings,
                    PhysicsBodyKind.BODY,
                    PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                    bodies -> {
                        for (int i = 0; i < count; i++) {
                            bodies.addBody(layout.positionX(i),
                                layout.positionY(i),
                                layout.positionZ(i));
                        }
                    });
            timing = new StressSpawnTiming(batchTiming.setupWallNanos(),
                batchTiming.requestEnqueueNanos(),
                0L);
        }
        PhysicsWorldCollisionSettings worldCollisionSettings =
            settings.getWorldCollisionSettings();
        PhysicsVisualMaterializationSettings visualMaterializationSettings =
            settings.getVisualMaterializationSettings();
        PhysicsVisualSyncSettings visualSyncSettings = settings.getVisualSyncSettings();
        PhysicsCollisionLodSettings collisionLodSettings = settings.getCollisionLodSettings();
        PhysicsWorldSettings worldSettings = resource.getWorldSettings();

        ctx.sender().sendMessage(Message.raw("Queued " + count
            + " stress bodies: setupWallMs="
            + millis(prewarmNanos + timing.setupWallNanos())
            + " prewarmMs=" + millis(prewarmNanos)
            + " requestEnqueueMs=" + millis(timing.requestEnqueueNanos())
            + (timing.entityAttachNanos() > 0L
                ? " entityAttachMs=" + millis(timing.entityAttachNanos())
                : "")
            + ": mode=" + mode.serialized()
            + " space=" + spaceId.value()
            + " worldCollision=streaming"
            + " bodyCollisionRadius=" + worldCollisionSettings.getWorldCollisionBodyRadius()
            + " prewarmedSections=" + prewarmedSections
            + " step=" + worldSettings.getStepMode().getSerializedName()
            + "/" + worldSettings.getSimulationSteps()
            + " maxStepDt=" + String.format(Locale.ROOT, "%.3f", worldSettings.getMaxStepDt())
            + " visuals=" + mode.visualDescription()
            + (mode == StressMode.ENTITY ? " blockType=" + visualSettings.blockType() : "")
            + (mode.usesDetachedBodies()
                ? " body-count and detached-view snapshots update after PhysicsStore drains queued requests"
                : "")
            + (mode == StressMode.DETACHED_VIEW
                ? " visualProxyCap="
                + visualMaterializationSettings.getDetachedVisualMaxMaterialized()
                + " visualRadius="
                + visualMaterializationSettings.getDetachedVisualMaterializationRadius()
                + " visualDematerializeRadius="
                + visualMaterializationSettings.getDetachedVisualDematerializationRadius()
                + " visualSpawnRate="
                + visualMaterializationSettings.getDetachedVisualMaxSpawnsPerTick() + "/tick"
                + " visualMaterialization=progressive"
                + " blockType=" + visualMaterializationSettings.getDetachedVisualBlockType()
                + " visualPrediction=" + visualSyncSettings.isVisualSnapshotPredictionEnabled()
                + " visualSmoothing=" + visualSyncSettings.isVisualSnapshotSmoothingEnabled()
                + " visibility=" + visibility.serialized()
                + " collisions=" + collisionPolicy.serialized()
                + " collisionLod=" + collisionLodSettings.isCollisionLodEnabled()
                : "")
            + "."));
        return CompletableFuture.completedFuture(null);
    }

    @Nonnull
    private static PhysicsSpaceSettings configureStressRuntime(@Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull StressMode mode,
        @Nonnull StressVisibility visibility,
        @Nonnull StressVisualSettings visualSettings,
        @Nullable Boolean collisionLod) {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(resource.getSpaceSettings(spaceId));
        PhysicsSolverSettings solverSettings = settings.getSolverSettings();
        solverSettings.setSolverIterations(1);
        solverSettings.setStabilizationIterations(1);
        settings.getExtensionSettings().setInt(RAPIER_SOLVER_EXTENSION_ID,
            RAPIER_INTERNAL_PGS_ITERATIONS,
            1);
        solverSettings.setDynamicSleepTuning(
            PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_LINEAR_THRESHOLD,
            PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_ANGULAR_THRESHOLD,
            PhysicsSolverSettings.DEFAULT_DYNAMIC_SLEEP_TIME_UNTIL_SLEEP);
        PhysicsWorldCollisionSettings worldCollisionSettings =
            settings.getWorldCollisionSettings();
        worldCollisionSettings.setWorldCollisionMode(WorldCollisionMode.STREAMING);
        worldCollisionSettings.setWorldCollisionBodyRadius(
            Math.max(worldCollisionSettings.getWorldCollisionBodyRadius(),
                STRESS_BODY_WORLD_COLLISION_RADIUS));
        if (mode.usesDetachedBodies()) {
            PhysicsVisualMaterializationSettings visualMaterializationSettings =
                settings.getVisualMaterializationSettings();
            PhysicsVisualSyncSettings visualSyncSettings = settings.getVisualSyncSettings();
            visualMaterializationSettings.setDetachedVisualMaterializationEnabled(
                mode == StressMode.DETACHED_VIEW);
            visualSyncSettings.setVisualVisibilityCullingEnabled(mode == StressMode.DETACHED_VIEW
                && visibility == StressVisibility.CONE);
            visualSyncSettings.setVisualFarSyncCutoffEnabled(false);
            visualSyncSettings.setVisualOcclusionMode(VisualOcclusionMode.OFF);
            visualMaterializationSettings.setDetachedVisualRadii(visualSettings.materializationRadius(),
                visualSettings.dematerializationRadius());
            visualMaterializationSettings.setDetachedVisualMaxMaterialized(visualSettings.maxMaterialized());
            visualMaterializationSettings.setDetachedVisualMaxSpawnsPerTick(visualSettings.spawnRate());
            visualMaterializationSettings.setDetachedVisualBlockType(visualSettings.blockType());
            visualSyncSettings.setVisualSnapshotPredictionEnabled(visualSettings.predictionEnabled());
            visualSyncSettings.setVisualSnapshotSmoothingEnabled(visualSettings.smoothingEnabled());
            if (collisionLod != null) {
                settings.getCollisionLodSettings().setCollisionLodEnabled(collisionLod);
            }
        }
        resource.setSpaceSettings(spaceId, settings);
        return settings;
    }

    private static int prewarmStressWorldCollision(@Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull StressMode mode,
        @Nonnull StressLayout layout,
        int count) {
        PhysicsWorldCollisionSettings worldCollisionSettings =
            settings.getWorldCollisionSettings();
        if (!mode.usesDetachedBodies()
            || worldCollisionSettings.getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
            return 0;
        }

        WorldCollisionPrewarmStats stats = resource.ensureWorldCollisionAround(world,
            spaceId,
            layout.positions(count),
            worldCollisionSettings.getWorldCollisionBodyRadius(),
            0L);
        return stats.sectionTargets();
    }

    @Nonnull
    private static RigidBodySpawnSettings detachedSpawnSettings(
        @Nonnull StressCollisionPolicy collisionPolicy) {
        int collisionMask = collisionPolicy == StressCollisionPolicy.WORLD
            ? PhysicsCollisionFilters.TERRAIN
            : PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY;
        return RigidBodySpawnSettings.of(0.45f,
            0.0f,
            0.02f,
            0.25f,
            PhysicsCollisionFilters.DYNAMIC_BODY,
            collisionMask);
    }

    @Nullable
    private StressMode parseMode(@Nonnull CommandContext ctx) {
        if (!modeArg.provided(ctx)) {
            return StressMode.DETACHED_VIEW;
        }

        String rawMode = modeArg.get(ctx).toLowerCase(Locale.ROOT);
        StressMode mode = StressMode.from(rawMode);
        if (mode == null) {
            ctx.sender().sendMessage(Message.raw("Unknown stress bodies mode '" + rawMode
                + "'. Expected entity, detached, or detached-view."));
        }
        return mode;
    }

    @Nullable
    private StressVisibility parseVisibility(@Nonnull CommandContext ctx) {
        if (!visibilityArg.provided(ctx)) {
            return StressVisibility.RANGE;
        }

        String rawVisibility = visibilityArg.get(ctx).toLowerCase(Locale.ROOT);
        StressVisibility visibility = StressVisibility.from(rawVisibility);
        if (visibility == null) {
            ctx.sender().sendMessage(Message.raw("Unknown stress bodies visibility '"
                + rawVisibility
                + "'. Expected cone or range."));
        }
        return visibility;
    }

    @Nullable
    private StressCollisionPolicy parseCollisionPolicy(@Nonnull CommandContext ctx,
        @Nonnull StressMode mode) {
        if (!mode.usesDetachedBodies()) {
            return StressCollisionPolicy.FULL;
        }
        if (!collisionsArg.provided(ctx)) {
            return StressCollisionPolicy.WORLD;
        }

        String rawPolicy = collisionsArg.get(ctx).toLowerCase(Locale.ROOT);
        StressCollisionPolicy policy = StressCollisionPolicy.from(rawPolicy);
        if (policy == null) {
            ctx.sender().sendMessage(Message.raw("Unknown stress bodies collisions '"
                + rawPolicy
                + "'. Expected world or full."));
        }
        return policy;
    }

    @Nullable
    private Boolean parseCollisionLod(@Nonnull CommandContext ctx) {
        if (!collisionLodArg.provided(ctx)) {
            return null;
        }
        Boolean collisionLod = parseBoolean(collisionLodArg.get(ctx));
        if (collisionLod == null) {
            ctx.sender().sendMessage(Message.raw("collisionLod must be true or false."));
        }
        return collisionLod;
    }

    @Nullable
    private StressVisualSettings parseVisualSettings(@Nonnull CommandContext ctx) {
        int materializationRadius = ExamplePhysicsUtils.optionalInt(ctx,
            visualRadiusArg,
            DETACHED_VISUAL_MATERIALIZATION_RADIUS,
            1,
            PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS);
        int dematerializationRadius = ExamplePhysicsUtils.optionalInt(ctx,
            visualDematerializeRadiusArg,
            defaultDematerializationRadius(materializationRadius),
            materializationRadius,
            PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS);
        int spawnRate = ExamplePhysicsUtils.optionalInt(ctx,
            visualSpawnRateArg,
            DETACHED_VISUAL_MAX_SPAWNS_PER_TICK,
            1,
            PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK);
        int maxMaterialized = ExamplePhysicsUtils.optionalInt(ctx,
            visualCapArg,
            DETACHED_VISUAL_MAX_MATERIALIZED,
            1,
            PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_MAX_MATERIALIZED);
        boolean predictionEnabled = false;
        if (visualPredictionArg.provided(ctx)) {
            Boolean parsed = parseBoolean(visualPredictionArg.get(ctx));
            if (parsed == null) {
                ctx.sender().sendMessage(Message.raw("visualPrediction must be true or false."));
                return null;
            }
            predictionEnabled = parsed;
        }
        boolean smoothingEnabled = true;
        if (visualSmoothingArg.provided(ctx)) {
            Boolean parsed = parseBoolean(visualSmoothingArg.get(ctx));
            if (parsed == null) {
                ctx.sender().sendMessage(Message.raw("visualSmoothing must be true or false."));
                return null;
            }
            smoothingEnabled = parsed;
        }
        return new StressVisualSettings(materializationRadius,
            dematerializationRadius,
            spawnRate,
            maxMaterialized,
            blockType(ctx),
            predictionEnabled,
            smoothingEnabled);
    }

    @Nonnull
    private String blockType(@Nonnull CommandContext ctx) {
        return blockTypeArg.provided(ctx)
            ? ExamplePhysicsUtils.resolveBlockType(blockTypeArg.get(ctx))
            : ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE;
    }

    private static int defaultDematerializationRadius(int materializationRadius) {
        return Math.min(PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS,
            materializationRadius + DETACHED_VISUAL_RADIUS_HYSTERESIS);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    @Nullable
    private static Boolean parseBoolean(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "enabled" -> true;
            case "false", "no", "off", "disabled" -> false;
            default -> null;
        };
    }

    private enum StressMode {
        ENTITY("entity"),
        DETACHED("detached"),
        DETACHED_VIEW("detached-view");

        private final String serialized;

        StressMode(@Nonnull String serialized) {
            this.serialized = serialized;
        }

        @Nonnull
        private String serialized() {
            return serialized;
        }

        private int maxCount() {
            return this == ENTITY ? MAX_ENTITY_COUNT : MAX_DETACHED_COUNT;
        }

        private boolean usesDetachedBodies() {
            return this == DETACHED || this == DETACHED_VIEW;
        }

        @Nonnull
        private String visualDescription() {
            return switch (this) {
                case ENTITY -> "entity-backed";
                case DETACHED -> "none";
                case DETACHED_VIEW -> "detached-proxies near real players";
            };
        }

        @Nullable
        private static StressMode from(@Nonnull String value) {
            return switch (value) {
                case "entity", "entities", "hytale", "hytale-entities", "entity-backed" -> ENTITY;
                case "detached", "managed", "physics-only", "physics_only" -> DETACHED;
                case "detached-view", "detached-viewer", "view", "viewed" -> DETACHED_VIEW;
                default -> null;
            };
        }
    }

    private enum StressVisibility {
        CONE("cone"),
        RANGE("range");

        private final String serialized;

        StressVisibility(@Nonnull String serialized) {
            this.serialized = serialized;
        }

        @Nonnull
        private String serialized() {
            return serialized;
        }

        @Nullable
        private static StressVisibility from(@Nonnull String value) {
            return switch (value) {
                case "cone", "view", "camera" -> CONE;
                case "range", "radius", "distance" -> RANGE;
                default -> null;
            };
        }
    }

    private enum StressCollisionPolicy {
        WORLD("world"),
        FULL("body");

        private final String serialized;

        StressCollisionPolicy(@Nonnull String serialized) {
            this.serialized = serialized;
        }

        @Nonnull
        private String serialized() {
            return serialized;
        }

        @Nullable
        private static StressCollisionPolicy from(@Nonnull String value) {
            return switch (value) {
                case "world", "terrain", "terrain-only", "world-only" -> WORLD;
                case "full", "body", "bodies", "body-body", "dynamic" -> FULL;
                default -> null;
            };
        }
    }

    private record StressVisualSettings(int materializationRadius,
                                        int dematerializationRadius,
                                        int spawnRate,
                                        int maxMaterialized,
                                        @Nonnull String blockType,
                                        boolean predictionEnabled,
                                        boolean smoothingEnabled) {
    }

    private record StressSpawnTiming(long setupWallNanos,
                                     long requestEnqueueNanos,
                                     long entityAttachNanos) {

        private StressSpawnTiming {
            setupWallNanos = Math.max(0L, setupWallNanos);
            requestEnqueueNanos = Math.max(0L, requestEnqueueNanos);
            entityAttachNanos = Math.max(0L, entityAttachNanos);
        }
    }

    private record StressLayout(@Nonnull Vector3d origin,
        int side,
        double spacing) {

        @Nonnull
        private static StressLayout forCount(int count,
            @Nonnull Vector3d playerPos) {
            int side = (int) Math.ceil(Math.cbrt(count));
            return new StressLayout(
                new Vector3d(playerPos).add(
                    -side * SPACING * 0.5,
                    5.0,
                    4.0 - side * SPACING * 0.5),
                side,
                SPACING);
        }

        @Nonnull
        private Vector3d position(int index) {
            int x = index % side;
            int z = (index / side) % side;
            int y = index / (side * side);
            return new Vector3d(origin).add(x * spacing, y * spacing, z * spacing);
        }

        private float positionX(int index) {
            int x = index % side;
            return (float) (origin.x + x * spacing);
        }

        private float positionY(int index) {
            int y = index / (side * side);
            return (float) (origin.y + y * spacing);
        }

        private float positionZ(int index) {
            int z = (index / side) % side;
            return (float) (origin.z + z * spacing);
        }

        @Nonnull
        private Iterable<Vector3d> positions(int count) {
            return () -> new Iterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < count;
                }

                @Override
                public Vector3d next() {
                    return position(index++);
                }
            };
        }
    }
}
