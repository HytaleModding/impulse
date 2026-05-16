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

    private static final float POSITION_SYNC_THRESHOLD = 1.0f / 32.0f;
    private static final float POSITION_SYNC_THRESHOLD_SQUARED =
        POSITION_SYNC_THRESHOLD * POSITION_SYNC_THRESHOLD;
    private static final float LOW_SPEED_POSITION_SYNC_THRESHOLD = 1.0f / 8.0f;
    private static final float LOW_SPEED_POSITION_SYNC_THRESHOLD_SQUARED =
        LOW_SPEED_POSITION_SYNC_THRESHOLD * LOW_SPEED_POSITION_SYNC_THRESHOLD;
    private static final float MID_RANGE_POSITION_SYNC_THRESHOLD = 0.5f;
    private static final float MID_RANGE_POSITION_SYNC_THRESHOLD_SQUARED =
        MID_RANGE_POSITION_SYNC_THRESHOLD * MID_RANGE_POSITION_SYNC_THRESHOLD;
    private static final float ROTATION_SYNC_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(1.0));
    private static final float LOW_SPEED_ROTATION_SYNC_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(3.0));
    private static final float MID_RANGE_ROTATION_SYNC_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(8.0));
    private static final float ACTIVE_KEEPALIVE_SECONDS = 0.25f;
    private static final float LOW_SPEED_KEEPALIVE_SECONDS = 1.25f;
    private static final float MID_RANGE_KEEPALIVE_SECONDS = 2.5f;
    private static final float LOW_SPEED_LINEAR_THRESHOLD_SQUARED = 0.2f * 0.2f;
    private static final float LOW_SPEED_ANGULAR_THRESHOLD_SQUARED = 0.5f * 0.5f;

    private static final ComponentType<EntityStore, PhysicsBodyComponent> PHYSICS_BODY_TYPE =
        PhysicsBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyVisualComponent>
        PHYSICS_BODY_VISUAL_TYPE = PhysicsBodyVisualComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(
        Query.or(PHYSICS_BODY_TYPE, PHYSICS_BODY_VISUAL_TYPE),
        TRANSFORM_TYPE);
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.BEFORE, UpdateLocationSystems.TickingSystem.class)
    );

    /**
     * Snapshot of player interest positions for the current world tick. The list is built on the
     * world thread before any parallel entity iteration and then treated as immutable.
     */
    @Nonnull
    private List<Vector3f> playerInterestPositions = List.of();

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
        return useParallel(archetypeChunkSize, taskCount);
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        playerInterestPositions = collectPlayerInterestPositions(store);
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
            playerInterestPositions = List.of();
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

        boolean rangeLimitedVisual = visual != null && physicsBody == null;
        SyncRangeTier rangeTier = resolveSyncRangeTier(resource,
            spaceId,
            rangeLimitedVisual,
            controlled,
            local.visualPosition);

        PhysicsWorldResource.BodySyncState syncState = resource.getOrCreateBodySyncState(entityRef);
        SyncDecision decision = resolveSyncDecision(syncState,
            local.visualPosition,
            local.visualRotation,
            sleeping,
            lowSpeed,
            controlled,
            rangeTier);
        if (decision == SyncDecision.SKIP_SLEEPING
            || decision == SyncDecision.SKIP_THRESHOLD
            || decision == SyncDecision.SKIP_VISUAL_DEADZONE
            || decision == SyncDecision.SKIP_VISUAL_RANGE) {
            syncState.recordSkip(dt);
            if (collector != null) {
                if (decision == SyncDecision.SKIP_SLEEPING) {
                    collector.incrementSkippedSleeping();
                } else if (decision == SyncDecision.SKIP_VISUAL_DEADZONE) {
                    collector.incrementSkippedVisualDeadzone();
                } else if (decision == SyncDecision.SKIP_VISUAL_RANGE) {
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
            if (decision == SyncDecision.TRANSITION) {
                collector.incrementTransitionSyncs();
            } else if (decision == SyncDecision.KEEPALIVE) {
                collector.incrementKeepaliveSyncs();
            }
        }
    }

    @Nonnull
    private List<Vector3f> collectPlayerInterestPositions(@Nonnull Store<EntityStore> store) {
        List<Vector3f> positions = new ArrayList<>();
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
            positions.add(new Vector3f((float) position.x, (float) position.y, (float) position.z));
        }
        return positions.isEmpty() ? List.of() : positions;
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

    @Nonnull
    private SyncRangeTier resolveSyncRangeTier(@Nonnull PhysicsWorldResource resource,
        @Nullable SpaceId spaceId,
        boolean rangeLimitedVisual,
        boolean controlled,
        @Nonnull Vector3f visualPosition) {
        if (!rangeLimitedVisual || controlled) {
            return SyncRangeTier.NEAR;
        }
        if (playerInterestPositions.isEmpty()) {
            return SyncRangeTier.FAR;
        }

        PhysicsSpaceSettings settings = resolveSpaceSettings(resource, spaceId);
        if (settings == null) {
            return SyncRangeTier.NEAR;
        }

        float fullRadiusSquared = square(settings.getVisualFullSyncRadius());
        float maxRadiusSquared = square(settings.getVisualMaxSyncRadius());
        float nearestDistanceSquared = Float.MAX_VALUE;
        for (Vector3f playerPosition : playerInterestPositions) {
            float distanceSquared = playerPosition.distanceSquared(visualPosition);
            if (distanceSquared <= fullRadiusSquared) {
                return SyncRangeTier.NEAR;
            }
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearestDistanceSquared <= maxRadiusSquared ? SyncRangeTier.MID : SyncRangeTier.FAR;
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

    @Nonnull
    private static SyncDecision resolveSyncDecision(@Nonnull PhysicsWorldResource.BodySyncState syncState,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        boolean sleeping,
        boolean lowSpeed,
        boolean controlled,
        @Nonnull SyncRangeTier rangeTier) {
        if (!syncState.isInitialized()) {
            return SyncDecision.INITIAL;
        }
        if (sleeping != syncState.isSleeping()) {
            return SyncDecision.TRANSITION;
        }
        if (rangeTier == SyncRangeTier.FAR) {
            return SyncDecision.SKIP_VISUAL_RANGE;
        }

        float positionThresholdSquared;
        float rotationDotThreshold;
        float keepaliveSeconds;
        if (rangeTier == SyncRangeTier.MID && !controlled) {
            positionThresholdSquared = MID_RANGE_POSITION_SYNC_THRESHOLD_SQUARED;
            rotationDotThreshold = MID_RANGE_ROTATION_SYNC_DOT_THRESHOLD;
            keepaliveSeconds = MID_RANGE_KEEPALIVE_SECONDS;
        } else {
            positionThresholdSquared = lowSpeed && !controlled
                ? LOW_SPEED_POSITION_SYNC_THRESHOLD_SQUARED : POSITION_SYNC_THRESHOLD_SQUARED;
            rotationDotThreshold = lowSpeed && !controlled
                ? LOW_SPEED_ROTATION_SYNC_DOT_THRESHOLD : ROTATION_SYNC_DOT_THRESHOLD;
            keepaliveSeconds = lowSpeed && !controlled
                ? LOW_SPEED_KEEPALIVE_SECONDS : ACTIVE_KEEPALIVE_SECONDS;
        }

        if (position.distanceSquared(syncState.getLastSyncedPosition()) >= positionThresholdSquared
            || rotationChangedEnough(rotation, syncState.getLastSyncedRotation(), rotationDotThreshold)) {
            return SyncDecision.THRESHOLD;
        }
        if (!sleeping && syncState.getSecondsSinceSync() >= keepaliveSeconds) {
            return SyncDecision.KEEPALIVE;
        }
        if (sleeping) {
            return SyncDecision.SKIP_SLEEPING;
        }
        if (rangeTier == SyncRangeTier.MID) {
            return SyncDecision.SKIP_VISUAL_RANGE;
        }
        return lowSpeed && !controlled ? SyncDecision.SKIP_VISUAL_DEADZONE
            : SyncDecision.SKIP_THRESHOLD;
    }

    private static boolean rotationChangedEnough(@Nonnull Quaternionf current,
        @Nonnull Quaternionf lastSynced,
        float dotThreshold) {
        float dot = Math.abs(current.x * lastSynced.x
            + current.y * lastSynced.y
            + current.z * lastSynced.z
            + current.w * lastSynced.w);
        return Math.min(dot, 1.0f) < dotThreshold;
    }

    private static float square(float value) {
        return value * value;
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

    private enum SyncDecision {
        INITIAL,
        THRESHOLD,
        TRANSITION,
        KEEPALIVE,
        SKIP_SLEEPING,
        SKIP_THRESHOLD,
        SKIP_VISUAL_DEADZONE,
        SKIP_VISUAL_RANGE
    }

    private enum SyncRangeTier {
        NEAR,
        MID,
        FAR
    }
}
