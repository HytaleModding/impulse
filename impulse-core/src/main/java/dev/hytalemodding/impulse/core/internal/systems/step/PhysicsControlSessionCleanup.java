package dev.hytalemodding.impulse.core.internal.systems.step;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

public final class PhysicsControlSessionCleanup {

    private PhysicsControlSessionCleanup() {
    }

    public static void cleanup(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsControlSessionComponent session) {
        cleanup(PhysicsWorldRuntimeResource.require(store), session);
    }

    public static void cleanup(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsControlSessionComponent session) {
        cleanup(PhysicsWorldRuntimeResource.require(resource), session);
    }

    public static void cleanup(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsControlSessionComponent session) {
        PhysicsBodyId bodyId = session.getBodyId();
        PhysicsBodyId anchorBodyId = session.getAnchorBodyId();
        resource.runOnPhysicsOwner("cleanup kinematic control session", access -> {
            if (bodyId != null) {
                resource.clearControlledBody(bodyId);
            }

            PhysicsSpace space = session.getSpaceId() != null
                ? access.getSpace(session.getSpaceId())
                : null;
            if (space != null && bodyId != null && anchorBodyId != null) {
                PhysicsControlJointResolver.removeControlJoint(access,
                    space,
                    bodyId,
                    anchorBodyId);
            }

            if (anchorBodyId != null) {
                resource.destroyBody(anchorBodyId);
            }
        });
    }
}
