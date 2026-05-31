package dev.hytalemodding.impulse.core.internal.resources.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldSnapshotState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import org.junit.jupiter.api.Test;

class PhysicsWorldSnapshotStateTest {

    @Test
    void applyPublishedSnapshotFrameReportsWhetherFrameMatchesCurrentWorldEpoch() {
        PhysicsWorldSnapshotState state = new PhysicsWorldSnapshotState();
        PublishedPhysicsSnapshotFrame currentFrame = PublishedPhysicsSnapshotFrame.empty(1L, 0L);

        PhysicsWorldSnapshotState.ApplyResult current =
            state.applyPublishedSnapshotFrame(currentFrame, new PhysicsBodyRegistry());

        assertTrue(current.currentWorldEpoch());
        assertEquals(0, current.appliedCount());

        state.markWorldChanged();

        PhysicsWorldSnapshotState.ApplyResult stale =
            state.applyPublishedSnapshotFrame(currentFrame, new PhysicsBodyRegistry());

        assertFalse(stale.currentWorldEpoch());
        assertEquals(0, stale.appliedCount());
    }
}
