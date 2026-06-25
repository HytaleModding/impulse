package dev.hytalemodding.impulse.builtin.control.internal;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.SubPluginLifecycleGate;
import dev.hytalemodding.impulse.builtin.control.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.builtin.control.internal.systems.PhysicsControlSessionCleanup;
import dev.hytalemodding.impulse.builtin.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

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

    public static int cleanupStoreForExternalCleanup(@Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, ImpulseControllableComponent> controllableType =
            ImpulseControllableComponent.isComponentTypeRegistered()
                ? ImpulseControllableComponent.getComponentType()
                : null;
        ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType =
            PhysicsControlSessionComponent.isComponentTypeRegistered()
                ? PhysicsControlSessionComponent.getComponentType()
                : null;
        if (controllableType == null && sessionType == null) {
            return 0;
        }
        return cleanupStore(store, controllableType, sessionType);
    }

    public static int cleanupSelectedStoreForExternalCleanup(@Nonnull Store<EntityStore> store,
        @Nonnull Set<UUID> selectedBodyUuids,
        @Nonnull Vector3d center,
        double radiusSquared) {
        if (!PhysicsControlSessionComponent.isComponentTypeRegistered()) {
            return 0;
        }
        return cleanupSelectedStoreOnWorldThread(store,
            PhysicsControlSessionComponent.getComponentType(),
            selectedBodyUuids,
            center,
            radiusSquared);
    }

    private static int cleanupStore(@Nonnull Store<EntityStore> store,
        @Nullable ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nullable ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        World world = store.getExternalData().getWorld();
        if (world.isInThread()) {
            return cleanupStoreOnWorldThread(store, controllableType, sessionType);
        }
        if (!world.isStarted()) {
            return 0;
        }

        // PluginManager.unload holds the asset write lock while world ticks drain queued
        // tasks under the read lock, so waiting here would stall until the timeout.
        boolean waitForCleanup = !isAssetWriteLockHeldByCurrentThread();
        CompletableFuture<Integer> cleanup = waitForCleanup ? new CompletableFuture<>() : null;
        try {
            world.execute(() -> cleanupStoreSafely(store, controllableType, sessionType, cleanup));
        } catch (RuntimeException exception) {
            if (isWorldTaskRejection(exception)) {
                return 0;
            }
            throw exception;
        }

        if (cleanup != null) {
            return cleanup.orTimeout(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        }
        return 0;
    }

    private static boolean isAssetWriteLockHeldByCurrentThread() {
        ReadWriteLock lock = AssetRegistry.ASSET_LOCK;
        return lock instanceof ReentrantReadWriteLock reentrantLock
            && reentrantLock.isWriteLockedByCurrentThread();
    }

    private static void cleanupStoreSafely(@Nonnull Store<EntityStore> store,
        @Nullable ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nullable ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType,
        @Nullable CompletableFuture<Integer> completion) {
        try {
            int removedSessions = cleanupStoreOnWorldThread(store, controllableType, sessionType);
            if (completion != null) {
                completion.complete(removedSessions);
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

    private static int cleanupStoreOnWorldThread(@Nonnull Store<EntityStore> store,
        @Nullable ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nullable ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        int removedSessions = 0;
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
            removedSessions = sessions.size();
        }
        if (controllableType != null) {
            store.forEachEntityParallel(controllableType,
                (index, archetypeChunk, commandBuffer) ->
                    commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index),
                        controllableType));
        }
        return removedSessions;
    }

    private static int cleanupSelectedStoreOnWorldThread(@Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType,
        @Nonnull Set<UUID> selectedBodyUuids,
        @Nonnull Vector3d center,
        double radiusSquared) {
        ArrayList<SessionCleanupTarget> sessions = new ArrayList<>();
        store.forEachEntityParallel(sessionType,
            (index, archetypeChunk, commandBuffer) -> {
                PhysicsControlSessionComponent session =
                    archetypeChunk.getComponent(index, sessionType);
                if (session == null || !controlSessionSelected(commandBuffer,
                    archetypeChunk,
                    index,
                    session,
                    selectedBodyUuids,
                    center,
                    radiusSquared)) {
                    return;
                }
                SessionCleanupTarget target = new SessionCleanupTarget(
                    archetypeChunk.getReferenceTo(index),
                    session);
                synchronized (sessions) {
                    sessions.add(target);
                }
            });
        for (SessionCleanupTarget target : sessions) {
            PhysicsControlSessionCleanup.cleanup(store, target.session());
            store.removeComponent(target.ref(), sessionType);
        }
        return sessions.size();
    }

    private static boolean controlSessionSelected(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull PhysicsControlSessionComponent session,
        @Nonnull Set<UUID> selectedBodyUuids,
        @Nonnull Vector3d center,
        double radiusSquared) {
        if (containsBody(selectedBodyUuids, session.getBodyRef())
            || containsBody(selectedBodyUuids, session.getAnchorBodyRef())
            || entityWithinRadius(archetypeChunk, index, center, radiusSquared)) {
            return true;
        }

        Ref<EntityStore> targetRef = session.getTargetRef();
        if (targetRef == null || !targetRef.isValid()) {
            return false;
        }

        TransformComponent targetTransform =
            commandBuffer.getComponent(targetRef, TransformComponent.getComponentType());
        return targetTransform != null && targetTransform.getPosition().distanceSquared(center)
            <= radiusSquared;
    }

    private static boolean containsBody(@Nonnull Set<UUID> bodyUuids,
        @Nullable Ref<PhysicsStore> bodyRef) {
        UUID bodyUuid = rowUuid(bodyRef);
        return bodyUuid != null && bodyUuids.contains(bodyUuid);
    }

    @Nullable
    private static UUID rowUuid(@Nullable Ref<PhysicsStore> bodyRef) {
        if (bodyRef == null || !bodyRef.isValid()) {
            return null;
        }
        UuidComponent uuid = bodyRef.getStore().getComponent(bodyRef, UuidComponent.getComponentType());
        return uuid != null ? uuid.getUuid() : null;
    }

    private static boolean entityWithinRadius(@Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull Vector3d center,
        double radiusSquared) {
        TransformComponent transform =
            archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        return transform != null && transform.getPosition().distanceSquared(center) <= radiusSquared;
    }

    private record SessionCleanupTarget(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsControlSessionComponent session) {
    }
}
