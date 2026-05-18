package dev.hytalemodding.impulse.core.systems;

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
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyVisualComponent;
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
 * <p>The authoritative body relationship still lives on {@link PhysicsBodyComponent}.
 * {@link PhysicsBodyVisualComponent} adds follower entities that can share the same
 * body and apply player-neighborhood visual sync policies.</p>
 */
public class PhysicsSyncSystem extends EntityTickingSystem<EntityStore> {

    private static final float LOW_SPEED_LINEAR_THRESHOLD_SQUARED = 0.2f * 0.2f;
    private static final float LOW_SPEED_ANGULAR_THRESHOLD_SQUARED = 0.5f * 0.5f;

    private static final ComponentType<EntityStore, PhysicsBodyComponent> PHYSICS_BODY_TYPE =
        PhysicsBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyVisualComponent>
        PHYSICS_BODY_VISUAL_TYPE = PhysicsBodyVisualComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, HeadRotation> HEAD_ROTATION_TYPE =
        HeadRotation.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(
        Query.or(PHYSICS_BODY_TYPE, PHYSICS_BODY_VISUAL_TYPE),
        TRANSFORM_TYPE);
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.BEFORE, UpdateLocationSystems.TickingSystem.class)
    );

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

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        // Backend bodies and per-body sync state are owned by the world tick thread.
        // Pose snapshots can be introduced later to parallelize the ECS write side.
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
        PhysicsBodyComponent physicsBody = chunk.getComponent(index, PHYSICS_BODY_TYPE);
        PhysicsBodyVisualComponent visual = chunk.getComponent(index, PHYSICS_BODY_VISUAL_TYPE);
        TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
        if ((physicsBody == null && visual == null) || transform == null) {
            return;
        }
        if (visual == null
            && physicsBody != null
            && physicsBody.getOwnerVisualMode() == PhysicsBodyComponent.OwnerVisualMode.NONE) {
            return;
        }

        PhysicsBody body = visual != null ? visual.getBody() : physicsBody.getBody();
        SpaceId spaceId = visual != null ? visual.getSpaceId() : physicsBody.getSpaceId();

        Scratch local = scratch.get();
        PhysicsWorldResource resource = local.getResource(store);
        PhysicsRuntimeProfilingResource.SyncCollector collector = local.getSyncCollector(store);
        if (collector != null) {
            collector.incrementBodiesInspected();
        }
        if (spaceId != null && resource.getSpace(spaceId) == null) {
            if (collector != null) {
                collector.incrementSkippedMissingSpace();
            }
            return;
        }

        if (body.isStatic()) {
            if (collector != null) {
                collector.incrementSkippedStatic();
            }
            return;
        }

        body.getPosition(local.position);
        body.getRotation(local.rotation);
        applyVisualPose(body, visual, local);

        boolean sleeping = body.isSleeping();
        boolean controlled = resource.isBodyControlled(body);
        boolean lowSpeed = false;
        if (!sleeping && body.isDynamic() && !controlled) {
            body.getLinearVelocity(local.linearVelocity);
            body.getAngularVelocity(local.angularVelocity);
            lowSpeed = local.linearVelocity.lengthSquared() <= LOW_SPEED_LINEAR_THRESHOLD_SQUARED
                && local.angularVelocity.lengthSquared() <= LOW_SPEED_ANGULAR_THRESHOLD_SQUARED;
        }

        PhysicsSpaceSettings settings = resolveSpaceSettings(resource, spaceId);
        boolean rangeLimitedVisual = shouldCullVisualSync(settings, visual, physicsBody, controlled);
        PhysicsSyncPolicy.SyncRangeTier rangeTier = PhysicsSyncPolicy.resolveRangeTier(
            settings,
            resource.getBodyVisualInterestState(body),
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

    private void applyVisualPose(@Nonnull PhysicsBody body,
        @Nullable PhysicsBodyVisualComponent visual,
        @Nonnull Scratch scratch) {
        float offsetY = body.getCenterOfMassOffsetY();
        scratch.visualPosition.set(scratch.position.x,
            scratch.position.y - offsetY,
            scratch.position.z);
        scratch.visualRotation.set(scratch.rotation);

        if (visual == null) {
            return;
        }

        scratch.rotation.transform(visual.getLocalPositionOffset(), scratch.worldOffset);
        scratch.visualPosition.add(scratch.worldOffset);
        scratch.visualRotation.mul(visual.getLocalRotationOffset());
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
        @Nullable PhysicsBodyVisualComponent visual,
        @Nullable PhysicsBodyComponent physicsBody,
        boolean controlled) {
        if (controlled) {
            return false;
        }
        if (visual != null && physicsBody == null) {
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

}
