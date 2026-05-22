package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource.BodyVisualInterestState;
import org.junit.jupiter.api.Test;

class BodyVisualInterestStateTest {

    @Test
    void raycastFreshnessUsesElapsedVisualTicks() {
        BodyVisualInterestState state = new BodyVisualInterestState();

        state.recordInterest(4.0f, true, true, true, 10L);

        assertTrue(state.hasFreshRaycast(3, 13L));
        assertFalse(state.hasFreshRaycast(3, 14L));
    }

    @Test
    void recordsWithoutRaycastDoNotRefreshFreshnessOrVisibility() {
        BodyVisualInterestState state = new BodyVisualInterestState();

        state.recordInterest(4.0f, true, false, true, 2L);
        state.recordInterest(3.0f, true, true, false, 4L);

        assertFalse(state.isRaycastVisible());
        assertTrue(state.hasFreshRaycast(2, 4L));

        state.recordInterest(2.0f, true, true, false, 5L);

        assertFalse(state.hasFreshRaycast(2, 5L));
    }

    @Test
    void legacyFreshnessCheckUsesAdvancedVisualTick() {
        BodyVisualInterestState state = new BodyVisualInterestState();

        state.recordInterest(1.0f, true, true, true, 1L);
        state.advanceVisualInterestTick(3L);

        assertTrue(state.hasFreshRaycast(2));

        state.advanceVisualInterestTick(4L);

        assertFalse(state.hasFreshRaycast(2));
    }
}
