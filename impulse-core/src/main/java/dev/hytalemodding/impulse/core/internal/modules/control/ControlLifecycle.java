package dev.hytalemodding.impulse.core.internal.modules.control;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.SubPluginLifecycleGate;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlSessionCleanup;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
    private static final Set<PhysicsWorldRuntimeResource> RESOURCES =
        Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Store<EntityStore>> STORES =
        Collections.newSetFromMap(new WeakHashMap<>());
    private static final long CLEANUP_TIMEOUT_SECONDS = 5L;

    static {
        GATE.onDisable(ControlLifecycle::cleanupStores);
        GATE.onDisable(ControlLifecycle::cleanupResources);
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

    public static long generation() {
        return GATE.generation();
    }

    public static void requireEnabled() {
        GATE.requireEnabled();
    }

    public static void registerResource(@Nonnull PhysicsWorldRuntimeResource resource) {
        synchronized (RESOURCES) {
            RESOURCES.add(resource);
        }
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
            cleanupStoreOnOwnerThread(store, controllableType, sessionType);
            return;
        }
        if (!world.isStarted()) {
            return;
        }

        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        try {
            world.execute(() -> {
                try {
                    cleanupStoreOnOwnerThread(store, controllableType, sessionType);
                    cleanup.complete(null);
                } catch (Throwable throwable) {
                    cleanup.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            if (isWorldTaskRejection(exception)) {
                return;
            }
            throw exception;
        }
        cleanup.orTimeout(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
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

    private static void cleanupStoreOnOwnerThread(@Nonnull Store<EntityStore> store,
        @Nullable ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nullable ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        if (sessionType != null) {
            PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
            store.forEachEntityParallel(sessionType,
                (index, archetypeChunk, commandBuffer) -> {
                    PhysicsControlSessionComponent session =
                        archetypeChunk.getComponent(index, sessionType);
                    if (session != null) {
                        PhysicsControlSessionCleanup.cleanupAndWait(store, resource, session);
                    }
                    commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index), sessionType);
                });
        }
        if (controllableType != null) {
            store.forEachEntityParallel(controllableType,
                (index, archetypeChunk, commandBuffer) ->
                    commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index),
                        controllableType));
        }
    }

    private static void cleanupResources() {
        ArrayList<PhysicsWorldRuntimeResource> resources;
        synchronized (RESOURCES) {
            resources = new ArrayList<>(RESOURCES);
        }
        for (PhysicsWorldRuntimeResource resource : resources) {
            resource.disableControlLifecycle();
        }
    }
}
