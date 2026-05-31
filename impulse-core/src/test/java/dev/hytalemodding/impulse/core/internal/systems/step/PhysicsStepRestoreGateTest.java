package dev.hytalemodding.impulse.core.internal.systems.step;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import org.junit.jupiter.api.Test;

class PhysicsStepRestoreGateTest {

    @Test
    void allowsStepSubmissionWhenRestoreIsSettled() {
        PersistentPhysicsWorldResource persistent = new PersistentPhysicsWorldResource();

        assertTrue(PhysicsStepRestoreGate.canSubmitStep(persistent));
    }

    @Test
    void blocksStepSubmissionDuringPendingOrFailedRestore() {
        PersistentPhysicsWorldResource pending = new PersistentPhysicsWorldResource();
        pending.markRuntimeRestorePending();
        PersistentPhysicsWorldResource failed = new PersistentPhysicsWorldResource();
        failed.markRuntimeRestorePending();
        failed.failRuntimeRestore("test failure");

        assertFalse(PhysicsStepRestoreGate.canSubmitStep(pending));
        assertFalse(PhysicsStepRestoreGate.canSubmitStep(failed));
    }
}
