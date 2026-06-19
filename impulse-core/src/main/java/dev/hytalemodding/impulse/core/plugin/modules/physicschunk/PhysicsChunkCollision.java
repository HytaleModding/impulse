package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreTopologyMutations;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkSettingsIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkSettingsIndexResource.PhysicsChunkSpaceSettings;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Public PhysicsChunk operations for chunk-backed collision.
 */
public final class PhysicsChunkCollision {

    private PhysicsChunkCollision() {
    }

    public static boolean isSubPluginEnabled() {
        return PhysicsChunkLifecycle.isEnabled();
    }

    @Nonnull
    public static PhysicsChunkCollisionBuildStats rebuildAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "rebuild PhysicsChunk collision");
        return rebuildAroundChecked(world,
            checkedStore,
            requireSpaceRef(checkedStore, spaceId),
            center,
            radius);
    }

    @Nonnull
    public static PhysicsChunkCollisionBuildStats rebuildAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Vector3d center,
        int radius) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "rebuild PhysicsChunk collision");
        return rebuildAroundChecked(world, checkedStore, spaceRef, center, radius);
    }

    @Nonnull
    private static PhysicsChunkCollisionBuildStats rebuildAroundChecked(@Nonnull World world,
        @Nonnull Store<PhysicsStore> checkedStore,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Vector3d center,
        int radius) {
        PhysicsChunkSpaceSettings settings = requireSettings(checkedStore, spaceRef);
        PhysicsChunkCollisionMutationQueueResource queue = stampedQueue(checkedStore);
        int removed = clearSpaceChunkCollisionRows(world, checkedStore, settings.spaceUuid());
        PhysicsChunkCollisionPrewarmStats stats = streaming(world).ensureAround(world,
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
    public static PhysicsChunkCollisionBuildStats refreshAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "refresh PhysicsChunk collision");
        return refreshAroundChecked(world,
            checkedStore,
            requireSpaceRef(checkedStore, spaceId),
            center,
            radius);
    }

    @Nonnull
    public static PhysicsChunkCollisionBuildStats refreshAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Vector3d center,
        int radius) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "refresh PhysicsChunk collision");
        return refreshAroundChecked(world, checkedStore, spaceRef, center, radius);
    }

    @Nonnull
    private static PhysicsChunkCollisionBuildStats refreshAroundChecked(@Nonnull World world,
        @Nonnull Store<PhysicsStore> checkedStore,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Vector3d center,
        int radius) {
        PhysicsChunkSpaceSettings settings = requireSettings(checkedStore, spaceRef);
        return streaming(world).refreshAround(world,
            settings.spaceUuid(),
            stampedQueue(checkedStore),
            Objects.requireNonNull(center, "center"),
            radius,
            Math.max(0L, world.getTick()),
            null,
            settings.buildOptions());
    }

    @Nonnull
    public static PhysicsChunkCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "ensure PhysicsChunk collision");
        return ensureAroundChecked(world,
            checkedStore,
            requireSpaceRef(checkedStore, spaceId),
            centers,
            radius,
            tick);
    }

    @Nonnull
    public static PhysicsChunkCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        requireEnabled();
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "ensure PhysicsChunk collision");
        return ensureAroundChecked(world, checkedStore, spaceRef, centers, radius, tick);
    }

    @Nonnull
    private static PhysicsChunkCollisionPrewarmStats ensureAroundChecked(@Nonnull World world,
        @Nonnull Store<PhysicsStore> checkedStore,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        PhysicsChunkSpaceSettings settings = requireSettings(checkedStore, spaceRef);
        return streaming(world).ensureAround(world,
            settings.spaceUuid(),
            stampedQueue(checkedStore),
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
            "clear PhysicsChunk collision");
        return clearSpaceChunkCollisionRows(world,
            checkedStore,
            requireSpaceUuid(checkedStore, requireSpaceRef(checkedStore, spaceId)));
    }

    public static int clearSpace(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Store<PhysicsStore> checkedStore = requireMatchingWorldThread(world,
            store,
            "clear PhysicsChunk collision");
        return clearSpaceChunkCollisionRows(world, checkedStore, requireSpaceUuid(checkedStore,
            spaceRef));
    }

    @Nonnull
    public static PhysicsChunkCollisionStats stats(@Nonnull World world) {
        Objects.requireNonNull(world, "world");
        if (!world.isInThread()) {
            throw new IllegalStateException("Cannot read PhysicsChunk collision stats "
                + "outside the owning world thread");
        }
        return isSubPluginEnabled()
            ? streaming(world).stats()
            : new PhysicsChunkCollisionStats(0, 0, 0, 0);
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
    private static PhysicsChunkSpaceSettings requireSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Ref<PhysicsStore> checkedRef = requireValidSpaceRef(store, spaceRef);
        UUID spaceUuid = requireSpaceUuid(store, checkedRef);
        ChunkCollisionSettingsComponent component =
            store.getComponent(checkedRef, ChunkCollisionSettingsComponent.getComponentType());
        ChunkCollisionSettingsComponent settings =
            component != null ? component : new ChunkCollisionSettingsComponent();
        if (settings.getMode() == PhysicsChunkCollisionMode.NONE) {
            throw new IllegalStateException("PhysicsChunk collision is disabled for space "
                + spaceUuid);
        }
        return new PhysicsChunkSpaceSettings(spaceUuid,
            settings.getMode(),
            settings.getEntityChunkBoundaryMode(),
            settings.isNativeVoxelCollisionEnabled(),
            settings.getRadius(),
            settings.getBodyRadius(),
            settings.getTtlTicks());
    }

    @Nonnull
    private static Ref<PhysicsStore> requireSpaceRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        SpaceId checkedSpaceId = Objects.requireNonNull(spaceId, "spaceId");
        Ref<PhysicsStore> ref = PhysicsSpaces.resolveRef(store, checkedSpaceId);
        if (ref != null) {
            return ref;
        }
        if (PhysicsSpaces.hasSpace(store, checkedSpaceId)) {
            throw new IllegalStateException("PhysicsStore space id=" + checkedSpaceId.value()
                + " is not bound yet");
        }
        throw new IllegalArgumentException("PhysicsStore space id=" + checkedSpaceId.value()
            + " does not exist");
    }

    @Nonnull
    private static Ref<PhysicsStore> requireValidSpaceRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Ref<PhysicsStore> checkedRef = Objects.requireNonNull(spaceRef, "spaceRef");
        if (checkedRef.getStore() != store || !checkedRef.isValid()) {
            throw new IllegalArgumentException("PhysicsStore space entity is not valid: "
                + checkedRef);
        }
        if (store.getComponent(checkedRef, SpaceComponent.getComponentType()) == null) {
            throw new IllegalArgumentException("PhysicsStore entity is not a space entity: "
                + checkedRef);
        }
        return checkedRef;
    }

    @Nonnull
    private static UUID requireSpaceUuid(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Ref<PhysicsStore> checkedRef = requireValidSpaceRef(store, spaceRef);
        UuidComponent uuid = store.getComponent(checkedRef, UuidComponent.getComponentType());
        if (uuid == null) {
            throw new IllegalStateException("PhysicsStore space entity has no UUID: "
                + checkedRef);
        }
        return uuid.getUuid();
    }

    @Nonnull
    private static PhysicsChunkCollisionStreamingResource streaming(@Nonnull World world) {
        Store<EntityStore> entityStore = Objects.requireNonNull(world, "world")
            .getEntityStore()
            .getStore();
        return entityStore.getResource(PhysicsChunkCollisionStreamingResource.getResourceType());
    }

    private static int clearSpaceChunkCollisionRows(@Nonnull World world,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        int removed = 0;
        if (isSubPluginEnabled()) {
            removed = streaming(world).clearSpace(spaceUuid, stampedQueue(store));
        }
        int directlyRemoved =
            PhysicsStoreTopologyMutations.clearChunkCollisionRowsForSpace(store, spaceUuid);
        return removed != 0 ? removed : directlyRemoved;
    }

    @Nonnull
    private static PhysicsChunkCollisionMutationQueueResource stampedQueue(
        @Nonnull Store<PhysicsStore> store) {
        PhysicsChunkCollisionMutationQueueResource queue = store.getResource(
            PhysicsChunkCollisionMutationQueueResource.getResourceType());
        PhysicsChunkSettingsIndexResource settingsIndex = store.getResource(
            PhysicsChunkSettingsIndexResource.getResourceType());
        queue.updateStamp(PhysicsChunkLifecycle.generation(), settingsIndex.generation());
        return queue;
    }

    @Nonnull
    private static PhysicsChunkCollisionBuildStats withRemovedBodies(
        @Nonnull PhysicsChunkCollisionBuildStats stats,
        int removedBodies) {
        return new PhysicsChunkCollisionBuildStats(stats.scannedBlocks(),
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
