package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import dev.hytalemodding.impulse.core.internal.modules.SubPluginLifecycleGate;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;

/**
 * Server-level lifecycle controlled by the Impulse world-collision subplugin.
 */
public final class WorldCollisionLifecycle {

    private static final SubPluginLifecycleGate GATE =
        new SubPluginLifecycleGate("Impulse world collision subplugin is disabled");
    private static final Set<PhysicsWorldRuntimeResource> RESOURCES =
        Collections.newSetFromMap(new WeakHashMap<>());

    static {
        GATE.onDisable(WorldCollisionLifecycle::cleanupResources);
    }

    private WorldCollisionLifecycle() {
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

    public static void registerResource(@Nonnull PhysicsWorldRuntimeResource resource) {
        synchronized (RESOURCES) {
            RESOURCES.add(resource);
        }
    }

    private static void cleanupResources() {
        ArrayList<PhysicsWorldRuntimeResource> resources;
        synchronized (RESOURCES) {
            resources = new ArrayList<>(RESOURCES);
        }
        for (PhysicsWorldRuntimeResource resource : resources) {
            resource.disableWorldCollisionLifecycle();
        }
    }
}
