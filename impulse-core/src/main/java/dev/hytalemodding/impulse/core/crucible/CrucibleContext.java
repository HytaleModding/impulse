package dev.hytalemodding.impulse.core.crucible;

import com.hypixel.hytale.server.core.universe.world.World;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reflection wrapper around Crucible's TestContext.
 */
final class CrucibleContext {

    private static final long MILLIS_PER_TICK = 50L;
    private static final ScheduledExecutorService DELAYED_WORLD_CALLBACKS =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Impulse Crucible delayed callbacks");
            thread.setDaemon(true);
            return thread;
        });

    private final Object handle;

    CrucibleContext(Object handle) {
        this.handle = handle;
    }

    World world() throws ReflectiveOperationException {
        Method getWorld = handle.getClass().getMethod("getWorld");
        return (World) getWorld.invoke(handle);
    }

    int wx(int value) throws ReflectiveOperationException {
        return coordinate("wx", value);
    }

    int wy(int value) throws ReflectiveOperationException {
        return coordinate("wy", value);
    }

    int wz(int value) throws ReflectiveOperationException {
        return coordinate("wz", value);
    }

    @SuppressWarnings("unchecked")
    CompletionStage<Void> waitTicks(int ticks) throws ReflectiveOperationException {
        Method waitTicks = handle.getClass().getMethod("waitTicks", int.class);
        return (CompletionStage<Void>) waitTicks.invoke(handle, ticks);
    }

    /**
     * Waits approximately long enough for the test world to tick, then resumes on
     * the world thread with one queued task. Crucible's built-in waitTicks helper
     * requeues itself through World.execute while the same task queue is draining,
     * which prevents the world tick counter from advancing on current Hytale builds.
     */
    CompletionStage<Void> waitApproxTicksOnWorld(int ticks) throws ReflectiveOperationException {
        World world = world();
        CompletableFuture<Void> future = new CompletableFuture<>();
        long delayMillis = Math.max(1L, ticks) * MILLIS_PER_TICK;
        DELAYED_WORLD_CALLBACKS.schedule(() -> {
            try {
                world.execute(() -> future.complete(null));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        return future;
    }

    private int coordinate(String methodName, int value) throws ReflectiveOperationException {
        Method method = handle.getClass().getMethod(methodName, int.class);
        return (int) method.invoke(handle, value);
    }
}
