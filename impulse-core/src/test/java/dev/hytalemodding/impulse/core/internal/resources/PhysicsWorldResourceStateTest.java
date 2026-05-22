package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend.InMemoryPhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.voxel.WorldCollisionMode;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsWorldResourceStateTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void bodySyncStateTracksLastSyncAndClampsNegativeSkipTime() {
        PhysicsWorldResource.BodySyncState syncState = new PhysicsWorldResource.BodySyncState();

        syncState.recordSync(new Vector3f(1.0f, 2.0f, 3.0f),
            new Quaternionf().rotateY(0.5f),
            true);
        syncState.recordSkip(-5.0f);
        syncState.recordSkip(0.75f);

        assertTrue(syncState.isInitialized());
        assertTrue(syncState.isSleeping());
        assertEquals(0.75f, syncState.getSecondsSinceSync(), 0.0001f);
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), syncState.getLastSyncedPosition());
        assertEquals(new Quaternionf().rotateY(0.5f), syncState.getLastSyncedRotation());
    }

    @Test
    void chunkBoundaryPauseStateCopiesProvidedVectors() {
        PhysicsWorldResource.ChunkBoundaryPauseState state =
            new PhysicsWorldResource.ChunkBoundaryPauseState();
        Vector3f linear = new Vector3f(1.0f, 2.0f, 3.0f);
        Vector3f angular = new Vector3f(4.0f, 5.0f, 6.0f);

        state.set(42L, PhysicsBodyType.KINEMATIC, linear, angular);
        linear.zero();
        angular.zero();

        assertEquals(42L, state.getTargetChunkIndex());
        assertEquals(PhysicsBodyType.KINEMATIC, state.getOriginalBodyType());
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), state.getLinearVelocity());
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), state.getAngularVelocity());
    }

    @Test
    void resetRuntimeStateKeepingSpacesReplacesNativeSpacesAndClearsRuntimeState() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:reset-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.streamingWorldCollision();
        settings.setSolverIterations(7);
        settings.setDetachedVisualMaxMaterialized(64);
        PhysicsSpace space = resource.createSpace(backend.getId(), "test-world", settings, true);
        space.setGravity(0.0f, -3.0f, 0.0f);

        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBodyId firstId = resource.addBody(PhysicsBodyId.random(),
            space.getId(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.addBody(space.getId(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        space.createFixedJoint(first, second, new Vector3f(), new Vector3f());
        resource.markContinuousCollisionForced(first);
        resource.markBodyControlled(firstId);
        resource.updateChunkBoundarySafeState(firstId, new Vector3f(1.0f), new Quaternionf());

        PhysicsWorldResource.RuntimeResetResult reset =
            resource.resetRuntimeStateKeepingSpaces("test-world");

        InMemoryPhysicsSpace original = backend.createdSpaces().get(0);
        InMemoryPhysicsSpace replacement = backend.createdSpaces().get(1);
        assertEquals(2, reset.removedBodies());
        assertEquals(1, reset.removedJoints());
        assertEquals(1, reset.keptSpaces());
        assertTrue(original.isClosed());
        assertFalse(replacement.isClosed());
        assertSame(replacement, resource.requireDefaultSpace());
        assertNotSame(original, resource.requireDefaultSpace());
        assertEquals(space.getId(), resource.requireDefaultSpaceId());
        assertEquals(new Vector3f(0.0f, -3.0f, 0.0f), replacement.getGravity());
        assertEquals(0, replacement.bodyCount());
        assertEquals(0, replacement.jointCount());
        assertEquals(7, replacement.getSolverIterations());

        PhysicsSpaceSettings preserved = resource.getSpaceSettings(space.getId());
        assertEquals(WorldCollisionMode.STREAMING, preserved.getWorldCollisionMode());
        assertEquals(64, preserved.getDetachedVisualMaxMaterialized());
        assertEquals(0, resource.getBodyRegistrations().size());
        assertEquals(0, resource.getBodySnapshotCount());
        assertNull(resource.getBody(firstId));
        assertFalse(resource.isBodyControlled(firstId));
        assertNull(resource.getChunkBoundarySafeState(firstId));
        assertTrue(resource.getForcedContinuousCollisionBodies().isEmpty());
    }

    @Test
    void destroyBodyClearsRuntimeStateAndSnapshotIndexes() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:destroy-cleanup-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsSpace space = resource.createSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setContinuousCollisionEnabled(true);
        PhysicsBodyId bodyId = resource.addBody(space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.markContinuousCollisionForced(body);
        resource.markBodyControlled(bodyId);
        resource.updateChunkBoundarySafeState(bodyId, new Vector3f(1.0f), new Quaternionf());
        resource.pauseChunkBoundaryBody(bodyId,
            42L,
            PhysicsBodyType.DYNAMIC,
            new Vector3f(2.0f, 0.0f, 0.0f),
            new Vector3f(0.0f, 3.0f, 0.0f));
        resource.getBodySnapshot(bodyId);

        resource.destroyBody(bodyId);

        assertEquals(0, space.bodyCount());
        assertNull(resource.getBody(bodyId));
        assertFalse(resource.isBodyControlled(bodyId));
        assertNull(resource.getChunkBoundarySafeState(bodyId));
        assertNull(resource.getChunkBoundaryPauseState(bodyId));
        assertTrue(resource.getForcedContinuousCollisionBodies().isEmpty());
        assertEquals(0, resource.getBodySnapshotCount());
        assertEquals(0, resource.getBodySnapshotCount(space.getId()));
        assertEquals(0, resource.getBodySnapshotCellCount());
        assertThrows(IllegalArgumentException.class, () -> resource.getBodySnapshot(bodyId));
    }

    @Test
    void bodySnapshotStoreRefreshesQueriesAndDropsStaleBodies() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:snapshot-store-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsSpace space = resource.createSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBody near = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        near.setPosition(0.0f, 0.0f, 0.0f);
        PhysicsBody far = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        far.setPosition(40.0f, 0.0f, 0.0f);
        PhysicsBodyId nearId = resource.addBody(space.getId(),
            near,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        resource.addBody(space.getId(),
            far,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        assertEquals(2, resource.refreshBodySnapshots());
        assertEquals(2, resource.getBodySnapshotCount(space.getId()));
        AtomicInteger nearMatches = new AtomicInteger();
        int candidates = resource.forEachBodySnapshotNear(space.getId(),
            new Vector3f(),
            8.0f,
            entry -> {
                assertEquals(nearId, entry.bodyId());
                nearMatches.incrementAndGet();
            });
        assertEquals(1, candidates);
        assertEquals(1, nearMatches.get());

        space.removeBody(far);
        assertEquals(1, resource.refreshBodySnapshots());

        assertEquals(1, resource.getBodySnapshotCount());
        assertEquals(1, resource.getBodySnapshotCount(space.getId()));
        AtomicInteger remainingSnapshots = new AtomicInteger();
        resource.forEachBodySnapshot(space.getId(), entry -> {
            assertEquals(nearId, entry.bodyId());
            remainingSnapshots.incrementAndGet();
        });
        assertEquals(1, remainingSnapshots.get());
        resource.clearBodies();
        assertEquals(0, resource.getBodySnapshotCount());
        assertEquals(0, resource.getBodySnapshotCellCount());
    }

    @Test
    void bodySnapshotStoreIgnoresUnregisteredSpaceBodies() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:snapshot-store-unregistered-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsSpace space = resource.createSpace(backend.getId(),
            "test-world",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBody registered = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        registered.setPosition(0.0f, 0.0f, 0.0f);
        PhysicsBody unregistered = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        unregistered.setPosition(2.0f, 0.0f, 0.0f);
        PhysicsBodyId registeredId = resource.addBody(space.getId(),
            registered,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        space.addBody(unregistered);

        assertEquals(1, resource.refreshBodySnapshots());
        assertEquals(1, resource.getBodySnapshotCount(space.getId()));

        AtomicInteger snapshots = new AtomicInteger();
        resource.forEachBodySnapshot(space.getId(), entry -> {
            assertEquals(registeredId, entry.bodyId());
            assertSame(registered, entry.snapshot().body());
            snapshots.incrementAndGet();
        });
        assertEquals(1, snapshots.get());
    }
}
