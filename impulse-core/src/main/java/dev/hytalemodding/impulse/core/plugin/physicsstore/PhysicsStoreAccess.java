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
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequestFenceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.SpaceRemoveRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.SpaceSettingsRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.SpaceUpsertRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
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
    public static PhysicsStoreBodySnapshot getBodySnapshot(@Nonnull World world,
        @Nonnull UUID bodyUuid) {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Store<PhysicsStore> store = require(world).getStore();
        return store.getResource(PhysicsSnapshotResource.getResourceType()).getBody(bodyUuid);
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
        SolverSettingsComponent solverSettings = store.getComponent(ref,
            SolverSettingsComponent.getComponentType());
        VisualSyncSettingsComponent visualSyncSettings = store.getComponent(ref,
            VisualSyncSettingsComponent.getComponentType());
        VisualMaterializationSettingsComponent visualMaterializationSettings =
            store.getComponent(ref, VisualMaterializationSettingsComponent.getComponentType());
        CollisionLodSettingsComponent collisionLodSettings = store.getComponent(ref,
            CollisionLodSettingsComponent.getComponentType());
        ExtensionSettingsComponent extensionSettings = store.getComponent(ref,
            ExtensionSettingsComponent.getComponentType());
        return toSpaceSettings(worldCollision,
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
    }

    @Nonnull
    public static UUID enqueueSpaceUpsert(@Nonnull World world,
        @Nonnull SpaceId compatibilitySpaceId,
        @Nonnull BackendId backendId,
        @Nonnull PhysicsSpaceSettings settings) {
        Objects.requireNonNull(compatibilitySpaceId, "compatibilitySpaceId");
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(settings, "settings");
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
        Objects.requireNonNull(settings, "settings");
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
    public static PhysicsStoreRequestFenceHandle enqueueAllFenced(@Nonnull World world,
        @Nonnull Iterable<? extends PhysicsStoreRequest> requests,
        long submittedServerTick) {
        Objects.requireNonNull(requests, "requests");
        List<PhysicsStoreRequest> copied = new ArrayList<>();
        for (PhysicsStoreRequest request : requests) {
            copied.add(Objects.requireNonNull(request, "request"));
        }
        Store<PhysicsStore> store = require(world).getStore();
        PhysicsRequestQueueResource queue = store.getResource(
            PhysicsRequestQueueResource.getResourceType());
        return queue.enqueueAllFenced(copied, submittedServerTick);
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
        @Nullable WorldCollisionComponent worldCollision,
        @Nullable SolverSettingsComponent solverSettings,
        @Nullable VisualSyncSettingsComponent visualSyncSettings,
        @Nullable VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nullable CollisionLodSettingsComponent collisionLodSettings,
        @Nullable ExtensionSettingsComponent extensionSettings) {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        if (worldCollision != null) {
            worldCollision.copyTo(settings);
        }
        if (solverSettings != null) {
            solverSettings.copyTo(settings);
        }
        if (visualSyncSettings != null) {
            visualSyncSettings.copyTo(settings);
        }
        if (visualMaterializationSettings != null) {
            visualMaterializationSettings.copyTo(settings);
        }
        if (collisionLodSettings != null) {
            collisionLodSettings.copyTo(settings);
        }
        if (extensionSettings != null) {
            extensionSettings.copyTo(settings);
        }
        return settings;
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
