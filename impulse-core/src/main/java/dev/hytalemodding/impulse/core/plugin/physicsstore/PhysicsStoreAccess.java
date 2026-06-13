package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.SpaceRemoveRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.SpaceSettingsRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.SpaceUpsertRequest;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Access to the early-plugin-injected PhysicsStore on a Hytale world.
 */
public final class PhysicsStoreAccess {

    @Nullable
    private static volatile MethodHandle worldGetPhysicsStore;

    private PhysicsStoreAccess() {
    }

    @Nonnull
    public static PhysicsStore require(@Nonnull World world) {
        Objects.requireNonNull(world, "world");
        try {
            return (PhysicsStore) worldAccessor().invoke(world);
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to access World.getPhysicsStore()", throwable);
        }
    }

    @Nullable
    public static UUID resolveSpaceUuid(@Nonnull World world,
        @Nonnull SpaceId spaceId) {
        Objects.requireNonNull(spaceId, "spaceId");
        Store<PhysicsStore> store = require(world).getStore();
        PhysicsSpaceCompatibilityIndexResource index = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        return index.getSpaceUuid(spaceId);
    }

    public static boolean hasSpace(@Nonnull World world, @Nonnull SpaceId spaceId) {
        Objects.requireNonNull(spaceId, "spaceId");
        Store<PhysicsStore> store = require(world).getStore();
        return store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .hasSpace(spaceId);
    }

    @Nonnull
    public static Collection<SpaceId> spaceIds(@Nonnull World world) {
        Store<PhysicsStore> store = require(world).getStore();
        return List.copyOf(store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .spaceIds());
    }

