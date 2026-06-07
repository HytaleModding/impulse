package dev.hytalemodding.impulse.core.internal.systems.owner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.owner.TestPhysicsOwnerLane;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerHandle;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerLaneResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerLaneScheduler;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerCommand;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerMutationCompletion;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResult;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerStepCompletion;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class PhysicsOwnerLifecycleSystemTest {

    @Test
    void storeAddAndRemoveStartAndCloseOwnerResource() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, TestPhysicsOwnerLane> ownerLaneType =
            registry.registerResource(TestPhysicsOwnerLane.class,
                TestPhysicsOwnerLane::new);
        ResourceType<EntityStore, CountingPhysicsWorldResource> physicsType =
            registry.registerResource(CountingPhysicsWorldResource.class,
                CountingPhysicsWorldResource::new);
        PhysicsOwnerLifecycleSystem lifecycle =
            new PhysicsOwnerLifecycleSystem(ownerLaneType, physicsType);
        registry.registerSystem(lifecycle);

        Store<EntityStore> store = registry.addStore(testEntityStore("lifecycle-test"),
            EmptyResourceStorage.get());
        TestPhysicsOwnerLane owner = store.getResource(ownerLaneType);
        CountingPhysicsWorldResource physics = store.getResource(physicsType);

        assertTrue(owner.isStarted());
        assertEquals(1, lifecycle.activeOwnerCount());

        registry.removeStore(store);

        assertTrue(owner.isClosed());
        assertEquals(1, physics.clearCalls);
        assertEquals(0, lifecycle.activeOwnerCount());
        lifecycle.close();
        registry.shutdown();
    }

    @Test
    void storeAddAndRemoveAttachInterfaceBackedOwnerResource() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, PhysicsOwnerResource> ownerType =
            registry.registerResource(PhysicsOwnerResource.class,
                TestPhysicsOwnerLane::new);
        ResourceType<EntityStore, CountingPhysicsWorldResource> physicsType =
            registry.registerResource(CountingPhysicsWorldResource.class,
                CountingPhysicsWorldResource::new);
        PhysicsOwnerLifecycleSystem lifecycle =
            new PhysicsOwnerLifecycleSystem(ownerType, physicsType);
        registry.registerSystem(lifecycle);

        Store<EntityStore> store = registry.addStore(testEntityStore("owner-lifecycle-test"),
            EmptyResourceStorage.get());
        PhysicsOwnerResource owner = store.getResource(ownerType);
        CountingPhysicsWorldResource physics = store.getResource(physicsType);

        assertInstanceOf(TestPhysicsOwnerLane.class, owner);
        assertTrue(owner.isStarted());
        assertEquals(1, lifecycle.activeOwnerCount());
        assertTrue(physics.hasOwnerExecutor());

        registry.removeStore(store);

        assertTrue(owner.isClosed());
        assertEquals(1, physics.clearCalls);
        assertEquals(0, lifecycle.activeOwnerCount());
        assertTrue(physics.detachedOwnerPublication());
        lifecycle.close();
        registry.shutdown();
    }

    @Test
    void storeAddAndRemoveAttachPooledOwnerLaneResource() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        try (PhysicsOwnerLaneScheduler scheduler = new PhysicsOwnerLaneScheduler(1,
            4,
            Duration.ofSeconds(2L))) {
            ResourceType<EntityStore, PhysicsOwnerResource> ownerType =
                registry.registerResource(PhysicsOwnerResource.class, scheduler::createLane);
            ResourceType<EntityStore, CountingPhysicsWorldResource> physicsType =
                registry.registerResource(CountingPhysicsWorldResource.class,
                    CountingPhysicsWorldResource::new);
            PhysicsOwnerLifecycleSystem lifecycle =
                new PhysicsOwnerLifecycleSystem(ownerType, physicsType);
            registry.registerSystem(lifecycle);

            Store<EntityStore> store = registry.addStore(testEntityStore("owner-lane-test"),
                EmptyResourceStorage.get());
            PhysicsOwnerResource owner = store.getResource(ownerType);
            CountingPhysicsWorldResource physics = store.getResource(physicsType);

            assertInstanceOf(PhysicsOwnerLaneResource.class, owner);
            assertTrue(owner.isStarted());
            assertEquals(1, lifecycle.activeOwnerCount());
            assertTrue(physics.hasOwnerExecutor());

            registry.removeStore(store);

            assertTrue(owner.isClosed());
            assertEquals(1, physics.clearCalls);
            assertEquals(0, lifecycle.activeOwnerCount());
            assertTrue(physics.detachedOwnerPublication());
            lifecycle.close();
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void startFailureDoesNotAttachOwnerExecutor() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, FailingStartOwnerResource> ownerType =
            registry.registerResource(FailingStartOwnerResource.class,
                FailingStartOwnerResource::new);
        ResourceType<EntityStore, CountingPhysicsWorldResource> physicsType =
            registry.registerResource(CountingPhysicsWorldResource.class,
                CountingPhysicsWorldResource::new);
        PhysicsOwnerLifecycleSystem lifecycle =
            new PhysicsOwnerLifecycleSystem(ownerType, physicsType);
        registry.registerSystem(lifecycle);

        Store<EntityStore> store = registry.addStore(testEntityStore("start-failure-test"),
            EmptyResourceStorage.get());
        FailingStartOwnerResource owner = store.getResource(ownerType);
        CountingPhysicsWorldResource physics = store.getResource(physicsType);

        assertTrue(owner.startAttempted);
        assertEquals(0, lifecycle.activeOwnerCount());
        assertTrue(physics.canAccessLiveBackendDirectly());

        registry.removeStore(store);
        lifecycle.close();
        registry.shutdown();
    }

    @Test
    void closeClosesTrackedOwnersAsShutdownFallback() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, TestPhysicsOwnerLane> ownerLaneType =
            registry.registerResource(TestPhysicsOwnerLane.class,
                TestPhysicsOwnerLane::new);
        ResourceType<EntityStore, CountingPhysicsWorldResource> physicsType =
            registry.registerResource(CountingPhysicsWorldResource.class,
                CountingPhysicsWorldResource::new);
        PhysicsOwnerLifecycleSystem lifecycle =
            new PhysicsOwnerLifecycleSystem(ownerLaneType, physicsType);
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane()) {

            lifecycle.startOwner(owner, "fallback-world");
            assertTrue(owner.isStarted());
            assertEquals(1, lifecycle.activeOwnerCount());

            lifecycle.close();

            assertTrue(owner.isClosed());
            assertEquals(0, lifecycle.activeOwnerCount());
        }
        registry.shutdown();
    }

    @Test
    void removeStoreFallsBackToDirectClearWhenOwnerClearFails() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, TestPhysicsOwnerLane> ownerLaneType =
            registry.registerResource(TestPhysicsOwnerLane.class,
                TestPhysicsOwnerLane::new);
        ResourceType<EntityStore, FailingOncePhysicsWorldResource> physicsType =
            registry.registerResource(FailingOncePhysicsWorldResource.class,
                FailingOncePhysicsWorldResource::new);
        PhysicsOwnerLifecycleSystem lifecycle =
            new PhysicsOwnerLifecycleSystem(ownerLaneType, physicsType);
        registry.registerSystem(lifecycle);

        Store<EntityStore> store = registry.addStore(testEntityStore("fallback-test"),
            EmptyResourceStorage.get());
        TestPhysicsOwnerLane owner = store.getResource(ownerLaneType);
        FailingOncePhysicsWorldResource physics = store.getResource(physicsType);

        registry.removeStore(store);

        assertTrue(owner.isClosed());
        assertEquals(2, physics.clearCalls);
        assertEquals(0, lifecycle.activeOwnerCount());
        lifecycle.close();
        registry.shutdown();
    }

    private static final class CountingPhysicsWorldResource extends LegacyLiveHandleTestResource {

        private int clearCalls;
        private boolean detachedOwnerPublication;

        @Override
        public void clearAllSpaces(@Nonnull String worldName) {
            clearCalls++;
            super.clearAllSpaces(worldName);
        }

        private boolean hasOwnerExecutor() {
            return !canAccessLiveBackendDirectly();
        }

        private boolean detachedOwnerPublication() {
            return detachedOwnerPublication;
        }

        @Override
        public void detachOwnerExecutor(@Nonnull PhysicsOwnerHandle ownerExecutor) {
            super.detachOwnerExecutor(ownerExecutor);
            detachedOwnerPublication = canAccessLiveBackendDirectly();
        }
    }

    private static final class FailingOncePhysicsWorldResource extends LegacyLiveHandleTestResource {

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

    private static final class FailingStartOwnerResource implements PhysicsOwnerResource {

        private boolean startAttempted;
        private boolean closed;

        @Override
        public void start(@Nonnull String worldName) {
            startAttempted = true;
            throw new IllegalStateException("forced start failure");
        }

        @Override
        public boolean isStarted() {
            return false;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Nonnull
        @Override
        public PhysicsOwnerResult submitAndDrain(@Nonnull PhysicsOwnerCommand command)
            throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException("not started");
        }

        @Override
        public boolean submitStepIfIdle(@Nonnull PhysicsOwnerCommand command) {
            throw new UnsupportedOperationException("not started");
        }

        @Nonnull
        @Override
        public PhysicsMutationHandle<Void> submitMutation(@Nonnull String operation,
            @Nonnull PhysicsOwnerCommand command) {
            throw new UnsupportedOperationException("not started");
        }

        @Nonnull
        @Override
        public <T> PhysicsMutationHandle<T> submitMutation(@Nonnull String operation,
            @Nullable T value,
            @Nonnull PhysicsOwnerCommand command) {
            throw new UnsupportedOperationException("not started");
        }

        @Nonnull
        @Override
        public CompletableFuture<PhysicsOwnerResult> submitMutationFuture(
            @Nonnull String operation,
            @Nonnull PhysicsOwnerCommand command) {
            throw new UnsupportedOperationException("not started");
        }

        @Nonnull
        @Override
        public List<PhysicsOwnerMutationCompletion> pollCompletedMutations(int maxCompletions) {
            return List.of();
        }

        @Nullable
        @Override
        public PhysicsOwnerStepCompletion pollCompletedStep() {
            return null;
        }

        @Override
        public boolean hasPendingStep() {
            return false;
        }

        @Override
        public long pendingStepAgeNanos() {
            return 0L;
        }

        @Override
        public int pendingMutations() {
            return 0;
        }

        @Override
        public int pendingCommands() {
            return 0;
        }

        @Override
        public boolean isOwnerContext() {
            return false;
        }

        @Override
        public void run(@Nonnull String operation,
            @Nonnull PhysicsOwnerMutation mutation) {
            throw new UnsupportedOperationException("not started");
        }

        @Nonnull
        @Override
        public <T> PhysicsMutationHandle<T> enqueue(@Nonnull String operation,
            @Nullable T value,
            @Nonnull PhysicsOwnerMutation mutation) {
            throw new UnsupportedOperationException("not started");
        }

        @Nonnull
        @Override
        public <T> CompletableFuture<T> enqueueCall(@Nonnull String operation,
            @Nonnull PhysicsOwnerCallable<T> callable) {
            throw new UnsupportedOperationException("not started");
        }

        @Nonnull
        @Override
        public <T> T call(@Nonnull String operation,
            @Nonnull PhysicsOwnerCallable<T> callable) {
            throw new UnsupportedOperationException("not started");
        }

        @Nonnull
        @Override
        public PhysicsOwnerResource clone() {
            return new FailingStartOwnerResource();
        }
    }

    @Nonnull
    private static EntityStore testEntityStore(@Nonnull String worldName) {
        return new EntityStore(TestInstanceFactory.world(worldName));
    }
}
