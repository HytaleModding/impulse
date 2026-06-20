package dev.hytalemodding.impulse.core.internal.modules.control;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.SubPluginLifecycleGate;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlSessionCleanup;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Server-level lifecycle controlled by the Impulse control subplugin.
 */
public final class ControlLifecycle {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final SubPluginLifecycleGate GATE = new SubPluginLifecycleGate(
        "Impulse control is disabled. Enable HytaleModding:ImpulseControl to start control sessions.");
    private static final Set<Store<EntityStore>> STORES =
        Collections.newSetFromMap(new WeakHashMap<>());
    private static final long CLEANUP_TIMEOUT_SECONDS = 5L;

    static {
        GATE.onDisable(ControlLifecycle::cleanupStores);
        GATE.onDisable(PhysicsControlRuntimeStates::clearAll);
    }

    private ControlLifecycle() {
    }

    public static void enable() {
        GATE.enable();
    }

    public static void disable() {
        GATE.disable();
    }

    public static boolean isEnabled() {
        return GATE.isEnabled();
    }

    public static void requireEnabled() {
        GATE.requireEnabled();
    }

    public static void registerStore(@Nonnull Store<EntityStore> store) {
        synchronized (STORES) {
            STORES.add(store);
        }
    }

    private static void cleanupStores() {
        ComponentType<EntityStore, ImpulseControllableComponent> controllableType =
            ImpulseControllableComponent.isComponentTypeRegistered()
                ? ImpulseControllableComponent.getComponentType()
                : null;
        ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType =
            PhysicsControlSessionComponent.isComponentTypeRegistered()
                ? PhysicsControlSessionComponent.getComponentType()
                : null;
        if (controllableType == null && sessionType == null) {
            return;
        }
        ArrayList<Store<EntityStore>> stores;
        synchronized (STORES) {
            stores = new ArrayList<>(STORES);
        }
        for (Store<EntityStore> store : stores) {
            try {
                cleanupStore(store, controllableType, sessionType);
            } catch (RuntimeException exception) {
                LOGGER.at(Level.WARNING).log("Failed to clean Impulse control components: %s",
                    exception.getMessage());
            }
        }
    }

    private static void cleanupStore(@Nonnull Store<EntityStore> store,
        @Nullable ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nullable ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        World world = store.getExternalData().getWorld();
        if (world.isInThread()) {
            cleanupStoreOnWorldThread(store, controllableType, sessionType);
            return;
        }
        if (!world.isStarted()) {
            return;
        }

        // PluginManager.unload holds the asset write lock while world ticks drain queued
        // tasks under the read lock, so waiting here would stall until the timeout.
        boolean waitForCleanup = !isAssetWriteLockHeldByCurrentThread();
        CompletableFuture<Void> cleanup = waitForCleanup ? new CompletableFuture<>() : null;
        try {
            world.execute(() -> cleanupStoreSafely(store, controllableType, sessionType, cleanup));
        } catch (RuntimeException exception) {
            if (isWorldTaskRejection(exception)) {
                return;
            }
            throw exception;
        }

        if (cleanup != null) {
            cleanup.orTimeout(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        }
    }

    private static boolean isAssetWriteLockHeldByCurrentThread() {
        ReadWriteLock lock = AssetRegistry.ASSET_LOCK;
        return lock instanceof ReentrantReadWriteLock reentrantLock
            && reentrantLock.isWriteLockedByCurrentThread();
    }

    private static void cleanupStoreSafely(@Nonnull Store<EntityStore> store,
        @Nullable ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nullable ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType,
        @Nullable CompletableFuture<Void> completion) {
        try {
            cleanupStoreOnWorldThread(store, controllableType, sessionType);
            if (completion != null) {
                completion.complete(null);
            }
        } catch (RuntimeException exception) {
            if (completion != null) {
                completion.completeExceptionally(exception);
            } else {
                LOGGER.at(Level.WARNING).log("Failed to clean Impulse control components: %s",
                    exception.getMessage());
            }
        }
    }

    private static boolean isWorldTaskRejection(@Nonnull RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof IllegalThreadStateException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void cleanupStoreOnWorldThread(@Nonnull Store<EntityStore> store,
        @Nullable ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nullable ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        if (sessionType != null) {
            ArrayList<SessionCleanupTarget> sessions = new ArrayList<>();
            store.forEachEntityParallel(sessionType,
                (index, archetypeChunk, _) -> {
                    PhysicsControlSessionComponent session =
                        archetypeChunk.getComponent(index, sessionType);
                    if (session != null) {
                        SessionCleanupTarget target = new SessionCleanupTarget(
                            archetypeChunk.getReferenceTo(index),
                            session);
                        synchronized (sessions) {
                            sessions.add(target);
                        }
                    }
                });
            for (SessionCleanupTarget target : sessions) {
                PhysicsControlSessionCleanup.cleanup(store, target.session());
                store.removeComponent(target.ref(), sessionType);
            }
        }
        if (controllableType != null) {
            store.forEachEntityParallel(controllableType,
                (index, archetypeChunk, commandBuffer) ->
                    commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index),
                        controllableType));
        }
    }

    private record SessionCleanupTarget(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsControlSessionComponent session) {
    }
}
