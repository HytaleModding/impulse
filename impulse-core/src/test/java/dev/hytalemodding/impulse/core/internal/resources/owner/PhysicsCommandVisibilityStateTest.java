package dev.hytalemodding.impulse.core.internal.resources.owner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.simulation.MutablePhysicsCommandContext;
import dev.hytalemodding.impulse.core.internal.simulation.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import org.junit.jupiter.api.Test;

class PhysicsCommandVisibilityStateTest {

    @Test
    void singleSpawnBodyCreationUsesExactPendingKeyUntilSnapshotIncludesBatch() {
        PhysicsCommandVisibilityState state = new PhysicsCommandVisibilityState();
        RigidBodyKey bodyKey = RigidBodyKey.random();
        RecordedPhysicsCommandBatch batch = singleSpawnBatch(7L, bodyKey);

        assertTrue(state.trackBodyCreationPublication(batch, true));

        assertTrue(state.isBodyCreationPending(bodyKey, false, true));
        assertFalse(state.isBodyCreationPending(RigidBodyKey.random(), false, true));

        state.applyLastIncludedCommandBatchSequence(7L);

        assertFalse(state.isBodyCreationPending(bodyKey, false, true));
    }

    @Test
    void completedSpawnBodyCreationStaysPendingUntilSnapshotIncludesBatch() {
        PhysicsCommandVisibilityState state = new PhysicsCommandVisibilityState();
        RigidBodyKey bodyKey = RigidBodyKey.random();
        RecordedPhysicsCommandBatch batch = singleSpawnBatch(8L, bodyKey);

        assertTrue(state.trackBodyCreationPublication(batch, true));

        state.markCommandBatchCompleted(8L);

        assertEquals(8L, state.completedCommandBatchSequence());
        assertTrue(state.isBodyCreationPending(bodyKey, false, true));
        assertFalse(state.isBodyCreationPending(RigidBodyKey.random(), false, true));

        state.applyLastIncludedCommandBatchSequence(7L);

        assertTrue(state.isBodyCreationPending(bodyKey, false, true));

        state.applyLastIncludedCommandBatchSequence(8L);

        assertFalse(state.isBodyCreationPending(bodyKey, false, true));
    }

    @Test
    void multiSingleSpawnBodyCreationTracksOnlySpawnedKeysUntilSnapshotIncludesBatch() {
        PhysicsCommandVisibilityState state = new PhysicsCommandVisibilityState();
        RigidBodyKey firstBodyKey = RigidBodyKey.random();
        RigidBodyKey secondBodyKey = RigidBodyKey.random();
        RecordedPhysicsCommandBatch batch = multiSingleSpawnBatch(11L, firstBodyKey, secondBodyKey);

        assertTrue(state.trackBodyCreationPublication(batch, true));

        assertTrue(state.isBodyCreationPending(firstBodyKey, false, true));
        assertTrue(state.isBodyCreationPending(secondBodyKey, false, true));
        assertFalse(state.isBodyCreationPending(RigidBodyKey.random(), false, true));

        state.applyLastIncludedCommandBatchSequence(10L);
        assertTrue(state.isBodyCreationPending(firstBodyKey, false, true));
        assertTrue(state.isBodyCreationPending(secondBodyKey, false, true));
        assertFalse(state.isBodyCreationPending(RigidBodyKey.random(), false, true));

        state.applyLastIncludedCommandBatchSequence(11L);
        assertFalse(state.isBodyCreationPending(firstBodyKey, false, true));
        assertFalse(state.isBodyCreationPending(secondBodyKey, false, true));
    }

    @Test
    void spawnBatchBodyCreationTracksOnlySpawnedKeysUntilSnapshotIncludesBatch() {
        PhysicsCommandVisibilityState state = new PhysicsCommandVisibilityState();
        RigidBodyKey firstBodyKey = RigidBodyKey.random();
        RigidBodyKey secondBodyKey = RigidBodyKey.random();
        RecordedPhysicsCommandBatch batch = spawnBatch(12L, firstBodyKey, secondBodyKey);

        assertTrue(state.trackBodyCreationPublication(batch, true));

        assertTrue(state.isBodyCreationPending(firstBodyKey, false, true));
        assertTrue(state.isBodyCreationPending(secondBodyKey, false, true));
        assertFalse(state.isBodyCreationPending(RigidBodyKey.random(), false, true));

        state.applyLastIncludedCommandBatchSequence(12L);

        assertFalse(state.isBodyCreationPending(firstBodyKey, false, true));
        assertFalse(state.isBodyCreationPending(secondBodyKey, false, true));
    }

