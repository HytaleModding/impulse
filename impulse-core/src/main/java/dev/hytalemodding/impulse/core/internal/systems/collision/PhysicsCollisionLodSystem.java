package dev.hytalemodding.impulse.core.internal.systems.collision;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.systems.visual.VisualInterestCollector;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Applies an opt-in distance collision LOD for default Impulse dynamic-body filters.
 */
public class PhysicsCollisionLodSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    @Nonnull
    private final Map<Store<EntityStore>, CollisionLodState> statesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        CollisionLodState state = stateFor(store);
        state.refreshPendingMutation();
        if (state.hasPendingMutation()) {
            return;
        }

        long tick = state.nextTick();
        List<CollisionLodUpdate> updates = collectUpdates(store, resource, state, tick);
        if (updates.isEmpty()) {
            return;
        }

        PhysicsMutationHandle<Void> handle = PhysicsWorkerAccess.runAsync(store,
            "apply collision LOD filters",
            () -> applyUpdates(resource, updates));
        state.trackPendingMutation(handle, updates);
    }

    @Nonnull
    private List<CollisionLodUpdate> collectUpdates(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull CollisionLodState state,
        long tick) {
        List<CollisionLodUpdate> updates = new ArrayList<>();
        IntOpenHashSet activeSpaces = new IntOpenHashSet();
        List<PhysicsWorldResource.VisualInterest> interests =
            VisualInterestCollector.collectMaterializationInterests(store, resource);
        for (PhysicsSpace space : resource.getSpaces()) {
            SpaceId spaceId = space.getId();
            activeSpaces.add(spaceId.value());
            PhysicsSpaceSettings settings = resource.getSpaceSettings(spaceId);
            if (!settings.getCollisionLodSettings().isCollisionLodEnabled()) {
                state.collectRestoreUpdates(spaceId, updates);
                continue;
            }
            if (!state.shouldRefresh(spaceId,
                settings.getCollisionLodSettings().getCollisionLodRefreshIntervalTicks(),
                tick)) {
                continue;
            }
            collectSpaceUpdates(resource, spaceId, settings, interests, state, updates);
        }
        state.pruneRemovedSpaces(activeSpaces);
        return updates;
    }

    private static void collectSpaceUpdates(@Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests,
        @Nonnull CollisionLodState state,
        @Nonnull List<CollisionLodUpdate> updates) {
        ObjectOpenHashSet<PhysicsBodyId> seenBodies = new ObjectOpenHashSet<>();
        resource.forEachBodySnapshot(spaceId, entry -> {
            PhysicsBodyId bodyId = entry.bodyId();
            seenBodies.add(bodyId);
            if (entry.persistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT) {
                state.recordRestore(spaceId, bodyId, updates);
                return;
            }
            if (!isCollisionLodCandidate(entry)) {
                return;
            }

            CollisionLodTier previousTier = state.tier(bodyId);
            CollisionLodTier tier = resource.isBodyControlled(bodyId)
                ? CollisionLodTier.NEAR_FULL
                : resolveTier(settings, previousTier, entry.snapshot().position(), interests);
            state.recordTier(spaceId, bodyId, tier, updates);
        });
        state.pruneMissingBodies(spaceId, seenBodies);
    }

    static CollisionLodTier resolveTier(@Nonnull PhysicsSpaceSettings settings,
        @Nullable CollisionLodTier previousTier,
        @Nonnull Vector3f position,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests) {
        float distanceSquared = nearestDistanceSquared(position, interests);
        if (distanceSquared == Float.POSITIVE_INFINITY) {
            return CollisionLodTier.FAR_SLEEPING;
        }

        int nearRadius = settings.getCollisionLodSettings().getCollisionLodNearRadius();
        int midRadius = settings.getCollisionLodSettings().getCollisionLodMidRadius();
        int hysteresis = settings.getCollisionLodSettings().getCollisionLodHysteresis();
        if (previousTier == CollisionLodTier.NEAR_FULL) {
            return distanceSquared <= squared(nearRadius + hysteresis)
                ? CollisionLodTier.NEAR_FULL
                : resolveTierWithoutHysteresis(distanceSquared, nearRadius, midRadius);
        }
        if (previousTier == CollisionLodTier.MID_TERRAIN) {
            if (distanceSquared <= squared(nearRadius)) {
                return CollisionLodTier.NEAR_FULL;
            }
            return distanceSquared <= squared(midRadius + hysteresis)
                ? CollisionLodTier.MID_TERRAIN
                : CollisionLodTier.FAR_SLEEPING;
        }
        return resolveTierWithoutHysteresis(distanceSquared, nearRadius, midRadius);
    }

    @Nonnull
    private static CollisionLodTier resolveTierWithoutHysteresis(float distanceSquared,
        int nearRadius,
        int midRadius) {
        if (distanceSquared <= squared(nearRadius)) {
            return CollisionLodTier.NEAR_FULL;
        }
        if (distanceSquared <= squared(midRadius)) {
            return CollisionLodTier.MID_TERRAIN;
        }
        return CollisionLodTier.FAR_SLEEPING;
    }

    private static float nearestDistanceSquared(@Nonnull Vector3f position,
        @Nonnull List<PhysicsWorldResource.VisualInterest> interests) {
        float nearest = Float.POSITIVE_INFINITY;
        for (PhysicsWorldResource.VisualInterest interest : interests) {
            nearest = Math.min(nearest, position.distanceSquared(interest.position()));
        }
        return nearest;
    }

    private static int squared(int value) {
        return value * value;
    }

    static boolean isCollisionLodCandidate(
        @Nonnull PhysicsWorldResource.BodySnapshotEntry entry) {
        return entry.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT
            && entry.kind() == PhysicsBodyKind.BODY
            && entry.snapshot().isDynamic()
            && !entry.snapshot().sensor();
    }

    private static void applyUpdates(@Nonnull PhysicsWorldResource resource,
        @Nonnull List<CollisionLodUpdate> updates) {
        for (CollisionLodUpdate update : updates) {
            PhysicsWorldResource.BodyRegistration registration =
                resource.getRegistration(update.bodyId());
            if (registration == null
                || !registration.spaceId().equals(update.spaceId())
                || registration.kind() != PhysicsBodyKind.BODY
                || (update.trackTier()
                    && registration.persistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT)) {
                continue;
            }
            PhysicsBody body = registration.body();
            if (!body.isDynamic() || body.isSensor() || !isDefaultDynamicFilter(body)) {
                continue;
            }
            PhysicsSpaceSettings settings = resource.getSpaceSettings(update.spaceId());
            applyTier(body, update.tier(), settings.getCollisionLodSettings().isCollisionLodFarSleepEnabled());
        }
    }

    private static boolean isDefaultDynamicFilter(@Nonnull PhysicsBody body) {
        int fullDynamicMask = PhysicsCollisionFilters.TERRAIN
            | PhysicsCollisionFilters.DYNAMIC_BODY;
        return body.getCollisionGroup() == PhysicsCollisionFilters.DYNAMIC_BODY
            && (body.getCollisionMask() == PhysicsCollisionFilters.TERRAIN
                || body.getCollisionMask() == fullDynamicMask);
    }

    private static void applyTier(@Nonnull PhysicsBody body,
        @Nonnull CollisionLodTier tier,
        boolean farSleepEnabled) {
        int terrainOnlyMask = PhysicsCollisionFilters.TERRAIN;
        int fullDynamicMask = PhysicsCollisionFilters.TERRAIN
            | PhysicsCollisionFilters.DYNAMIC_BODY;
        switch (tier) {
            case NEAR_FULL -> {
                body.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY, fullDynamicMask);
                body.activate();
            }
            case MID_TERRAIN -> {
                body.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY, terrainOnlyMask);
                body.activate();
            }
            case FAR_SLEEPING -> {
                body.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY, terrainOnlyMask);
                if (farSleepEnabled) {
                    body.sleep();
                }
            }
        }
    }

    @Nonnull
    private CollisionLodState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (statesByStore) {
            return statesByStore.computeIfAbsent(store, ignored -> new CollisionLodState());
        }
    }

    enum CollisionLodTier {
        NEAR_FULL,
        MID_TERRAIN,
        FAR_SLEEPING
    }

    record CollisionLodUpdate(@Nonnull SpaceId spaceId,
                              @Nonnull PhysicsBodyId bodyId,
                              @Nonnull CollisionLodTier tier,
                              boolean trackTier) {
    }

    private record BodyTier(@Nonnull SpaceId spaceId, @Nonnull CollisionLodTier tier) {
    }

    static final class CollisionLodState {

        @Nonnull
        private final Object2ObjectMap<PhysicsBodyId, BodyTier> tiers =
            new Object2ObjectOpenHashMap<>();
        @Nonnull
        private final Int2LongOpenHashMap nextRefreshTicks = new Int2LongOpenHashMap();
        @Nonnull
        private final Queue<CollisionLodUpdate> pendingUpdates = new ArrayDeque<>();
        @Nullable
        private PhysicsMutationHandle<Void> pendingHandle;
        private long tick;

        CollisionLodState() {
            nextRefreshTicks.defaultReturnValue(0L);
        }

        private long nextTick() {
            return ++tick;
        }

        boolean hasPendingMutation() {
            return pendingHandle != null;
        }

        void refreshPendingMutation() {
            if (pendingHandle == null || !pendingHandle.isDone()) {
                return;
            }
            Throwable failure = pendingHandle.failure();
            if (failure != null) {
                LOGGER.at(Level.WARNING)
                    .log("Collision LOD worker mutation failed: %s", failure.getMessage());
                clearPendingRefreshes();
            } else {
                commitPendingUpdates();
            }
            pendingUpdates.clear();
            pendingHandle = null;
        }

        void trackPendingMutation(@Nonnull PhysicsMutationHandle<Void> handle,
            @Nonnull List<CollisionLodUpdate> updates) {
            pendingHandle = handle;
            pendingUpdates.addAll(updates);
        }

        boolean shouldRefresh(@Nonnull SpaceId spaceId, int interval, long tick) {
            long nextRefreshTick = nextRefreshTicks.get(spaceId.value());
            if (tick < nextRefreshTick) {
                return false;
            }
            nextRefreshTicks.put(spaceId.value(), tick + interval);
            return true;
        }

        @Nullable
        CollisionLodTier tier(@Nonnull PhysicsBodyId bodyId) {
            BodyTier bodyTier = tiers.get(bodyId);
            return bodyTier != null ? bodyTier.tier() : null;
        }

        void recordTier(@Nonnull SpaceId spaceId,
            @Nonnull PhysicsBodyId bodyId,
            @Nonnull CollisionLodTier tier,
            @Nonnull List<CollisionLodUpdate> updates) {
            BodyTier previous = tiers.get(bodyId);
            if (previous != null && previous.spaceId().equals(spaceId) && previous.tier() == tier) {
                return;
            }
            updates.add(new CollisionLodUpdate(spaceId, bodyId, tier, true));
        }

        void recordRestore(@Nonnull SpaceId spaceId,
            @Nonnull PhysicsBodyId bodyId,
            @Nonnull List<CollisionLodUpdate> updates) {
            BodyTier previous = tiers.get(bodyId);
            if (previous == null || !previous.spaceId().equals(spaceId)) {
                return;
            }
            updates.add(new CollisionLodUpdate(spaceId,
                bodyId,
                CollisionLodTier.NEAR_FULL,
                false));
        }

        private void collectRestoreUpdates(@Nonnull SpaceId spaceId,
            @Nonnull List<CollisionLodUpdate> updates) {
            for (Object2ObjectMap.Entry<PhysicsBodyId, BodyTier> entry
                : tiers.object2ObjectEntrySet()) {
                if (!entry.getValue().spaceId().equals(spaceId)) {
                    continue;
                }
                updates.add(new CollisionLodUpdate(spaceId,
                    entry.getKey(),
                    CollisionLodTier.NEAR_FULL,
                    false));
            }
            nextRefreshTicks.remove(spaceId.value());
        }

        private void commitPendingUpdates() {
            for (CollisionLodUpdate update : pendingUpdates) {
                if (update.trackTier()) {
                    tiers.put(update.bodyId(), new BodyTier(update.spaceId(), update.tier()));
                } else {
                    tiers.remove(update.bodyId());
                }
            }
        }

        private void clearPendingRefreshes() {
            for (CollisionLodUpdate update : pendingUpdates) {
                nextRefreshTicks.remove(update.spaceId().value());
            }
        }

        private void pruneMissingBodies(@Nonnull SpaceId spaceId,
            @Nonnull ObjectOpenHashSet<PhysicsBodyId> seenBodies) {
            tiers.object2ObjectEntrySet()
                .removeIf(entry -> entry.getValue().spaceId().equals(spaceId)
                    && !seenBodies.contains(entry.getKey()));
        }

        private void pruneRemovedSpaces(@Nonnull IntOpenHashSet activeSpaces) {
            tiers.object2ObjectEntrySet()
                .removeIf(entry -> !activeSpaces.contains(entry.getValue().spaceId().value()));
            nextRefreshTicks.keySet().removeIf(spaceValue -> !activeSpaces.contains(spaceValue));
        }
    }
}
