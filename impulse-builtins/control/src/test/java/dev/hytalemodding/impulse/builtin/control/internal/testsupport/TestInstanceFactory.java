package dev.hytalemodding.impulse.builtin.control.internal.testsupport;

import com.hypixel.hytale.server.core.universe.world.World;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.objenesis.ObjenesisStd;

/**
 * Allocates Hytale server classes whose public constructors bind to global server state.
 */
public final class TestInstanceFactory {

    private static final ObjenesisStd OBJENESIS = new ObjenesisStd();

    private TestInstanceFactory() {
    }

    @Nonnull
    public static World world(@Nonnull String worldName) {
        World world = allocate(World.class);
        setField(world, World.class, "name", worldName);
        return world;
    }

    @Nonnull
    private static <T> T allocate(@Nonnull Class<T> type) {
        T constructed = constructNoArg(type);
        if (constructed != null) {
            return constructed;
        }
        return constructWithoutInvokingConstructor(type);
    }

    @Nullable
    private static <T> T constructNoArg(@Nonnull Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to allocate test instance of " + type.getName(), exception);
        }
    }

    @Nonnull
    private static <T> T constructWithoutInvokingConstructor(@Nonnull Class<T> type) {
        try {
            return OBJENESIS.newInstance(type);
        } catch (RuntimeException exception) {
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
}
