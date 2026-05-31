package dev.hytalemodding.impulse.core.internal.systems.step;

import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import javax.annotation.Nonnull;

final class PhysicsStepRestoreGate {

    private PhysicsStepRestoreGate() {
    }

    static boolean canSubmitStep(@Nonnull PersistentPhysicsWorldResource persistent) {
        return !persistent.isRuntimeRestorePending() && !persistent.hasRuntimeRestoreFailed();
    }
}
