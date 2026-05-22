package dev.hytalemodding.impulse.core.internal.systems.step;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCommand;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Submits world physics steps to the physics worker without draining them on
 * the main tick.
 */
public class PhysicsStepSystem extends TickingSystem<ChunkStore> implements AutoCloseable {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private final AtomicLong nextStepSequence = new AtomicLong(1L);
    private volatile boolean closed;

    public PhysicsStepSystem() {
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store) {
        if (closed) {
            return;
        }

        var world = store.getExternalData().getWorld();
        var entityStore = world.getEntityStore().getStore();
        PhysicsWorldResource resource = entityStore.getResource(
            PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource profiling = entityStore.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        PhysicsWorldWorkerResource worker = entityStore.getResource(
            PhysicsWorldWorkerResource.getResourceType());
        if (worker.isClosed()) {
            return;
        }

        submitStepIfIdle(worker, resource, dt, profiling);
    }

    private void submitStepIfIdle(@Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull PhysicsWorldResource resource,
        float dt,
        @Nonnull PhysicsRuntimeProfilingResource profiling) {
        boolean profilingEnabled = profiling.isEnabled();
        long sequence = nextStepSequence.getAndIncrement();
        PhysicsWorkerStepCommand command = new PhysicsWorkerStepCommand(resource,
            dt,
            profilingEnabled,
            sequence,
            sequence);
        try {
            if (!worker.submitStepIfIdle(command) && profilingEnabled) {
                profiling.recordStepSkippedPending(worker.pendingStepAgeNanos());
            }
        } catch (RejectedExecutionException exception) {
            if (!worker.isClosed()) {
                LOGGER.at(Level.WARNING).log(
                    "Skipping async physics step because the worker path is unavailable: %s",
                    exception.getMessage());
            }
        }
    }
}
