package dev.hytalemodding.impulse.examples.commands.stress;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.physics.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

final class StressBodyBatches {

    private StressBodyBatches() {
    }

    @Nonnull
    static BodyEntityBatchTiming addDynamicBodyBatchMeasured(@Nonnull World world,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        DynamicBodyBatchPlan plan = dynamicBodyBatchPlan(spaceRef,
            spaceId,
            expectedBodies,
            shape,
            mass,
            settings,
            builder);
        if (plan.isEmpty()) {
            return new BodyEntityBatchTiming(0, plan.setupWallNanos(), 0L);
        }

        long applyStartNanos = System.nanoTime();
        addPhysicsStoreBodies(PhysicsThreading.store(world), plan.bodies());
        long physicsStoreApplyNanos = System.nanoTime() - applyStartNanos;
        return new BodyEntityBatchTiming(plan.count(),
            plan.setupWallNanos(),
            physicsStoreApplyNanos);
    }

    @Nonnull
    static BlockBodyBatchTiming spawnBlockBodiesMeasured(@Nonnull Store<EntityStore> store,
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
        Objects.requireNonNull(spaceRef, "spaceRef");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(settings, "settings");

        PhysicsThreading.requireWorldThread(spaceRef.getStore(),
            "spawn stress PhysicsStore block body entities");
        if (!spaceRef.isValid()) {
            throw new IllegalStateException("Cannot spawn block body batch because the target "
                + "PhysicsStore space entity is no longer valid: " + spaceId.value());
        }

        BlockBodyBatchBuilder batch = new BlockBodyBatchBuilder(expectedBodies);
        Objects.requireNonNull(builder, "builder").accept(batch);
        batch.seal();
        if (batch.isEmpty()) {
            return new BlockBodyBatchTiming(0, 0L, 0L);
        }

        List<Holder<PhysicsStore>> bodyHolders = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            UUID bodyUuid = batch.bodyUuid(i);
            bodyHolders.add(PhysicsBodyEntities.dynamicBodyHolder(spaceRef,
                bodyUuid,
                new Vector3f(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                shape,
                mass,
                settings,
                null));
        }

        long physicsStoreApplyStartNanos = System.nanoTime();
        addPhysicsStoreBodies(spaceRef.getStore(), bodyHolders);
        long physicsStoreApplyNanos = System.nanoTime() - physicsStoreApplyStartNanos;

        long visualAttachStartNanos = System.nanoTime();
        for (int i = 0; i < batch.size(); i++) {
            UUID bodyUuid = batch.bodyUuid(i);
            store.addEntity(ExamplePhysicsUtils.attachedPhysicsBlockEntityHolder(time,
                    null,
                    bodyUuid,
                    blockType,
                    new Vector3d(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                    new Vector3f(),
                    new Quaternionf(),
                    Float.NaN,
                    mass > 0.0f),
                AddReason.SPAWN);
        }
        long visualAttachNanos = System.nanoTime() - visualAttachStartNanos;
        return new BlockBodyBatchTiming(batch.size(), physicsStoreApplyNanos, visualAttachNanos);
    }

    @Nonnull
    private static DynamicBodyBatchPlan dynamicBodyBatchPlan(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        int expectedBodies,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull Consumer<BlockBodyBatchBuilder> builder) {
        Objects.requireNonNull(spaceRef, "spaceRef");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(settings, "settings");

        PhysicsThreading.requireWorldThread(spaceRef.getStore(),
            "add stress dynamic PhysicsStore body entities");
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

        List<Holder<PhysicsStore>> bodies = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            UUID bodyUuid = batch.bodyUuid(i);
            bodies.add(PhysicsBodyEntities.dynamicBodyHolder(spaceRef,
                bodyUuid,
                new Vector3f(batch.positionX(i), batch.positionY(i), batch.positionZ(i)),
                shape,
                mass,
                settings,
                null));
        }
        return new DynamicBodyBatchPlan(bodies, System.nanoTime() - setupStartNanos);
    }

    private static void addPhysicsStoreBodies(@Nonnull Store<PhysicsStore> store,
        @Nonnull Iterable<Holder<PhysicsStore>> bodyHolders) {
        Objects.requireNonNull(bodyHolders, "bodyHolders");
        PhysicsThreading.requireWorldThread(store, "add stress PhysicsStore body entities");
        List<Holder<PhysicsStore>> holders = new ArrayList<>();
        for (Holder<PhysicsStore> holder : bodyHolders) {
            holders.add(Objects.requireNonNull(holder, "holder"));
        }
        if (!holders.isEmpty()) {
            @SuppressWarnings("unchecked")
            Holder<PhysicsStore>[] holderArray = holders.toArray(Holder[]::new);
            store.addEntities(holderArray, AddReason.SPAWN);
        }
    }

    record BlockBodyBatchTiming(int count,
                                long physicsStoreApplyNanos,
                                long visualAttachNanos) {

        BlockBodyBatchTiming {
            count = Math.max(0, count);
            physicsStoreApplyNanos = Math.max(0L, physicsStoreApplyNanos);
            visualAttachNanos = Math.max(0L, visualAttachNanos);
        }
    }

    record BodyEntityBatchTiming(int count,
                                 long setupWallNanos,
                                 long physicsStoreApplyNanos) {

        BodyEntityBatchTiming {
            count = Math.max(0, count);
            setupWallNanos = Math.max(0L, setupWallNanos);
            physicsStoreApplyNanos = Math.max(0L, physicsStoreApplyNanos);
        }
    }

    private record DynamicBodyBatchPlan(@Nonnull List<Holder<PhysicsStore>> bodies,
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
}
