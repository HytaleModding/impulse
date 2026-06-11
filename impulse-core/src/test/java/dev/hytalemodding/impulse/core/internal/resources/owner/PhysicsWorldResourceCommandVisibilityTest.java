package dev.hytalemodding.impulse.core.internal.resources.owner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SpaceBodyCountQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class PhysicsWorldResourceCommandVisibilityTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void ownerCommandSpawnPublishesRegistrationViewsWithSnapshotFrame() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-registration-view");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-view");
            resource.attachOwnerExecutor(owner);

            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            owner.submitMutation("block command-buffer spawn", () -> {
                blockerStarted.countDown();
                assertTrue(releaseBlocker.await(2, TimeUnit.SECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

            RigidBodyKey bodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(201L, commands -> commands
                .spawnBody(bodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()));

            assertFalse(handle.completion().toCompletableFuture().isDone());
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertEquals(0, resource.getBodyRegistrationCount());

            releaseBlocker.countDown();
            var results = handle.completion()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);

            assertEquals(PhysicsCommandResult.Status.APPLIED, results.getFirst().status());
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertEquals(0, resource.getBodyRegistrationCount());
            assertTrue(resource.isBodyCreationPending(bodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                202L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);

            assertEquals(1, frame.bodyCount());
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertEquals(0, resource.getBodyRegistrationCount());

            resource.applyPublishedSnapshotFrame(frame);

            assertNotNull(resource.getBodyRegistrationView(bodyId));
            assertEquals(1, resource.getBodyRegistrationCount());
            assertFalse(resource.isBodyCreationPending(bodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void multiSingleSpawnCommandTracksOnlySpawnedPendingKeys() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-registration-multi-single");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-multi-single");
            resource.attachOwnerExecutor(owner);

            RigidBodyKey firstBodyId = RigidBodyKey.random();
            RigidBodyKey secondBodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(301L, commands -> {
                commands.spawnBody(firstBodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
                commands.spawnBody(secondBodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
            });

            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertTrue(resource.isBodyCreationPending(firstBodyId));
            assertTrue(resource.isBodyCreationPending(secondBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(firstBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(secondBodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));
            assertFalse(resource.hasPublishedOrPendingBodyRegistration(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                302L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertFalse(resource.isBodyCreationPending(firstBodyId));
            assertFalse(resource.isBodyCreationPending(secondBodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void commandSpawnedBodyIsIndexedForInternalReadersBeforePublishedRegistration() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-indexed-snapshot");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-indexed-snapshot");
            resource.attachOwnerExecutor(owner);

            RigidBodyKey bodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(306L, commands -> commands
                .spawnBody(bodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()
                    .kind(PhysicsBodyKind.BODY)
                    .runtimeOnly()));

            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertNull(resource.getBodyRegistrationView(bodyId));
            assertTrue(resource.isBodyCreationPending(bodyId));

            AtomicInteger indexedSnapshots = new AtomicInteger();
            AtomicBoolean foundBody = new AtomicBoolean();
            resource.callOwner("inspect command-spawned indexed body snapshot", () -> {
                resource.forEachIndexedBodySnapshot(fixture.spaceId(),
                    (snapshotBodyId, snapshot, snapshotSpaceId, kind, persistenceMode) -> {
                        indexedSnapshots.incrementAndGet();
                        if (bodyId.equals(snapshotBodyId)) {
                            foundBody.set(true);
                            assertEquals(fixture.spaceId(), snapshotSpaceId);
                            assertTrue(snapshot.isDynamic());
                            assertEquals(PhysicsBodyKind.BODY, kind);
                            assertEquals(PhysicsBodyPersistenceMode.RUNTIME_ONLY, persistenceMode);
                        }
                    });
                return null;
            });

            assertEquals(1, indexedSnapshots.get());
            assertTrue(foundBody.get());
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void bulkSpawnCommandTracksOnlySpawnedPendingKeys() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-registration-bulk");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-bulk");
            resource.attachOwnerExecutor(owner);

            RigidBodyKey firstBodyId = RigidBodyKey.random();
            RigidBodyKey secondBodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(311L, commands -> commands.spawnBodies(2, spawns -> {
                spawns.body(firstBodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
                spawns.body(secondBodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
            }));

            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertTrue(resource.isBodyCreationPending(firstBodyId));
            assertTrue(resource.isBodyCreationPending(secondBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(firstBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(secondBodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));
            assertFalse(resource.hasPublishedOrPendingBodyRegistration(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                312L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertFalse(resource.isBodyCreationPending(firstBodyId));
            assertFalse(resource.isBodyCreationPending(secondBodyId));
            assertNotNull(resource.getBodyRegistrationView(firstBodyId));
            assertNotNull(resource.getBodyRegistrationView(secondBodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void templateSpawnCommandTracksOnlySpawnedPendingKeys() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-registration-template");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-template");
            resource.attachOwnerExecutor(owner);

            RigidBodyKey firstBodyId = RigidBodyKey.random();
            RigidBodyKey secondBodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(321L, commands -> commands.spawnBodies(2,
                fixture.spaceId(),
                PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
                1.0f,
                PhysicsBodyType.DYNAMIC,
                RigidBodySpawnSettings.defaults(),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                spawns -> spawns
                    .body(firstBodyId, 0.0f, 0.0f, 0.0f)
                    .body(secondBodyId, 1.0f, 0.0f, 0.0f)));

            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertTrue(resource.isBodyCreationPending(firstBodyId));
            assertTrue(resource.isBodyCreationPending(secondBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(firstBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(secondBodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));
            assertFalse(resource.hasPublishedOrPendingBodyRegistration(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                322L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertFalse(resource.isBodyCreationPending(firstBodyId));
            assertFalse(resource.isBodyCreationPending(secondBodyId));
            assertNotNull(resource.getBodyRegistrationView(firstBodyId));
            assertNotNull(resource.getBodyRegistrationView(secondBodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void rejectedSpawnCommandClearsPendingKeyAfterIncludedSnapshotFrame() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-registration-rejected");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-rejected");
            resource.attachOwnerExecutor(owner);

            RigidBodyKey bodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(331L, commands -> commands
                .spawnBody(bodyId, spawn -> spawn
                    .space(new SpaceId(Integer.MAX_VALUE))
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()));

            var results = handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(PhysicsCommandResult.Status.REJECTED, results.getFirst().status());
            assertTrue(resource.isBodyCreationPending(bodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                332L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertFalse(resource.isBodyCreationPending(bodyId));
            assertNull(resource.getBodyRegistrationView(bodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void partiallyRejectedBulkSpawnClearsAllRequestedPendingKeysAfterIncludedSnapshotFrame() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-registration-partial");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        RigidBodyKey createdBodyId = RigidBodyKey.random();
        RigidBodyKey duplicateBodyId = RigidBodyKey.random();
        spawnPublishedBody(resource, fixture.spaceId(), duplicateBodyId);

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-partial");
            resource.attachOwnerExecutor(owner);

            var handle = resource.submitCommands(341L, commands -> commands.spawnBodies(2, spawns -> {
                spawns.body(createdBodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
                spawns.body(duplicateBodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic());
            }));

            var results = handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertEquals(PhysicsCommandResult.Status.REJECTED, results.getFirst().status());
            assertTrue(resource.isBodyCreationPending(createdBodyId));
            assertTrue(resource.isBodyCreationPending(duplicateBodyId));
            assertFalse(resource.isBodyCreationPending(RigidBodyKey.random()));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                342L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            resource.applyPublishedSnapshotFrame(frame);

            assertNotNull(resource.getBodyRegistrationView(createdBodyId));
            assertNotNull(resource.getBodyRegistrationView(duplicateBodyId));
            assertFalse(resource.isBodyCreationPending(createdBodyId));
            assertFalse(resource.isBodyCreationPending(duplicateBodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void nullableSnapshotLookupToleratesStalePublishedRegistrationAfterDestroy() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-stale-registration-snapshot");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-stale-registration-snapshot");
            resource.attachOwnerExecutor(owner);

            RigidBodyKey bodyId = RigidBodyKey.random();
            spawnPublishedBody(resource, fixture.spaceId(), bodyId);

            resource.destroyBodyAsync(bodyId).completion()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);

            assertNotNull(resource.getBodyRegistrationView(bodyId));
            assertNull(resource.getBodySnapshotIfRegistered(bodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void queuedDestroyAfterCommandSpawnRemovesBodyBeforePublication() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-spawn-destroy-before-publication");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-spawn-destroy-before-publication");
            resource.attachOwnerExecutor(owner);

            CountDownLatch blockerStarted = new CountDownLatch(1);
            CountDownLatch releaseBlocker = new CountDownLatch(1);
            owner.submitMutation("block command-buffer spawn before destroy", () -> {
                blockerStarted.countDown();
                assertTrue(releaseBlocker.await(2, TimeUnit.SECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));

            RigidBodyKey bodyId = RigidBodyKey.random();
            var spawnHandle = resource.submitCommands(361L, commands -> commands
                .spawnBody(bodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()));
            CompletableFuture<Void> destroyFuture =
                CompletableFuture.runAsync(() -> resource.destroyBody(bodyId));

            assertTrue(resource.isBodyCreationPending(bodyId));
            assertFalse(spawnHandle.completion().toCompletableFuture().isDone());
            assertFalse(destroyFuture.isDone());

            releaseBlocker.countDown();

            var spawnResults = spawnHandle.completion()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);
            destroyFuture.get(2, TimeUnit.SECONDS);

            assertEquals(PhysicsCommandResult.Status.APPLIED, spawnResults.getFirst().status());
            assertFalse(resource.isBodyCreationPending(bodyId));
            assertNull(resource.getBodyRegistrationView(bodyId));
            assertEquals(0, resource.query(new SpaceBodyCountQuery(fixture.spaceId()))
                .completion()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS));

            PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
                362L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            assertEquals(0, frame.bodyCount());
            resource.applyPublishedSnapshotFrame(frame);
            assertFalse(resource.isBodyCreationPending(bodyId));
            assertNull(resource.getBodyRegistrationView(bodyId));
            resource.detachOwnerExecutor(owner);
        }
    }

    @Test
    void ownerDetachPublishesCommandSpawnRegistrationViewsAndClearsPendingKeys() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture("owner-command-registration-detach");
        PhysicsWorldRuntimeResource resource = fixture.resource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("owner-command-registration-detach");
            resource.attachOwnerExecutor(owner);

            RigidBodyKey bodyId = RigidBodyKey.random();
            var handle = resource.submitCommands(351L, commands -> commands
                .spawnBody(bodyId, spawn -> spawn
                    .space(fixture.spaceId())
                    .box(0.5f, 0.5f, 0.5f)
                    .dynamic()));

            handle.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);

            assertNull(resource.getBodyRegistrationView(bodyId));
            assertTrue(resource.isBodyCreationPending(bodyId));

            resource.detachOwnerExecutor(owner);

            assertNotNull(resource.getBodyRegistrationView(bodyId));
            assertFalse(resource.isBodyCreationPending(bodyId));
        }
    }

    @Nonnull
    private static RuntimeFixture createRuntimeFixture(@Nonnull String name) {
        BackendId backendId = new BackendId("test:" + name + "-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerRuntimeProvider(new FakePhysicsBackendRuntimeProvider(backendId,
            false,
            false));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        SpaceId spaceId = resource.createSpace(backendId,
            "test-world",
            PhysicsSpaceSettings.defaults());
        return new RuntimeFixture(resource, spaceId);
    }

    private static void spawnPublishedBody(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyId) {
        var handle = resource.submitCommands(0L, commands -> commands
            .spawnBody(bodyId, spawn -> spawn
                .space(spaceId)
                .box(0.5f, 0.5f, 0.5f)
                .dynamic()
                .kind(PhysicsBodyKind.BODY)
                .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY)));

        var results = handle.completion().toCompletableFuture().join();
        assertEquals(PhysicsCommandResult.Status.APPLIED, results.getFirst().status());

        PublishedPhysicsSnapshotFrame frame = resource.capturePublishedSnapshotFrame(1L,
            0L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            0L,
            false);
        resource.applyPublishedSnapshotFrame(frame);
        assertNotNull(resource.getBodyRegistrationView(bodyId));
        assertFalse(resource.isBodyCreationPending(bodyId));
    }

    private record RuntimeFixture(@Nonnull PhysicsWorldRuntimeResource resource,
                                  @Nonnull SpaceId spaceId) {
    }
}
