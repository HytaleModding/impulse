package dev.hytalemodding.impulse.core.internal.systems.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.TestPhysicsOwnerLane;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResource;
import dev.hytalemodding.impulse.core.internal.systems.collision.PhysicsChunkBoundarySystem;
import dev.hytalemodding.impulse.core.internal.systems.collision.PhysicsCollisionLodSystem;
import dev.hytalemodding.impulse.core.internal.systems.collision.PhysicsWorldCollisionStreamingSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsWorldSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsDetachedVisualMaterializationSystem;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerBridge;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerStepCommand;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsSnapshotPublicationSystemTest {

    @Test
    void publicationDependenciesKeepReadersBehindSnapshotApply() {
        Set<Dependency<EntityStore>> dependencies =
            new PhysicsSnapshotPublicationSystem().getDependencies();

        assertSystemDependency(dependencies, Order.BEFORE, PhysicsCollisionLodSystem.class);
        assertSystemDependency(dependencies, Order.BEFORE, PhysicsChunkBoundarySystem.class);
        assertSystemDependency(dependencies, Order.BEFORE, PhysicsWorldCollisionStreamingSystem.class);
        assertSystemDependency(dependencies,
            Order.BEFORE,
            PhysicsDetachedVisualMaterializationSystem.class);
        assertSystemDependency(dependencies, Order.BEFORE, PhysicsSyncSystem.class);
        assertSystemDependency(new PersistentPhysicsWorldSyncSystem().getDependencies(),
            Order.AFTER,
            PhysicsSnapshotPublicationSystem.class);
    }

    @Test
    void completedOwnerStepPublishesSnapshotWithoutDrain() throws Exception {
        BackendId backendId = new BackendId("test:async-publication");
        Impulse.registerBackend(new FakePhysicsBackend(backendId));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        var settings = resource.getWorldSettings();
        settings.setStepMode(PhysicsStepMode.FIXED);
        settings.setSimulationSteps(1);
        settings.setEventCollectionMode(PhysicsEventCollectionMode.CONTACTS);
        resource.setWorldSettings(settings);
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        profiling.setEnabled(true);

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane()) {
            owner.start("async-publication-test");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backendId,
                "async-publication-test",
                PhysicsSpaceSettings.defaults());
            FakePhysicsBackend.InMemoryPhysicsSpace inMemorySpace =
                (FakePhysicsBackend.InMemoryPhysicsSpace) space;
            AtomicReference<PhysicsBody> bodyRef = new AtomicReference<>();
            RigidBodyKey bodyId = PhysicsOwnerBridge.call(owner,
                "create async publication body",
                () -> {
                    PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                    body.setPosition(1.0f, 2.0f, 3.0f);
                    bodyRef.set(body);
                    return resource.addBody(space.id(),
                        body,
                        PhysicsBodyKind.BODY,
                        PhysicsBodyPersistenceMode.RUNTIME_ONLY);
                });
            PhysicsBody secondBody = PhysicsOwnerBridge.call(owner,
                "create async publication contact body",
                () -> {
                    PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                    resource.addBody(space.id(),
                        body,
                        PhysicsBodyKind.BODY,
                        PhysicsBodyPersistenceMode.RUNTIME_ONLY);
                    return body;
                });
            inMemorySpace.addContact(new PhysicsContact(bodyRef.get(),
                secondBody,
                new Vector3f(1.0f, 2.0f, 3.0f),
                new Vector3f(4.0f, 5.0f, 6.0f),
                new Vector3f(0.0f, 1.0f, 0.0f),
                -0.125f,
                2.5f));
            assertEquals(new Vector3f(1.0f, 2.0f, 3.0f),
                resource.getBodySnapshot(bodyId).position());

            PhysicsOwnerBridge.run(owner, "move async publication body",
                () -> bodyRef.get().setPosition(4.0f, 5.0f, 6.0f));
            assertEquals(new Vector3f(1.0f, 2.0f, 3.0f),
                resource.getBodySnapshot(bodyId).position());

            PhysicsOwnerStepCommand command = new PhysicsOwnerStepCommand(resource,
                0.05f,
                true,
                1L,
                1L);
            assertTrue(owner.submitStepIfIdle(command));
            assertFalse(owner.submitStepIfIdle(new PhysicsOwnerStepCommand(resource,
                0.05f,
                false,
                2L,
                2L)));

            PhysicsEventFrame eventFrame = publishWhenReady(owner, resource, profiling);

            PhysicsBodySnapshot snapshot = resource.getBodySnapshot(bodyId);
            assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), snapshot.position());
            assertEquals(1, eventFrame.physicsEventCount());
            assertEquals(1, profiling.getCumulativeStep().getTickSamples());
            assertEquals(2, profiling.getCumulativeStep().getBodySnapshots());
            assertTrue(profiling.getCumulativeStep().getOwnerRunNanos() > 0L);
            assertTrue(profiling.getCumulativeStep().getOwnerQueuedNanos() >= 0L);
            assertFalse(owner.hasPendingStep());
        }
    }

    @Test
    void publicationSystemDrainsCompletedAsyncMutations() throws Exception {
        AtomicInteger mutations = new AtomicInteger();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane()) {
            owner.start("async-mutation-publication-test");
            owner.submitMutation("test mutation", () -> {
                mutations.incrementAndGet();
                return PhysicsOwnerSnapshot.empty();
            });

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            int published = 0;
            while (System.nanoTime() < deadline && published == 0) {
                published += PhysicsSnapshotPublicationSystem.publishCompletedMutations(owner);
                if (published == 0) {
                    Thread.sleep(10L);
                }
            }

            assertEquals(1, published);
            assertEquals(1, mutations.get());
            assertEquals(0, owner.pendingMutations());
        }
    }

    @Test
    void publicationSystemCapsCompletedAsyncMutationDrainPerTick() throws Exception {
        AtomicInteger mutations = new AtomicInteger();
        int mutationCount = 80;
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane()) {
            owner.start("async-mutation-publication-cap-test");
            for (int index = 0; index < mutationCount; index++) {
                owner.submitMutation("test mutation " + index, () -> {
                    mutations.incrementAndGet();
                    return PhysicsOwnerSnapshot.empty();
                });
            }

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
            while (System.nanoTime() < deadline && owner.pendingCommands() > 0) {
                Thread.sleep(10L);
            }
            assertEquals(0, owner.pendingCommands());
            assertEquals(mutationCount, mutations.get());

            int firstTickPublished = PhysicsSnapshotPublicationSystem.publishCompletedMutations(owner);

            assertEquals(64, firstTickPublished);
            assertEquals(mutationCount - 64, owner.pendingMutations());
            assertEquals(mutationCount - 64,
                PhysicsSnapshotPublicationSystem.publishCompletedMutations(owner));
            assertEquals(0, owner.pendingMutations());
        }
    }

    @Test
    void completedOwnerStepDoesNotRepublishAfterWorldMutation() throws Exception {
        BackendId backendId = new BackendId("test:stale-owner-publication");
        Impulse.registerBackend(new FakePhysicsBackend(backendId));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane()) {
            owner.start("stale-owner-publication-test");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backendId,
                "stale-owner-publication-test",
                PhysicsSpaceSettings.defaults());
            RigidBodyKey bodyId = PhysicsOwnerBridge.call(owner,
                "create stale publication body",
                () -> {
                    PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                    body.setPosition(1.0f, 2.0f, 3.0f);
                    return resource.addBody(space.id(),
                        body,
                        PhysicsBodyKind.BODY,
                        PhysicsBodyPersistenceMode.RUNTIME_ONLY);
                });

            PhysicsOwnerStepCommand command = new PhysicsOwnerStepCommand(resource,
                0.05f,
                false,
                1L,
                1L);
            assertTrue(owner.submitStepIfIdle(command));
            waitForPublishedFrame(command);
            assertEquals(1, command.publishedFrame().bodyCount());

            resource.destroyBody(bodyId);
            assertEquals(0, resource.getBodySnapshotCount());
            assertNull(resource.getBodyRegistrationView(bodyId));

            PhysicsEventFrame eventFrame =
                PhysicsSnapshotPublicationSystem.publishCompletedStep(owner, resource, profiling);

            assertNull(eventFrame);
            assertFalse(owner.hasPendingStep());
            assertEquals(0, resource.getBodySnapshotCount());
            assertNull(resource.getBodyRegistrationView(bodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    private static PhysicsEventFrame publishWhenReady(PhysicsOwnerResource owner,
        PhysicsWorldRuntimeResource resource,
        PhysicsRuntimeProfilingResource profiling) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        PhysicsEventFrame eventFrame = null;
        while (System.nanoTime() < deadline) {
            eventFrame = PhysicsSnapshotPublicationSystem.publishCompletedStep(owner, resource, profiling);
            if (!owner.hasPendingStep()) {
                return eventFrame;
            }
            Thread.sleep(10L);
        }
        eventFrame = PhysicsSnapshotPublicationSystem.publishCompletedStep(owner, resource, profiling);
        assertFalse(owner.hasPendingStep());
        return eventFrame;
    }

    private static void waitForPublishedFrame(PhysicsOwnerStepCommand command)
        throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline && command.publishedFrame() == null) {
            Thread.sleep(10L);
        }
        assertTrue(command.publishedFrame() != null);
    }

    private static void assertSystemDependency(Set<Dependency<EntityStore>> dependencies,
        Order order,
        Class<?> systemClass) {
        assertTrue(dependencies.stream().anyMatch(dependency ->
            dependency.getOrder() == order
                && dependency instanceof SystemDependency<?, ?> systemDependency
                && systemDependency.getSystemClass().equals(systemClass)));
    }
}
