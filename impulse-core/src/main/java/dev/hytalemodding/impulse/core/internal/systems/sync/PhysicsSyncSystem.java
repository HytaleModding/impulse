package dev.hytalemodding.impulse.core.internal.systems.sync;

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
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.math.PhysicsVisualPoseMath;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.systems.visual.GeneratedProxyLifecycle;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsDetachedVisualMaterializationSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.VisualInterestCollector;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAccess;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Synchronizes physics bodies with Hytale transforms each tick.
 *
 * <p>Runs after the persistence restore group so that newly bootstrapped spaces,
 * hydrated bodies, and hydrated joints are all settled before this system reads
 * body transforms.</p>
 *
 * <p>Entities attach to authoritative PhysicsStore body UUIDs. Backend body destruction is explicit
 * through PhysicsStore requests; removing an EntityStore attachment only removes the projection.</p>
 */
public class PhysicsSyncSystem extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, BodyAttachmentComponent> ATTACHMENT_TYPE =
        BodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(ATTACHMENT_TYPE, TRANSFORM_TYPE);
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.AFTER, PhysicsDetachedVisualMaterializationSystem.class),
        new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class),
        new SystemDependency<>(Order.BEFORE, UpdateLocationSystems.TickingSystem.class)
    );

    /*
     * Low-speed uncontrolled dynamic bodies get a wider visual deadzone and a
     * slower keepalive. Controlled bodies bypass this classification so player
     * input stays responsive.
     */
    private static final float LOW_SPEED_LINEAR_THRESHOLD_SQUARED = 0.2f * 0.2f;
    private static final float LOW_SPEED_ANGULAR_THRESHOLD_SQUARED = 0.5f * 0.5f;
    private static final float MIN_PREDICTED_ANGULAR_SPEED = 1.0e-4f;
    private static final float MIN_SMOOTHING_ALPHA = 0.05f;
    private static final float MAX_SMOOTHING_TELEPORT_DISTANCE_SQUARED = 4.0f * 4.0f;

    @Nonnull
    private final ThreadLocal<List<PhysicsSyncPolicy.PlayerInterest>> playerInterests =
        ThreadLocal.withInitial(List::of);
    @Nonnull
    private final ThreadLocal<Long> syncNanos = ThreadLocal.withInitial(() -> 0L);
    @Nonnull
    private final ThreadLocal<PhysicsSnapshotResource> physicsStoreSnapshots = new ThreadLocal<>();

    /**
     * Hytale may run entity ticks in parallel. Each tick task needs independent temporary objects
     * because the backend out-parameter getters write into caller-owned vectors.
     */
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        // Backend bodies and per-body sync state are owned by the world tick thread.
        // Body poses are read from the world-level snapshot cache before parallel ECS writes.
        return false;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        playerInterests.set(VisualInterestCollector.collectSyncInterests(store));
        syncNanos.set(System.nanoTime());
        PhysicsRuntimeProfilingResource profiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        PhysicsRuntimeProfilingResource.SyncCollector collector = profiling.isEnabled()
            ? profiling.beginSyncSample() : null;
        long startNanos = collector != null ? System.nanoTime() : 0L;
        try {
            physicsStoreSnapshots.set(collectPhysicsStoreSnapshotResource(store));
            super.tick(dt, systemIndex, store);
        } finally {
            if (collector != null) {
                profiling.finishSyncSample(collector, System.nanoTime() - startNanos);
            }
            playerInterests.remove();
            syncNanos.remove();
            physicsStoreSnapshots.remove();
        }
    }

    @Override
    public void tick(float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        BodyAttachmentComponent attachment = chunk.getComponent(index, ATTACHMENT_TYPE);
        TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
        if (attachment == null || transform == null) {
            return;
        }

        Scratch local = scratch.get();
        PhysicsRuntimeProfilingResource.SyncCollector collector = local.getSyncCollector(store);
        if (collector != null) {
            collector.incrementBodiesInspected();
        }
        UUID bodyUuid = attachment.getBodyUuid();
        PhysicsSnapshotResource snapshotResource = physicsStoreSnapshots.get();
        PhysicsStoreBodySnapshot physicsStoreSnapshot = snapshotResource.getBody(bodyUuid);
        if (physicsStoreSnapshot != null) {
            if (!PhysicsTransformAuthority.shouldApplyBodyTransform(attachment)) {
                return;
            }
            applyPhysicsStoreSnapshot(transform, attachment, physicsStoreSnapshot, local);
            if (collector != null) {
                collector.incrementBodiesSynced();
            }
            return;
        }
        clearMissingPhysicsStoreAttachment(entityRef, attachment, commandBuffer);
    }

    @Nonnull
    private static PhysicsSnapshotResource collectPhysicsStoreSnapshotResource(
        @Nonnull Store<EntityStore> store) {
        PhysicsStore physicsStore = PhysicsStoreAccess.require(store.getExternalData().getWorld());
        return physicsStore.getStore().getResource(
            PhysicsSnapshotResource.getResourceType());
    }

    private static void applyPhysicsStoreSnapshot(@Nonnull TransformComponent transform,
        @Nonnull BodyAttachmentComponent attachment,
        @Nonnull PhysicsStoreBodySnapshot snapshot,
        @Nonnull Scratch scratch) {
        scratch.position.set(snapshot.position());
        scratch.rotation.set(snapshot.rotation());
        PhysicsVisualPoseMath.visualPositionFromBodyPose(scratch.position,
            scratch.rotation,
            attachment.resolveVisualOriginOffsetY(snapshot.centerOfMassOffsetY()),
            attachment.getLocalPositionOffset(),
            scratch.visualPosition,
            scratch.worldOffset);
        scratch.visualRotation.set(scratch.rotation);
        scratch.visualRotation.mul(attachment.getLocalRotationOffset());
        transform.getPosition().set(scratch.visualPosition.x,
            scratch.visualPosition.y,
            scratch.visualPosition.z);
        scratch.visualRotation.getEulerAnglesYXZ(scratch.euler);
        transform.getRotation().set(scratch.euler.x, scratch.euler.y, scratch.euler.z);
    }

    private static void clearMissingPhysicsStoreAttachment(@Nonnull Ref<EntityStore> entityRef,
        @Nonnull BodyAttachmentComponent attachment,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // PhysicsStore snapshot publication is intentionally one completed frame behind request
        // ingestion. Absence from the latest frame is not enough evidence that the body row is gone.
    }

    private static float distance(@Nonnull Vector3d from, @Nonnull Vector3f to) {
        double dx = from.x - to.x;
        double dy = from.y - to.y;
        double dz = from.z - to.z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(distanceSquared)) {
            return Float.NaN;
        }
        return (float) Math.sqrt(distanceSquared);
    }

    private void applyVisualPose(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull BodyAttachmentComponent attachment,
        @Nonnull Scratch scratch) {
        PhysicsVisualPoseMath.visualPositionFromBodyPose(scratch.position,
            scratch.rotation,
            attachment.resolveVisualOriginOffsetY(snapshot.centerOfMassOffsetY()),
            attachment.getLocalPositionOffset(),
            scratch.visualPosition,
            scratch.worldOffset);
        scratch.visualRotation.set(scratch.rotation);

        scratch.visualRotation.mul(attachment.getLocalRotationOffset());
    }

    private static boolean shouldSmoothVisual(@Nullable PhysicsSpaceSettings settings,
        @Nonnull PhysicsBodySnapshot snapshot,
        boolean controlled,
        @Nonnull PhysicsSyncPolicy.SyncRangeTier rangeTier,
        @Nonnull PhysicsBodyRuntimeState.BodySyncState syncState,
        @Nonnull PhysicsSyncPolicy.SyncDecision decision) {
        return settings != null
            && settings.getVisualSyncSettings().isVisualSnapshotSmoothingEnabled()
            && !controlled
            && rangeTier == PhysicsSyncPolicy.SyncRangeTier.NEAR
            && snapshot.isDynamic()
            && !snapshot.sleeping()
            && syncState.isInitialized()
            && decision != PhysicsSyncPolicy.SyncDecision.INITIAL
            && decision != PhysicsSyncPolicy.SyncDecision.TRANSITION;
    }

    private static void applyVisualSmoothing(@Nonnull PhysicsSpaceSettings settings,
        float dt,
        @Nonnull PhysicsBodyRuntimeState.BodySyncState syncState,
        @Nonnull Scratch scratch) {
        if (scratch.visualPosition.distanceSquared(syncState.getLastSyncedPosition())
            > MAX_SMOOTHING_TELEPORT_DISTANCE_SQUARED) {
            return;
        }
        float alpha = smoothingAlpha(settings, dt);
        scratch.smoothingTargetPosition.set(scratch.visualPosition);
        scratch.visualPosition.set(syncState.getLastSyncedPosition())
            .lerp(scratch.smoothingTargetPosition, alpha);

        scratch.smoothingTargetRotation.set(scratch.visualRotation);
        scratch.visualRotation.set(syncState.getLastSyncedRotation())
            .slerp(scratch.smoothingTargetRotation, alpha)
            .normalize();
    }

    static float smoothingAlpha(@Nonnull PhysicsSpaceSettings settings, float dt) {
        if (!Float.isFinite(dt) || dt <= 0.0f) {
            return 1.0f;
        }
        return Math.clamp(dt * settings.getVisualSyncSettings().getVisualSnapshotSmoothingRate(),
            MIN_SMOOTHING_ALPHA, 1.0f);
    }

    private static void applySnapshotPrediction(@Nonnull PhysicsBodySnapshot snapshot,
        float predictionSeconds,
        @Nonnull Scratch scratch) {
        if (predictionSeconds <= 0.0f || !snapshot.isDynamic() || snapshot.sleeping()) {
            return;
        }

        snapshot.copyLinearVelocityTo(scratch.linearVelocity);
        if (isFinite(scratch.linearVelocity)) {
            scratch.position.fma(predictionSeconds, scratch.linearVelocity);
        }

        snapshot.copyAngularVelocityTo(scratch.angularVelocity);
        if (!isFinite(scratch.angularVelocity)) {
            return;
        }
        float angularSpeed = scratch.angularVelocity.length();
        if (angularSpeed <= MIN_PREDICTED_ANGULAR_SPEED) {
            return;
        }
        float inverseAngularSpeed = 1.0f / angularSpeed;
        scratch.predictedRotation.rotationAxis(angularSpeed * predictionSeconds,
            scratch.angularVelocity.x * inverseAngularSpeed,
            scratch.angularVelocity.y * inverseAngularSpeed,
            scratch.angularVelocity.z * inverseAngularSpeed);
        scratch.rotation.mul(scratch.predictedRotation).normalize();
    }

    private static boolean isFinite(@Nonnull Vector3f vector) {
        return Float.isFinite(vector.x)
            && Float.isFinite(vector.y)
            && Float.isFinite(vector.z);
    }

    @Nullable
    private static PhysicsSpaceSettings resolveSpaceSettings(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nullable SpaceId spaceId) {
        if (spaceId != null) {
            return resource.getLiveSpaceSettings(spaceId);
        }
        return null;
    }

    private static boolean shouldCullVisualSync(@Nullable PhysicsSpaceSettings settings,
        @Nonnull BodyAttachmentComponent attachment,
        boolean controlled) {
        if (controlled) {
            return false;
        }
        if (attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY) {
            return true;
        }
        return settings != null && settings.getVisualSyncSettings().isEntityVisualSyncCullingEnabled();
    }

    private static final class Scratch {

        private final Vector3f position = new Vector3f();
        private final Quaternionf rotation = new Quaternionf();
        private final Vector3f visualPosition = new Vector3f();
        private final Quaternionf visualRotation = new Quaternionf();
        private final Vector3f worldOffset = new Vector3f();
        private final Vector3f euler = new Vector3f();
        private final Vector3f linearVelocity = new Vector3f();
        private final Vector3f angularVelocity = new Vector3f();
        private final Quaternionf predictedRotation = new Quaternionf();
        private final Vector3f smoothingTargetPosition = new Vector3f();
        private final Quaternionf smoothingTargetRotation = new Quaternionf();

        @Nullable
        private Store<EntityStore> cachedResourceStore;
        @Nullable
        private Store<EntityStore> cachedProfilingStore;
        @Nullable
        private PhysicsWorldRuntimeResource cachedResource;
        @Nullable
        private PhysicsRuntimeProfilingResource cachedProfiling;

        @Nonnull
        private PhysicsWorldRuntimeResource getResource(@Nonnull Store<EntityStore> store) {
            if (cachedResourceStore != store || cachedResource == null) {
                cachedResourceStore = store;
                cachedResource = PhysicsWorldRuntimeResource.require(store);
            }
            return cachedResource;
        }

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

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

}
