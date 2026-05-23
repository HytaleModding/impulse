package dev.hytalemodding.impulse.core.internal.systems.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsKinematicControlSystem.ControlAnchorUpdate;
import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsKinematicControlSystem.ControlMutationState;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import java.util.concurrent.CompletableFuture;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsKinematicControlSystemTest {

    @Test
    void controlAnchorUpdateCopiesMutableVectors() {
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        PhysicsBodyId anchorBodyId = PhysicsBodyId.random();
        Vector3f target = new Vector3f(1.0f, 2.0f, 3.0f);
        Vector3f releaseVelocity = new Vector3f(4.0f, 5.0f, 6.0f);
        ControlAnchorUpdate update = new ControlAnchorUpdate(bodyId,
            anchorBodyId,
            target,
            releaseVelocity);
        target.zero();
        releaseVelocity.zero();

        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), update.target());
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), update.releaseVelocity());
    }

    @Test
    void pendingControlMutationBlocksAnotherSubmissionUntilComplete() {
        ControlMutationState state = new ControlMutationState();
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        PhysicsMutationHandle<Void> handle = PhysicsMutationHandle.fromCompletion("test",
            null,
            completion);

        state.trackPendingMutation(bodyId, handle);

        assertTrue(state.hasPendingMutation(bodyId));

        completion.complete(null);

        assertFalse(state.hasPendingMutation(bodyId));
    }

    @Test
    void clearingControlMutationStateAllowsImmediateRetry() {
        ControlMutationState state = new ControlMutationState();
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        PhysicsMutationHandle<Void> handle = PhysicsMutationHandle.fromCompletion("test",
            null,
            completion);

        state.trackPendingMutation(bodyId, handle);
        state.clear(bodyId);

        assertFalse(state.hasPendingMutation(bodyId));

        completion.complete(null);
        assertFalse(state.hasPendingMutation(bodyId));
    }
}
