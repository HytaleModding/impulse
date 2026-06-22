package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.EventTitleUtil;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointType;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsJointEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.physics.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils.CreatedBlockBody;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class DirectionalPendulumsCommand extends AbstractAsyncPlayerCommand {

    private static final float GRAVITY = 9.81f;
    private static final float PIVOT_HALF_SIZE = 0.25f;
    private static final float LINK_HALF_LENGTH = 1.0f;
    private static final float LINK_THICKNESS = 0.18f;
    private static final float UPPER_SWING_SPEED = 0.9f;
    private static final float LOWER_RELATIVE_SWING_SPEED = -0.55f;
    private static final float CINEMATIC_UPPER_SWING_SPEED = 2.1f;
    private static final float CINEMATIC_LOWER_RELATIVE_SWING_SPEED = -1.2f;
    static final String PENDULUM_BLOCK_TYPE = "Rock_Marble";
    static final String CINEMATIC_JOLT_BLOCK_TYPE = "Rock_Aqua";
    static final String CINEMATIC_RAPIER_BLOCK_TYPE = "Rock_Basalt";
    static final float PENDULUM_VISUAL_ORIGIN_OFFSET_Y = 0.5f;
    static final float PENDULUM_SLEEP_LINEAR_THRESHOLD = 0.0f;
    static final float PENDULUM_SLEEP_ANGULAR_THRESHOLD = 0.0f;
    static final float PENDULUM_TIME_UNTIL_SLEEP = Float.MAX_VALUE;
    private static final float CINEMATIC_GRAVITY_ARROW_DURATION_SECONDS = 12.0f;
    private static final BackendId JOLT_BACKEND_ID = new BackendId("impulse:jolt");
    private static final BackendId RAPIER_BACKEND_ID = new BackendId("impulse:rapier");
    private static final RigidBodySpawnSettings PENDULUM_BODY_SETTINGS =
        RigidBodySpawnSettings.material(0.6f, 0.05f)
            .withCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.TERRAIN);

    private static final DirectionSpec[] DIRECTIONS = {
        new DirectionSpec(new Vector3f(0.0f, -GRAVITY, 0.0f),
            new Vector3d(-8.0, 0.0, -8.0),
            new Vector3d(1.0, 0.0, 0.0),
            BackendSlot.JOLT),
        new DirectionSpec(new Vector3f(0.0f, GRAVITY, 0.0f),
            new Vector3d(8.0, 0.0, -8.0),
            new Vector3d(1.0, 0.0, 0.0),
            BackendSlot.JOLT),
        new DirectionSpec(new Vector3f(GRAVITY, 0.0f, 0.0f),
            new Vector3d(-8.0, 0.0, 8.0),
            new Vector3d(0.0, 0.0, 1.0),
            BackendSlot.RAPIER),
        new DirectionSpec(new Vector3f(-GRAVITY, 0.0f, 0.0f),
            new Vector3d(8.0, 0.0, 8.0),
            new Vector3d(0.0, 0.0, 1.0),
            BackendSlot.RAPIER)
    };

    private static final DirectionSpec[] CINEMATIC_DIRECTIONS = {
        new DirectionSpec(new Vector3f(0.0f, -GRAVITY, 0.0f),
            new Vector3d(-4.0, 0.0, -4.0),
            new Vector3d(1.0, 0.0, 0.0),
            BackendSlot.JOLT),
        new DirectionSpec(new Vector3f(0.0f, GRAVITY, 0.0f),
            new Vector3d(4.0, 0.0, -4.0),
            new Vector3d(1.0, 0.0, 0.0),
            BackendSlot.JOLT),
        new DirectionSpec(new Vector3f(GRAVITY, 0.0f, 0.0f),
            new Vector3d(-4.0, 0.0, 4.0),
            new Vector3d(0.0, 0.0, 1.0),
            BackendSlot.RAPIER),
        new DirectionSpec(new Vector3f(-GRAVITY, 0.0f, 0.0f),
            new Vector3d(4.0, 0.0, 4.0),
            new Vector3d(0.0, 0.0, 1.0),
            BackendSlot.RAPIER)
    };

    private final OptionalArg<String> presetArg = this.withOptionalArg(
        "preset",
        "Preset: default, clip, cinematic, or gravity-compass",
        ArgTypes.STRING);

    public DirectionalPendulumsCommand() {
        super("directional-pendulums",
            "Spawn four non-streaming spaces with directional double pendulums");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        Preset preset = parsePreset(ctx);
        if (preset == null) {
            return CompletableFuture.completedFuture(null);
        }

        BackendSelection backendSelection = resolveBackendSelection(ctx);
        if (backendSelection == null) {
            return CompletableFuture.completedFuture(null);
        }

        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        Vector3d playerPosition = new Vector3d(playerRef.getTransform().getPosition());
        Vector3d center = playerPosition.add(0.0, 6.0, 8.0);
        try {
            SpawnResult result = spawnDemo(physicsStore, backendSelection, center, preset);
            TimeResource time = store.getResource(TimeResource.getResourceType());
            for (CreatedBlockBody createdBody : result.createdBodies()) {
                ExamplePhysicsUtils.attachBlockBody(store, time, createdBody);
            }
            if (preset == Preset.CINEMATIC) {
                try {
                    showGravityCompassPresentation(world, playerRef, result);
                } catch (RuntimeException presentationException) {
                    ctx.sender().sendMessage(Message.raw("Spawned Gravity Compass, but cinematic "
                        + "presentation failed: " + presentationException.getMessage()));
                }
            }
            ctx.sender().sendMessage(Message.raw("Spawned four directional double pendulum spaces "
                + "with physicsChunk=none, backends=2x " + backendSelection.joltBackendId().value()
                + "/2x " + backendSelection.rapierBackendId().value()
                + ", preset=" + preset.label()
                + ", blocks=" + preset.blockSummary()
                + ": " + spaceIds(result) + "."));
        } catch (RuntimeException exception) {
            ctx.sender().sendMessage(Message.raw("Failed to spawn directional pendulums: "
                + exception.getMessage()));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Nonnull
    static SpawnResult spawnDemo(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull BackendId backendId,
        @Nonnull Vector3d center) {
        return spawnDemo(physicsStore, backendId, center, Preset.DEFAULT);
    }

    @Nonnull
    static SpawnResult spawnDemo(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull BackendId backendId,
        @Nonnull Vector3d center,
        @Nonnull Preset preset) {
        return spawnDemo(physicsStore,
            new BackendSelection(backendId, backendId),
            center,
            preset);
    }

    @Nonnull
    static SpawnResult spawnDemo(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull BackendId joltBackendId,
        @Nonnull BackendId rapierBackendId,
        @Nonnull Vector3d center) {
        return spawnDemo(physicsStore, joltBackendId, rapierBackendId, center, Preset.DEFAULT);
    }

    @Nonnull
    static SpawnResult spawnDemo(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull BackendId joltBackendId,
        @Nonnull BackendId rapierBackendId,
        @Nonnull Vector3d center,
        @Nonnull Preset preset) {
        return spawnDemo(physicsStore,
            new BackendSelection(joltBackendId, rapierBackendId),
            center,
            preset);
    }

    @Nonnull
    private static SpawnResult spawnDemo(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull BackendSelection backendSelection,
        @Nonnull Vector3d center,
        @Nonnull Preset preset) {
        PhysicsThreading.requireWorldThread(physicsStore,
            "spawn directional double pendulum spaces");
        DirectionSpec[] directions = preset.directions();
        List<CreatedPendulum> pendulums = new ArrayList<>(directions.length);
        List<CreatedBlockBody> createdBodies = new ArrayList<>(directions.length * 3);
        for (DirectionSpec direction : directions) {
            BackendId backendId = backendSelection.backendId(direction.backendSlot());
            SpaceId spaceId = PhysicsSpaces.create(physicsStore, backendId);
            Ref<PhysicsStore> spaceRef = PhysicsSpaces.resolveRef(physicsStore, spaceId);
            if (spaceRef == null) {
                throw new IllegalStateException("Created physics space id=" + spaceId.value()
                    + " is not bound.");
            }
            putChunkCollisionNone(physicsStore, spaceRef);
            putGravity(physicsStore, spaceRef, direction.gravity());
            putNoSleepSolverSettings(physicsStore, spaceRef);
            CreatedPendulum pendulum = spawnPendulum(physicsStore,
                spaceId,
                spaceRef,
                backendId,
                direction.gravity(),
                new Vector3d(center).add(direction.originOffset()),
                direction.tangent(),
                preset.blockType(direction.backendSlot()),
                preset.upperSwingSpeed(),
                preset.lowerRelativeSwingSpeed());
            pendulums.add(pendulum);
            createdBodies.add(pendulum.anchor());
            createdBodies.add(pendulum.upper());
            createdBodies.add(pendulum.lower());
        }
        return new SpawnResult(pendulums, createdBodies);
    }

    @Nonnull
    private static CreatedPendulum spawnPendulum(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull SpaceId spaceId,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull BackendId backendId,
        @Nonnull Vector3f gravity,
        @Nonnull Vector3d anchorPosition,
        @Nonnull Vector3d tangent,
        @Nonnull String blockType,
        float upperSwingSpeed,
        float lowerRelativeSwingSpeed) {
        Vector3d down = new Vector3d(gravity.x, gravity.y, gravity.z).normalize();
        Vector3d upperPosition = new Vector3d(anchorPosition)
            .add(new Vector3d(down).mul(LINK_HALF_LENGTH));
        Vector3d lowerPosition = new Vector3d(anchorPosition)
            .add(new Vector3d(down).mul(LINK_HALF_LENGTH * 3.0));
        InitialVelocity upperVelocity = initialVelocity(down,
            tangent,
            upperSwingSpeed,
            upperSwingSpeed);
        InitialVelocity lowerVelocity = initialVelocity(down,
            tangent,
            upperSwingSpeed * 2.0f + lowerRelativeSwingSpeed,
            lowerRelativeSwingSpeed);

        CreatedBlockBody anchor = spawnBody(physicsStore,
            spaceRef,
            spaceId,
            anchorPosition,
            PhysicsShapeSpec.box(PIVOT_HALF_SIZE, PIVOT_HALF_SIZE, PIVOT_HALF_SIZE),
            PhysicsBodyType.STATIC,
            0.0f,
            null,
            blockType);
        CreatedBlockBody upper = spawnBody(physicsStore,
            spaceRef,
            spaceId,
            upperPosition,
            linkShape(down),
            PhysicsBodyType.DYNAMIC,
            1.0f,
            upperVelocity,
            blockType);
        CreatedBlockBody lower = spawnBody(physicsStore,
            spaceRef,
            spaceId,
            lowerPosition,
            linkShape(down),
            PhysicsBodyType.DYNAMIC,
            1.0f,
            lowerVelocity,
            blockType);
        Ref<PhysicsStore> topJointRef = addPointJoint(physicsStore,
            spaceRef,
            anchor,
            upper,
            new Vector3f(),
            anchor(down, -LINK_HALF_LENGTH));
        Ref<PhysicsStore> middleJointRef = addPointJoint(physicsStore,
            spaceRef,
            upper,
            lower,
            anchor(down, LINK_HALF_LENGTH),
            anchor(down, -LINK_HALF_LENGTH));
        return new CreatedPendulum(spaceId,
            spaceRef,
            backendId,
            gravity,
            anchor,
            upper,
            lower,
            topJointRef,
            middleJointRef);
    }

    @Nonnull
    private static CreatedBlockBody spawnBody(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position,
        @Nonnull PhysicsShapeSpec shape,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        @Nullable InitialVelocity initialVelocity,
        @Nonnull String blockType) {
        UUID bodyUuid = UUID.randomUUID();
        Ref<PhysicsStore> bodyRef = physicsStore.addEntity(PhysicsBodyEntities.bodyHolder(
                spaceRef,
                bodyUuid,
                ExamplePhysicsUtils.toVector3f(position),
                shape,
                bodyType,
                mass,
                PENDULUM_BODY_SETTINGS,
                initialVelocity != null ? initialVelocity.linear() : null),
            AddReason.SPAWN);
        assert bodyRef != null;
        if (initialVelocity != null) {
            TargetComponent target = physicsStore.getComponent(bodyRef,
                TargetComponent.getComponentType());
            if (target == null) {
                throw new IllegalStateException("PhysicsStore body is missing target component: "
                    + bodyRef);
            }
            TargetComponent updated = target.clone();
            updated.setAngularVelocity(initialVelocity.angular());
            physicsStore.putComponent(bodyRef, TargetComponent.getComponentType(), updated);
        }
        return new CreatedBlockBody(bodyUuid,
            bodyRef,
            spaceId,
            blockType,
            (float) position.x,
            (float) position.y,
            (float) position.z,
            mass > 0.0f,
            PENDULUM_VISUAL_ORIGIN_OFFSET_Y);
    }

    @Nonnull
    private static InitialVelocity initialVelocity(@Nonnull Vector3d down,
        @Nonnull Vector3d tangent,
        float centerSpeed,
        float angularEndpointSpeed) {
        Vector3d angular = new Vector3d(down)
            .cross(tangent)
            .mul(angularEndpointSpeed / LINK_HALF_LENGTH);
        return new InitialVelocity(ExamplePhysicsUtils.toVector3f(
                new Vector3d(tangent).mul(centerSpeed)),
            ExamplePhysicsUtils.toVector3f(angular));
    }

    @Nonnull
    private static Ref<PhysicsStore> addPointJoint(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull CreatedBlockBody bodyA,
        @Nonnull CreatedBlockBody bodyB,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB) {
        JointComponent joint = PhysicsJointEntities.joint(spaceRef,
            bodyA.bodyRef(),
            bodyB.bodyRef(),
            JointType.POINT,
            anchorA,
            anchorB,
            new Vector3f());
        Ref<PhysicsStore> jointRef = physicsStore.addEntity(PhysicsEntities.jointHolder(
                physicsStore,
                UUID.randomUUID(),
                joint),
            AddReason.SPAWN);
        assert jointRef != null;
        return jointRef;
    }

    @Nonnull
    private static Vector3f anchor(@Nonnull Vector3d down, float scale) {
        return new Vector3f((float) down.x, (float) down.y, (float) down.z).mul(scale);
    }

    @Nonnull
    private static PhysicsShapeSpec linkShape(@Nonnull Vector3d down) {
        float x = Math.abs(down.x) > 0.5 ? LINK_HALF_LENGTH : LINK_THICKNESS;
        float y = Math.abs(down.y) > 0.5 ? LINK_HALF_LENGTH : LINK_THICKNESS;
        float z = Math.abs(down.z) > 0.5 ? LINK_HALF_LENGTH : LINK_THICKNESS;
        return PhysicsShapeSpec.box(x, y, z);
    }

    private static void putChunkCollisionNone(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        PhysicsChunkCollisionSettings settings = new PhysicsChunkCollisionSettings();
        settings.setMode(PhysicsChunkCollisionMode.NONE);
        PhysicsSpaces.putChunkCollisionSettings(physicsStore, spaceRef, settings);
    }

    private static void putGravity(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Vector3f gravity) {
        SpaceComponent space = PhysicsSpaces.getSpaceComponent(physicsStore,
            spaceRef,
            SpaceComponent.getComponentType());
        if (space == null) {
            throw new IllegalStateException("PhysicsStore entity is not a space entity: "
                + spaceRef);
        }
        SpaceComponent updated = space.clone();
        updated.setGravity(gravity);
        PhysicsSpaces.putSpaceComponent(physicsStore,
            spaceRef,
            SpaceComponent.getComponentType(),
            updated);
    }

    private static void putNoSleepSolverSettings(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        PhysicsSolverSettings settings = PhysicsSpaces.solverSettings(physicsStore, spaceRef);
        if (settings == null) {
            throw new IllegalStateException("PhysicsStore entity is not a space entity: "
                + spaceRef);
        }
        settings.setDynamicSleepTuning(PENDULUM_SLEEP_LINEAR_THRESHOLD,
            PENDULUM_SLEEP_ANGULAR_THRESHOLD,
            PENDULUM_TIME_UNTIL_SLEEP);
        PhysicsSpaces.putSolverSettings(physicsStore, spaceRef, settings);
    }

    @Nullable
    private Preset parsePreset(@Nonnull CommandContext ctx) {
        if (!presetArg.provided(ctx)) {
            return Preset.DEFAULT;
        }
        String rawPreset = presetArg.get(ctx);
        Preset preset = Preset.from(rawPreset);
        if (preset != null) {
            return preset;
        }
        ctx.sender().sendMessage(Message.raw("Unknown directional pendulums preset '"
            + rawPreset + "'. Expected default, clip, cinematic, or gravity-compass."));
        return null;
    }

    private static void showGravityCompassPresentation(@Nonnull World world,
        @Nonnull PlayerRef playerRef,
        @Nonnull SpawnResult result) {
        EventTitleUtil.showEventTitleToPlayer(playerRef,
            Message.raw("Gravity Compass"),
            Message.raw("Jolt cyan x2 / Rapier basalt x2, four gravity vectors"),
            true);
        for (CreatedPendulum pendulum : result.pendulums()) {
            drawGravityArrow(world, pendulum);
        }
    }

    private static void drawGravityArrow(@Nonnull World world,
        @Nonnull CreatedPendulum pendulum) {
        Vector3d direction = new Vector3d(pendulum.gravity().x,
            pendulum.gravity().y,
            pendulum.gravity().z).normalize().mul(2.5);
        Vector3d origin = bodyPosition(pendulum.anchor())
            .sub(new Vector3d(direction).mul(0.5));
        DebugUtils.addArrow(world,
            origin,
            direction,
            gravityArrowColor(pendulum),
            1.0f,
            CINEMATIC_GRAVITY_ARROW_DURATION_SECONDS,
            DebugUtils.FLAG_FADE);
    }

    @Nonnull
    private static Vector3f gravityArrowColor(@Nonnull CreatedPendulum pendulum) {
        return CINEMATIC_JOLT_BLOCK_TYPE.equals(pendulum.anchor().blockType())
            ? DebugUtils.COLOR_CYAN
            : DebugUtils.COLOR_MAGENTA;
    }

    @Nonnull
    private static Vector3d bodyPosition(@Nonnull CreatedBlockBody body) {
        return new Vector3d(body.positionX(), body.positionY(), body.positionZ());
    }

    @Nullable
    private BackendSelection resolveBackendSelection(@Nonnull CommandContext ctx) {
        List<BackendId> missing = new ArrayList<>(2);
        if (!backendRegistered(JOLT_BACKEND_ID)) {
            missing.add(JOLT_BACKEND_ID);
        }
        if (!backendRegistered(RAPIER_BACKEND_ID)) {
            missing.add(RAPIER_BACKEND_ID);
        }
        if (missing.isEmpty()) {
            return new BackendSelection(JOLT_BACKEND_ID, RAPIER_BACKEND_ID);
        }
        String missingIds = String.join(", ",
            missing.stream().map(BackendId::value).toList());
        ctx.sender().sendMessage(Message.raw("Directional pendulums require registered backends "
            + missingIds + ". Available backends: " + availableBackendIds()));
        return null;
    }

    private static boolean backendRegistered(@Nonnull BackendId backendId) {
        try {
            Impulse.getRuntimeProvider(backendId);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Nonnull
    private static String availableBackendIds() {
        List<String> backendIds = new ArrayList<>();
        for (PhysicsBackendRuntimeProvider provider : Impulse.getRuntimeProviders()) {
            backendIds.add(provider.getId().value());
        }
        backendIds.sort(String::compareTo);
        return backendIds.isEmpty() ? "<none>" : String.join(", ", backendIds);
    }

    @Nonnull
    private static String spaceIds(@Nonnull SpawnResult result) {
        List<String> ids = result.pendulums()
            .stream()
            .map(pendulum -> Integer.toString(pendulum.spaceId().value()))
            .toList();
        return String.join(", ", ids);
    }

    record SpawnResult(@Nonnull List<CreatedPendulum> pendulums,
                       @Nonnull List<CreatedBlockBody> createdBodies) {

        SpawnResult {
            pendulums = List.copyOf(Objects.requireNonNull(pendulums, "pendulums"));
            createdBodies = List.copyOf(Objects.requireNonNull(createdBodies, "createdBodies"));
        }
    }

    record CreatedPendulum(@Nonnull SpaceId spaceId,
                           @Nonnull Ref<PhysicsStore> spaceRef,
                           @Nonnull BackendId backendId,
                           @Nonnull Vector3f gravity,
                           @Nonnull CreatedBlockBody anchor,
                           @Nonnull CreatedBlockBody upper,
                           @Nonnull CreatedBlockBody lower,
                           @Nonnull Ref<PhysicsStore> topJointRef,
                           @Nonnull Ref<PhysicsStore> middleJointRef) {

        CreatedPendulum {
            Objects.requireNonNull(spaceId, "spaceId");
            Objects.requireNonNull(spaceRef, "spaceRef");
            Objects.requireNonNull(backendId, "backendId");
            gravity = new Vector3f(Objects.requireNonNull(gravity, "gravity"));
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(upper, "upper");
            Objects.requireNonNull(lower, "lower");
            Objects.requireNonNull(topJointRef, "topJointRef");
            Objects.requireNonNull(middleJointRef, "middleJointRef");
        }
    }

    private record DirectionSpec(@Nonnull Vector3f gravity,
                                 @Nonnull Vector3d originOffset,
                                 @Nonnull Vector3d tangent,
                                 @Nonnull BackendSlot backendSlot) {

        private DirectionSpec {
            gravity = new Vector3f(Objects.requireNonNull(gravity, "gravity"));
            originOffset = new Vector3d(Objects.requireNonNull(originOffset, "originOffset"));
            tangent = new Vector3d(Objects.requireNonNull(tangent, "tangent")).normalize();
            Objects.requireNonNull(backendSlot, "backendSlot");
        }
    }

    private record BackendSelection(@Nonnull BackendId joltBackendId,
                                    @Nonnull BackendId rapierBackendId) {

        private BackendSelection {
            Objects.requireNonNull(joltBackendId, "joltBackendId");
            Objects.requireNonNull(rapierBackendId, "rapierBackendId");
        }

        @Nonnull
        private BackendId backendId(@Nonnull BackendSlot backendSlot) {
            return switch (backendSlot) {
                case JOLT -> joltBackendId;
                case RAPIER -> rapierBackendId;
            };
        }
    }

    enum Preset {
        DEFAULT("default"),
        CINEMATIC("clip");

        private final String label;

        Preset(@Nonnull String label) {
            this.label = label;
        }

        @Nullable
        private static Preset from(@Nullable String rawPreset) {
            if (rawPreset == null || rawPreset.isBlank()) {
                return DEFAULT;
            }
            return switch (rawPreset.trim().toLowerCase(Locale.ROOT)) {
                case "default", "classic" -> DEFAULT;
                case "clip", "cinematic", "gravity-compass", "gravity_compass" -> CINEMATIC;
                default -> null;
            };
        }

        @Nonnull
        private String label() {
            return label;
        }

        @Nonnull
        private DirectionSpec[] directions() {
            return switch (this) {
                case DEFAULT -> DIRECTIONS;
                case CINEMATIC -> CINEMATIC_DIRECTIONS;
            };
        }

        @Nonnull
        private String blockType(@Nonnull BackendSlot backendSlot) {
            return switch (this) {
                case DEFAULT -> PENDULUM_BLOCK_TYPE;
                case CINEMATIC -> switch (backendSlot) {
                    case JOLT -> CINEMATIC_JOLT_BLOCK_TYPE;
                    case RAPIER -> CINEMATIC_RAPIER_BLOCK_TYPE;
                };
            };
        }

        private float upperSwingSpeed() {
            return switch (this) {
                case DEFAULT -> UPPER_SWING_SPEED;
                case CINEMATIC -> CINEMATIC_UPPER_SWING_SPEED;
            };
        }

        private float lowerRelativeSwingSpeed() {
            return switch (this) {
                case DEFAULT -> LOWER_RELATIVE_SWING_SPEED;
                case CINEMATIC -> CINEMATIC_LOWER_RELATIVE_SWING_SPEED;
            };
        }

        @Nonnull
        private String blockSummary() {
            return switch (this) {
                case DEFAULT -> PENDULUM_BLOCK_TYPE;
                case CINEMATIC -> "Jolt=" + CINEMATIC_JOLT_BLOCK_TYPE
                    + ", Rapier=" + CINEMATIC_RAPIER_BLOCK_TYPE;
            };
        }
    }

    private enum BackendSlot {
        JOLT,
        RAPIER
    }

    private record InitialVelocity(@Nonnull Vector3f linear,
                                   @Nonnull Vector3f angular) {

        private InitialVelocity {
            linear = new Vector3f(Objects.requireNonNull(linear, "linear"));
            angular = new Vector3f(Objects.requireNonNull(angular, "angular"));
        }
    }
}
