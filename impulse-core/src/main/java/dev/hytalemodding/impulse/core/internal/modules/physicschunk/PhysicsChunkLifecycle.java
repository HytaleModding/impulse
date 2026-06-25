package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import dev.hytalemodding.impulse.core.internal.modules.SubPluginLifecycleGate;

/**
 * Server-level lifecycle controlled by the Impulse PhysicsChunk subplugin.
 */
public final class PhysicsChunkLifecycle {

    private static final SubPluginLifecycleGate GATE =
        new SubPluginLifecycleGate("Impulse PhysicsChunk subplugin is disabled");

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
}
