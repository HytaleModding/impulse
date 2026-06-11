package dev.hytalemodding.impulse.core.internal.systems.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import org.junit.jupiter.api.Test;

class PersistentPhysicsJointHydrationSystemTest {

    @Test
    void hardJointHydrationFailureDoesNotFinalizeRestoreAsSuccessful() {
        PersistentPhysicsWorldResource persistent = new PersistentPhysicsWorldResource();

        persistent.markRuntimeRestorePending();
        persistent.failRuntimeRestore("forced joint failure");

        assertFalse(PersistentPhysicsJointHydrationSystem.shouldFinalizeRuntimeRestore(persistent));
    }

    @Test
    void pendingRestoreWithoutHardFailureCanFinalizeAfterJointHydration() {
        PersistentPhysicsWorldResource persistent = new PersistentPhysicsWorldResource();

        persistent.markRuntimeRestorePending();

        assertTrue(PersistentPhysicsJointHydrationSystem.shouldFinalizeRuntimeRestore(persistent));
    }
}
