package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Access to the early-plugin-injected PhysicsStore on a Hytale world.
 */
public final class PhysicsStoreAccess {

    @Nonnull
    private static final MethodHandle WORLD_GET_PHYSICS_STORE = findWorldAccessor();

    private PhysicsStoreAccess() {
    }

    @Nonnull
    public static PhysicsStore require(@Nonnull World world) {
        Objects.requireNonNull(world, "world");
        try {
            return (PhysicsStore) WORLD_GET_PHYSICS_STORE.invoke(world);
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to access World.getPhysicsStore()", throwable);
        }
    }

    @Nonnull
    private static MethodHandle findWorldAccessor() {
        try {
            return MethodHandles.publicLookup()
                .findVirtual(World.class, "getPhysicsStore", MethodType.methodType(PhysicsStore.class));
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Impulse requires the PhysicsStore early plugin. "
                + "Install impulse-early-plugin as a Hytale early plugin before using PhysicsStore APIs.",
                exception);
        }
    }
}
