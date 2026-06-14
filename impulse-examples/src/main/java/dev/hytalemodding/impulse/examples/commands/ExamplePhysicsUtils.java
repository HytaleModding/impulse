package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource.SpaceWorldCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodyRows;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.BodyRowDescriptor;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ExamplePhysicsUtils {

    public static final String DEFAULT_BLOCK_TYPE =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE;
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, BodyAttachmentComponent> ATTACHMENT_TYPE =
        BodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE = ModelComponent.getComponentType();
    private static final ComponentType<EntityStore, HeadRotation> HEAD_ROTATION_TYPE = HeadRotation.getComponentType();

    private ExamplePhysicsUtils() {
    }

    @Nullable
    public static Vector3d playerPosition(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref) {
        TransformComponent playerTransform = store.getComponent(ref, TRANSFORM_TYPE);
        if (playerTransform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return null;
        }
        return new Vector3d(playerTransform.getPosition());
    }

    @Nonnull
    public static PhysicsWorldResource resource(@Nonnull Store<EntityStore> store) {
        return store.getResource(PhysicsWorldResource.getResourceType());
    }

    @Nonnull
    public static Store<PhysicsStore> physicsStore(@Nonnull World world) {
        return ((PhysicsStoreWorld) Objects.requireNonNull(world, "world")).getPhysicsStore()
            .getStore();
    }

    @Nullable
    public static UUID resolvePhysicsStoreSpaceUuid(@Nonnull World world,
        @Nonnull SpaceId spaceId) {
        Store<PhysicsStore> store = physicsStore(world);
        PhysicsStoreThreading.requireWorldThread(store, "resolve a PhysicsStore space UUID");
        return store
            .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(Objects.requireNonNull(spaceId, "spaceId"));
    }

    @Nonnull
    public static WorldCollisionBuildStats rebuildPhysicsStoreWorldCollisionAround(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        PhysicsStoreWorldCollisionStreamingResource streaming = worldCollisionStreaming(store);
        SpaceWorldCollisionSettings settings = requireWorldCollisionSettings(world, spaceId);
        PhysicsTerrainMutationQueueResource queue = terrainMutationQueue(world);
        int removed = streaming.clearSpace(settings.spaceUuid(), queue);
        WorldCollisionPrewarmStats stats = streaming.ensureAround(world,
            settings.spaceUuid(),
            queue,
            List.of(center),
            radius,
            Math.max(0L, world.getTick()),
            null,
            settings.buildOptions());
        return withRemovedBodies(stats.buildStats(), stats.buildStats().removedBodies() + removed);
    }

    @Nonnull
    public static WorldCollisionBuildStats refreshPhysicsStoreWorldCollisionAround(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        SpaceWorldCollisionSettings settings = requireWorldCollisionSettings(world, spaceId);
        return worldCollisionStreaming(store).refreshAround(world,
            settings.spaceUuid(),
            terrainMutationQueue(world),
            center,
            radius,
            Math.max(0L, world.getTick()),
            null,
            settings.buildOptions());
    }

    @Nonnull
    public static WorldCollisionPrewarmStats ensurePhysicsStoreWorldCollisionAround(
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        SpaceWorldCollisionSettings settings = requireWorldCollisionSettings(world, spaceId);
        return worldCollisionStreaming(store).ensureAround(world,
            settings.spaceUuid(),
            terrainMutationQueue(world),
            centers,
            radius,
            tick,
            null,
            settings.buildOptions());
    }

    public static int clearPhysicsStoreWorldCollision(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull SpaceId spaceId) {
        SpaceWorldCollisionSettings settings = requireWorldCollisionSettings(world, spaceId);
        return worldCollisionStreaming(store).clearSpace(settings.spaceUuid(),
            terrainMutationQueue(world));
    }

    @Nonnull
    public static WorldCollisionStats physicsStoreWorldCollisionStats(
        @Nonnull Store<EntityStore> store) {
        return worldCollisionStreaming(store).stats();
    }

    @Nonnull
    private static PhysicsStoreWorldCollisionStreamingResource worldCollisionStreaming(
        @Nonnull Store<EntityStore> store) {
        return store.getResource(PhysicsStoreWorldCollisionStreamingResource.getResourceType());
    }

    @Nonnull
    private static PhysicsTerrainMutationQueueResource terrainMutationQueue(@Nonnull World world) {
        Store<PhysicsStore> store = physicsStore(world);
        PhysicsStoreThreading.requireWorldThread(store,
            "read PhysicsStore terrain mutation queue");
        return store.getResource(PhysicsTerrainMutationQueueResource.getResourceType());
    }

    @Nonnull
    private static SpaceWorldCollisionSettings requireWorldCollisionSettings(@Nonnull World world,
        @Nonnull SpaceId spaceId) {
        Store<PhysicsStore> physics = physicsStore(world);
        PhysicsStoreThreading.requireWorldThread(physics,
            "read PhysicsStore world-collision settings");
        UUID spaceUuid = resolvePhysicsStoreSpaceUuid(world, spaceId);
        if (spaceUuid == null) {
            throw new IllegalStateException("PhysicsStore space id=" + spaceId.value()
                + " is not bound yet");
        }
        Ref<PhysicsStore> spaceRef = physics
            .getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);
        if (spaceRef == null || !spaceRef.isValid()) {
            throw new IllegalStateException("PhysicsStore space id=" + spaceId.value()
                + " is not bound yet");
        }
        WorldCollisionComponent component = physics.getComponent(spaceRef,
            WorldCollisionComponent.getComponentType());
        WorldCollisionComponent settings = component != null ? component : new WorldCollisionComponent();
        if (settings.getMode() == WorldCollisionMode.NONE) {
            throw new IllegalStateException("World collision is disabled for space " + spaceId);
        }
        return new SpaceWorldCollisionSettings(spaceUuid,
            settings.getMode(),
            settings.getEntityChunkBoundaryMode(),
            settings.isNativeVoxelTerrainEnabled(),
            settings.getRadius(),
            settings.getBodyRadius(),
            settings.getTtlTicks(),
            settings.getTerrainFriction(),
            settings.getTerrainRestitution());
    }

    @Nonnull
    private static WorldCollisionBuildStats withRemovedBodies(
        @Nonnull WorldCollisionBuildStats stats,
        int removedBodies) {
        return new WorldCollisionBuildStats(stats.scannedBlocks(),
            stats.solidBlocks(),
            stats.culledInteriorBlocks(),
            stats.fullCubeRuns(),
            stats.detailBoxes(),
            stats.colliderBodies(),
            removedBodies,
            stats.sectionsBuilt(),
            stats.sectionsRebuilt(),
            stats.voxelBodies());
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull BodyRowDescriptor row) {
        return addPhysicsStoreBody(physicsStore(world), row);
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull BodyRowDescriptor row,
        @Nonnull BodyCommandComponent command) {
        Store<PhysicsStore> store = physicsStore(world);
        Ref<PhysicsStore> bodyRef = addPhysicsStoreBody(store, row);
        appendPhysicsStoreBodyCommand(store, bodyRef, command);
        return bodyRef;
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull BodyRowDescriptor row,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target) {
        return addPhysicsStoreBody(physicsStore(world), row, dynamics, target);
    }

    public static void addPhysicsStoreBodies(@Nonnull World world,
        @Nonnull Iterable<BodyRowDescriptor> rows) {
        Objects.requireNonNull(rows, "rows");
        Store<PhysicsStore> store = physicsStore(world);
        PhysicsStoreThreading.requireWorldThread(store, "add PhysicsStore body rows");
        for (BodyRowDescriptor row : rows) {
            addPhysicsStoreBodyUnchecked(store, row, row.dynamics(), row.target());
        }
    }

    @Nonnull
    private static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyRowDescriptor row) {
        Objects.requireNonNull(row, "row");
        return addPhysicsStoreBody(store, row, row.dynamics(), row.target());
    }

    @Nonnull
    private static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyRowDescriptor row,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target) {
        Objects.requireNonNull(row, "row");
        PhysicsStoreThreading.requireWorldThread(store, "add a PhysicsStore body row");
        return addPhysicsStoreBodyUnchecked(store, row, dynamics, target);
    }

    @Nonnull
    private static Ref<PhysicsStore> addPhysicsStoreBodyUnchecked(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyRowDescriptor row,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target) {
        Objects.requireNonNull(row, "row");
        return store.addEntity(PhysicsStoreEntities.bodyHolder(store,
            row.bodyUuid(),
            row.body(),
            Objects.requireNonNull(dynamics, "dynamics"),
            target,
            row.collider(),
            row.shape(),
            row.material(),
            row.filter()), AddReason.SPAWN);
    }

    public static boolean appendPhysicsStoreBodyCommand(@Nonnull World world,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyCommandComponent command) {
        Objects.requireNonNull(command, "command");
        Store<PhysicsStore> store = physicsStore(world);
        PhysicsStoreThreading.requireWorldThread(store, "append a PhysicsStore body command");
        Ref<PhysicsStore> bodyRef = store
            .getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(Objects.requireNonNull(bodyUuid, "bodyUuid"));
        if (bodyRef == null || !bodyRef.isValid()) {
            return false;
        }
        appendPhysicsStoreBodyCommand(store, bodyRef, command);
        return true;
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreJoint(@Nonnull World world,
        @Nonnull UUID jointUuid,
        @Nonnull JointComponent joint) {
        Store<PhysicsStore> store = physicsStore(world);
        PhysicsStoreThreading.requireWorldThread(store, "add a PhysicsStore joint row");
        return store.addEntity(PhysicsStoreEntities.jointHolder(store,
            Objects.requireNonNull(jointUuid, "jointUuid"),
            joint), AddReason.SPAWN);
    }

    public static void appendPhysicsStoreBodyCommand(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull BodyCommandComponent command) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(bodyRef, "bodyRef");
        Objects.requireNonNull(command, "command");
        PhysicsStoreThreading.requireWorldThread(store, "append a PhysicsStore body command");
        BodyCommandComponent existing = store.getComponent(bodyRef,
            BodyCommandComponent.getComponentType());
        BodyCommandComponent merged = existing != null ? existing.append(command) : command;
        store.putComponent(bodyRef, BodyCommandComponent.getComponentType(), merged);
    }

    @Nullable
    public static SpaceId spaceId(@Nonnull CommandContext ctx,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull OptionalArg<Integer> spaceArg) {
        if (spaceArg.provided(ctx)) {
            int rawSpaceId = spaceArg.get(ctx);
            if (rawSpaceId <= 0) {
                ctx.sender().sendMessage(Message.raw("Space id must be a positive integer."));
                return null;
            }
            SpaceId spaceId = new SpaceId(rawSpaceId);
            if (!resource.hasSpace(spaceId)) {
                ctx.sender().sendMessage(Message.raw("No physics space id=" + rawSpaceId + " exists."));
                return null;
            }
            return spaceId;
        }

        SpaceId firstSpaceId = resource.getSpaceIds()
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
        if (firstSpaceId == null) {
            ctx.sender().sendMessage(Message.raw("No physics space exists. Run "
                + "`/impulse space create --backend=<id>` before running Impulse example commands."));
        }
        return firstSpaceId;
    }

    @Nonnull
    public static SpawnedBlockBody spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings) {
        return spawnBlockBody(store, time, resource, spaceId, visualPosition, DEFAULT_BLOCK_TYPE,
            shape, mass, settings);
    }

    @Nonnull
    public static SpawnedBlockBody spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings) {
        return spawnBlockBody(store,
            time,
            resource,
            spaceId,
            visualPosition,
            blockType,
            shape,
            mass,
            settings,
            null);
    }

    @Nonnull
    public static SpawnedBlockBody spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        PendingBlockBody physicsStoreBody = tryRecordPhysicsStoreBlockBody(store,
            spaceId,
            visualPosition,
            blockType,
            shape,
            mass,
            settings,
            linearVelocity);
        if (physicsStoreBody != null) {
            return attachPhysicsStoreBlockBody(store, time, physicsStoreBody);
        }

        throw new IllegalStateException("Cannot spawn block body because the target space is not "
            + "bound in PhysicsStore: " + spaceId.value());
    }

    @Nullable
    private static PendingBlockBody tryRecordPhysicsStoreBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(visualPosition, "visualPosition");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(settings, "settings");

        World world = store.getExternalData().getWorld();
        UUID spaceUuid;
        try {
            spaceUuid = resolvePhysicsStoreSpaceUuid(world, spaceId);
        } catch (IllegalStateException exception) {
            return null;
        }
        if (spaceUuid == null) {
            return null;
        }

        RigidBodyKey bodyKey = RigidBodyKey.random();
        UUID bodyUuid = bodyKey.value();
        Vector3f bodyCenter = toVector3f(visualPosition);
        try {
            addPhysicsStoreBody(world,
                bodyRow(spaceUuid,
                    bodyUuid,
                    bodyCenter,
                    shape,
                    mass,
                    settings,
                    linearVelocity));
        } catch (IllegalStateException exception) {
            return null;
        }

        return new PendingBlockBody(bodyKey,
            spaceId,
            blockType,
            (float) visualPosition.x,
            (float) visualPosition.y,
            (float) visualPosition.z,
            mass > 0.0f);
    }

    @Nonnull
    public static BodyRowDescriptor bodyRow(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        return PhysicsBodyRows.dynamicBody(spaceUuid,
            bodyUuid,
            bodyCenter,
            shape,
            mass,
            settings,
            linearVelocity,
            PhysicsBodyPersistenceMode.PERSISTENT);
    }

    @Nonnull
    private static BodyRowDescriptor bodyRow(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return PhysicsBodyRows.body(spaceUuid,
            bodyUuid,
            bodyCenter,
            shape,
            PhysicsBodyType.DYNAMIC,
            mass,
            settings,
            linearVelocity,
            kind,
            persistenceMode);
    }

    @Nonnull
    public static BodyRowBatchTiming addDynamicBodyBatchMeasured(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull Consumer<BlockBodyBatchRecorder> recipe) {
        DynamicBodyBatchPlan plan = dynamicBodyBatchPlan(world,
            spaceId,
            expectedBodies,
            shape,
            mass,
            settings,
            kind,
            persistenceMode,
            recipe);
        if (plan.isEmpty()) {
            return new BodyRowBatchTiming(0, plan.setupWallNanos(), 0L);
        }

        long applyStartNanos = System.nanoTime();
        addPhysicsStoreBodies(world, plan.bodies());
        long rowApplyNanos = System.nanoTime() - applyStartNanos;
        return new BodyRowBatchTiming(plan.count(),
            plan.setupWallNanos(),
            rowApplyNanos);
    }

    @Nonnull
    private static DynamicBodyBatchPlan dynamicBodyBatchPlan(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull Consumer<BlockBodyBatchRecorder> recipe) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(persistenceMode, "persistenceMode");

        long setupStartNanos = System.nanoTime();
        BlockBodyBatchRecorder batch = new BlockBodyBatchRecorder(expectedBodies);
        Objects.requireNonNull(recipe, "recipe").accept(batch);
        batch.seal();
        if (batch.isEmpty()) {
            return new DynamicBodyBatchPlan(List.of(), 0L);
        }

        UUID spaceUuid = resolvePhysicsStoreSpaceUuid(world, spaceId);
        if (spaceUuid == null) {
            throw new IllegalStateException("Cannot add dynamic body rows because the target space is not "
                + "bound in PhysicsStore: " + spaceId.value());
        }

        List<BodyRowDescriptor> bodies = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            RigidBodyKey bodyKey = batch.bodyKey(i);
            bodies.add(bodyRow(spaceUuid,
                bodyKey.value(),
                new Vector3f(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                shape,
                mass,
                settings,
                null,
                kind,
                persistenceMode));
        }

        return new DynamicBodyBatchPlan(bodies, System.nanoTime() - setupStartNanos);
    }

    @Nonnull
    public static SpawnedBlockBody attachPhysicsStoreBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PendingBlockBody pending) {
        Ref<EntityStore> entity = spawnAttachedPhysicsStoreBlockEntity(store,
            time,
            pending.bodyKey().value(),
            pending.spaceId(),
            pending.blockType(),
            new Vector3d(pending.positionX(), pending.positionY(), pending.positionZ()),
            pending.controllable());
        assert entity != null;
        return new SpawnedBlockBody(pending.bodyKey(), pending.spaceId(), entity);
    }

    @Nonnull
    public static SpawnedBlockBody[] spawnBlockBodies(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        long serverTick,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchRecorder> recipe) {
        return spawnBlockBodiesInternal(store,
            time,
            resource,
            serverTick,
            spaceId,
            expectedBodies,
            blockType,
            shape,
            mass,
            settings,
            recipe,
            true).collectedBodies();
    }

    @Nonnull
    public static BlockBodyBatchTiming spawnBlockBodiesMeasured(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        long serverTick,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchRecorder> recipe) {
        return spawnBlockBodiesInternal(store,
            time,
            resource,
            serverTick,
            spaceId,
            expectedBodies,
            blockType,
            shape,
            mass,
            settings,
            recipe,
            false).timing();
    }

    @Nonnull
    private static BlockBodyBatchResult spawnBlockBodiesInternal(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        long serverTick,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchRecorder> recipe,
        boolean collectBodies) {
        BlockBodyBatchRecorder batch = new BlockBodyBatchRecorder(expectedBodies);
        Objects.requireNonNull(recipe, "recipe").accept(batch);
        batch.seal();
        if (batch.isEmpty()) {
            return new BlockBodyBatchResult(collectBodies ? new SpawnedBlockBody[0] : null,
                0,
                0L,
                0L);
        }

        World world = store.getExternalData().getWorld();
        UUID spaceUuid = resolvePhysicsStoreSpaceUuid(world, spaceId);
        if (spaceUuid == null) {
            throw new IllegalStateException("Cannot spawn block body batch because the target space is not "
                + "bound in PhysicsStore: " + spaceId.value());
        }

        List<BodyRowDescriptor> rows = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            RigidBodyKey bodyKey = batch.bodyKey(i);
            rows.add(bodyRow(spaceUuid,
                bodyKey.value(),
                new Vector3f(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                shape,
                mass,
                settings,
                null));
        }

        long rowApplyStartNanos = System.nanoTime();
        addPhysicsStoreBodies(world, rows);
        long rowApplyNanos = System.nanoTime() - rowApplyStartNanos;

        long entityAttachStartNanos = System.nanoTime();
        SpawnedBlockBody[] spawned = collectBodies ? new SpawnedBlockBody[batch.size()] : null;
        for (int i = 0; i < batch.size(); i++) {
            RigidBodyKey bodyKey = batch.bodyKey(i);
            Ref<EntityStore> entity = spawnAttachedPhysicsStoreBlockEntity(store,
                time,
                bodyKey.value(),
                spaceId,
                blockType,
                new Vector3d(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                mass > 0.0f);
            if (spawned != null) {
                assert entity != null;
                spawned[i] = new SpawnedBlockBody(bodyKey, spaceId, entity);
            }
        }
        long entityAttachNanos = System.nanoTime() - entityAttachStartNanos;
        return new BlockBodyBatchResult(spawned,
            batch.size(),
            rowApplyNanos,
            entityAttachNanos);
    }

    static void addControllableMarkerIfAvailable(@Nonnull Holder<EntityStore> holder,
        @Nonnull PhysicsBodyType bodyType) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(bodyType, "bodyType");
        if (bodyType == PhysicsBodyType.DYNAMIC && PhysicsControlSessions.isAvailable()) {
            holder.addComponent(ImpulseControllableComponent.getComponentType(),
                new ImpulseControllableComponent());
        }
    }

    @Nullable
    public static Ref<EntityStore> spawnExternalBodyViewBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType) {
        Holder<EntityStore> holder = blockEntityHolder(time, blockType, visualPosition);
        holder.addComponent(ATTACHMENT_TYPE,
            BodyAttachmentComponent.externalEntity(bodyKey.value(), spaceId));
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    static Vector3d visualPositionFromBodyCenter(@Nonnull Vector3d bodyCenter,
        @Nonnull PhysicsShapeSpec shape) {
        return ExamplePhysicsOriginMath.visualPositionFromBodyCenter(bodyCenter, shape);
    }

    @Nullable
    private static Ref<EntityStore> spawnAttachedPhysicsStoreBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull UUID physicsBodyUuid,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        boolean controllable) {
        Holder<EntityStore> holder = attachedPhysicsStoreBlockEntityHolder(time,
            physicsBodyUuid,
            spaceId,
            blockType,
            visualPosition,
            new Vector3f(),
            new Quaternionf(),
            Float.NaN,
            controllable);
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    public static Holder<EntityStore> attachedPhysicsStoreBlockEntityHolder(@Nonnull TimeResource time,
        @Nonnull UUID physicsBodyUuid,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY,
        boolean controllable) {
        Holder<EntityStore> holder = blockEntityHolder(time, blockType, visualPosition);
        holder.addComponent(ATTACHMENT_TYPE,
            BodyAttachmentComponent.impulseOwnedVisual(physicsBodyUuid,
            spaceId,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY));
        if (controllable && PhysicsControlSessions.isAvailable()) {
            holder.addComponent(ImpulseControllableComponent.getComponentType(),
                new ImpulseControllableComponent());
        }
        return holder;
    }

    @Nonnull
    private static Holder<EntityStore> blockEntityHolder(@Nonnull TimeResource time,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition) {
        return ExampleBlockEntityVisuals.impulseOwnedBlockVisual(time, blockType, visualPosition);
    }

    @Nonnull
    public static String resolveBlockType(@Nullable String blockType) {
        return ExampleBlockEntityVisuals.resolveBlockType(blockType);
    }

    @Nonnull
    static Vector3d eyePosition(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TransformComponent transform) {
        return new Vector3d(transform.getPosition()).add(0.0, eyeHeight(store, ref), 0.0);
    }

    static double eyeHeight(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ModelComponent modelComponent = store.getComponent(ref, MODEL_TYPE);
        if (modelComponent == null) {
            return 1.6;
        }

        Model model = modelComponent.getModel();
        if (model == null) {
            return 1.6;
        }
        return model.getEyeHeight(ref, store);
    }

    @Nonnull
    public static Vector3d lookDirection(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = store.getComponent(ref, HEAD_ROTATION_TYPE);
        Rotation3f rotation = headRotation != null ? headRotation.getRotation() : transform.getRotation();
        Quaterniond quaternion = rotation.getQuaternion(new Quaterniond());
        Vector3d direction = new Vector3d(Vector3dUtil.FORWARD);
        quaternion.transform(direction);
        if (direction.lengthSquared() == 0.0) {
            return new Vector3d(Vector3dUtil.FORWARD);
        }
        return direction.normalize();
    }

    public static int optionalInt(@Nonnull CommandContext ctx,
        @Nonnull OptionalArg<Integer> arg,
        int defaultValue,
        int min,
        int max) {
        int value = arg.provided(ctx) ? arg.get(ctx) : defaultValue;
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    static Vector3f toVector3f(@Nonnull Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    public record SpawnedBlockBody(@Nonnull RigidBodyKey bodyKey,
                                   @Nonnull SpaceId spaceId,
                                   @Nonnull Ref<EntityStore> entity) {
    }

    public record BlockBodyBatchTiming(int count,
                                       long rowApplyNanos,
                                       long entityAttachNanos) {

        public BlockBodyBatchTiming {
            count = Math.max(0, count);
            rowApplyNanos = Math.max(0L, rowApplyNanos);
            entityAttachNanos = Math.max(0L, entityAttachNanos);
        }
    }

    public record BodyRowBatchTiming(int count,
                                     long setupWallNanos,
                                     long rowApplyNanos) {

        public BodyRowBatchTiming {
            count = Math.max(0, count);
            setupWallNanos = Math.max(0L, setupWallNanos);
            rowApplyNanos = Math.max(0L, rowApplyNanos);
        }
    }

    private record DynamicBodyBatchPlan(@Nonnull List<BodyRowDescriptor> bodies,
                                        long setupWallNanos) {

        DynamicBodyBatchPlan {
            bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
            setupWallNanos = Math.max(0L, setupWallNanos);
        }

        private int count() {
            return bodies.size();
        }

        private boolean isEmpty() {
            return bodies.isEmpty();
        }
    }

    public record PendingBlockBody(@Nonnull RigidBodyKey bodyKey,
                                   @Nonnull SpaceId spaceId,
                                   @Nullable String blockType,
                                   float positionX,
                                   float positionY,
                                   float positionZ,
                                   boolean controllable) {

        public PendingBlockBody {
            Objects.requireNonNull(bodyKey, "bodyKey");
            Objects.requireNonNull(spaceId, "spaceId");
        }
    }

    public static final class BlockBodyBatchRecorder {

        private static final int POSITION_STRIDE = 3;

        private final long bodyKeyRunId = RigidBodyKey.random().mostSignificantBits();
        private long[] bodyKeyMostSignificantBits;
        private long[] bodyKeyLeastSignificantBits;
        private float[] positions;
        private int size;
        private boolean sealed;

        private BlockBodyBatchRecorder(int expectedBodies) {
            int capacity = Math.max(1, expectedBodies);
            bodyKeyMostSignificantBits = new long[capacity];
            bodyKeyLeastSignificantBits = new long[capacity];
            positions = new float[capacity * POSITION_STRIDE];
        }

        @Nonnull
        public BlockBodyBatchRecorder addBody(float positionX,
            float positionY,
            float positionZ) {
            return addBody(bodyKeyRunId,
                size + 1L,
                positionX,
                positionY,
                positionZ);
        }

        @Nonnull
        public BlockBodyBatchRecorder addBody(@Nonnull RigidBodyKey bodyKey,
            float positionX,
            float positionY,
            float positionZ) {
            Objects.requireNonNull(bodyKey, "bodyKey");
            return addBody(bodyKey.mostSignificantBits(),
                bodyKey.leastSignificantBits(),
                positionX,
                positionY,
                positionZ);
        }

        @Nonnull
        public RigidBodyKey body(float positionX,
            float positionY,
            float positionZ) {
            long leastSignificantBits = size + 1L;
            addBody(bodyKeyRunId, leastSignificantBits, positionX, positionY, positionZ);
            return RigidBodyKey.of(bodyKeyRunId, leastSignificantBits);
        }

        @Nonnull
        public RigidBodyKey body(@Nonnull RigidBodyKey bodyKey,
            float positionX,
            float positionY,
            float positionZ) {
            addBody(bodyKey, positionX, positionY, positionZ);
            return bodyKey;
        }

        @Nonnull
        private BlockBodyBatchRecorder addBody(long bodyKeyMostSignificantBits,
            long bodyKeyLeastSignificantBits,
            float positionX,
            float positionY,
            float positionZ) {
            assertMutable();
            ensureCapacity(size + 1);
            this.bodyKeyMostSignificantBits[size] = bodyKeyMostSignificantBits;
            this.bodyKeyLeastSignificantBits[size] = bodyKeyLeastSignificantBits;
            int positionOffset = size * POSITION_STRIDE;
            positions[positionOffset] = positionX;
            positions[positionOffset + 1] = positionY;
            positions[positionOffset + 2] = positionZ;
            size++;
            return this;
        }

        private void seal() {
            sealed = true;
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private int size() {
            return size;
        }

        @Nonnull
        private RigidBodyKey bodyKey(int index) {
            checkIndex(index);
            return RigidBodyKey.of(bodyKeyMostSignificantBits[index],
                bodyKeyLeastSignificantBits[index]);
        }

        private long bodyKeyMostSignificantBits(int index) {
            checkIndex(index);
            return bodyKeyMostSignificantBits[index];
        }

        private long bodyKeyLeastSignificantBits(int index) {
            checkIndex(index);
            return bodyKeyLeastSignificantBits[index];
        }

        private float positionX(int index) {
            return position(index, 0);
        }

        private float positionY(int index) {
            return position(index, 1);
        }

        private float positionZ(int index) {
            return position(index, 2);
        }

        private float position(int index, int slot) {
            checkIndex(index);
            return positions[index * POSITION_STRIDE + slot];
        }

        private void ensureCapacity(int required) {
            if (required <= bodyKeyMostSignificantBits.length) {
                return;
            }
            int nextCapacity = Math.max(required,
                bodyKeyMostSignificantBits.length + (bodyKeyMostSignificantBits.length >> 1) + 1);
            bodyKeyMostSignificantBits = Arrays.copyOf(bodyKeyMostSignificantBits, nextCapacity);
            bodyKeyLeastSignificantBits = Arrays.copyOf(bodyKeyLeastSignificantBits, nextCapacity);
            positions = Arrays.copyOf(positions, nextCapacity * POSITION_STRIDE);
        }

        private void checkIndex(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(index);
            }
        }

        private void assertMutable() {
            if (sealed) {
                throw new IllegalStateException("Block body batch recorder is already sealed");
            }
        }
    }

    private record BlockBodyBatchResult(@Nullable SpawnedBlockBody[] bodies,
                                        int count,
                                        long rowApplyNanos,
                                        long entityAttachNanos) {

        private BlockBodyBatchResult {
            if (bodies != null && bodies.length != count) {
                throw new IllegalArgumentException("Collected body count does not match batch count");
            }
            count = Math.max(0, count);
            rowApplyNanos = Math.max(0L, rowApplyNanos);
            entityAttachNanos = Math.max(0L, entityAttachNanos);
        }

        @Nonnull
        private SpawnedBlockBody[] collectedBodies() {
            if (bodies == null) {
                throw new IllegalStateException("Block body batch did not collect body results");
            }
            return bodies;
        }

        @Nonnull
        private BlockBodyBatchTiming timing() {
            return new BlockBodyBatchTiming(count,
                rowApplyNanos,
                entityAttachNanos);
        }
    }
}