    @Test
    void templateSpawnBodyCreationTracksOnlySpawnedKeysUntilSnapshotIncludesBatch() {
        PhysicsCommandVisibilityState state = new PhysicsCommandVisibilityState();
        RigidBodyKey firstBodyKey = RigidBodyKey.random();
        RigidBodyKey secondBodyKey = RigidBodyKey.random();
        RecordedPhysicsCommandBatch batch = templateSpawnBatch(13L, firstBodyKey, secondBodyKey);

        assertTrue(state.trackBodyCreationPublication(batch, true));

        assertTrue(state.isBodyCreationPending(firstBodyKey, false, true));
        assertTrue(state.isBodyCreationPending(secondBodyKey, false, true));
        assertFalse(state.isBodyCreationPending(RigidBodyKey.random(), false, true));

        state.applyLastIncludedCommandBatchSequence(13L);

        assertFalse(state.isBodyCreationPending(firstBodyKey, false, true));
        assertFalse(state.isBodyCreationPending(secondBodyKey, false, true));
    }

    @Test
    void commandWorldEpochOnlyChangesOutsideCommandBatchExecution() {
        PhysicsCommandVisibilityState state = new PhysicsCommandVisibilityState();

        assertEquals(0L, state.commandWorldEpoch());
        assertTrue(state.markWorldChanged());
        assertEquals(1L, state.commandWorldEpoch());

        int previousDepth = state.enterCommandBatchExecution();
        try {
            assertFalse(state.markWorldChanged());
            assertEquals(1L, state.commandWorldEpoch());
        } finally {
            state.exitCommandBatchExecution(previousDepth);
        }

        assertTrue(state.markWorldChanged());
        assertEquals(2L, state.commandWorldEpoch());
    }

    @Test
    void commandCompletionSequenceTracksMaxCompletedBatch() {
        PhysicsCommandVisibilityState state = new PhysicsCommandVisibilityState();

        state.markCommandBatchCompleted(4L);
        state.markCommandBatchCompleted(2L);

        assertEquals(4L, state.completedCommandBatchSequence());
    }

    private static RecordedPhysicsCommandBatch singleSpawnBatch(long sequence,
        RigidBodyKey bodyKey) {
        MutablePhysicsCommandContext context = new MutablePhysicsCommandContext(1L, 0L);
        context.spawnBody(bodyKey, spawn -> spawn
            .space(new SpaceId(1))
            .box(0.5f, 0.5f, 0.5f)
            .mass(1.0f)
            .dynamic()
            .kind(PhysicsBodyKind.BODY)
            .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY));
        return context.freezeInternal(sequence);
    }

    private static RecordedPhysicsCommandBatch multiSingleSpawnBatch(long sequence,
        RigidBodyKey firstBodyKey,
        RigidBodyKey secondBodyKey) {
        MutablePhysicsCommandContext context = new MutablePhysicsCommandContext(1L, 0L);
        context.spawnBody(firstBodyKey, spawn -> spawn
            .space(new SpaceId(1))
            .box(0.5f, 0.5f, 0.5f)
            .mass(1.0f)
            .dynamic()
            .kind(PhysicsBodyKind.BODY)
            .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY));
        context.spawnBody(secondBodyKey, spawn -> spawn
            .space(new SpaceId(1))
            .box(0.5f, 0.5f, 0.5f)
            .mass(1.0f)
            .dynamic()
            .kind(PhysicsBodyKind.BODY)
            .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY));
        return context.freezeInternal(sequence);
    }

    private static RecordedPhysicsCommandBatch spawnBatch(long sequence,
        RigidBodyKey firstBodyKey,
        RigidBodyKey secondBodyKey) {
        MutablePhysicsCommandContext context = new MutablePhysicsCommandContext(1L, 0L);
        context.spawnBodies(2, spawns -> {
            spawns.body(firstBodyKey, spawn -> spawn
                .space(new SpaceId(1))
                .box(0.5f, 0.5f, 0.5f)
                .mass(1.0f)
                .dynamic()
                .kind(PhysicsBodyKind.BODY)
                .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY));
            spawns.body(secondBodyKey, spawn -> spawn
                .space(new SpaceId(1))
                .box(0.5f, 0.5f, 0.5f)
                .mass(1.0f)
                .dynamic()
                .kind(PhysicsBodyKind.BODY)
                .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY));
        });
        return context.freezeInternal(sequence);
    }

    private static RecordedPhysicsCommandBatch templateSpawnBatch(long sequence,
        RigidBodyKey firstBodyKey,
        RigidBodyKey secondBodyKey) {
        MutablePhysicsCommandContext context = new MutablePhysicsCommandContext(1L, 0L);
        context.spawnBodies(2,
            new SpaceId(1),
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
            1.0f,
            PhysicsBodyType.DYNAMIC,
            RigidBodySpawnSettings.defaults(),
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            spawns -> {
                spawns.body(firstBodyKey, 0.0f, 0.0f, 0.0f);
                spawns.body(secondBodyKey, 1.0f, 0.0f, 0.0f);
            });
        return context.freezeInternal(sequence);
    }
}
