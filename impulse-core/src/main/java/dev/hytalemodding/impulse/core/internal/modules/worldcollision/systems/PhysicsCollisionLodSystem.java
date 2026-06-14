package dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshots;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime;
import dev.hytalemodding.impulse.core.internal.systems.visual.VisualInterestCollector;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerBridge;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.store.integration.PhysicsStoreEarlyPluginProbe;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsSnapshotPublicationSystem;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
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
import java.util.Set;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Applies an opt-in distance collision LOD for default Impulse dynamic-body filters.
 */
public class PhysicsCollisionLodSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PhysicsSnapshotPublicationSystem.class)
    );

    @Nonnull
    private final Map<Store<EntityStore>, CollisionLodState> statesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (!WorldCollisionLifecycle.isEnabled()) {
            return;
        }
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
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

        PhysicsMutationHandle<Void> handle = isAuthoritativePhysicsStoreActive()
            ? applyAuthoritativeUpdatesAsync(store, updates)
            : PhysicsOwnerBridge.runAsync(store,
                "apply collision LOD filters",
                () -> applyUpdates(resource, updates));
        state.trackPendingMutation(handle, updates);
    }

    @Nonnull
    private List<CollisionLodUpdate> collectUpdates(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull CollisionLodState state,
        long tick) {
        List<CollisionLodUpdate> updates = new ArrayList<>();
        IntOpenHashSet activeSpaces = new IntOpenHashSet();
        List<PhysicsVisualRuntime.VisualInterest> interests =
            VisualInterestCollector.collectMaterializationInterests(store, resource);
        if (isAuthoritativePhysicsStoreActive()) {
            for (SpaceId spaceId : resource.getSpaceIds()) {
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
        for (PhysicsSpaceBinding space : resource.getSpaceBindings()) {
            SpaceId spaceId = space.spaceId();
            activeSpaces.add(spaceId.value());
            PhysicsSpaceSettings settings = resource.getLiveSpaceSettings(spaceId);
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

    private static void collectSpaceUpdates(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<PhysicsVisualRuntime.VisualInterest> interests,
        @Nonnull CollisionLodState state,
        @Nonnull List<CollisionLodUpdate> updates) {
        ObjectOpenHashSet<RigidBodyKey> seenBodies = new ObjectOpenHashSet<>();
        resource.forEachIndexedBodySnapshot(spaceId, (bodyKey, snapshot, _, kind, persistenceMode) -> {
            seenBodies.add(bodyKey);
            if (persistenceMode == PhysicsBodyPersistenceMode.PERSISTENT) {
                state.recordRestore(spaceId, bodyKey, updates);
                return;
            }
            if (!isCollisionLodCandidate(snapshot, kind, persistenceMode)) {
                return;
            }

            CollisionLodTier previousTier = state.tier(bodyKey);
            CollisionLodTier tier = resource.isBodyControlled(bodyKey)
                ? CollisionLodTier.NEAR_FULL
                : resolveTier(settings,
                    previousTier,
                    snapshot.positionX(),
                    snapshot.positionY(),
                    snapshot.positionZ(),
                    interests);
            state.recordTier(spaceId,
                bodyKey,
                tier,
                settings.getCollisionLodSettings().isCollisionLodFarSleepEnabled(),
                updates);
        });
        state.pruneMissingBodies(spaceId, seenBodies);
    }

    static CollisionLodTier resolveTier(@Nonnull PhysicsSpaceSettings settings,
        @Nullable CollisionLodTier previousTier,
        @Nonnull Vector3f position,
        @Nonnull List<PhysicsVisualRuntime.VisualInterest> interests) {
        return resolveTier(settings, previousTier, position.x, position.y, position.z, interests);
    }

    static CollisionLodTier resolveTier(@Nonnull PhysicsSpaceSettings settings,
        @Nullable CollisionLodTier previousTier,
        float positionX,
        float positionY,
        float positionZ,
        @Nonnull List<PhysicsVisualRuntime.VisualInterest> interests) {
        float distanceSquared = nearestDistanceSquared(positionX, positionY, positionZ, interests);
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

    private static float nearestDistanceSquared(float positionX,
        float positionY,
        float positionZ,
        @Nonnull List<PhysicsVisualRuntime.VisualInterest> interests) {
        float nearest = Float.POSITIVE_INFINITY;
        for (PhysicsVisualRuntime.VisualInterest interest : interests) {
            Vector3f interestPosition = interest.position();
            float dx = positionX - interestPosition.x;
            float dy = positionY - interestPosition.y;
            float dz = positionZ - interestPosition.z;
            nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
        }
        return nearest;
    }

    private static int squared(int value) {
        return value * value;
    }

    static boolean isCollisionLodCandidate(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return persistenceMode != PhysicsBodyPersistenceMode.PERSISTENT
            && kind == PhysicsBodyKind.BODY
            && snapshot.isDynamic()
            && !snapshot.sensor();
    }

    @Nonnull
    private static PhysicsMutationHandle<Void> applyAuthoritativeUpdatesAsync(
        @Nonnull Store<EntityStore> store,
        @Nonnull List<CollisionLodUpdate> updates) {
        World world = store.getExternalData().getWorld();
        return PhysicsMutationHandle.fromCompletion("apply collision LOD filters",
            null,
            PhysicsStoreThreading.executeOnWorldThread(world,
                "apply collision LOD filters",
                physics -> applyAuthoritativeUpdates(physics, updates)));
    }

    private static void applyAuthoritativeUpdates(@Nonnull Store<PhysicsStore> store,
        @Nonnull List<CollisionLodUpdate> updates) {
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        for (CollisionLodUpdate update : updates) {
            UUID spaceUuid = compatibility.getSpaceUuid(update.spaceId());
            if (spaceUuid == null) {
                continue;
            }
            Ref<PhysicsStore> bodyRef = identity.getByUuid(update.bodyKey().value());
            if (bodyRef == null || !bodyRef.isValid()) {
                continue;
            }
            BodyComponent body = store.getComponent(bodyRef, BodyComponent.getComponentType());
            if (body == null
                || !spaceUuid.equals(body.getSpaceUuid())
                || body.getKind() != PhysicsBodyKind.BODY
                || (update.trackTier()
                    && body.getPersistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT)) {
                continue;
            }
            appendBodyCommand(store, bodyRef, collisionFilterCommand(update));
            if (update.tier() == CollisionLodTier.FAR_SLEEPING && update.farSleepEnabled()) {
                appendBodyCommand(store, bodyRef, BodyCommandComponent.sleep());
            }
        }
    }

    @Nonnull
    private static BodyCommandComponent collisionFilterCommand(@Nonnull CollisionLodUpdate update) {
        int terrainOnlyMask = PhysicsCollisionFilters.TERRAIN;
        int fullDynamicMask = PhysicsCollisionFilters.TERRAIN
            | PhysicsCollisionFilters.DYNAMIC_BODY;
        int mask = update.tier() == CollisionLodTier.NEAR_FULL
            ? fullDynamicMask
            : terrainOnlyMask;
        return BodyCommandComponent.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
            mask,
            update.tier() != CollisionLodTier.FAR_SLEEPING);
    }

    private static void appendBodyCommand(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull BodyCommandComponent command) {
        BodyCommandComponent existing = store.getComponent(bodyRef,
            BodyCommandComponent.getComponentType());
        BodyCommandComponent merged = existing != null ? existing.append(command) : command;
        store.putComponent(bodyRef, BodyCommandComponent.getComponentType(), merged);
    }

    private static void applyUpdates(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull List<CollisionLodUpdate> updates) {
        for (CollisionLodUpdate update : updates) {
            PhysicsBodyRegistration registration =
                resource.getRegistration(update.bodyKey());
            if (registration == null
                || !registration.spaceId().equals(update.spaceId())
                || registration.kind() != PhysicsBodyKind.BODY
                || (update.trackTier()
                    && registration.persistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT)) {
                continue;
            }
            PhysicsSpaceBinding space = resource.getSpaceBinding(registration.spaceId());
            if (space == null) {
                continue;
            }
            PhysicsBodySnapshot snapshot = PhysicsBodySnapshots.read(space, registration.backendBodyHandle().value());
            if (snapshot == null || !snapshot.isDynamic() || snapshot.sensor()) {
                continue;
            }
            applyTier(space,
                registration.backendBodyHandle().value(),
                update.tier(),
                update.farSleepEnabled());
        }
    }

    private static boolean isAuthoritativePhysicsStoreActive() {
        return PhysicsStoreEarlyPluginProbe.isAvailable();
    }

    private static void applyTier(@Nonnull PhysicsSpaceBinding space,
        long backendBodyId,
        @Nonnull CollisionLodTier tier,
        boolean farSleepEnabled) {
        int terrainOnlyMask = PhysicsCollisionFilters.TERRAIN;
        int fullDynamicMask = PhysicsCollisionFilters.TERRAIN
            | PhysicsCollisionFilters.DYNAMIC_BODY;
        switch (tier) {
            case NEAR_FULL -> {
                space.runtime().setBodyCollisionFilter(space.backendSpaceHandle().value(),
                    backendBodyId,
                    PhysicsCollisionFilters.DYNAMIC_BODY,
                    fullDynamicMask);
                space.runtime().activateBody(space.backendSpaceHandle().value(), backendBodyId);
            }
            case MID_TERRAIN -> {
                space.runtime().setBodyCollisionFilter(space.backendSpaceHandle().value(),
                    backendBodyId,
                    PhysicsCollisionFilters.DYNAMIC_BODY,
                    terrainOnlyMask);
                space.runtime().activateBody(space.backendSpaceHandle().value(), backendBodyId);
            }
            case FAR_SLEEPING -> {
                space.runtime().setBodyCollisionFilter(space.backendSpaceHandle().value(),
                    backendBodyId,
                    PhysicsCollisionFilters.DYNAMIC_BODY,
                    terrainOnlyMask);
                if (farSleepEnabled) {
                    space.runtime().sleepBody(space.backendSpaceHandle().value(), backendBodyId);
                }
            }
        }
    }

    @Nonnull
    private CollisionLodState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (statesByStore) {
            return statesByStore.computeIfAbsent(store, _ -> new CollisionLodState());
        }
    }

    enum CollisionLodTier {
        NEAR_FULL,
        MID_TERRAIN,
        FAR_SLEEPING
    }

    record CollisionLodUpdate(@Nonnull SpaceId spaceId,
                              @Nonnull RigidBodyKey bodyKey,
                              @Nonnull CollisionLodTier tier,
                              boolean farSleepEnabled,
                              boolean trackTier) {
    }

    private record BodyTier(@Nonnull SpaceId spaceId, @Nonnull CollisionLodTier tier) {
    }

    static final class CollisionLodState {

        @Nonnull
        private final Object2ObjectMap<RigidBodyKey, BodyTier> tiers =
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
                    .log("Collision LOD owner mutation failed: %s", failure.getMessage());
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
        CollisionLodTier tier(@Nonnull RigidBodyKey bodyKey) {
            BodyTier bodyTier = tiers.get(bodyKey);
            return bodyTier != null ? bodyTier.tier() : null;
        }

        void recordTier(@Nonnull SpaceId spaceId,
            @Nonnull RigidBodyKey bodyKey,
            @Nonnull CollisionLodTier tier,
            boolean farSleepEnabled,
            @Nonnull List<CollisionLodUpdate> updates) {
            BodyTier previous = tiers.get(bodyKey);
            if (previous != null && previous.spaceId().equals(spaceId) && previous.tier() == tier) {
                return;
            }
            updates.add(new CollisionLodUpdate(spaceId, bodyKey, tier, farSleepEnabled, true));
        }

        void recordRestore(@Nonnull SpaceId spaceId,
            @Nonnull RigidBodyKey bodyKey,
            @Nonnull List<CollisionLodUpdate> updates) {
            BodyTier previous = tiers.get(bodyKey);
            if (previous == null || !previous.spaceId().equals(spaceId)) {
                return;
            }
            updates.add(new CollisionLodUpdate(spaceId,
                bodyKey,
                CollisionLodTier.NEAR_FULL,
                false,
                false));
        }

        private void collectRestoreUpdates(@Nonnull SpaceId spaceId,
            @Nonnull List<CollisionLodUpdate> updates) {
            for (Object2ObjectMap.Entry<RigidBodyKey, BodyTier> entry
                : tiers.object2ObjectEntrySet()) {
                if (!entry.getValue().spaceId().equals(spaceId)) {
                    continue;
                }
                updates.add(new CollisionLodUpdate(spaceId,
                    entry.getKey(),
                    CollisionLodTier.NEAR_FULL,
                    false,
                    false));
            }
            nextRefreshTicks.remove(spaceId.value());
        }

        private void commitPendingUpdates() {
            for (CollisionLodUpdate update : pendingUpdates) {
                if (update.trackTier()) {
                    tiers.put(update.bodyKey(), new BodyTier(update.spaceId(), update.tier()));
                } else {
                    tiers.remove(update.bodyKey());
                }
            }
        }

        private void clearPendingRefreshes() {
            for (CollisionLodUpdate update : pendingUpdates) {
                nextRefreshTicks.remove(update.spaceId().value());
            }
        }

        private void pruneMissingBodies(@Nonnull SpaceId spaceId,
            @Nonnull ObjectOpenHashSet<RigidBodyKey> seenBodies) {
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

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
