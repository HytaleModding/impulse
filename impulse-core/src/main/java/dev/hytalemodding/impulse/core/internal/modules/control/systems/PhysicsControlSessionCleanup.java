package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import javax.annotation.Nonnull;

public final class PhysicsControlSessionCleanup {

    private PhysicsControlSessionCleanup() {
    }

    public static void cleanup(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsControlSessionComponent session) {
        cleanupInternal(store, PhysicsWorldRuntimeResource.require(store), session);
    }

    public static void cleanup(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsControlSessionComponent session) {
        cleanupInternal(store, resource, session);
    }

    private static void cleanupInternal(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsControlSessionComponent session) {
        PhysicsKinematicControlSystem.clearMutationState(store, session.getAnchorBodyRef());
        if (!session.isActive()) {
            return;
        }

        Ref<PhysicsStore> bodyRef = session.getBodyRef();
        if (bodyRef != null) {
            resource.clearControlledBody(bodyRef);
        }

        PhysicsStoreControlSessionMutations.applyRelease(store, session);
        session.deactivate();
    }
}
