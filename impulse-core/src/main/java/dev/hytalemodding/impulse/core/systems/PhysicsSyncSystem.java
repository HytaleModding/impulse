package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
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
 * <p>Entities attach to body ids. Backend body destruction is explicit at the
 * world resource boundary; missing generated visual proxies are removed, while
 * gameplay entities merely lose the attachment.</p>
 */
public class PhysicsSyncSystem extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, HeadRotation> HEAD_ROTATION_TYPE =
        HeadRotation.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(ATTACHMENT_TYPE, TRANSFORM_TYPE);
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.BEFORE, UpdateLocationSystems.TickingSystem.class)
    );

    private static final float LOW_SPEED_LINEAR_THRESHOLD_SQUARED = 0.2f * 0.2f;
    private static final float LOW_SPEED_ANGULAR_THRESHOLD_SQUARED = 0.5f * 0.5f;

    /**
     * Snapshot of player interest positions/directions for the current world tick. The list is built on the
     * world thread before any parallel entity iteration and then treated as immutable.
     */
    @Nonnull
    private List<PhysicsSyncPolicy.PlayerInterest> playerInterests = List.of();

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
        playerInterests = collectPlayerInterests(store);
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
            playerInterests = List.of();
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
        PhysicsWorldResource resource = local.getResource(store);
        PhysicsRuntimeProfilingResource.SyncCollector collector = local.getSyncCollector(store);
        if (collector != null) {
            collector.incrementBodiesInspected();
        }
        PhysicsWorldResource.BodyRegistration registration =
            resource.getRegistration(attachment.getBodyId());
        if (registration == null) {
            clearMissingAttachment(entityRef, attachment, resource, commandBuffer);
            return;
        }

        SpaceId spaceId = registration.spaceId();
        if (spaceId != null && resource.getSpace(spaceId) == null) {
            if (collector != null) {
                collector.incrementSkippedMissingSpace();
            }
            clearMissingAttachment(entityRef, attachment, resource, commandBuffer);
            return;
        }

        PhysicsBodySnapshot snapshot = resource.getBodySnapshot(registration.id());
        if (snapshot.isStatic()) {
            if (collector != null) {
                collector.incrementSkippedStatic();
            }
            return;
        }

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

        PhysicsSpaceSettings settings = resolveSpaceSettings(resource, spaceId);
        boolean rangeLimitedVisual = shouldCullVisualSync(settings, attachment, controlled);
        PhysicsSyncPolicy.SyncRangeTier rangeTier = PhysicsSyncPolicy.resolveRangeTier(
            settings,
            resource.getBodyVisualInterestState(registration.id()),
            rangeLimitedVisual,
            controlled,
            playerInterests,
            local.visualPosition);

        PhysicsWorldResource.BodySyncState syncState = resource.getOrCreateBodySyncState(entityRef);
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

    private static void clearMissingAttachment(@Nonnull Ref<EntityStore> entityRef,
        @Nonnull PhysicsBodyAttachmentComponent attachment,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        resource.unregisterBodyAttachment(attachment.getBodyId(), entityRef);
        resource.clearBodySyncState(entityRef);
        if (attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY) {
            resource.clearGeneratedVisualProxy(attachment.getBodyId());
            commandBuffer.removeEntity(entityRef, RemoveReason.REMOVE);
        } else {
            commandBuffer.removeComponent(entityRef, ATTACHMENT_TYPE);
        }
    }

    @Nonnull
    private List<PhysicsSyncPolicy.PlayerInterest> collectPlayerInterests(@Nonnull Store<EntityStore> store) {
        List<PhysicsSyncPolicy.PlayerInterest> interests = new ArrayList<>();
        for (PlayerRef playerRef : store.getExternalData().getWorld().getPlayerRefs()) {
            Ref<EntityStore> playerEntity = playerRef.getReference();
            if (playerEntity == null || !playerEntity.isValid()) {
                continue;
            }

            TransformComponent transform = store.getComponent(playerEntity, TRANSFORM_TYPE);
            if (transform == null) {
                continue;
            }

            Vector3d position = transform.getPosition();
            interests.add(new PhysicsSyncPolicy.PlayerInterest(
                new Vector3f((float) position.x, (float) position.y, (float) position.z),
                playerLookDirection(store, playerEntity, transform)));
        }
        return interests.isEmpty() ? List.of() : interests;
    }

    @Nonnull
    private Vector3f playerLookDirection(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = store.getComponent(playerEntity, HEAD_ROTATION_TYPE);
        Rotation3f rotation = headRotation != null ? headRotation.getRotation() : transform.getRotation();
        Vector3d direction = new Vector3d(Vector3dUtil.FORWARD);
        rotation.getQuaternion(new Quaterniond()).transform(direction);
        if (direction.lengthSquared() == 0.0) {
            direction.set(Vector3dUtil.FORWARD);
        } else {
            direction.normalize();
        }
        return new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
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

    @Nullable
    private static PhysicsSpaceSettings resolveSpaceSettings(@Nonnull PhysicsWorldResource resource,
        @Nullable SpaceId spaceId) {
        if (spaceId != null) {
            return resource.getSpaceSettings(spaceId);
        }
        SpaceId defaultSpaceId = resource.getDefaultSpaceId();
        return defaultSpaceId != null ? resource.getSpaceSettings(defaultSpaceId) : null;
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
        return settings != null && settings.isEntityVisualSyncCullingEnabled();
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

        @Nullable
        private Store<EntityStore> cachedStore;
        @Nullable
        private PhysicsWorldResource cachedResource;
        @Nullable
        private PhysicsRuntimeProfilingResource cachedProfiling;

        @Nonnull
        private PhysicsWorldResource getResource(@Nonnull Store<EntityStore> store) {
            if (cachedStore != store || cachedResource == null) {
                cachedStore = store;
                cachedResource = store.getResource(PhysicsWorldResource.getResourceType());
            }
            return cachedResource;
        }

        @Nullable
        private PhysicsRuntimeProfilingResource.SyncCollector getSyncCollector(
            @Nonnull Store<EntityStore> store) {
            if (cachedStore != store || cachedProfiling == null) {
                cachedStore = store;
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
