package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkTerrainStreamingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreTopologyMutations;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkSettingsIndexResource.PhysicsChunkSpaceSettings;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Public PhysicsChunk operations for terrain-backed collision.
 */
public final class PhysicsChunkTerrain {

    private PhysicsChunkTerrain() {
    }

    public static boolean isSubPluginEnabled() {
        return PhysicsChunkLifecycle.isEnabled();
    }

    @Nonnull
    public static PhysicsChunkTerrainBuildStats rebuildAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "rebuild PhysicsChunk terrain");
        PhysicsChunkSpaceSettings settings = requireSettings(checkedStore, spaceId);
        PhysicsChunkCollisionMutationQueueResource queue = checkedStore.getResource(
            PhysicsChunkCollisionMutationQueueResource.getResourceType());
        int removed = clearSpaceRows(world, checkedStore, settings.spaceUuid());
        PhysicsChunkTerrainPrewarmStats stats = streaming(world).ensureAround(world,
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
    public static PhysicsChunkTerrainBuildStats refreshAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "refresh PhysicsChunk terrain");
        PhysicsChunkSpaceSettings settings = requireSettings(checkedStore, spaceId);
        return streaming(world).refreshAround(world,
            settings.spaceUuid(),
            checkedStore.getResource(PhysicsChunkCollisionMutationQueueResource.getResourceType()),
            Objects.requireNonNull(center, "center"),
            radius,
            Math.max(0L, world.getTick()),
            null,
            settings.buildOptions());
    }

    @Nonnull
    public static PhysicsChunkTerrainPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "ensure PhysicsChunk terrain");
        PhysicsChunkSpaceSettings settings = requireSettings(checkedStore, spaceId);
        return streaming(world).ensureAround(world,
            settings.spaceUuid(),
            checkedStore.getResource(PhysicsChunkCollisionMutationQueueResource.getResourceType()),
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
            "clear PhysicsChunk terrain");
        UUID spaceUuid = PhysicsStoreSpaceMutations.requireSpaceUuid(checkedStore,
            Objects.requireNonNull(spaceId, "spaceId"));
        return clearSpaceRows(world, checkedStore, spaceUuid);
    }

    @Nonnull
    public static PhysicsChunkTerrainStats stats(@Nonnull World world) {
        Objects.requireNonNull(world, "world");
        if (!world.isInThread()) {
            throw new IllegalStateException("Cannot read PhysicsChunk terrain stats "
                + "outside the owning world thread");
        }
        return isSubPluginEnabled()
            ? streaming(world).stats()
            : new PhysicsChunkTerrainStats(0, 0, 0, 0);
    }

    private static void requireEnabled() {
        if (!isSubPluginEnabled()) {
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
    private static PhysicsChunkSpaceSettings requireSettings(
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
        ChunkCollisionSettingsComponent component =
            store.getComponent(spaceRef, ChunkCollisionSettingsComponent.getComponentType());
        ChunkCollisionSettingsComponent settings =
            component != null ? component : new ChunkCollisionSettingsComponent();
        if (settings.getMode() == PhysicsChunkTerrainMode.NONE) {
            throw new IllegalStateException("PhysicsChunk terrain is disabled for space "
                + spaceId);
        }
        MaterialComponent material =
            store.getComponent(spaceRef, MaterialComponent.getComponentType());
        CollisionFilterComponent filter =
            store.getComponent(spaceRef, CollisionFilterComponent.getComponentType());
        return new PhysicsChunkSpaceSettings(spaceUuid,
            settings.getMode(),
            settings.getEntityChunkBoundaryMode(),
            settings.isNativeVoxelCollisionEnabled(),
            settings.getRadius(),
            settings.getBodyRadius(),
            settings.getTtlTicks(),
            material != null
                ? material.getFriction()
                : PhysicsChunkTerrainSettings.DEFAULT_CHUNK_COLLISION_FRICTION,
            material != null
                ? material.getRestitution()
                : PhysicsChunkTerrainSettings.DEFAULT_CHUNK_COLLISION_RESTITUTION,
            filter != null
                ? filter.getCollisionGroup()
                : PhysicsCollisionFilters.TERRAIN,
            filter != null
                ? filter.getCollisionMask()
                : PhysicsCollisionFilters.ALL);
    }

    @Nonnull
    private static PhysicsChunkTerrainStreamingResource streaming(@Nonnull World world) {
        Store<EntityStore> entityStore = Objects.requireNonNull(world, "world")
            .getEntityStore()
            .getStore();
        return entityStore.getResource(PhysicsChunkTerrainStreamingResource.getResourceType());
    }

    private static int clearSpaceRows(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        int removed = 0;
        if (isSubPluginEnabled()) {
            removed = streaming(world).clearSpace(spaceUuid,
                store.getResource(PhysicsChunkCollisionMutationQueueResource.getResourceType()));
        }
        int directlyRemoved =
            PhysicsStoreTopologyMutations.clearTerrainForSpace(store, spaceUuid);
        return removed != 0 ? removed : directlyRemoved;
    }

    @Nonnull
    private static PhysicsChunkTerrainBuildStats withRemovedBodies(
        @Nonnull PhysicsChunkTerrainBuildStats stats,
        int removedBodies) {
        return new PhysicsChunkTerrainBuildStats(stats.scannedBlocks(),
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
