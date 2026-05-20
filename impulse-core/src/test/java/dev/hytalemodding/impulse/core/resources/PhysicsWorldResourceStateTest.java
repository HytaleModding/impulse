package dev.hytalemodding.impulse.core.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.InMemoryPhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.InMemoryPhysicsBackend.InMemoryPhysicsSpace;
import dev.hytalemodding.impulse.core.voxel.WorldCollisionMode;
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
        InMemoryPhysicsBackend backend =
            new InMemoryPhysicsBackend("test:reset-" + BACKEND_COUNTER.incrementAndGet());
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
}
