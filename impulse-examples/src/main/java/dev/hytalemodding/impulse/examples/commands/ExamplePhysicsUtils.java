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
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodySpawnRequests;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyUpsertRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequestFenceHandle;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.PhysicsCommandRecorder;
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
        return physicsStore(world)
            .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(Objects.requireNonNull(spaceId, "spaceId"));
    }

    public static void enqueuePhysicsStoreRequest(@Nonnull World world,
        @Nonnull PhysicsStoreRequest request) {
        physicsStore(world)
            .getResource(PhysicsRequestQueueResource.getResourceType())
            .enqueue(Objects.requireNonNull(request, "request"));
    }

    public static void enqueuePhysicsStoreRequests(@Nonnull World world,
        @Nonnull Iterable<? extends PhysicsStoreRequest> requests) {
        physicsStore(world)
            .getResource(PhysicsRequestQueueResource.getResourceType())
            .enqueueAll(Objects.requireNonNull(requests, "requests"));
    }

    @Nonnull
    public static PhysicsStoreRequestFenceHandle enqueuePhysicsStoreRequestsFenced(
        @Nonnull World world,
        @Nonnull Iterable<? extends PhysicsStoreRequest> requests,
        long submittedServerTick) {
        return physicsStore(world)
            .getResource(PhysicsRequestQueueResource.getResourceType())
            .enqueueAllFenced(Objects.requireNonNull(requests, "requests"),
                Math.max(0L, submittedServerTick));
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull BodyUpsertRequest request) {
        return addPhysicsStoreBody(physicsStore(world), request);
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull BodyUpsertRequest request,
        @Nonnull BodyCommandComponent command) {
        Store<PhysicsStore> store = physicsStore(world);
        Ref<PhysicsStore> bodyRef = addPhysicsStoreBody(store, request);
        appendPhysicsStoreBodyCommand(store, bodyRef, command);
        return bodyRef;
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull BodyUpsertRequest request,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target) {
        return addPhysicsStoreBody(physicsStore(world), request, dynamics, target);
    }

    public static void addPhysicsStoreBodies(@Nonnull World world,
        @Nonnull Iterable<BodyUpsertRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        for (BodyUpsertRequest request : requests) {
            addPhysicsStoreBody(world, request);
        }
    }

    @Nonnull
    private static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyUpsertRequest request) {
        Objects.requireNonNull(request, "request");
        return addPhysicsStoreBody(store, request, request.dynamics(), request.target());
    }

    @Nonnull
    private static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyUpsertRequest request,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target) {
        Objects.requireNonNull(request, "request");
        return store.addEntity(PhysicsStoreEntities.bodyHolder(store,
            request.bodyUuid(),
            request.body(),
            Objects.requireNonNull(dynamics, "dynamics"),
            target,
            request.collider(),
            request.shape(),
            request.material(),
            request.filter()), AddReason.SPAWN);
    }

    public static boolean appendPhysicsStoreBodyCommand(@Nonnull World world,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyCommandComponent command) {
        Objects.requireNonNull(command, "command");
        Store<PhysicsStore> store = physicsStore(world);
        Ref<PhysicsStore> bodyRef = store
            .getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(Objects.requireNonNull(bodyUuid, "bodyUuid"));
        if (bodyRef == null || !bodyRef.isValid()) {
            return false;
        }
        appendPhysicsStoreBodyCommand(store, bodyRef, command);
        return true;
    }

    public static void appendPhysicsStoreBodyCommand(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull BodyCommandComponent command) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(bodyRef, "bodyRef");
        Objects.requireNonNull(command, "command");
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

    @Nonnull
    public static SpawnedBlockBody spawnBlockBodyLegacy(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        PendingBlockBodyCapture pending = new PendingBlockBodyCapture();
        requireApplied(resource.submitCommands(0L, linearVelocity != null ? 2 : 1, commands ->
            pending.set(recordBlockBodySpawn(commands,
                spaceId,
                visualPosition,
                blockType,
                shape,
                mass,
                settings,
                linearVelocity))), "spawn attached block body");
        return attachRecordedBlockBody(store, time, pending.require());
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
                bodyUpsertRequest(spaceUuid,
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
    public static BodyUpsertRequest bodyUpsertRequest(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        return PhysicsBodySpawnRequests.dynamicBody(spaceUuid,
            bodyUuid,
            bodyCenter,
            shape,
            mass,
            settings,
            linearVelocity,
            PhysicsBodyPersistenceMode.PERSISTENT);
    }

    @Nonnull
    private static BodyUpsertRequest bodyUpsertRequest(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return PhysicsBodySpawnRequests.body(spaceUuid,
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
    public static BodyRequestBatchTiming enqueueDynamicBodyBatchMeasured(@Nonnull World world,
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
            return new BodyRequestBatchTiming(0, plan.setupWallNanos(), 0L);
        }

        long requestStartNanos = System.nanoTime();
        enqueuePhysicsStoreRequests(world, plan.requests());
        long requestEnqueueNanos = System.nanoTime() - requestStartNanos;
        return new BodyRequestBatchTiming(plan.count(),
            plan.setupWallNanos(),
            requestEnqueueNanos);
    }

    @Nonnull
    public static FencedBodyRequestBatchTiming enqueueDynamicBodyBatchFencedMeasured(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        long submittedServerTick,
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

        long requestStartNanos = System.nanoTime();
        PhysicsStoreRequestFenceHandle fence = enqueuePhysicsStoreRequestsFenced(world,
            plan.requests(),
            submittedServerTick);
        long requestEnqueueNanos = System.nanoTime() - requestStartNanos;
        return new FencedBodyRequestBatchTiming(plan.count(),
            plan.setupWallNanos(),
            requestEnqueueNanos,
            fence);
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
            throw new IllegalStateException("Cannot enqueue dynamic body batch because the target space is not "
                + "bound in PhysicsStore: " + spaceId.value());
        }

        List<BodyUpsertRequest> requests = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            RigidBodyKey bodyKey = batch.bodyKey(i);
            requests.add(bodyUpsertRequest(spaceUuid,
                bodyKey.value(),
                new Vector3f(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                shape,
                mass,
                settings,
                null,
                kind,
                persistenceMode));
        }

        return new DynamicBodyBatchPlan(requests, System.nanoTime() - setupStartNanos);
    }

    @Nonnull
    public static PendingBlockBody recordBlockBodySpawn(@Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings) {
        return recordBlockBodySpawn(commands,
            spaceId,
            visualPosition,
            DEFAULT_BLOCK_TYPE,
            shape,
            mass,
            settings,
            null);
    }

    @Nonnull
    public static PendingBlockBody recordBlockBodySpawn(@Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings) {
        return recordBlockBodySpawn(commands,
            spaceId,
            visualPosition,
            blockType,
            shape,
            mass,
            settings,
            null);
    }

    @Nonnull
    public static PendingBlockBody recordBlockBodySpawn(@Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(visualPosition, "visualPosition");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(settings, "settings");
        return recordBlockBodySpawnAtResolvedPose(commands,
            spaceId,
            toVector3f(visualPosition),
            visualPosition,
            blockType,
            shape,
            mass,
            settings,
            linearVelocity);
    }

    @Nonnull
    public static PendingBlockBody recordBlockBodySpawnAtBodyCenter(@Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d bodyCenter,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings) {
        return recordBlockBodySpawnAtBodyCenter(commands,
            spaceId,
            bodyCenter,
            blockType,
            shape,
            mass,
            settings,
            null);
    }

    @Nonnull
    public static PendingBlockBody recordBlockBodySpawnAtBodyCenter(@Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d bodyCenter,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        Objects.requireNonNull(bodyCenter, "bodyCenter");
        Objects.requireNonNull(shape, "shape");
        return recordBlockBodySpawnAtResolvedPose(commands,
            spaceId,
            new Vector3f((float) bodyCenter.x, (float) bodyCenter.y, (float) bodyCenter.z),
            visualPositionFromBodyCenter(bodyCenter, shape),
            blockType,
            shape,
            mass,
            settings,
            linearVelocity);
    }

    @Nonnull
    private static PendingBlockBody recordBlockBodySpawnAtResolvedPose(@Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f bodyCenter,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(bodyCenter, "bodyCenter");
        Objects.requireNonNull(visualPosition, "visualPosition");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(settings, "settings");
        RigidBodyKey bodyKey = RigidBodyKey.random();
        commands.spawnBody(bodyKey, spawn -> spawn
            .space(spaceId)
            .shape(shape)
            .mass(mass)
            .dynamic()
            .position(bodyCenter.x,
                bodyCenter.y,
                bodyCenter.z)
            .settings(settings)
            .persistent());
        if (linearVelocity != null) {
            commands.setBodyVelocity(bodyKey,
                linearVelocity.x,
                linearVelocity.y,
                linearVelocity.z,
                0.0f,
                0.0f,
                0.0f,
                true);
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
    public static SpawnedBlockBody attachRecordedBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PendingBlockBody pending) {
        Ref<EntityStore> entity = spawnAttachedBlockEntity(store,
            time,
            pending.bodyKey(),
            pending.spaceId(),
            pending.blockType(),
            new Vector3d(pending.positionX(), pending.positionY(), pending.positionZ()),
            pending.controllable());
        assert entity != null;
        return new SpawnedBlockBody(pending.bodyKey(), pending.spaceId(), entity);
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

        List<BodyUpsertRequest> requests = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            RigidBodyKey bodyKey = batch.bodyKey(i);
            requests.add(bodyUpsertRequest(spaceUuid,
                bodyKey.value(),
                new Vector3f(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                shape,
                mass,
                settings,
                null));
        }

        long commandStartNanos = System.nanoTime();
        addPhysicsStoreBodies(world, requests);
        long commandApplyNanos = System.nanoTime() - commandStartNanos;

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
            commandApplyNanos,
            entityAttachNanos);
    }

    public static void requireApplied(@Nonnull PhysicsCommandHandle handle,
        @Nonnull String operation) {
        handle.firstRejected()
            .toCompletableFuture()
            .join()
            .ifPresent(result -> {
                throw new IllegalStateException(operation + " command "
                    + result.commandSequence() + " rejected: " + result.message());
            });
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
    public static Ref<EntityStore> spawnAttachedBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        boolean controllable) {
        Holder<EntityStore> holder = attachedBlockEntityHolder(time,
            bodyKey,
            spaceId,
            blockType,
            visualPosition,
            controllable);
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    public static Holder<EntityStore> attachedBlockEntityHolder(@Nonnull TimeResource time,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        boolean controllable) {
        return attachedBlockEntityHolder(time,
            bodyKey,
            spaceId,
            blockType,
            visualPosition,
            new Vector3f(),
            controllable);
    }

    @Nonnull
    public static Holder<EntityStore> attachedBlockEntityHolder(@Nonnull TimeResource time,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        @Nonnull Vector3f localPositionOffset,
        boolean controllable) {
        return attachedBlockEntityHolder(time,
            bodyKey,
            spaceId,
            blockType,
            visualPosition,
            localPositionOffset,
            new Quaternionf(),
            controllable);
    }

    @Nonnull
    public static Holder<EntityStore> attachedBlockEntityHolder(@Nonnull TimeResource time,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        @Nonnull Vector3f localPositionOffset,
        float visualOriginOffsetY,
        boolean controllable) {
        return attachedBlockEntityHolder(time,
            bodyKey,
            spaceId,
            blockType,
            visualPosition,
            localPositionOffset,
            new Quaternionf(),
            visualOriginOffsetY,
            controllable);
    }

    @Nonnull
    public static Holder<EntityStore> attachedBlockEntityHolder(@Nonnull TimeResource time,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        boolean controllable) {
        return attachedBlockEntityHolder(time,
            bodyKey,
            spaceId,
            blockType,
            visualPosition,
            localPositionOffset,
            localRotationOffset,
            Float.NaN,
            controllable);
    }

    @Nonnull
    public static Holder<EntityStore> attachedBlockEntityHolder(@Nonnull TimeResource time,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY,
        boolean controllable) {
        Holder<EntityStore> holder = blockEntityHolder(time, blockType, visualPosition);
        holder.addComponent(ATTACHMENT_TYPE,
            BodyAttachmentComponent.impulseOwnedVisual(bodyKey.value(),
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
                                       long commandApplyNanos,
                                       long entityAttachNanos) {

        public BlockBodyBatchTiming {
            count = Math.max(0, count);
            commandApplyNanos = Math.max(0L, commandApplyNanos);
            entityAttachNanos = Math.max(0L, entityAttachNanos);
        }
    }

    public record BodyRequestBatchTiming(int count,
                                         long setupWallNanos,
                                         long requestEnqueueNanos) {

        public BodyRequestBatchTiming {
            count = Math.max(0, count);
            setupWallNanos = Math.max(0L, setupWallNanos);
            requestEnqueueNanos = Math.max(0L, requestEnqueueNanos);
        }
    }

    public record FencedBodyRequestBatchTiming(int count,
                                               long setupWallNanos,
                                               long requestEnqueueNanos,
                                               @Nonnull PhysicsStoreRequestFenceHandle fence) {

        public FencedBodyRequestBatchTiming {
            count = Math.max(0, count);
            setupWallNanos = Math.max(0L, setupWallNanos);
            requestEnqueueNanos = Math.max(0L, requestEnqueueNanos);
            Objects.requireNonNull(fence, "fence");
        }
    }

    private record DynamicBodyBatchPlan(@Nonnull List<BodyUpsertRequest> requests,
                                        long setupWallNanos) {

        DynamicBodyBatchPlan {
            requests = List.copyOf(Objects.requireNonNull(requests, "requests"));
            setupWallNanos = Math.max(0L, setupWallNanos);
        }

        private int count() {
            return requests.size();
        }

        private boolean isEmpty() {
            return requests.isEmpty();
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

    private static final class PendingBlockBodyCapture {

        @Nullable
        private PendingBlockBody body;

        private void set(@Nonnull PendingBlockBody body) {
            this.body = Objects.requireNonNull(body, "body");
        }

        @Nonnull
        private PendingBlockBody require() {
            if (body == null) {
                throw new IllegalStateException("Pending block body was not recorded");
            }
            return body;
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
                                        long commandApplyNanos,
                                        long entityAttachNanos) {

        private BlockBodyBatchResult {
            if (bodies != null && bodies.length != count) {
                throw new IllegalArgumentException("Collected body count does not match batch count");
            }
            count = Math.max(0, count);
            commandApplyNanos = Math.max(0L, commandApplyNanos);
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
                commandApplyNanos,
                entityAttachNanos);
        }
    }
}
