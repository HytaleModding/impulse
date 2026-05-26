package dev.hytalemodding.impulse.core.internal.systems.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

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

        Store<EntityStore> store = registry.addStore(testEntityStore("lifecycle-test"),
            EmptyResourceStorage.get());
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
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource()) {

            lifecycle.startWorker(worker, "fallback-world");
            assertTrue(worker.isStarted());
            assertEquals(1, lifecycle.activeWorkerCount());

            lifecycle.close();

            assertTrue(worker.isClosed());
            assertEquals(0, lifecycle.activeWorkerCount());
        }
        registry.shutdown();
    }

    @Test
    void removeStoreFallsBackToDirectClearWhenWorkerClearFails() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, PhysicsWorldWorkerResource> workerType =
            registry.registerResource(PhysicsWorldWorkerResource.class,
                PhysicsWorldWorkerResource::new);
        ResourceType<EntityStore, FailingOncePhysicsWorldResource> physicsType =
            registry.registerResource(FailingOncePhysicsWorldResource.class,
                FailingOncePhysicsWorldResource::new);
        PhysicsWorldWorkerLifecycleSystem lifecycle =
            new PhysicsWorldWorkerLifecycleSystem(workerType, physicsType);
        registry.registerSystem(lifecycle);

        Store<EntityStore> store = registry.addStore(testEntityStore("fallback-test"),
            EmptyResourceStorage.get());
        PhysicsWorldWorkerResource worker = store.getResource(workerType);
        FailingOncePhysicsWorldResource physics = store.getResource(physicsType);

        registry.removeStore(store);

        assertTrue(worker.isClosed());
        assertEquals(2, physics.clearCalls);
        assertEquals(0, lifecycle.activeWorkerCount());
        lifecycle.close();
        registry.shutdown();
    }

    private static final class CountingPhysicsWorldResource extends PhysicsWorldRuntimeResource {

        private int clearCalls;

        @Override
        public void clearAllSpaces(@Nonnull String worldName) {
            clearCalls++;
            super.clearAllSpaces(worldName);
        }
    }

    private static final class FailingOncePhysicsWorldResource extends PhysicsWorldRuntimeResource {

        private int clearCalls;

        @Override
        public void clearAllSpaces(@Nonnull String worldName) {
            clearCalls++;
            if (clearCalls == 1) {
                throw new IllegalStateException("forced clear failure");
            }
            super.clearAllSpaces(worldName);
        }
    }

    @Nonnull
    private static EntityStore testEntityStore(@Nonnull String worldName) {
        return new EntityStore(testWorld(worldName));
    }

    @Nonnull
    private static World testWorld(@Nonnull String worldName) {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            World world = (World) unsafe.allocateInstance(World.class);
            Field nameField = World.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(world, worldName);
            return world;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to create test world", exception);
        }
    }
}
