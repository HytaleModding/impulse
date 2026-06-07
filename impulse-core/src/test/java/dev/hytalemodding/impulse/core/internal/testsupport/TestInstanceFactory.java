package dev.hytalemodding.impulse.core.internal.testsupport;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import sun.misc.Unsafe;

/**
 * Allocates Hytale server classes whose public constructors bind to global server or plugin state.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class TestInstanceFactory {

    private static final Unsafe UNSAFE = unsafe();

    private TestInstanceFactory() {
    }

    @Nonnull
    public static World world(@Nonnull String worldName) {
        World world = allocate(World.class);
        setField(world, World.class, "name", worldName);
        return world;
    }

    @Nonnull
    public static ImpulsePlugin impulsePlugin() {
        return allocate(ImpulsePlugin.class);
    }

    @Nonnull
    private static <T> T allocate(@Nonnull Class<T> type) {
        try {
            return type.cast(UNSAFE.allocateInstance(type));
        } catch (InstantiationException exception) {
            throw new AssertionError("Failed to allocate test instance of " + type.getName(), exception);
        }
    }

    private static void setField(@Nonnull Object target,
        @Nonnull Class<?> owner,
        @Nonnull String name,
        @Nonnull Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to set test field " + owner.getName() + "." + name, exception);
        }
    }

    @Nonnull
    private static Unsafe unsafe() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to access Unsafe for test instance allocation", exception);
        }
    }
}
