package dev.hytalemodding.impulse.core.internal.modules.control;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.SubPluginLifecycleGate;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlSessionCleanup;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;

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
        if (!PhysicsControlSessionComponent.isComponentTypeRegistered()) {
            return;
        }
        ArrayList<Store<EntityStore>> stores;
        synchronized (STORES) {
            stores = new ArrayList<>(STORES);
        }
        ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType =
            PhysicsControlSessionComponent.getComponentType();
        for (Store<EntityStore> store : stores) {
            try {
                PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
                store.forEachEntityParallel(sessionType,
                    (index, archetypeChunk, commandBuffer) -> {
                        PhysicsControlSessionComponent session =
                            archetypeChunk.getComponent(index, sessionType);
                        if (session != null) {
                            PhysicsControlSessionCleanup.cleanup(resource, session);
                        }
                        commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index), sessionType);
                    });
            } catch (RuntimeException exception) {
                LOGGER.at(Level.WARNING).log("Failed to clean Impulse control sessions: %s",
                    exception.getMessage());
            }
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
