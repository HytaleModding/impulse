package dev.hytalemodding.impulse.core.plugin.modules.control;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandRecorder;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Public stateless helper for Impulse-managed kinematic control sessions.
 */
public final class PhysicsControlSessions {

    private PhysicsControlSessions() {
    }

    /**
     * Returns whether the controller entity currently has an active Impulse control session.
     */
    public static boolean hasSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef) {
        PhysicsControlSessionComponent session =
            store.getComponent(controllerRef, PhysicsControlSessionComponent.getComponentType());
        return session != null && session.isActive();
    }

    /**
     * Starts or replaces the controller entity's Impulse control session and marks the body as
     * externally controlled.
     */
    public static void startSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyKey anchorBodyKey,
        @Nullable Ref<EntityStore> targetRef,
        @Nullable SpaceId spaceId,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        startSession(store,
            controllerRef,
            bodyKey,
            anchorBodyKey,
            null,
            targetRef,
            spaceId,
            originalBodyType,
            grabDistance,
            viewOffset,
            previousTarget);
    }

    /**
     * Starts or replaces the controller entity's Impulse control session with the created control
     * joint handle.
     */
    public static void startSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyKey anchorBodyKey,
        @Nullable JointKey controlJointKey,
        @Nullable Ref<EntityStore> targetRef,
        @Nullable SpaceId spaceId,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        ControlLifecycle.requireEnabled();
        ControlLifecycle.registerStore(store);
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        releaseSession(resource, store, controllerRef);
        store.putComponent(controllerRef,
            PhysicsControlSessionComponent.getComponentType(),
            new PhysicsControlSessionComponent(bodyKey,
                anchorBodyKey,
                controlJointKey,
                targetRef,
                spaceId,
                originalBodyType,
                grabDistance,
                viewOffset,
                previousTarget));
        resource.markBodyControlled(bodyKey);
    }

    /**
     * Releases and removes an active Impulse control session.
     *
     * @return false when no session existed
     */
    public static boolean releaseSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef) {
        return releaseSession(PhysicsWorldRuntimeResource.require(store), store, controllerRef);
    }

    private static boolean releaseSession(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef) {
        PhysicsControlSessionComponent session =
            store.getComponent(controllerRef, PhysicsControlSessionComponent.getComponentType());
        if (session == null) {
            return false;
        }

        releaseSession(resource, store, controllerRef, session);
        return true;
    }

    private static void releaseSession(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef,
        @Nonnull PhysicsControlSessionComponent session) {
        RigidBodyKey bodyKey = session.getBodyKey();
        RigidBodyKey anchorBodyKey = session.getAnchorBodyKey();
        JointKey controlJointKey = session.getControlJointKey();
        SpaceId spaceId = session.getSpaceId();
        if (bodyKey != null) {
            resource.clearControlledBody(bodyKey);
        }
        boolean releaseJoint = (spaceId != null && anchorBodyKey != null && bodyKey != null)
            || controlJointKey != null;
        boolean restoreBody = bodyKey != null && resource.getBodyRegistrationView(bodyKey) != null;
        if (releaseJoint || restoreBody || anchorBodyKey != null) {
            /*
             * Explicit release/start helpers keep synchronous semantics so command handlers can
             * report replacement state immediately. Tick-driven cleanup uses
             * PhysicsControlSessionCleanup and does not join the owner lane.
             */
            resource.submitCommands(0L, 4, commands -> {
                addJointReleaseCommand(commands, controlJointKey, spaceId, anchorBodyKey, bodyKey);
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
                        commands.setBodyVelocity(bodyKey, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, true);
                    }
                }
                if (anchorBodyKey != null) {
                    commands.destroyBody(anchorBodyKey);
                }
            }).completionSummary().toCompletableFuture().join();
        }

        session.deactivate();
        store.removeComponent(controllerRef, PhysicsControlSessionComponent.getComponentType());
    }

    private static void addJointReleaseCommand(@Nonnull PhysicsCommandRecorder commands,
        @Nullable JointKey controlJointKey,
        @Nullable SpaceId spaceId,
        @Nullable RigidBodyKey anchorBodyKey,
        @Nullable RigidBodyKey bodyKey) {
        if (spaceId != null && anchorBodyKey != null && bodyKey != null) {
            commands.destroyJointBetween(controlJointKey, spaceId, anchorBodyKey, bodyKey);
        } else if (controlJointKey != null) {
            commands.destroyJoint(controlJointKey);
        }
    }
}
