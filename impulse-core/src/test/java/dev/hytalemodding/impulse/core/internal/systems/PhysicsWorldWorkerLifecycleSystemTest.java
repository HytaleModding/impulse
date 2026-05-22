package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class PhysicsWorldWorkerLifecycleSystemTest {

    @Test
    void storeAddAndRemoveStartAndCloseWorkerResource() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, PhysicsWorldWorkerResource> workerType =
            registry.registerResource(PhysicsWorldWorkerResource.class,
                PhysicsWorldWorkerResource::new);
        ResourceType<EntityStore, CountingPhysicsWorldResource> physicsType =
            registry.registerResource(CountingPhysicsWorldResource.class,
                CountingPhysicsWorldResource::new);
        PhysicsWorldWorkerLifecycleSystem lifecycle =
            new PhysicsWorldWorkerLifecycleSystem(workerType, physicsType);
        registry.registerSystem(lifecycle);

        Store<EntityStore> store = registry.addStore(null, EmptyResourceStorage.get());
        PhysicsWorldWorkerResource worker = store.getResource(workerType);
        CountingPhysicsWorldResource physics = store.getResource(physicsType);

        assertTrue(worker.isStarted());
        assertEquals(1, lifecycle.activeWorkerCount());

        registry.removeStore(store);

        assertTrue(worker.isClosed());
        assertEquals(1, physics.clearCalls);
        assertEquals(0, lifecycle.activeWorkerCount());
        lifecycle.close();
        registry.shutdown();
    }

    @Test
    void closeClosesTrackedWorkersAsShutdownFallback() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, PhysicsWorldWorkerResource> workerType =
            registry.registerResource(PhysicsWorldWorkerResource.class,
                PhysicsWorldWorkerResource::new);
        ResourceType<EntityStore, CountingPhysicsWorldResource> physicsType =
            registry.registerResource(CountingPhysicsWorldResource.class,
                CountingPhysicsWorldResource::new);
        PhysicsWorldWorkerLifecycleSystem lifecycle =
            new PhysicsWorldWorkerLifecycleSystem(workerType, physicsType);
        PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource();

        lifecycle.startWorker(worker, "fallback-world");
        assertTrue(worker.isStarted());
        assertEquals(1, lifecycle.activeWorkerCount());

        lifecycle.close();

        assertTrue(worker.isClosed());
        assertEquals(0, lifecycle.activeWorkerCount());
        registry.shutdown();
    }

    private static final class CountingPhysicsWorldResource extends PhysicsWorldResource {

        private int clearCalls;

        @Override
        public void clearAllSpaces(@Nonnull String worldName) {
            clearCalls++;
            super.clearAllSpaces(worldName);
        }
    }
}
