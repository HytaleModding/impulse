package dev.hytalemodding.impulse.core.internal.systems.step;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
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
        if (!session.isActive()) {
            return;
        }

        RigidBodyKey bodyKey = session.getBodyKey();
        RigidBodyKey anchorBodyKey = session.getAnchorBodyKey();
        JointKey controlJointKey = session.getControlJointKey();
        if (bodyKey != null) {
            resource.clearControlledBody(bodyKey);
        }

        if (controlJointKey != null || anchorBodyKey != null) {
            // Tick-driven session cleanup must not wait behind worker backlog.
            // The owner command releases the backend joint/body when it reaches
            // the worker; command/admin helpers may still choose synchronous
            // semantics when they need immediate textual feedback.
            resource.submitCommands(0L, 2, commands -> {
                if (session.getSpaceId() != null && bodyKey != null && anchorBodyKey != null) {
                    commands.destroyJointBetween(controlJointKey, session.getSpaceId(), anchorBodyKey, bodyKey);
                } else if (controlJointKey != null) {
                    commands.destroyJoint(controlJointKey);
                }
                if (anchorBodyKey != null) {
                    commands.destroyBody(anchorBodyKey);
                }
            });
        }
    }
}
