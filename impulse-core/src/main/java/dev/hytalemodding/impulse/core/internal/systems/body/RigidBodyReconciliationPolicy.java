package dev.hytalemodding.impulse.core.internal.systems.body;

import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyLifecycleComponent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class RigidBodyReconciliationPolicy {

    private RigidBodyReconciliationPolicy() {
    }

    static boolean shouldSuppressBodyReconciliation(@Nullable PhysicsBodyLifecycleComponent lifecycle,
        @Nullable PersistentPhysicsWorldResource persistent,
        @Nonnull RigidBodyKey bodyKey) {
        if (lifecycle != null
            && lifecycle.getState() == PhysicsBodyLifecycleComponent.State.DESTROYED) {
            return true;
        }
        return persistent != null && persistent.isRuntimeBodySkipped(bodyKey);
    }
}
