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
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.systems.visual.GeneratedProxyLifecycle;
import dev.hytalemodding.impulse.core.internal.systems.visual.VisualInterestCollector;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Synchronizes physics bodies with Hytale transforms each tick.
 *
 * <p>Runs after the persistence restore group so that newly bootstrapped spaces,
 * hydrated bodies, and hydrated joints are all settled before this system reads
 * body transforms.</p>
 *
 * <p>Entities attach to body ids. Backend body destruction is explicit at the
 * world resource boundary; missing generated visual proxies are removed, while
 * gameplay entities merely lose the attachment.</p>
 */
public class PhysicsSyncSystem extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(ATTACHMENT_TYPE, TRANSFORM_TYPE);
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
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

    /**
     * Hytale may run entity ticks in parallel. Each worker needs independent temporary objects
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
            super.tick(dt, systemIndex, store);
        } finally {
            if (collector != null) {
                profiling.finishSyncSample(collector, System.nanoTime() - startNanos);
            }
            playerInterests.remove();
            syncNanos.remove();
        }
    }

    @Override
    public void tick(float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> entityRef = chunk.getReferenceTo(index);
        PhysicsBodyAttachmentComponent attachment = chunk.getComponent(index, ATTACHMENT_TYPE);
        TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
        if (attachment == null || transform == null) {
            return;
        }

        Scratch local = scratch.get();
        PhysicsWorldRuntimeResource resource = local.getResource(store);
        PhysicsRuntimeProfilingResource.SyncCollector collector = local.getSyncCollector(store);
        if (collector != null) {
            collector.incrementBodiesInspected();
        }
        PhysicsBodyRegistrationView registration =
            resource.getBodyRegistrationView(attachment.getBodyId());
        if (registration == null) {
            if (resource.isBodyCreationPending(attachment.getBodyId())) {
                return;
            }
            GeneratedProxyLifecycle.clearMissingAttachment(entityRef, attachment, resource, commandBuffer);
            return;
        }

        SpaceId spaceId = registration.spaceId();
        if (resource.getSpace(spaceId) == null) {
            if (collector != null) {
                collector.incrementSkippedMissingSpace();
            }
            GeneratedProxyLifecycle.clearMissingAttachment(entityRef, attachment, resource, commandBuffer);
            return;
        }

        PhysicsBodySnapshot snapshot = resource.getBodySnapshot(registration.id());
        if (snapshot.isStatic()) {
            if (collector != null) {
                collector.incrementSkippedStatic();
            }
            return;
        }

        PhysicsSpaceSettings settings = resolveSpaceSettings(resource, spaceId);
        local.position.set(snapshot.position());
        local.rotation.set(snapshot.rotation());
        applyVisualPose(snapshot, attachment, local);

        boolean sleeping = snapshot.sleeping();
        boolean controlled = resource.isBodyControlled(registration.id());
        boolean lowSpeed = false;
        if (!sleeping && snapshot.isDynamic() && !controlled) {
            local.linearVelocity.set(snapshot.linearVelocity());
            local.angularVelocity.set(snapshot.angularVelocity());
            lowSpeed = local.linearVelocity.lengthSquared() <= LOW_SPEED_LINEAR_THRESHOLD_SQUARED
                && local.angularVelocity.lengthSquared() <= LOW_SPEED_ANGULAR_THRESHOLD_SQUARED;
        }

        boolean rangeLimitedVisual = shouldCullVisualSync(settings, attachment, controlled);
        PhysicsSyncPolicy.SyncRangeTier rangeTier = PhysicsSyncPolicy.resolveRangeTier(
            settings,
            resource.getBodyVisualInterestState(registration.id()),
            rangeLimitedVisual,
            controlled,
            playerInterests.get(),
            local.visualPosition);
        if (rangeTier == PhysicsSyncPolicy.SyncRangeTier.NEAR) {
            local.position.set(snapshot.position());
            local.rotation.set(snapshot.rotation());
            applySnapshotPrediction(snapshot,
                PhysicsSyncPolicy.visualPredictionSeconds(settings,
                    syncNanos.get(),
                    resource.getLatestSnapshotAppliedNanos()),
                local);
            applyVisualPose(snapshot, attachment, local);
        }

        PhysicsBodyRuntimeState.BodySyncState syncState = resource.getOrCreateBodySyncState(entityRef);
        PhysicsSyncPolicy.SyncDecision decision = PhysicsSyncPolicy.resolveSyncDecision(syncState,
            settings,
            local.visualPosition,
            local.visualRotation,
            sleeping,
            lowSpeed,
            controlled,
            rangeTier);
        if (decision == PhysicsSyncPolicy.SyncDecision.SKIP_SLEEPING
            || decision == PhysicsSyncPolicy.SyncDecision.SKIP_THRESHOLD
            || decision == PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_DEADZONE
            || decision == PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_RANGE) {
            syncState.recordSkip(dt);
            if (collector != null) {
                if (decision == PhysicsSyncPolicy.SyncDecision.SKIP_SLEEPING) {
                    collector.incrementSkippedSleeping();
                } else if (decision == PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_DEADZONE) {
                    collector.incrementSkippedVisualDeadzone();
                } else if (decision == PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_RANGE) {
                    collector.incrementSkippedVisualRange();
                } else {
                    collector.incrementSkippedThreshold();
                }
            }
            return;
        }

        if (shouldSmoothVisual(settings, snapshot, controlled, rangeTier, syncState, decision)) {
            applyVisualSmoothing(settings, dt, syncState, local);
        }

        transform.getPosition().set(local.visualPosition.x,
            local.visualPosition.y,
            local.visualPosition.z);
        local.visualRotation.getEulerAnglesYXZ(local.euler);
        transform.getRotation().set(local.euler.x, local.euler.y, local.euler.z);
        syncState.recordSync(local.visualPosition, local.visualRotation, sleeping);
        if (collector != null) {
            collector.incrementBodiesSynced();
            if (decision == PhysicsSyncPolicy.SyncDecision.TRANSITION) {
                collector.incrementTransitionSyncs();
            } else if (decision == PhysicsSyncPolicy.SyncDecision.KEEPALIVE) {
                collector.incrementKeepaliveSyncs();
            }
        }
    }

    private void applyVisualPose(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsBodyAttachmentComponent attachment,
        @Nonnull Scratch scratch) {
        float offsetY = snapshot.centerOfMassOffsetY();
        scratch.visualPosition.set(scratch.position.x,
            scratch.position.y - offsetY,
            scratch.position.z);
        scratch.visualRotation.set(scratch.rotation);

        scratch.rotation.transform(attachment.getLocalPositionOffset(), scratch.worldOffset);
        scratch.visualPosition.add(scratch.worldOffset);
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
        return Math.min(1.0f,
            Math.max(MIN_SMOOTHING_ALPHA, dt * settings.getVisualSyncSettings().getVisualSnapshotSmoothingRate()));
    }

    private static void applySnapshotPrediction(@Nonnull PhysicsBodySnapshot snapshot,
        float predictionSeconds,
        @Nonnull Scratch scratch) {
        if (predictionSeconds <= 0.0f || !snapshot.isDynamic() || snapshot.sleeping()) {
            return;
        }

        scratch.linearVelocity.set(snapshot.linearVelocity());
        if (isFinite(scratch.linearVelocity)) {
            scratch.position.fma(predictionSeconds, scratch.linearVelocity);
        }

        scratch.angularVelocity.set(snapshot.angularVelocity());
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
            return resource.getSpaceSettings(spaceId);
        }
        return null;
    }

    private static boolean shouldCullVisualSync(@Nullable PhysicsSpaceSettings settings,
        @Nonnull PhysicsBodyAttachmentComponent attachment,
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
