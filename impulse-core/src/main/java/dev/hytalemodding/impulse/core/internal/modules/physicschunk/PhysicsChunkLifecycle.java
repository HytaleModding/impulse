package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import dev.hytalemodding.impulse.core.internal.modules.SubPluginLifecycleGate;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nonnull;

/**
 * Server-level lifecycle controlled by the Impulse PhysicsChunk subplugin.
 */
public final class PhysicsChunkLifecycle {

    private static final SubPluginLifecycleGate GATE =
        new SubPluginLifecycleGate("Impulse PhysicsChunk subplugin is disabled");
    private static final Set<PhysicsWorldRuntimeResource> RESOURCES =
        Collections.newSetFromMap(new WeakHashMap<>());

    static {
        GATE.onDisable(PhysicsChunkLifecycle::cleanupResources);
    }

    private PhysicsChunkLifecycle() {
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
            resource.disablePhysicsChunkLifecycle();
        }
    }
}
