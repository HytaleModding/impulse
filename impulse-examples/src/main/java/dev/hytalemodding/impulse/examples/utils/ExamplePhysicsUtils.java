package dev.hytalemodding.impulse.examples.utils;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityAttachments;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.BodyEntityDescriptor;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ExamplePhysicsUtils {

    public static final String DEFAULT_BLOCK_TYPE =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE;

    private ExamplePhysicsUtils() {
    }

    @Nullable
    public static Ref<PhysicsStore> resolveSpaceRef(@Nonnull World world,
        @Nonnull SpaceId spaceId) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        return PhysicsSpaces.resolveRef(store, spaceId);
    }

    @Nullable
    public static SpaceSelection spaceSelection(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        if (spaceArg.provided(ctx)) {
            int rawSpaceId = spaceArg.get(ctx);
            if (rawSpaceId <= 0) {
                ctx.sender().sendMessage(Message.raw("Space id must be a positive integer."));
                return null;
            }
            SpaceId spaceId = new SpaceId(rawSpaceId);
            Ref<PhysicsStore> spaceRef = PhysicsSpaces.resolveRef(store, spaceId);
            if (spaceRef != null) {
                return new SpaceSelection(spaceId, spaceRef);
            }
            if (PhysicsSpaces.hasSpace(store, spaceId)) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + rawSpaceId
                    + " is not bound yet."));
            } else {
                ctx.sender().sendMessage(Message.raw("No physics space id=" + rawSpaceId + " exists."));
            }
            return null;
        }

        SpaceId firstSpaceId = PhysicsSpaces.spaceIds(store)
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
        if (firstSpaceId == null) {
            ctx.sender().sendMessage(Message.raw("No physics space exists. Run "
                + "`/impulse space create --backend=<id>` before running Impulse example commands."));
            return null;
        }
        Ref<PhysicsStore> spaceRef = PhysicsSpaces.resolveRef(store, firstSpaceId);
        if (spaceRef == null) {
            ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + firstSpaceId.value()
                + " is not bound yet."));
            return null;
        }
        return new SpaceSelection(firstSpaceId, spaceRef);
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull BodyEntityDescriptor descriptor) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        return addPhysicsStoreBody(store, descriptor);
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull BodyEntityDescriptor descriptor,
        @Nonnull BodyCommandComponent command) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        Ref<PhysicsStore> bodyRef = addPhysicsStoreBody(store, descriptor);
        PhysicsBodies.appendCommand(store, bodyRef, command);
        return bodyRef;
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull BodyEntityDescriptor descriptor,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        return addPhysicsStoreBody(store, descriptor, dynamics, target);
    }

    public static void addPhysicsStoreBodies(@Nonnull World world,
        @Nonnull Iterable<BodyEntityDescriptor> descriptors) {
        Objects.requireNonNull(descriptors, "descriptors");
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        PhysicsThreading.requireWorldThread(store, "add PhysicsStore body entities");
        List<Holder<PhysicsStore>> holders = new ArrayList<>();
        for (BodyEntityDescriptor descriptor : descriptors) {
            holders.add(bodyHolder(store,
                Objects.requireNonNull(descriptor, "descriptor"),
                descriptor.dynamics(),
                descriptor.target()));
        }
        if (!holders.isEmpty()) {
            @SuppressWarnings("unchecked")
            Holder<PhysicsStore>[] holderArray = holders.toArray(Holder[]::new);
            store.addEntities(holderArray, AddReason.SPAWN);
        }
    }

    @Nonnull
    private static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyEntityDescriptor descriptor) {
        return addPhysicsStoreBody(store,
            descriptor,
            descriptor.dynamics(),
            descriptor.target());
    }

    @Nonnull
    private static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyEntityDescriptor descriptor,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target) {
        Objects.requireNonNull(descriptor, "descriptor");
        PhysicsThreading.requireWorldThread(store, "add a PhysicsStore body entity");
        return addPhysicsStoreBodyUnchecked(store, descriptor, dynamics, target);
    }

    @Nonnull
    private static Ref<PhysicsStore> addPhysicsStoreBodyUnchecked(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyEntityDescriptor descriptor,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target) {
        return store.addEntity(bodyHolder(store, descriptor, dynamics, target), AddReason.SPAWN);
    }

    @Nonnull
    public static Holder<PhysicsStore> bodyHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyEntityDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return bodyHolder(store, descriptor, descriptor.dynamics(), descriptor.target());
    }

    @Nonnull
    private static Holder<PhysicsStore> bodyHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodyEntityDescriptor descriptor,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target) {
        return PhysicsEntities.bodyHolder(store,
            descriptor.bodyUuid(),
            descriptor.body(),
            Objects.requireNonNull(dynamics, "dynamics"),
            target,
            descriptor.collider(),
            descriptor.shape(),
            descriptor.material(),
            descriptor.filter());
    }

    @Nonnull
    public static Ref<PhysicsStore> addJoint(@Nonnull World world,
        @Nonnull UUID jointUuid,
        @Nonnull JointComponent joint) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        PhysicsThreading.requireWorldThread(store, "add a PhysicsStore joint entity");
        return store.addEntity(PhysicsEntities.jointHolder(store,
            Objects.requireNonNull(jointUuid, "jointUuid"),
            joint), AddReason.SPAWN);
    }

    @Nullable
    public static SpaceId spaceId(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        if (spaceArg.provided(ctx)) {
            int rawSpaceId = spaceArg.get(ctx);
            if (rawSpaceId <= 0) {
                ctx.sender().sendMessage(Message.raw("Space id must be a positive integer."));
                return null;
            }
            SpaceId spaceId = new SpaceId(rawSpaceId);
            if (!PhysicsSpaces.hasSpace(store, spaceId)) {
                ctx.sender().sendMessage(Message.raw("No physics space id=" + rawSpaceId + " exists."));
                return null;
            }
            return spaceId;
        }

        SpaceId firstSpaceId = PhysicsSpaces.spaceIds(store)
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
    public static BodyEntityDescriptor bodyEntity(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        return PhysicsBodyEntities.dynamicBody(spaceRef,
            bodyUuid,
            bodyCenter,
            shape,
            mass,
            settings,
            linearVelocity,
            PhysicsBodyPersistenceMode.PERSISTENT);
    }

    @Nonnull
    private static BodyEntityDescriptor bodyEntity(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return PhysicsBodyEntities.body(spaceRef,
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
    public static BodyEntityBatchTiming addDynamicBodyBatchMeasured(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        DynamicBodyBatchPlan plan = dynamicBodyBatchPlan(world,
            spaceId,
            expectedBodies,
            shape,
            mass,
            settings,
            kind,
            persistenceMode,
            builder);
        if (plan.isEmpty()) {
            return new BodyEntityBatchTiming(0, plan.setupWallNanos(), 0L);
        }

        long applyStartNanos = System.nanoTime();
        addPhysicsStoreBodies(world, plan.bodies());
        long entityApplyNanos = System.nanoTime() - applyStartNanos;
        return new BodyEntityBatchTiming(plan.count(),
            plan.setupWallNanos(),
            entityApplyNanos);
    }

    @Nonnull
    public static BodyEntityBatchTiming addDynamicBodyBatchMeasured(@Nonnull World world,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        DynamicBodyBatchPlan plan = dynamicBodyBatchPlan(spaceRef,
            spaceId,
            expectedBodies,
            shape,
            mass,
            settings,
            kind,
            persistenceMode,
            builder);
        if (plan.isEmpty()) {
            return new BodyEntityBatchTiming(0, plan.setupWallNanos(), 0L);
        }

        long applyStartNanos = System.nanoTime();
        addPhysicsStoreBodies(world, plan.bodies());
        long entityApplyNanos = System.nanoTime() - applyStartNanos;
        return new BodyEntityBatchTiming(plan.count(),
            plan.setupWallNanos(),
            entityApplyNanos);
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
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(persistenceMode, "persistenceMode");

        long setupStartNanos = System.nanoTime();
        BlockBodyBatchBuilder batch = new BlockBodyBatchBuilder(expectedBodies);
        Objects.requireNonNull(builder, "builder").accept(batch);
        batch.seal();
        if (batch.isEmpty()) {
            return new DynamicBodyBatchPlan(List.of(), 0L);
        }

        Ref<PhysicsStore> spaceRef = resolveSpaceRef(world, spaceId);
        if (spaceRef == null) {
            throw new IllegalStateException("Cannot add dynamic body entities because the target space is not "
                + "bound in PhysicsStore: " + spaceId.value());
        }

        return dynamicBodyBatchPlan(spaceRef,
            spaceId,
            shape,
            mass,
            settings,
            kind,
            persistenceMode,
            batch,
            setupStartNanos);
    }

    @Nonnull
    private static DynamicBodyBatchPlan dynamicBodyBatchPlan(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        Objects.requireNonNull(spaceRef, "spaceRef");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(persistenceMode, "persistenceMode");

        PhysicsThreading.requireWorldThread(spaceRef.getStore(),
            "add dynamic PhysicsStore body entities");
        if (!spaceRef.isValid()) {
            throw new IllegalStateException("Cannot add dynamic body entities because the target "
                + "PhysicsStore space entity is no longer valid: " + spaceId.value());
        }

        long setupStartNanos = System.nanoTime();
        BlockBodyBatchBuilder batch = new BlockBodyBatchBuilder(expectedBodies);
        Objects.requireNonNull(builder, "builder").accept(batch);
        batch.seal();
        if (batch.isEmpty()) {
            return new DynamicBodyBatchPlan(List.of(), 0L);
        }

        return dynamicBodyBatchPlan(spaceRef,
            spaceId,
            shape,
            mass,
            settings,
            kind,
            persistenceMode,
            batch,
            setupStartNanos);
    }

    @Nonnull
    private static DynamicBodyBatchPlan dynamicBodyBatchPlan(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull BlockBodyBatchBuilder batch,
        long setupStartNanos) {
        List<BodyEntityDescriptor> bodies = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            UUID bodyUuid = batch.bodyUuid(i);
            bodies.add(bodyEntity(spaceRef,
                bodyUuid,
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
    public static SpawnedBlockBody attachBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull CreatedBlockBody created) {
        Ref<PhysicsStore> bodyRef = created.bodyRef();
        PhysicsThreading.requireWorldThread(bodyRef.getStore(),
            "attach a visual to a created PhysicsStore body entity");
        if (!bodyRef.isValid()) {
            throw new IllegalStateException("Cannot attach visual because PhysicsStore body entity "
                + "is no longer valid: " + created.bodyUuid());
        }
        Ref<EntityStore> entity = spawnAttachedBlockEntity(store,
            time,
            bodyRef,
            created.bodyUuid(),
            created.blockType(),
            new Vector3d(created.positionX(), created.positionY(), created.positionZ()),
            created.controllable());
        assert entity != null;
        return new SpawnedBlockBody(created.bodyUuid(), created.spaceId(), entity);
    }

    @Nonnull
    public static SpawnedBlockBody[] spawnBlockBodies(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        long serverTick,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        return spawnBlockBodiesInternal(store,
            time,
            serverTick,
            spaceId,
            expectedBodies,
            blockType,
            shape,
            mass,
            settings,
            builder,
            true).collectedBodies();
    }

    @Nonnull
    public static SpawnedBlockBody[] spawnBlockBodies(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        long serverTick,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        return spawnBlockBodiesInternal(store,
            time,
            serverTick,
            spaceRef,
            spaceId,
            expectedBodies,
            blockType,
            shape,
            mass,
            settings,
            builder,
            true).collectedBodies();
    }

    @Nonnull
    public static BlockBodyBatchTiming spawnBlockBodiesMeasured(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        long serverTick,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        return spawnBlockBodiesInternal(store,
            time,
            serverTick,
            spaceId,
            expectedBodies,
            blockType,
            shape,
            mass,
            settings,
            builder,
            false).timing();
    }

    @Nonnull
    public static BlockBodyBatchTiming spawnBlockBodiesMeasured(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        long serverTick,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        return spawnBlockBodiesInternal(store,
            time,
            serverTick,
            spaceRef,
            spaceId,
            expectedBodies,
            blockType,
            shape,
            mass,
            settings,
            builder,
            false).timing();
    }

    @Nonnull
    private static BlockBodyBatchResult spawnBlockBodiesInternal(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        long serverTick,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder,
        boolean collectBodies) {
        BlockBodyBatchBuilder batch = new BlockBodyBatchBuilder(expectedBodies);
        Objects.requireNonNull(builder, "builder").accept(batch);
        batch.seal();
        if (batch.isEmpty()) {
            return new BlockBodyBatchResult(collectBodies ? new SpawnedBlockBody[0] : null,
                0,
                0L,
                0L);
        }

        World world = store.getExternalData().getWorld();
        Ref<PhysicsStore> spaceRef = resolveSpaceRef(world, spaceId);
        if (spaceRef == null) {
            throw new IllegalStateException("Cannot spawn block body batch because the target space is not "
                + "bound in PhysicsStore: " + spaceId.value());
        }

        return spawnBlockBodiesInternal(store,
            time,
            serverTick,
            spaceRef,
            spaceId,
            blockType,
            shape,
            mass,
            settings,
            batch,
            collectBodies);
    }

    @Nonnull
    private static BlockBodyBatchResult spawnBlockBodiesInternal(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        long serverTick,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder,
        boolean collectBodies) {
        Objects.requireNonNull(spaceRef, "spaceRef");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(settings, "settings");

        PhysicsThreading.requireWorldThread(spaceRef.getStore(),
            "spawn PhysicsStore block body entities");
        if (!spaceRef.isValid()) {
            throw new IllegalStateException("Cannot spawn block body batch because the target "
                + "PhysicsStore space entity is no longer valid: " + spaceId.value());
        }

        BlockBodyBatchBuilder batch = new BlockBodyBatchBuilder(expectedBodies);
        Objects.requireNonNull(builder, "builder").accept(batch);
        batch.seal();
        if (batch.isEmpty()) {
            return new BlockBodyBatchResult(collectBodies ? new SpawnedBlockBody[0] : null,
                0,
                0L,
                0L);
        }

        return spawnBlockBodiesInternal(store,
            time,
            serverTick,
            spaceRef,
            spaceId,
            blockType,
            shape,
            mass,
            settings,
            batch,
            collectBodies);
    }

    @Nonnull
    private static BlockBodyBatchResult spawnBlockBodiesInternal(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        long serverTick,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull BlockBodyBatchBuilder batch,
        boolean collectBodies) {
        World world = store.getExternalData().getWorld();
        List<BodyEntityDescriptor> descriptors = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            UUID bodyUuid = batch.bodyUuid(i);
            descriptors.add(bodyEntity(spaceRef,
                bodyUuid,
                new Vector3f(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                shape,
                mass,
                settings,
                null));
        }

        long entityApplyStartNanos = System.nanoTime();
        addPhysicsStoreBodies(world, descriptors);
        long entityApplyNanos = System.nanoTime() - entityApplyStartNanos;

        long entityAttachStartNanos = System.nanoTime();
        SpawnedBlockBody[] spawned = collectBodies ? new SpawnedBlockBody[batch.size()] : null;
        for (int i = 0; i < batch.size(); i++) {
            UUID bodyUuid = batch.bodyUuid(i);
            Ref<EntityStore> entity = spawnAttachedBlockEntity(store,
                time,
                null,
                bodyUuid,
                blockType,
                new Vector3d(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                mass > 0.0f);
            if (spawned != null) {
                assert entity != null;
                spawned[i] = new SpawnedBlockBody(bodyUuid, spaceId, entity);
            }
        }
        long entityAttachNanos = System.nanoTime() - entityAttachStartNanos;
        return new BlockBodyBatchResult(spawned,
            batch.size(),
            entityApplyNanos,
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
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType) {
        return spawnExternalBodyViewBlockEntity(store,
            time,
            null,
            bodyUuid,
            visualPosition,
            blockType);
    }

    @Nullable
    public static Ref<EntityStore> spawnExternalBodyViewBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType) {
        requirePhysicsEntityVisuals();
        Holder<EntityStore> holder = blockEntityHolder(time, blockType, visualPosition);
        holder.addComponent(BodyAttachmentComponent.getComponentType(),
            externalBodyAttachment(bodyUuid, bodyRef));
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nullable
    private static Ref<EntityStore> spawnAttachedBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID physicsBodyUuid,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        boolean controllable) {
        Holder<EntityStore> holder = attachedPhysicsStoreBlockEntityHolder(time,
            bodyRef,
            physicsBodyUuid,
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
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY,
        boolean controllable) {
        return attachedPhysicsStoreBlockEntityHolder(time,
            null,
            physicsBodyUuid,
            blockType,
            visualPosition,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY,
            controllable);
    }

    @Nonnull
    public static Holder<EntityStore> attachedPhysicsStoreBlockEntityHolder(@Nonnull TimeResource time,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID physicsBodyUuid,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY,
        boolean controllable) {
        requirePhysicsEntityVisuals();
        Holder<EntityStore> holder = blockEntityHolder(time, blockType, visualPosition);
        holder.addComponent(BodyAttachmentComponent.getComponentType(),
            impulseOwnedBodyAttachment(physicsBodyUuid,
                bodyRef,
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
    static BodyAttachmentComponent externalBodyAttachment(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        BodyAttachmentComponent attachment = BodyAttachmentComponent.externalEntity(bodyUuid);
        attachment.setBodyRef(bodyRef);
        return attachment;
    }

    @Nonnull
    static BodyAttachmentComponent impulseOwnedBodyAttachment(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY) {
        BodyAttachmentComponent attachment = BodyAttachmentComponent.impulseOwnedVisual(bodyUuid,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
        attachment.setBodyRef(bodyRef);
        return attachment;
    }

    @Nonnull
    private static Holder<EntityStore> blockEntityHolder(@Nonnull TimeResource time,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition) {
        return ExampleBlockEntityVisuals.impulseOwnedBlockVisual(time, blockType, visualPosition);
    }

    private static void requirePhysicsEntityVisuals() {
        if (!PhysicsEntityAttachments.isAvailable()) {
            throw new IllegalStateException(
                "Impulse PhysicsEntity integration is not available. "
                    + "Enable HytaleModding:ImpulsePhysicsEntity to spawn entity-backed example visuals.");
        }
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

    public static Vector3f toVector3f(@Nonnull Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    public record SpawnedBlockBody(@Nonnull UUID bodyUuid,
                                   @Nonnull SpaceId spaceId,
                                   @Nonnull Ref<EntityStore> entity) {
    }

    public record SpaceSelection(@Nonnull SpaceId spaceId,
                                 @Nonnull Ref<PhysicsStore> spaceRef) {

        public SpaceSelection {
            Objects.requireNonNull(spaceId, "spaceId");
            Objects.requireNonNull(spaceRef, "spaceRef");
        }
    }

    public record BlockBodyBatchTiming(int count,
                                       long entityApplyNanos,
                                       long entityAttachNanos) {

        public BlockBodyBatchTiming {
            count = Math.max(0, count);
            entityApplyNanos = Math.max(0L, entityApplyNanos);
            entityAttachNanos = Math.max(0L, entityAttachNanos);
        }
    }

    public record BodyEntityBatchTiming(int count,
                                        long setupWallNanos,
                                        long entityApplyNanos) {

        public BodyEntityBatchTiming {
            count = Math.max(0, count);
            setupWallNanos = Math.max(0L, setupWallNanos);
            entityApplyNanos = Math.max(0L, entityApplyNanos);
        }
    }

    private record DynamicBodyBatchPlan(@Nonnull List<BodyEntityDescriptor> bodies,
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

    public record CreatedBlockBody(@Nonnull UUID bodyUuid,
                                   @Nonnull Ref<PhysicsStore> bodyRef,
                                   @Nonnull SpaceId spaceId,
                                   @Nullable String blockType,
                                   float positionX,
                                   float positionY,
                                   float positionZ,
                                   boolean controllable) {

        public CreatedBlockBody {
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            Objects.requireNonNull(bodyRef, "bodyRef");
            Objects.requireNonNull(spaceId, "spaceId");
        }
    }

}
