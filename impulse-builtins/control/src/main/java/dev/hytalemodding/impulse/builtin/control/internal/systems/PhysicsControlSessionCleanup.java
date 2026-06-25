package dev.hytalemodding.impulse.builtin.control.internal.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.builtin.control.internal.PhysicsControlRuntimeStates;
import dev.hytalemodding.impulse.builtin.control.internal.components.PhysicsControlSessionComponent;
import javax.annotation.Nonnull;

public final class PhysicsControlSessionCleanup {

    private PhysicsControlSessionCleanup() {
    }

    public static void cleanup(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsControlSessionComponent session) {
        cleanupInternal(store, session);
    }

    private static void cleanupInternal(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsControlSessionComponent session) {
        PhysicsKinematicControlSystem.clearMutationState(store, session.getAnchorBodyRef());
        if (!session.isActive()) {
            return;
        }

        Ref<PhysicsStore> bodyRef = session.getBodyRef();
        if (bodyRef != null) {
            PhysicsControlRuntimeStates.clearControlled(bodyRef);
        }

        PhysicsStoreControlSessionMutations.applyRelease(store, session);
        session.deactivate();
    }
}
