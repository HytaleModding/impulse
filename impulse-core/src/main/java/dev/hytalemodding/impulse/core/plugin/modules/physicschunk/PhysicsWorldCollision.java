package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreTopologyMutations;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldCollisionIndexResource.SpaceWorldCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Public PhysicsChunk operations for terrain-backed world collision.
 */
public final class PhysicsWorldCollision {

    private PhysicsWorldCollision() {
    }

    public static void enableModule() {
        WorldCollisionLifecycle.enable();
    }

    public static void disableModule() {
        WorldCollisionLifecycle.disable();
    }

    public static boolean isModuleEnabled() {
        return WorldCollisionLifecycle.isEnabled();
    }

    @Nonnull
    public static WorldCollisionBuildStats rebuildAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "rebuild PhysicsStore world collision");
        SpaceWorldCollisionSettings settings = requireSettings(checkedStore, spaceId);
        PhysicsTerrainMutationQueueResource queue = checkedStore.getResource(
            PhysicsTerrainMutationQueueResource.getResourceType());
        int removed = clearSpaceRows(world, checkedStore, settings.spaceUuid());
        WorldCollisionPrewarmStats stats = streaming(world).ensureAround(world,
            settings.spaceUuid(),
            queue,
            List.of(Objects.requireNonNull(center, "center")),
            radius,
            Math.max(0L, world.getTick()),
            null,
            settings.buildOptions());
        return withRemovedBodies(stats.buildStats(),
            stats.buildStats().removedBodies() + removed);
    }

    @Nonnull
    public static WorldCollisionBuildStats refreshAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "refresh PhysicsStore world collision");
        SpaceWorldCollisionSettings settings = requireSettings(checkedStore, spaceId);
        return streaming(world).refreshAround(world,
            settings.spaceUuid(),
            checkedStore.getResource(PhysicsTerrainMutationQueueResource.getResourceType()),
            Objects.requireNonNull(center, "center"),
            radius,
            Math.max(0L, world.getTick()),
            null,
            settings.buildOptions());
    }

    @Nonnull
    public static WorldCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "ensure PhysicsStore world collision");
        SpaceWorldCollisionSettings settings = requireSettings(checkedStore, spaceId);
        return streaming(world).ensureAround(world,
            settings.spaceUuid(),
            checkedStore.getResource(PhysicsTerrainMutationQueueResource.getResourceType()),
            Objects.requireNonNull(centers, "centers"),
            radius,
            tick,
            null,
            settings.buildOptions());
    }

    public static int clearSpace(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "clear PhysicsStore world collision");
        UUID spaceUuid = PhysicsStoreSpaceMutations.requireSpaceUuid(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"));
        return clearSpaceRows(world, checkedStore, spaceUuid);
    }

    @Nonnull
    public static WorldCollisionStats stats(@Nonnull World world) {
        Objects.requireNonNull(world, "world");
        if (!world.isInThread()) {
            throw new IllegalStateException("Cannot read PhysicsChunk world-collision stats "
                + "outside the owning world thread");
        }
        return isModuleEnabled()
            ? streaming(world).stats()
            : new WorldCollisionStats(0, 0, 0, 0);
    }

    private static void requireEnabled() {
        if (!isModuleEnabled()) {
            throw new IllegalStateException("Impulse physics chunk subplugin is disabled");
        }
    }

    @Nonnull
    private static Store<PhysicsStore> requireMatchingWorldThread(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull String operation) {
        World checkedWorld = Objects.requireNonNull(world, "world");
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore, operation);
        if (PhysicsThreading.world(checkedStore) != checkedWorld) {
            throw new IllegalArgumentException("PhysicsStore does not belong to the supplied world");
        }
        return checkedStore;
    }

    @Nonnull
    private static SpaceWorldCollisionSettings requireSettings(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        UUID spaceUuid = PhysicsStoreSpaceMutations.requireSpaceUuid(store,
            Objects.requireNonNull(spaceId, "spaceId"));
        Ref<PhysicsStore> spaceRef = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);
        if (spaceRef == null || !spaceRef.isValid()) {
            throw new IllegalStateException("PhysicsStore space id=" + spaceId.value()
                + " is not bound yet");
        }
        WorldCollisionComponent component =
            store.getComponent(spaceRef, WorldCollisionComponent.getComponentType());
        WorldCollisionComponent settings = component != null ? component : new WorldCollisionComponent();
        if (settings.getMode() == WorldCollisionMode.NONE) {
            throw new IllegalStateException("World collision is disabled for space " + spaceId);
        }
        return new SpaceWorldCollisionSettings(spaceUuid,
            settings.getMode(),
            settings.getEntityChunkBoundaryMode(),
            settings.isNativeVoxelTerrainEnabled(),
            settings.getRadius(),
            settings.getBodyRadius(),
            settings.getTtlTicks(),
            settings.getTerrainFriction(),
            settings.getTerrainRestitution());
    }

    @Nonnull
    private static PhysicsStoreWorldCollisionStreamingResource streaming(@Nonnull World world) {
        Store<EntityStore> entityStore = Objects.requireNonNull(world, "world")
            .getEntityStore()
            .getStore();
        return entityStore.getResource(PhysicsStoreWorldCollisionStreamingResource.getResourceType());
    }

    private static int clearSpaceRows(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        int removed = 0;
        if (isModuleEnabled()) {
            removed = streaming(world).clearSpace(spaceUuid,
                store.getResource(PhysicsTerrainMutationQueueResource.getResourceType()));
        }
        int directlyRemoved = PhysicsStoreTopologyMutations.clearTerrainForSpace(store, spaceUuid);
        return removed != 0 ? removed : directlyRemoved;
    }

    @Nonnull
    private static WorldCollisionBuildStats withRemovedBodies(
        @Nonnull WorldCollisionBuildStats stats,
        int removedBodies) {
        return new WorldCollisionBuildStats(stats.scannedBlocks(),
            stats.solidBlocks(),
            stats.culledInteriorBlocks(),
            stats.fullCubeRuns(),
            stats.detailBoxes(),
            stats.colliderBodies(),
            removedBodies,
            stats.sectionsBuilt(),
            stats.sectionsRebuilt(),
            stats.voxelBodies());
    }
}
