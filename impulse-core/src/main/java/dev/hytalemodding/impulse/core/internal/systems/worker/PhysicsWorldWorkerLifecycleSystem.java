package dev.hytalemodding.impulse.core.internal.systems.worker;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Starts and stops the per-world physics worker with the EntityStore lifecycle.
 */
public final class PhysicsWorldWorkerLifecycleSystem extends StoreSystem<EntityStore>
    implements AutoCloseable {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    @Nonnull
    private final ResourceType<EntityStore, PhysicsWorldWorkerResource> workerResourceType;
    @Nonnull
    private final ResourceType<EntityStore, ? extends PhysicsWorldResource> physicsWorldResourceType;
    @Nonnull
    private final Set<PhysicsWorldWorkerResource> activeWorkers = ConcurrentHashMap.newKeySet();

    public PhysicsWorldWorkerLifecycleSystem() {
        this(PhysicsWorldWorkerResource.getResourceType(), PhysicsWorldResource.getResourceType());
    }

    PhysicsWorldWorkerLifecycleSystem(
        @Nonnull ResourceType<EntityStore, PhysicsWorldWorkerResource> workerResourceType,
        @Nonnull ResourceType<EntityStore, ? extends PhysicsWorldResource> physicsWorldResourceType) {
        this.workerResourceType = workerResourceType;
        this.physicsWorldResourceType = physicsWorldResourceType;
    }

    @Override
    public void onSystemAddedToStore(@Nonnull Store<EntityStore> store) {
        PhysicsWorldWorkerResource worker = store.getResource(workerResourceType);
        startWorker(worker, worldName(store));
        store.getResource(physicsWorldResourceType).attachWorkerResource(worker);
    }

    @Override
    public void onSystemRemovedFromStore(@Nonnull Store<EntityStore> store) {
        String worldName = worldName(store);
        PhysicsWorldWorkerResource worker = store.getResource(workerResourceType);
        PhysicsWorldResource physics = store.getResource(physicsWorldResourceType);
        boolean clearedSpaces = clearSpaces(physics, worldName);
        boolean closedWorker = closeWorker(worker);
        physics.detachWorkerResource(worker);

        // Retry if it failed before, this could happen.
        if (!clearedSpaces && closedWorker) {
            clearSpaces(physics, worldName);
        }
    }

    @Override
    public void close() {
        for (PhysicsWorldWorkerResource worker : new ArrayList<>(activeWorkers)) {
            closeWorker(worker);
        }
    }

    void startWorker(@Nonnull PhysicsWorldWorkerResource worker, @Nonnull String worldName) {
        try {
            worker.start(worldName);
            if (worker.isStarted()) {
                activeWorkers.add(worker);
            }
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log("Physics worker could not be started for world %s: %s",
                worldName,
                exception.getMessage());
        }
    }

    boolean closeWorker(@Nonnull PhysicsWorldWorkerResource worker) {
        activeWorkers.remove(worker);
        try {
            worker.close();
            return true;
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log("Failed to close physics worker runner: %s",
                exception.getMessage());
            return false;
        }
    }

    int activeWorkerCount() {
        return activeWorkers.size();
    }

    @Nonnull
    private static String worldName(@Nonnull Store<EntityStore> store) {
        return store.getExternalData().getWorld().getName();
    }

    private static boolean clearSpaces(@Nonnull PhysicsWorldResource physics,
        @Nonnull String worldName) {
        try {
            physics.clearAllSpaces(worldName);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log("Failed to clear physics spaces for world %s: %s",
                worldName,
                exception.getMessage());
            return false;
        }
    }
}
