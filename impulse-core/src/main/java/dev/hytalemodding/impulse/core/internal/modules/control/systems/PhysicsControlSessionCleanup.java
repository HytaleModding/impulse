package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

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
        cleanup(resource, session, false);
    }

    public static void cleanupAndWait(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsControlSessionComponent session) {
        cleanup(resource, session, true);
    }

    private static void cleanup(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsControlSessionComponent session,
        boolean waitForCompletion) {
        if (!session.isActive()) {
            return;
        }

        RigidBodyKey bodyKey = session.getBodyKey();
        RigidBodyKey anchorBodyKey = session.getAnchorBodyKey();
        JointKey controlJointKey = session.getControlJointKey();
        boolean restoreBody = bodyKey != null
            && resource.hasPublishedOrPendingBodyRegistration(bodyKey);
        if (bodyKey != null) {
            resource.clearControlledBody(bodyKey);
        }

        if (shouldReleaseJoint(controlJointKey, session.getSpaceId(), anchorBodyKey, bodyKey)
            || restoreBody
            || anchorBodyKey != null) {
            // Tick cleanup queues this work; subplugin shutdown waits for it.
            PhysicsCommandHandle handle =
                resource.submitCommands(0L, 5, commands -> {
                if (session.getSpaceId() != null && bodyKey != null && anchorBodyKey != null) {
                    commands.destroyJointBetween(controlJointKey, session.getSpaceId(), anchorBodyKey, bodyKey);
                } else if (controlJointKey != null) {
                    commands.destroyJoint(controlJointKey);
                }
                if (restoreBody) {
                    PhysicsBodyType originalBodyType = session.getOriginalBodyType();
                    commands.setBodyType(bodyKey, originalBodyType);
                    if (originalBodyType == PhysicsBodyType.DYNAMIC) {
                        Vector3f releaseVelocity = session.getReleaseVelocity();
                        commands.setBodyVelocity(bodyKey,
                            releaseVelocity.x,
                            releaseVelocity.y,
                            releaseVelocity.z,
                            0.0f,
                            0.0f,
                            0.0f,
                            true);
                    } else {
                        commands.setBodyVelocity(bodyKey,
                            0.0f,
                            0.0f,
                            0.0f,
                            0.0f,
                            0.0f,
                            0.0f,
                            true);
                    }
                }
                if (anchorBodyKey != null) {
                    commands.destroyBody(anchorBodyKey);
                }
            });
            if (waitForCompletion) {
                handle.completionSummary().toCompletableFuture().join();
            }
        }
        session.deactivate();
    }

    private static boolean shouldReleaseJoint(@Nullable JointKey controlJointKey,
        @Nullable SpaceId spaceId,
        @Nullable RigidBodyKey anchorBodyKey,
        @Nullable RigidBodyKey bodyKey) {
        return (spaceId != null && anchorBodyKey != null && bodyKey != null)
            || controlJointKey != null;
    }
}
