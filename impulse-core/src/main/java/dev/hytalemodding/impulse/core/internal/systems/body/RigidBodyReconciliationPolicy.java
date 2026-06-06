package dev.hytalemodding.impulse.core.internal.systems.body;

import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyLifecycleComponent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class RigidBodyReconciliationPolicy {

    private RigidBodyReconciliationPolicy() {
    }

    static boolean shouldSuppressBodyReconciliation(@Nullable RigidBodyLifecycleComponent lifecycle,
        @Nullable PersistentPhysicsWorldResource persistent,
        @Nonnull RigidBodyKey bodyKey) {
        if (lifecycle != null
            && lifecycle.getState() == RigidBodyLifecycleComponent.State.DESTROYED) {
            return true;
        }
        return persistent != null && persistent.isRuntimeBodySkipped(bodyKey);
    }
}
