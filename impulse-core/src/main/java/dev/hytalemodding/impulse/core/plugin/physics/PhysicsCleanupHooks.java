package dev.hytalemodding.impulse.core.plugin.physics;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreCleanupHooks;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Cleanup extension points for optional Impulse plugins that own runtime state.
 */
public final class PhysicsCleanupHooks {

    @Nonnull
    private static final Set<EntityStoreCleanup> ENTITY_STORE_CLEANUPS =
        new CopyOnWriteArraySet<>();
    @Nonnull
    private static final Set<SelectedEntityStoreCleanup> SELECTED_ENTITY_STORE_CLEANUPS =
        new CopyOnWriteArraySet<>();
    @Nonnull
    private static final Set<ComponentType<EntityStore, ? extends Component<EntityStore>>>
        DETACHABLE_ENTITY_MARKERS = new CopyOnWriteArraySet<>();
    @Nonnull
    private static final ConcurrentMap<BodyRowCleanup, PhysicsStoreCleanupHooks.BodyRowCleanup>
        BODY_ROW_CLEANUP_ADAPTERS = new ConcurrentHashMap<>();

    private PhysicsCleanupHooks() {
    }

    public static void registerEntityStoreCleanup(@Nonnull EntityStoreCleanup cleanup) {
        ENTITY_STORE_CLEANUPS.add(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void unregisterEntityStoreCleanup(@Nonnull EntityStoreCleanup cleanup) {
        ENTITY_STORE_CLEANUPS.remove(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static int cleanupEntityStore(@Nonnull Store<EntityStore> store) {
        Objects.requireNonNull(store, "store");
        RuntimeException failure = null;
        int removed = 0;
        for (EntityStoreCleanup cleanup : ENTITY_STORE_CLEANUPS) {
            try {
                removed += cleanup.cleanup(store);
            } catch (RuntimeException exception) {
                failure = append(failure, exception);
            }
        }
        throwIfFailed(failure);
        return removed;
    }

    public static void registerSelectedEntityStoreCleanup(
        @Nonnull SelectedEntityStoreCleanup cleanup) {
        SELECTED_ENTITY_STORE_CLEANUPS.add(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void unregisterSelectedEntityStoreCleanup(
        @Nonnull SelectedEntityStoreCleanup cleanup) {
        SELECTED_ENTITY_STORE_CLEANUPS.remove(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static int cleanupSelectedEntityStore(@Nonnull Store<EntityStore> store,
        @Nonnull Set<UUID> selectedBodyUuids,
        @Nonnull Vector3d center,
        double radiusSquared) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(selectedBodyUuids, "selectedBodyUuids");
        Objects.requireNonNull(center, "center");
        RuntimeException failure = null;
        int removed = 0;
        for (SelectedEntityStoreCleanup cleanup : SELECTED_ENTITY_STORE_CLEANUPS) {
            try {
                removed += cleanup.cleanup(store, selectedBodyUuids, center, radiusSquared);
            } catch (RuntimeException exception) {
                failure = append(failure, exception);
            }
        }
        throwIfFailed(failure);
        return removed;
    }

    public static void registerDetachableEntityMarker(
        @Nonnull ComponentType<EntityStore, ? extends Component<EntityStore>> markerType) {
        DETACHABLE_ENTITY_MARKERS.add(Objects.requireNonNull(markerType, "markerType"));
    }

    public static void unregisterDetachableEntityMarker(
        @Nonnull ComponentType<EntityStore, ? extends Component<EntityStore>> markerType) {
        DETACHABLE_ENTITY_MARKERS.remove(Objects.requireNonNull(markerType, "markerType"));
    }

    @Nonnull
    public static List<ComponentType<EntityStore, ? extends Component<EntityStore>>>
        detachableEntityMarkers() {
        return List.copyOf(DETACHABLE_ENTITY_MARKERS);
    }

    public static void registerBodyRuntimeCleanup(
        @Nonnull Consumer<Store<PhysicsStore>> cleanup) {
        PhysicsStoreCleanupHooks.registerBodyRuntimeCleanup(cleanup);
    }

    public static void unregisterBodyRuntimeCleanup(
        @Nonnull Consumer<Store<PhysicsStore>> cleanup) {
        PhysicsStoreCleanupHooks.unregisterBodyRuntimeCleanup(cleanup);
    }

    public static void registerBodyRowCleanup(@Nonnull BodyRowCleanup cleanup) {
        BodyRowCleanup checkedCleanup = Objects.requireNonNull(cleanup, "cleanup");
        PhysicsStoreCleanupHooks.BodyRowCleanup adapter =
            (store, bodyUuid, bodyRef) -> checkedCleanup.cleanup(store, bodyUuid, bodyRef);
        PhysicsStoreCleanupHooks.BodyRowCleanup previous =
            BODY_ROW_CLEANUP_ADAPTERS.putIfAbsent(checkedCleanup, adapter);
        if (previous == null) {
            PhysicsStoreCleanupHooks.registerBodyRowCleanup(adapter);
        }
    }

    public static void unregisterBodyRowCleanup(@Nonnull BodyRowCleanup cleanup) {
        PhysicsStoreCleanupHooks.BodyRowCleanup adapter =
            BODY_ROW_CLEANUP_ADAPTERS.remove(Objects.requireNonNull(cleanup, "cleanup"));
        if (adapter != null) {
            PhysicsStoreCleanupHooks.unregisterBodyRowCleanup(adapter);
        }
    }

    @Nullable
    private static RuntimeException append(@Nullable RuntimeException failure,
        @Nonnull RuntimeException exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }

    private static void throwIfFailed(@Nullable RuntimeException failure) {
        if (failure != null) {
            throw failure;
        }
    }

    @FunctionalInterface
    public interface EntityStoreCleanup {

        int cleanup(@Nonnull Store<EntityStore> store);
    }

    @FunctionalInterface
    public interface SelectedEntityStoreCleanup {

        int cleanup(@Nonnull Store<EntityStore> store,
            @Nonnull Set<UUID> selectedBodyUuids,
            @Nonnull Vector3d center,
            double radiusSquared);
    }

    @FunctionalInterface
    public interface BodyRowCleanup {

        void cleanup(@Nonnull Store<PhysicsStore> store,
            @Nonnull UUID bodyUuid,
            @Nonnull Ref<PhysicsStore> bodyRef);
    }
}
