package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

        UUID bodyUuid = rowUuid(session.getBodyRef());
        if (bodyUuid != null) {
            resource.clearControlledBody(bodyUuid);
        }

        PhysicsStoreControlSessionMutations.applyRelease(store, session);
        session.deactivate();
    }

    @Nullable
    private static UUID rowUuid(@Nullable Ref<PhysicsStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        UuidComponent uuid = ref.getStore().getComponent(ref, UuidComponent.getComponentType());
        return uuid != null ? uuid.getUuid() : null;
    }
}
