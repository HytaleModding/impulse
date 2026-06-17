package dev.hytalemodding.impulse.core.internal.modules.physicsentity;

import dev.hytalemodding.impulse.core.internal.modules.SubPluginLifecycleGate;

/**
 * Server-level lifecycle controlled by the Impulse PhysicsEntity subplugin.
 */
public final class PhysicsEntityLifecycle {

    public static final String DISABLED_MESSAGE =
        "Impulse PhysicsEntity integration is not available. "
            + "Enable HytaleModding:ImpulsePhysicsEntity to use EntityStore physics projections.";

    private static final SubPluginLifecycleGate GATE =
        new SubPluginLifecycleGate(DISABLED_MESSAGE);

    private PhysicsEntityLifecycle() {
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
}
