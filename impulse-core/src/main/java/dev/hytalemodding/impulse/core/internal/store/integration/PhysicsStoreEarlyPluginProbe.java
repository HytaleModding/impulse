package dev.hytalemodding.impulse.core.internal.store.integration;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.system.WorldConfigSaveSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;

/**
 * Reflection-only readiness check for the PhysicsStore early plugin transform.
 */
public final class PhysicsStoreEarlyPluginProbe {

    private static final String PHYSICS_STORE_CLASS =
        "com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore";
    private static final String WORLD_LIFECYCLE_MARKER =
        "impulse$physicsStoreLifecyclePatched";
    private static final String WORLD_RESOURCE_SAVE_MARKER =
        "impulse$physicsStoreResourceSavePatched";

    private PhysicsStoreEarlyPluginProbe() {
    }

    public static void requireAvailable() {
        Class<?> physicsStoreClass = requireClass(PHYSICS_STORE_CLASS);
        requireMethod(PluginBase.class, "getPhysicsStoreRegistry");
        Method worldAccessor = requireMethod(World.class, "getPhysicsStore");
        if (!worldAccessor.getReturnType().equals(physicsStoreClass)) {
            throw new IllegalStateException("Impulse PhysicsStore early plugin installed an "
                + "unexpected World.getPhysicsStore() return type: "
                + worldAccessor.getReturnType().getName());
        }
        requireField(World.class, WORLD_LIFECYCLE_MARKER);
        requireField(WorldConfigSaveSystem.class, WORLD_RESOURCE_SAVE_MARKER);
    }

    @Nonnull
    private static Class<?> requireClass(@Nonnull String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw unavailable(exception);
        }
    }

    @Nonnull
    private static Method requireMethod(@Nonnull Class<?> owner, @Nonnull String methodName) {
        try {
            return owner.getMethod(methodName);
        } catch (NoSuchMethodException exception) {
            throw unavailable(exception);
        }
    }

    private static void requireField(@Nonnull Class<?> owner, @Nonnull String fieldName) {
        try {
            owner.getDeclaredField(fieldName);
        } catch (NoSuchFieldException exception) {
            throw unavailable(exception);
        }
    }

    @Nonnull
    private static IllegalStateException unavailable(@Nonnull ReflectiveOperationException cause) {
        return new IllegalStateException("Impulse requires the PhysicsStore early plugin. "
            + "Install impulse-early-plugin as a Hytale early plugin and start the server with "
            + "--accept-early-plugins.", cause);
    }
}
