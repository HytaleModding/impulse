package dev.hytalemodding.impulse.core.internal.physics;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Module-owned PhysicsStore cleanup callbacks.
 */
public final class PhysicsStoreCleanupHooks {

    @Nonnull
    private static final Set<Consumer<Store<PhysicsStore>>> FULL_STORE_CLEANUPS =
        new CopyOnWriteArraySet<>();
    @Nonnull
    private static final Set<Consumer<Store<PhysicsStore>>> BODY_RUNTIME_CLEANUPS =
        new CopyOnWriteArraySet<>();
    @Nonnull
    private static final Set<SpaceCleanup> SPACE_CLEANUPS = new CopyOnWriteArraySet<>();
    @Nonnull
    private static final Set<BodyRowCleanup> BODY_ROW_CLEANUPS = new CopyOnWriteArraySet<>();

    private PhysicsStoreCleanupHooks() {
    }

    public static void registerFullStoreCleanup(
        @Nonnull Consumer<Store<PhysicsStore>> cleanup) {
        FULL_STORE_CLEANUPS.add(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void unregisterFullStoreCleanup(
        @Nonnull Consumer<Store<PhysicsStore>> cleanup) {
        FULL_STORE_CLEANUPS.remove(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void registerBodyRuntimeCleanup(
        @Nonnull Consumer<Store<PhysicsStore>> cleanup) {
        BODY_RUNTIME_CLEANUPS.add(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void unregisterBodyRuntimeCleanup(
        @Nonnull Consumer<Store<PhysicsStore>> cleanup) {
        BODY_RUNTIME_CLEANUPS.remove(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void registerSpaceCleanup(@Nonnull SpaceCleanup cleanup) {
        SPACE_CLEANUPS.add(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void unregisterSpaceCleanup(@Nonnull SpaceCleanup cleanup) {
        SPACE_CLEANUPS.remove(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void registerBodyRowCleanup(@Nonnull BodyRowCleanup cleanup) {
        BODY_ROW_CLEANUPS.add(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void unregisterBodyRowCleanup(@Nonnull BodyRowCleanup cleanup) {
        BODY_ROW_CLEANUPS.remove(Objects.requireNonNull(cleanup, "cleanup"));
    }

    public static void clearFullStoreRuntimeResources(@Nonnull Store<PhysicsStore> store) {
        RuntimeException failure = null;
        for (Consumer<Store<PhysicsStore>> cleanup : FULL_STORE_CLEANUPS) {
            failure = run(failure, () -> cleanup.accept(store));
        }
        throwIfFailed(failure);
    }

    static void clearBodyRuntimeResources(@Nonnull Store<PhysicsStore> store) {
        RuntimeException failure = null;
        for (Consumer<Store<PhysicsStore>> cleanup : BODY_RUNTIME_CLEANUPS) {
            failure = run(failure, () -> cleanup.accept(store));
        }
        throwIfFailed(failure);
    }

    static void cleanupSpaceRuntimeResources(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        RuntimeException failure = null;
        for (SpaceCleanup cleanup : SPACE_CLEANUPS) {
            failure = run(failure, () -> cleanup.cleanup(store, spaceUuid));
        }
        throwIfFailed(failure);
    }

    static void cleanupBodyRowRuntimeResources(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        RuntimeException failure = null;
        for (BodyRowCleanup cleanup : BODY_ROW_CLEANUPS) {
            failure = run(failure, () -> cleanup.cleanup(store, bodyUuid, bodyRef));
        }
        throwIfFailed(failure);
    }

    @Nullable
    private static RuntimeException run(@Nullable RuntimeException failure,
        @Nonnull Runnable action) {
        try {
            action.run();
            return failure;
        } catch (RuntimeException exception) {
            if (failure == null) {
                return exception;
            }
            failure.addSuppressed(exception);
            return failure;
        }
    }

    private static void throwIfFailed(@Nullable RuntimeException failure) {
        if (failure != null) {
            throw failure;
        }
    }

    @FunctionalInterface
    public interface SpaceCleanup {

        void cleanup(@Nonnull Store<PhysicsStore> store, @Nonnull UUID spaceUuid);
    }

    @FunctionalInterface
    public interface BodyRowCleanup {

        void cleanup(@Nonnull Store<PhysicsStore> store,
            @Nonnull UUID bodyUuid,
            @Nonnull Ref<PhysicsStore> bodyRef);
    }
}
