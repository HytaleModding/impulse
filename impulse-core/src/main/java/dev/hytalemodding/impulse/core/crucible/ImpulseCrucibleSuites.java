package dev.hytalemodding.impulse.core.crucible;

/**
 * Entry point used by the core plugin to register optional Crucible suites.
 */
public final class ImpulseCrucibleSuites {

    private ImpulseCrucibleSuites() {
    }

    /**
     * Registers all Impulse suites using the classloader that loaded Crucible.
     *
     * @param crucibleLoader the classloader for the Crucible plugin
     * @throws ReflectiveOperationException if Crucible's API shape changed
     */
    public static void register(ClassLoader crucibleLoader)
        throws ReflectiveOperationException {

        CrucibleBridge bridge = CrucibleBridge.create(crucibleLoader);
        ImpulseApiCrucibleTests.register(bridge, crucibleLoader);
        ImpulseLiveCrucibleTests.register(bridge, crucibleLoader);
    }
}
