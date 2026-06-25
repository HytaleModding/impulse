package dev.hytalemodding.impulse.core.internal.modules.physicsentity.systems.sync;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.internal.math.PhysicsVisualPoseMath;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.resources.PhysicsBodySyncStateResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.resources.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.systems.visual.PhysicsProjectionCleanupSystem;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.systems.visual.VisualInterestCollector;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Synchronizes physics bodies with Hytale transforms each tick.
 *
 * <p>Runs after the persistence restore group so that newly bootstrapped spaces,
 * hydrated bodies and hydrated joints are all settled before this system reads
 * body transforms.</p>
 *
 * <p>Entities attach to authoritative PhysicsStore body UUIDs. Backend body destruction is explicit
 * through PhysicsStore entities; removing an EntityStore attachment only removes the projection.</p>
 */
public class PhysicsSyncSystem extends EntityTickingSystem<EntityStore> {

    private static final float TRANSFORM_POSITION_EPSILON = 0.000001f;
    private static final float TRANSFORM_ROTATION_EPSILON = 0.000001f;

    @Nonnull
    private final ComponentType<EntityStore, BodyAttachmentComponent> attachmentType;
    @Nonnull
    private final ComponentType<EntityStore, TransformComponent> transformType;
    @Nonnull
    private final Query<EntityStore> query;
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, PhysicsEntityTypes.persistenceRestoreGroup()),
        new SystemDependency<>(Order.AFTER, PhysicsProjectionCleanupSystem.class),
        new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class),
        new SystemDependency<>(Order.BEFORE, UpdateLocationSystems.TickingSystem.class)
    );

    @Nonnull
    private final ThreadLocal<SyncTickContext> syncTickContext = new ThreadLocal<>();

    /**
     * Hytale may run entity ticks in parallel. Each tick task needs independent temporary objects
     * because the backend out-parameter getters write into caller-owned vectors.
     */
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    public PhysicsSyncSystem() {
        this(BodyAttachmentComponent.getComponentType(), TransformComponent.getComponentType());
    }

    PhysicsSyncSystem(
        @Nonnull ComponentType<EntityStore, BodyAttachmentComponent> attachmentType,
        @Nonnull ComponentType<EntityStore, TransformComponent> transformType) {
        this.attachmentType = Objects.requireNonNull(attachmentType, "attachmentType");
        this.transformType = Objects.requireNonNull(transformType, "transformType");
        this.query = Query.and(attachmentType, transformType);
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        // Backend bodies and per-body sync state are owned by the world tick thread.
        // Body poses are read from the world-level snapshot cache before parallel ECS writes.
        return false;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        assert PhysicsRuntimeProfilingResource.getResourceType() != null;
        PhysicsRuntimeProfilingResource profiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        PhysicsRuntimeProfilingResource.SyncCollector collector = profiling.isEnabled()
            ? profiling.beginSyncSample() : null;
        long startNanos = collector != null ? System.nanoTime() : 0L;
        try {
            syncTickContext.set(collectSyncTickContext(store));
            super.tick(dt, systemIndex, store);
        } finally {
            if (collector != null) {
                profiling.finishSyncSample(collector, System.nanoTime() - startNanos);
            }
            syncTickContext.remove();
        }
    }

    @Override
    public void tick(float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        BodyAttachmentComponent attachment = chunk.getComponent(index, attachmentType);
        TransformComponent transform = chunk.getComponent(index, transformType);
        if (attachment == null || transform == null) {
            return;
        }

        Scratch local = scratch.get();
        PhysicsRuntimeProfilingResource.SyncCollector collector = local.getSyncCollector(store);
        if (collector != null) {
            collector.incrementBodiesInspected();
        }
        SyncTickContext context = syncTickContext.get();
        PhysicsBodySnapshot physicsStoreSnapshot = resolvePhysicsStoreSnapshot(entityRef,
            attachment,
            context.snapshotResource(),
            store);
        if (physicsStoreSnapshot != null) {
            if (!PhysicsTransformAuthority.shouldApplyBodyTransform(attachment)) {
                if (collector != null) {
                    collector.incrementSkippedStatic();
                }
                return;
            }
            PhysicsBodyRuntimeState.BodySyncState syncState = store.getResource(
                    PhysicsBodySyncStateResource.getResourceType())
                .getOrCreate(entityRef);
            PhysicsVisualSyncSettings settings =
                context.visualSyncSettings(physicsStoreSnapshot.spaceUuid());
            computeVisualPose(attachment, physicsStoreSnapshot, local);
            boolean kinematic = physicsStoreSnapshot.bodyType() == PhysicsBodyType.KINEMATIC;
            SyncResult result = applyComputedPhysicsStoreSnapshot(transform,
                physicsStoreSnapshot,
                local,
                syncState,
                settings,
                resolveRangeTier(settings,
                    attachment,
                    context.playerInterests(),
                    local.visualPosition,
                    kinematic),
                kinematic,
                dt);
            if (collector != null) {
                recordSyncResult(collector, result);
            }
            return;
        }
        if (collector != null) {
            collector.incrementSkippedMissingSpace();
        }
    }

    @Nullable
    private static PhysicsBodySnapshot resolvePhysicsStoreSnapshot(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull BodyAttachmentComponent attachment,
        @Nonnull PhysicsSnapshotResource snapshotResource,
        @Nonnull Store<EntityStore> store) {
        Ref<PhysicsStore> oldBodyRef = attachment.getBodyRef();
        PhysicsBodySnapshot snapshot = null;
        if (oldBodyRef != null && oldBodyRef.isValid()) {
            snapshot = snapshotResource.getBody(oldBodyRef);
            if (snapshot != null && !snapshot.bodyUuid().equals(attachment.getBodyUuid())) {
                snapshot = null;
            }
        }
        if (snapshot == null) {
            snapshot = snapshotResource.getBody(attachment.getBodyUuid());
        }
        Ref<PhysicsStore> newBodyRef = snapshot != null ? snapshot.bodyRef() : null;
        if (!sameRef(oldBodyRef, newBodyRef)) {
            store.getResource(PhysicsProjectionIndexResource.getResourceType())
                .updateAttachmentBodyRef(attachment.getBodyUuid(),
                    oldBodyRef,
                    newBodyRef,
                    entityRef,
                    attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY);
            attachment.setBodyRef(newBodyRef);
        }
        return snapshot;
    }

    private static boolean sameRef(@Nullable Ref<PhysicsStore> first,
        @Nullable Ref<PhysicsStore> second) {
        return first == second
            || (first != null
                && second != null
                && first.getStore() == second.getStore()
                && first.getIndex() == second.getIndex());
    }

    @Nonnull
    private static SyncTickContext collectSyncTickContext(
        @Nonnull Store<EntityStore> store) {
        Store<PhysicsStore> physics = PhysicsThreading.store(store.getExternalData().getWorld());
        PhysicsThreading.requireWorldThread(physics,
            "read copied PhysicsStore sync snapshots");
        return new SyncTickContext(physics,
            physics.getResource(PhysicsSnapshotResource.getResourceType()),
            VisualInterestCollector.collectSyncInterests(store));
    }

    static boolean applyPhysicsStoreSnapshot(@Nonnull TransformComponent transform,
        @Nonnull BodyAttachmentComponent attachment,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Scratch scratch) {
        computeVisualPose(attachment, snapshot, scratch);
        return writeTransformIfChanged(transform, scratch);
    }

    @Nonnull
    static SyncResult applyPhysicsStoreSnapshot(@Nonnull TransformComponent transform,
        @Nonnull BodyAttachmentComponent attachment,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Scratch scratch,
        @Nonnull PhysicsBodyRuntimeState.BodySyncState syncState,
        @Nullable PhysicsVisualSyncSettings settings,
        @Nonnull PhysicsSyncPolicy.SyncRangeTier rangeTier,
        float dt) {
        computeVisualPose(attachment, snapshot, scratch);
        return applyComputedPhysicsStoreSnapshot(transform,
            snapshot,
            scratch,
            syncState,
            settings,
            rangeTier,
            false,
            dt);
    }

    @Nonnull
    private static SyncResult applyComputedPhysicsStoreSnapshot(
        @Nonnull TransformComponent transform,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Scratch scratch,
        @Nonnull PhysicsBodyRuntimeState.BodySyncState syncState,
        @Nullable PhysicsVisualSyncSettings settings,
        @Nonnull PhysicsSyncPolicy.SyncRangeTier rangeTier,
        boolean kinematic,
        float dt) {
        if (!syncState.isInitializedFor(snapshot.bodyUuid())) {
            syncState.clear();
        }
        PhysicsSyncPolicy.SyncDecision decision = PhysicsSyncPolicy.resolveSyncDecision(syncState,
            settings,
            scratch.visualPosition,
            scratch.visualRotation,
            snapshot.sleeping(),
            kinematic,
            rangeTier);
        if (!shouldWriteTransform(decision)) {
            syncState.recordSkip(dt);
            return new SyncResult(decision, false);
        }
        boolean changed = writeTransformIfChanged(transform, scratch);
        syncState.recordSync(snapshot.bodyUuid(),
            scratch.visualPosition,
            scratch.visualRotation,
            snapshot.sleeping());
        return new SyncResult(decision, changed);
    }

    private static void computeVisualPose(@Nonnull BodyAttachmentComponent attachment,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Scratch scratch) {
        scratch.position.set(snapshot.positionX(), snapshot.positionY(), snapshot.positionZ());
        setFiniteUnitQuaternionOrIdentity(scratch.rotation,
            snapshot.rotationX(),
            snapshot.rotationY(),
            snapshot.rotationZ(),
            snapshot.rotationW());
        PhysicsVisualPoseMath.visualPositionFromBodyPose(scratch.position,
            scratch.rotation,
            attachment.resolveVisualOriginOffsetY(snapshot.centerOfMassOffsetY()),
            attachment.getLocalPositionOffset(),
            scratch.visualPosition,
            scratch.worldOffset);
        scratch.visualRotation.set(scratch.rotation);
        setFiniteUnitQuaternionOrIdentity(scratch.localRotationOffset,
            attachment.getLocalRotationOffset());
        scratch.visualRotation.mul(scratch.localRotationOffset);
        setFiniteUnitQuaternionOrIdentity(scratch.visualRotation, scratch.visualRotation);
        scratch.visualRotation.getEulerAnglesYXZ(scratch.euler);
        if (!isFinite(scratch.euler)) {
            scratch.euler.zero();
        }
    }

    private static boolean writeTransformIfChanged(@Nonnull TransformComponent transform,
        @Nonnull Scratch scratch) {
        if (matchesTransform(transform, scratch.visualPosition, scratch.euler)) {
            return false;
        }
        transform.getPosition().set(scratch.visualPosition.x,
            scratch.visualPosition.y,
            scratch.visualPosition.z);
        transform.getRotation().set(scratch.euler.x, scratch.euler.y, scratch.euler.z);
        return true;
    }

    private static boolean shouldWriteTransform(
        @Nonnull PhysicsSyncPolicy.SyncDecision decision) {
        return decision == PhysicsSyncPolicy.SyncDecision.INITIAL
            || decision == PhysicsSyncPolicy.SyncDecision.THRESHOLD
            || decision == PhysicsSyncPolicy.SyncDecision.TRANSITION
            || decision == PhysicsSyncPolicy.SyncDecision.KEEPALIVE;
    }

    @Nonnull
    private static PhysicsSyncPolicy.SyncRangeTier resolveRangeTier(
        @Nullable PhysicsVisualSyncSettings settings,
        @Nonnull BodyAttachmentComponent attachment,
        @Nonnull List<PhysicsSyncPolicy.PlayerInterest> playerInterests,
        @Nonnull Vector3f visualPosition,
        boolean kinematic) {
        boolean generatedProxy = attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY;
        boolean rangeLimitedVisual = generatedProxy
            || settings != null && settings.isEntityVisualSyncCullingEnabled();
        return PhysicsSyncPolicy.resolveRangeTier(settings,
            null,
            rangeLimitedVisual,
            kinematic,
            playerInterests,
            visualPosition);
    }

    private static void recordSyncResult(
        @Nonnull PhysicsRuntimeProfilingResource.SyncCollector collector,
        @Nonnull SyncResult result) {
        if (result.transformChanged()) {
            collector.incrementBodiesSynced();
        }
        switch (result.decision()) {
            case TRANSITION -> collector.incrementTransitionSyncs();
            case KEEPALIVE -> collector.incrementKeepaliveSyncs();
            case SKIP_SLEEPING -> collector.incrementSkippedSleeping();
            case SKIP_THRESHOLD -> collector.incrementSkippedThreshold();
            case SKIP_VISUAL_DEADZONE -> collector.incrementSkippedVisualDeadzone();
            case SKIP_VISUAL_RANGE -> collector.incrementSkippedVisualRange();
            default -> {
            }
        }
    }

    private static boolean matchesTransform(@Nonnull TransformComponent transform,
        @Nonnull Vector3f position,
        @Nonnull Vector3f rotation) {
        return Math.abs(transform.getPosition().x - position.x) <= TRANSFORM_POSITION_EPSILON
            && Math.abs(transform.getPosition().y - position.y) <= TRANSFORM_POSITION_EPSILON
            && Math.abs(transform.getPosition().z - position.z) <= TRANSFORM_POSITION_EPSILON
            && Math.abs(transform.getRotation().x() - rotation.x) <= TRANSFORM_ROTATION_EPSILON
            && Math.abs(transform.getRotation().y() - rotation.y) <= TRANSFORM_ROTATION_EPSILON
            && Math.abs(transform.getRotation().z() - rotation.z) <= TRANSFORM_ROTATION_EPSILON;
    }

    private static void setFiniteUnitQuaternionOrIdentity(@Nonnull Quaternionf target,
        float x,
        float y,
        float z,
        float w) {
        target.set(x, y, z, w);
        setFiniteUnitQuaternionOrIdentity(target, target);
    }

    private static void setFiniteUnitQuaternionOrIdentity(@Nonnull Quaternionf target,
        @Nonnull Quaternionf source) {
        if (target != source) {
            target.set(source);
        }
        if (!isFiniteAndNonZero(target)) {
            target.identity();
            return;
        }
        target.normalize();
        if (!isFiniteAndNonZero(target)) {
            target.identity();
        }
    }

    private static boolean isFiniteAndNonZero(@Nonnull Quaternionf quaternion) {
        float lengthSquared = quaternion.x * quaternion.x
            + quaternion.y * quaternion.y
            + quaternion.z * quaternion.z
            + quaternion.w * quaternion.w;
        return Float.isFinite(lengthSquared) && lengthSquared > 0.0f;
    }

    private static boolean isFinite(@Nonnull Vector3f vector) {
        return Float.isFinite(vector.x)
            && Float.isFinite(vector.y)
            && Float.isFinite(vector.z);
    }

    static final class Scratch {

        private final Vector3f position = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Quaternionf localRotationOffset = new Quaternionf();
        private final Vector3f visualPosition = new Vector3f();
        private final Quaternionf visualRotation = new Quaternionf();
        private final Vector3f worldOffset = new Vector3f();
        private final Vector3f euler = new Vector3f();

        @Nullable
        private Store<EntityStore> cachedProfilingStore;
        @Nullable
        private PhysicsRuntimeProfilingResource cachedProfiling;

        @Nullable
        private PhysicsRuntimeProfilingResource.SyncCollector getSyncCollector(
            @Nonnull Store<EntityStore> store) {
            if (cachedProfilingStore != store || cachedProfiling == null) {
                cachedProfilingStore = store;
                cachedProfiling = store.getResource(PhysicsRuntimeProfilingResource.getResourceType());
            }
            return cachedProfiling.isEnabled() ? cachedProfiling.getActiveSyncCollector() : null;
        }
    }

    record SyncResult(@Nonnull PhysicsSyncPolicy.SyncDecision decision,
                      boolean transformChanged) {
    }

    private record SyncTickContext(@Nonnull Store<PhysicsStore> physicsStore,
                                   @Nonnull PhysicsSnapshotResource snapshotResource,
                                   @Nonnull List<PhysicsSyncPolicy.PlayerInterest> playerInterests,
                                   @Nonnull Map<UUID, PhysicsVisualSyncSettings> settingsBySpace) {

        private SyncTickContext(@Nonnull Store<PhysicsStore> physicsStore,
            @Nonnull PhysicsSnapshotResource snapshotResource,
            @Nonnull List<PhysicsSyncPolicy.PlayerInterest> playerInterests) {
            this(physicsStore, snapshotResource, playerInterests, new HashMap<>());
        }

        @Nullable
        private PhysicsVisualSyncSettings visualSyncSettings(@Nonnull UUID spaceUuid) {
            if (settingsBySpace.containsKey(spaceUuid)) {
                return settingsBySpace.get(spaceUuid);
            }
            Ref<PhysicsStore> spaceRef = physicsStore.getExternalData().getRefFromUUID(spaceUuid);
            PhysicsVisualSyncSettings settings = null;
            if (spaceRef != null && spaceRef.getStore() == physicsStore && spaceRef.isValid()) {
                VisualSyncSettingsComponent component = physicsStore.getComponent(spaceRef,
                    VisualSyncSettingsComponent.getComponentType());
                settings = new PhysicsVisualSyncSettings();
                if (component != null) {
                    component.copyTo(settings);
                }
            }
            settingsBySpace.put(spaceUuid, settings);
            return settings;
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

}
