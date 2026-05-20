package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
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
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.voxel.WorldCollisionMode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
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
    private static final int DETACHED_VISUAL_MAX_SPAWNS_PER_TICK = 128;
    private static final int STRESS_BODY_WORLD_COLLISION_RADIUS = 8;
    private static final List<PhysicsBodyId> STRESS_DETACHED_BODIES = new ArrayList<>();

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

        int count = ExamplePhysicsUtils.optionalInt(ctx, countArg, DEFAULT_COUNT, 1, mode.maxCount());
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        if (mode.usesDetachedBodies()) {
            clearPreviousStressDetachedBodies(store, resource);
        }
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }
        PhysicsSpaceSettings settings = configureStressRuntime(resource, space, mode, visibility, count);
        TimeResource time = store.getResource(TimeResource.getResourceType());

        StressLayout layout = StressLayout.forMode(mode, count, playerPos);
        int prewarmedSections = prewarmStressWorldCollision(world,
            resource,
            space,
            settings,
            mode,
            layout,
            count);

        long startNanos = System.nanoTime();
        for (int i = 0; i < count; i++) {
            Vector3d position = layout.position(i);
            if (mode == StressMode.ENTITY) {
                PhysicsBody body = createEntityBody(space);
                ExamplePhysicsUtils.spawnBlockBody(store, time, resource, space.getId(), space, body, position);
                continue;
            }

            PhysicsBody body = createDetachedBody(space, collisionPolicy);
            body.setPosition((float) position.x, (float) position.y, (float) position.z);
            PhysicsBodyId bodyId = resource.addBody(space.getId(),
                body,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY);
            rememberStressDetachedBody(bodyId);
        }
        if (mode.usesDetachedBodies()) {
            resource.refreshBodySnapshots();
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        ctx.sender().sendMessage(Message.raw("Spawned " + count
            + " stress bodies in "
            + millis(elapsedNanos)
            + " ms: mode=" + mode.serialized()
            + " space=" + space.getId().value()
            + " worldCollision=streaming"
            + " bodyCollisionRadius=" + settings.getWorldCollisionBodyRadius()
            + " prewarmedSections=" + prewarmedSections
            + " step=" + resource.getStepMode().getSerializedName()
            + "/" + resource.getSimulationSteps()
            + " maxStepDt=" + String.format(Locale.ROOT, "%.3f", resource.getMaxStepDt())
            + " visuals=" + mode.visualDescription()
            + (mode == StressMode.DETACHED_VIEW
                ? " visualProxyCap=" + count
                + " visualSpawnRate=" + DETACHED_VISUAL_MAX_SPAWNS_PER_TICK + "/tick"
                + " visibility=" + visibility.serialized()
                + " collisions=" + collisionPolicy.serialized()
                : "")
            + "."));
        return CompletableFuture.completedFuture(null);
    }

    @Nonnull
    private static PhysicsSpaceSettings configureStressRuntime(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull StressMode mode,
        @Nonnull StressVisibility visibility,
        int count) {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(resource.getSpaceSettings(space.getId()));
        settings.setSolverIterations(1);
        settings.setInternalPgsIterations(1);
        settings.setStabilizationIterations(1);
        settings.setDynamicSleepTuning(
            PhysicsSpaceSettings.DEFAULT_DYNAMIC_SLEEP_LINEAR_THRESHOLD,
            PhysicsSpaceSettings.DEFAULT_DYNAMIC_SLEEP_ANGULAR_THRESHOLD,
            PhysicsSpaceSettings.DEFAULT_DYNAMIC_SLEEP_TIME_UNTIL_SLEEP);
        settings.setWorldCollisionMode(WorldCollisionMode.STREAMING);
        settings.setWorldCollisionBodyRadius(Math.max(settings.getWorldCollisionBodyRadius(),
            STRESS_BODY_WORLD_COLLISION_RADIUS));
        if (mode.usesDetachedBodies()) {
            settings.setDetachedVisualMaterializationEnabled(mode == StressMode.DETACHED_VIEW);
            settings.setVisualVisibilityCullingEnabled(mode == StressMode.DETACHED_VIEW
                && visibility == StressVisibility.CONE);
            settings.setDetachedVisualMaterializationRadius(DETACHED_VISUAL_MATERIALIZATION_RADIUS);
            settings.setDetachedVisualDematerializationRadius(DETACHED_VISUAL_DEMATERIALIZATION_RADIUS);
            settings.setDetachedVisualMaxMaterialized(Math.max(1, count));
            settings.setDetachedVisualMaxSpawnsPerTick(DETACHED_VISUAL_MAX_SPAWNS_PER_TICK);
        }
        resource.setSpaceSettings(space.getId(), settings);
        return settings;
    }

    private static int prewarmStressWorldCollision(@Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull StressMode mode,
        @Nonnull StressLayout layout,
        int count) {
        if (!mode.usesDetachedBodies() || settings.getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
            return 0;
        }

        LongSet visitedSections = new LongOpenHashSet();
        for (int i = 0; i < count; i++) {
            Vector3d position = layout.position(i);
            resource.getWorldVoxelCollisionCache().ensureAround(world,
                space,
                position,
                settings.getWorldCollisionBodyRadius(),
                0L,
                null,
                visitedSections);
        }
        return visitedSections.size();
    }

    private static void rememberStressDetachedBody(@Nonnull PhysicsBodyId bodyId) {
        synchronized (STRESS_DETACHED_BODIES) {
            STRESS_DETACHED_BODIES.add(bodyId);
        }
    }

    private static void clearPreviousStressDetachedBodies(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource) {
        List<PhysicsBodyId> bodyIds;
        synchronized (STRESS_DETACHED_BODIES) {
            if (STRESS_DETACHED_BODIES.isEmpty()) {
                return;
            }
            bodyIds = new ArrayList<>(STRESS_DETACHED_BODIES);
            STRESS_DETACHED_BODIES.clear();
        }

        for (PhysicsBodyId bodyId : bodyIds) {
            Ref<EntityStore> proxy = resource.getGeneratedVisualProxy(bodyId);
            if (proxy != null && proxy.isValid()) {
                store.removeEntity(proxy, RemoveReason.REMOVE);
            }
            resource.destroyBody(bodyId);
        }
    }

    @Nonnull
    private static PhysicsBody createEntityBody(@Nonnull PhysicsSpace space) {
        PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
        body.setFriction(0.65f);
        body.setRestitution(0.15f);
        return body;
    }

    @Nonnull
    private static PhysicsBody createDetachedBody(@Nonnull PhysicsSpace space,
        @Nonnull StressCollisionPolicy collisionPolicy) {
        PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
        body.setFriction(0.45f);
        body.setRestitution(0.0f);
        body.setDamping(0.02f, 0.25f);
        if (collisionPolicy == StressCollisionPolicy.WORLD) {
            body.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY, PhysicsCollisionFilters.TERRAIN);
        } else {
            body.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
        }
        return body;
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

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
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

    private record StressLayout(@Nonnull StressMode mode,
        @Nonnull Vector3d origin,
        int side,
        double spacing,
        boolean flat) {

        @Nonnull
        private static StressLayout forMode(@Nonnull StressMode mode,
            int count,
            @Nonnull Vector3d playerPos) {
            if (mode.usesDetachedBodies()) {
                int side = (int) Math.ceil(Math.cbrt(count));
                return new StressLayout(mode,
                    new Vector3d(playerPos).add(
                        -side * SPACING * 0.5,
                        5.0,
                        4.0 - side * SPACING * 0.5),
                    side,
                    SPACING,
                    false);
            }

            int side = (int) Math.ceil(Math.cbrt(count));
            return new StressLayout(mode,
                new Vector3d(playerPos).add(
                    -side * SPACING * 0.5,
                    5.0,
                    4.0 - side * SPACING * 0.5),
                side,
                SPACING,
                false);
        }

        @Nonnull
        private Vector3d position(int index) {
            int x = index % side;
            int z = flat ? index / side : (index / side) % side;
            int y = flat ? 0 : index / (side * side);
            return new Vector3d(origin).add(x * spacing, y * spacing, z * spacing);
        }
    }
}
