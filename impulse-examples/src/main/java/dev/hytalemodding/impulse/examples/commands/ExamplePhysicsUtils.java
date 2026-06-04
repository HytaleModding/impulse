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
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKeyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKinematicTargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyMassComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyMaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyOwnershipComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyPersistenceComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodySpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyTypeComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.PhysicsCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
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
    private static final ComponentType<EntityStore, DespawnComponent> DESPAWN_TYPE =
        DespawnComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, ImpulseControllableComponent> IMPULSE_CONTROLLABLE_TYPE =
        ImpulseControllableComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyKeyComponent> RIGID_BODY_KEY_TYPE =
        RigidBodyKeyComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodySpaceComponent> RIGID_BODY_SPACE_TYPE =
        RigidBodySpaceComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyShapeComponent> RIGID_BODY_SHAPE_TYPE =
        RigidBodyShapeComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyTypeComponent> RIGID_BODY_TYPE_TYPE =
        RigidBodyTypeComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyMassComponent> RIGID_BODY_MASS_TYPE =
        RigidBodyMassComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyMaterialComponent> RIGID_BODY_MATERIAL_TYPE =
        RigidBodyMaterialComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyCollisionComponent> RIGID_BODY_COLLISION_TYPE =
        RigidBodyCollisionComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyPersistenceComponent> RIGID_BODY_PERSISTENCE_TYPE =
        RigidBodyPersistenceComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyOwnershipComponent> RIGID_BODY_OWNERSHIP_TYPE =
        RigidBodyOwnershipComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyKinematicTargetComponent>
        RIGID_BODY_KINEMATIC_TARGET_TYPE = RigidBodyKinematicTargetComponent.getComponentType();
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
        PendingBlockBody[] pending = new PendingBlockBody[1];
        requireApplied(resource.submitCommands(0L, linearVelocity != null ? 2 : 1, commands ->
            pending[0] = recordBlockBodySpawn(commands,
                spaceId,
                visualPosition,
                blockType,
                shape,
                mass,
                settings,
                linearVelocity)), "spawn attached block body");
        return attachRecordedBlockBody(store, time, pending[0]);
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
        RigidBodyKey bodyKey = RigidBodyKey.random();
        commands.spawnBody(bodyKey, spawn -> spawn
            .space(spaceId)
            .shape(shape)
            .mass(mass)
            .dynamic()
            .position((float) visualPosition.x,
                (float) visualPosition.y,
                (float) visualPosition.z)
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

        long commandStartNanos = System.nanoTime();
        requireApplied(resource.submitCommands(serverTick, 1, commands ->
            commands.spawnBodies(batch.size(),
                spaceId,
                shape,
                mass,
                PhysicsBodyType.DYNAMIC,
                settings,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.PERSISTENT,
                spawns -> {
                    for (int i = 0; i < batch.size(); i++) {
                        spawns.body(batch.bodyKeyMostSignificantBits(i),
                            batch.bodyKeyLeastSignificantBits(i),
                            batch.positionX(i),
                            batch.positionY(i),
                            batch.positionZ(i));
                    }
                })), "spawn attached block bodies");
        long commandApplyNanos = System.nanoTime() - commandStartNanos;

        long entityAttachStartNanos = System.nanoTime();
        SpawnedBlockBody[] spawned = collectBodies ? new SpawnedBlockBody[batch.size()] : null;
        for (int i = 0; i < batch.size(); i++) {
            RigidBodyKey bodyKey = batch.bodyKey(i);
            Ref<EntityStore> entity = spawnAttachedBlockEntity(store,
                time,
                bodyKey,
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

    @Nullable
    public static Ref<EntityStore> spawnRigidBodyComponentBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        @Nonnull RigidBodyOwnershipComponent.Ownership ownership,
        @Nullable RigidBodyKinematicTargetComponent kinematicTarget) {
        Holder<EntityStore> holder = blockEntityHolder(time, blockType, visualPosition);
        holder.addComponent(RIGID_BODY_KEY_TYPE, new RigidBodyKeyComponent(bodyKey));
        holder.addComponent(RIGID_BODY_SPACE_TYPE, new RigidBodySpaceComponent(spaceId));
        holder.addComponent(RIGID_BODY_SHAPE_TYPE,
            new RigidBodyShapeComponent(dev.hytalemodding.impulse.api.ShapeType.BOX,
                0.5f,
                0.5f,
                0.5f,
                0.0f,
                0.0f,
                dev.hytalemodding.impulse.api.PhysicsAxis.Y,
                0.0f));
        holder.addComponent(RIGID_BODY_TYPE_TYPE, new RigidBodyTypeComponent(bodyType));
        holder.addComponent(RIGID_BODY_MASS_TYPE, new RigidBodyMassComponent(mass));
        holder.addComponent(RIGID_BODY_MATERIAL_TYPE, new RigidBodyMaterialComponent(0.5f, 0.2f, 0.0f, 0.0f));
        holder.addComponent(RIGID_BODY_COLLISION_TYPE, new RigidBodyCollisionComponent(false,
            dev.hytalemodding.impulse.api.PhysicsCollisionFilters.DYNAMIC_BODY,
            dev.hytalemodding.impulse.api.PhysicsCollisionFilters.TERRAIN
                | dev.hytalemodding.impulse.api.PhysicsCollisionFilters.DYNAMIC_BODY));
        holder.addComponent(RIGID_BODY_PERSISTENCE_TYPE,
            new RigidBodyPersistenceComponent(PhysicsBodyPersistenceMode.PERSISTENT));
        holder.addComponent(RIGID_BODY_OWNERSHIP_TYPE, new RigidBodyOwnershipComponent(ownership));
        if (kinematicTarget != null) {
            holder.addComponent(RIGID_BODY_KINEMATIC_TARGET_TYPE, kinematicTarget);
        }
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    public static RigidBodyKinematicTargetComponent kinematicTargetAt(@Nonnull Vector3d position) {
        return new RigidBodyKinematicTargetComponent(toVector3f(position),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            true,
            false,
            true);
    }

    @Nullable
    public static Ref<EntityStore> spawnAttachedBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        boolean controllable) {
        Holder<EntityStore> holder = blockEntityHolder(time, blockType, visualPosition);
        holder.addComponent(ATTACHMENT_TYPE,
            PhysicsBodyAttachmentComponent.externalEntity(bodyKey, spaceId));
        if (controllable) {
            holder.addComponent(IMPULSE_CONTROLLABLE_TYPE, new ImpulseControllableComponent());
        }
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    private static Holder<EntityStore> blockEntityHolder(@Nonnull TimeResource time,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition) {
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            resolveBlockType(blockType),
            new Vector3d(visualPosition)
        );
        holder.removeComponent(DESPAWN_TYPE);
        return holder;
    }

    @Nonnull
    public static String resolveBlockType(@Nullable String blockType) {
        return blockType == null || blockType.isBlank()
            ? DEFAULT_BLOCK_TYPE
            : blockType.trim();
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
