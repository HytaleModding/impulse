package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.BodyVisualInterestState;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3f;
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
    void freshnessCheckUsesAdvancedVisualTick() {
        BodyVisualInterestState state = new BodyVisualInterestState();

        state.recordInterest(1.0f, true, true, true, 1L);
        state.advanceVisualInterestTick(3L);

        assertTrue(state.hasFreshRaycast(2));

        state.advanceVisualInterestTick(4L);

        assertFalse(state.hasFreshRaycast(2));
    }

    @Test
    void pendingRaycastResultsArePolledWithoutBlocking() {
        BodyVisualInterestState state = new BodyVisualInterestState();
        CompletableFuture<Optional<RaycastHitView>> pending = new CompletableFuture<>();
        RigidBodyKey bodyKey = RigidBodyKey.random();
        RaycastHitView hit = new RaycastHitView(bodyKey,
            PhysicsBodyType.DYNAMIC,
            new Vector3f(),
            new Vector3f(0.0f, 1.0f, 0.0f),
            ShapeType.BOX,
            0.5f,
            4.0f);

        assertTrue(state.startPendingRaycast(pending));
        assertFalse(state.startPendingRaycast(CompletableFuture.completedFuture(Optional.empty())));
        assertNull(state.pollCompletedRaycast());

        pending.complete(Optional.of(hit));

        assertEquals(Optional.of(hit), state.pollCompletedRaycast());
        assertNull(state.pollCompletedRaycast());
        assertTrue(state.startPendingRaycast(CompletableFuture.completedFuture(Optional.empty())));
    }
}