    public static int spaceCount(@Nonnull World world) {
        Store<PhysicsStore> store = require(world).getStore();
        return store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType()).size();
    }

    @Nullable
    public static PhysicsSpaceSettings getSpaceSettings(@Nonnull World world,
        @Nonnull SpaceId spaceId) {
        Objects.requireNonNull(spaceId, "spaceId");
        Store<PhysicsStore> store = require(world).getStore();
        UUID spaceUuid = store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(spaceId);
        if (spaceUuid == null) {
            return null;
        }
        Ref<PhysicsStore> ref = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        SpaceComponent space = store.getComponent(ref, SpaceComponent.getComponentType());
        if (space == null) {
            return null;
        }
        WorldCollisionComponent worldCollision = store.getComponent(ref,
            WorldCollisionComponent.getComponentType());
        return toSpaceSettings(worldCollision);
    }

    @Nonnull
    public static UUID enqueueSpaceUpsert(@Nonnull World world,
        @Nonnull SpaceId compatibilitySpaceId,
        @Nonnull BackendId backendId,
        @Nonnull PhysicsSpaceSettings settings) {
        Objects.requireNonNull(compatibilitySpaceId, "compatibilitySpaceId");
        Objects.requireNonNull(backendId, "backendId");
        requireRepresentedSpaceSettings(settings);
        Impulse.getRuntimeProvider(backendId);
        UUID spaceUuid = UUID.randomUUID();
        enqueue(world, SpaceUpsertRequest.of(spaceUuid, compatibilitySpaceId, backendId, settings));
        return spaceUuid;
    }

    public static void enqueueSpaceRemove(@Nonnull World world, @Nonnull SpaceId spaceId) {
        UUID spaceUuid = requireSpaceUuid(world, spaceId);
        enqueue(world, SpaceRemoveRequest.of(spaceUuid));
    }

    public static void enqueueSpaceSettings(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        requireRepresentedSpaceSettings(settings);
        UUID spaceUuid = requireSpaceUuid(world, spaceId);
        enqueue(world, SpaceSettingsRequest.of(spaceUuid, settings));
    }

    public static void enqueue(@Nonnull World world,
        @Nonnull PhysicsStoreRequest request) {
        Objects.requireNonNull(request, "request");
        Store<PhysicsStore> store = require(world).getStore();
        store.getResource(PhysicsRequestQueueResource.getResourceType())
            .enqueue(request);
    }

    public static void enqueueAll(@Nonnull World world,
        @Nonnull Iterable<? extends PhysicsStoreRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        List<PhysicsStoreRequest> copied = new ArrayList<>();
        for (PhysicsStoreRequest request : requests) {
            copied.add(Objects.requireNonNull(request, "request"));
        }
        Store<PhysicsStore> store = require(world).getStore();
        PhysicsRequestQueueResource queue = store.getResource(
            PhysicsRequestQueueResource.getResourceType());
        queue.enqueueAll(copied);
    }

    @Nonnull
    private static MethodHandle worldAccessor() {
        MethodHandle accessor = worldGetPhysicsStore;
        if (accessor != null) {
            return accessor;
        }
        synchronized (PhysicsStoreAccess.class) {
            accessor = worldGetPhysicsStore;
            if (accessor == null) {
                accessor = findWorldAccessor();
                worldGetPhysicsStore = accessor;
            }
            return accessor;
        }
    }

    @Nonnull
    private static UUID requireSpaceUuid(@Nonnull World world, @Nonnull SpaceId spaceId) {
        UUID spaceUuid = resolveSpaceUuid(world, spaceId);
        if (spaceUuid == null) {
            throw new IllegalArgumentException("PhysicsStore space id=" + spaceId.value()
                + " is not registered");
        }
        return spaceUuid;
    }

    @Nonnull
    private static PhysicsSpaceSettings toSpaceSettings(
        @Nullable WorldCollisionComponent worldCollision) {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        if (worldCollision == null) {
            return settings;
        }
        PhysicsWorldCollisionSettings target = settings.getWorldCollisionSettings();
        target.setWorldCollisionMode(worldCollision.getMode());
        target.setNativeVoxelTerrainEnabled(worldCollision.isNativeVoxelTerrainEnabled());
        target.setWorldCollisionRadius(worldCollision.getRadius());
        target.setWorldCollisionBodyRadius(worldCollision.getBodyRadius());
        target.setWorldCollisionTtlTicks(worldCollision.getTtlTicks());
        target.setTerrainMaterial(worldCollision.getTerrainFriction(),
            worldCollision.getTerrainRestitution());
        return settings;
    }

    private static void requireRepresentedSpaceSettings(@Nonnull PhysicsSpaceSettings settings) {
        Objects.requireNonNull(settings, "settings");
        PhysicsSpaceSettings defaults = PhysicsSpaceSettings.defaults();
        if (!solverSettingsEqual(settings.getSolverSettings(), defaults.getSolverSettings())
            || !visualSyncSettingsEqual(settings.getVisualSyncSettings(),
                defaults.getVisualSyncSettings())
            || !visualMaterializationSettingsEqual(settings.getVisualMaterializationSettings(),
                defaults.getVisualMaterializationSettings())
            || !collisionLodSettingsEqual(settings.getCollisionLodSettings(),
                defaults.getCollisionLodSettings())
            || !settings.getExtensionSettings().isEmpty()) {
            throw new IllegalArgumentException("Authoritative PhysicsStore space requests currently "
                + "support world-collision settings only; solver, visual sync, visual "
                + "materialization, collision LOD, and extension settings need a dedicated "
                + "PhysicsStore space-settings model before they can be applied.");
        }
    }

    private static boolean solverSettingsEqual(@Nonnull PhysicsSolverSettings left,
        @Nonnull PhysicsSolverSettings right) {
        return left.getSolverIterations() == right.getSolverIterations()
            && left.getStabilizationIterations() == right.getStabilizationIterations()
            && Float.compare(left.getDynamicSleepLinearThreshold(),
                right.getDynamicSleepLinearThreshold()) == 0
            && Float.compare(left.getDynamicSleepAngularThreshold(),
                right.getDynamicSleepAngularThreshold()) == 0
            && Float.compare(left.getDynamicSleepTimeUntilSleep(),
                right.getDynamicSleepTimeUntilSleep()) == 0;
    }

    private static boolean visualSyncSettingsEqual(@Nonnull PhysicsVisualSyncSettings left,
        @Nonnull PhysicsVisualSyncSettings right) {
        return left.getVisualFullSyncRadius() == right.getVisualFullSyncRadius()
            && left.getVisualMaxSyncRadius() == right.getVisualMaxSyncRadius()
            && left.isVisualFarSyncCutoffEnabled() == right.isVisualFarSyncCutoffEnabled()
            && left.getVisualMidSyncIntervalTicks() == right.getVisualMidSyncIntervalTicks()
            && left.getVisualFarSyncIntervalTicks() == right.getVisualFarSyncIntervalTicks()
            && left.getVisualOcclusionMode() == right.getVisualOcclusionMode()
            && left.getVisualOcclusionRaycastsPerTick()
                == right.getVisualOcclusionRaycastsPerTick()
            && left.getVisualOcclusionCacheTicks() == right.getVisualOcclusionCacheTicks()
            && left.isVisualSnapshotPredictionEnabled()
                == right.isVisualSnapshotPredictionEnabled()
            && Float.compare(left.getVisualSnapshotPredictionMaxSeconds(),
                right.getVisualSnapshotPredictionMaxSeconds()) == 0
            && left.isVisualSnapshotSmoothingEnabled()
                == right.isVisualSnapshotSmoothingEnabled()
            && Float.compare(left.getVisualSnapshotSmoothingRate(),
                right.getVisualSnapshotSmoothingRate()) == 0
            && left.isEntityVisualSyncCullingEnabled()
                == right.isEntityVisualSyncCullingEnabled()
            && left.isVisualVisibilityCullingEnabled() == right.isVisualVisibilityCullingEnabled();
    }

    private static boolean visualMaterializationSettingsEqual(
        @Nonnull PhysicsVisualMaterializationSettings left,
        @Nonnull PhysicsVisualMaterializationSettings right) {
        return left.isDetachedVisualMaterializationEnabled()
            == right.isDetachedVisualMaterializationEnabled()
            && left.getDetachedVisualMaterializationRadius()
                == right.getDetachedVisualMaterializationRadius()
            && left.getDetachedVisualDematerializationRadius()
                == right.getDetachedVisualDematerializationRadius()
            && left.getDetachedVisualMaxSpawnsPerTick()
                == right.getDetachedVisualMaxSpawnsPerTick()
            && left.getDetachedVisualMaxMaterialized()
                == right.getDetachedVisualMaxMaterialized()
            && left.getDetachedVisualInterestRefreshIntervalTicks()
                == right.getDetachedVisualInterestRefreshIntervalTicks()
            && left.getDetachedVisualCandidateRefreshIntervalTicks()
                == right.getDetachedVisualCandidateRefreshIntervalTicks()
            && left.getDetachedVisualVisibilityCheckIntervalTicks()
                == right.getDetachedVisualVisibilityCheckIntervalTicks()
            && left.getDetachedVisualBlockType().equals(right.getDetachedVisualBlockType());
    }

    private static boolean collisionLodSettingsEqual(@Nonnull PhysicsCollisionLodSettings left,
        @Nonnull PhysicsCollisionLodSettings right) {
        return left.isCollisionLodEnabled() == right.isCollisionLodEnabled()
            && left.getCollisionLodNearRadius() == right.getCollisionLodNearRadius()
            && left.getCollisionLodMidRadius() == right.getCollisionLodMidRadius()
            && left.getCollisionLodHysteresis() == right.getCollisionLodHysteresis()
            && left.getCollisionLodRefreshIntervalTicks()
                == right.getCollisionLodRefreshIntervalTicks()
            && left.isCollisionLodFarSleepEnabled() == right.isCollisionLodFarSleepEnabled();
    }

    @Nonnull
    private static MethodHandle findWorldAccessor() {
        try {
            return MethodHandles.publicLookup()
                .findVirtual(World.class, "getPhysicsStore", MethodType.methodType(PhysicsStore.class));
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Impulse requires the PhysicsStore early plugin. "
                + "Install impulse-early-plugin as a Hytale early plugin before using PhysicsStore APIs.",
                exception);
        }
    }
}
