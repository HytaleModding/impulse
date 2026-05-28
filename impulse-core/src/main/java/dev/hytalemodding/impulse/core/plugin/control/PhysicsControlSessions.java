package dev.hytalemodding.impulse.core.plugin.control;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.control.PhysicsControlJointResolver;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.joint.PhysicsJointId;
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
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodyId anchorBodyId,
        @Nullable Ref<EntityStore> targetRef,
        @Nullable SpaceId spaceId,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        startSession(store,
            controllerRef,
            bodyId,
            anchorBodyId,
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
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodyId anchorBodyId,
        @Nullable PhysicsJointId controlJointId,
        @Nullable Ref<EntityStore> targetRef,
        @Nullable SpaceId spaceId,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        releaseSession(resource, store, controllerRef);
        store.putComponent(controllerRef,
            PhysicsControlSessionComponent.getComponentType(),
            new PhysicsControlSessionComponent(bodyId,
                anchorBodyId,
                controlJointId,
                targetRef,
                spaceId,
                originalBodyType,
                grabDistance,
                viewOffset,
                previousTarget));
        resource.markBodyControlled(bodyId);
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
        PhysicsBodyId bodyId = session.getBodyId();
        PhysicsBodyId anchorBodyId = session.getAnchorBodyId();
        PhysicsJointId controlJointId = session.getControlJointId();
        SpaceId spaceId = session.getSpaceId();
        if (bodyId != null) {
            resource.clearControlledBody(bodyId);
        }
        if (bodyId != null && resource.getBodyRegistrationView(bodyId) != null) {
            PhysicsBodyType originalBodyType = session.getOriginalBodyType();
            resource.runOnPhysicsOwner("release grabbed physics body",
                access -> {
                    boolean removedControlJoint =
                        controlJointId != null && resource.removeJoint(controlJointId);
                    PhysicsSpace space = spaceId != null ? access.getSpace(spaceId) : null;
                    if (!removedControlJoint && space != null && anchorBodyId != null) {
                        PhysicsControlJointResolver.removeControlJoint(access,
                            space,
                            bodyId,
                            anchorBodyId);
                    }
                    PhysicsBody body = access.getBody(bodyId);
                    if (body == null) {
                        return;
                    }
                    body.setBodyType(originalBodyType);
                    if (originalBodyType == PhysicsBodyType.DYNAMIC) {
                        body.setLinearVelocity(session.getReleaseVelocity());
                    } else {
                        body.setLinearVelocity(0.0f, 0.0f, 0.0f);
                    }
                    body.activate();
                });
        } else if (bodyId != null && anchorBodyId != null && spaceId != null) {
            resource.runOnPhysicsOwner("release grabbed physics joint",
                access -> {
                    boolean removedControlJoint =
                        controlJointId != null && resource.removeJoint(controlJointId);
                    PhysicsSpace space = access.getSpace(spaceId);
                    if (!removedControlJoint && space != null) {
                        PhysicsControlJointResolver.removeControlJoint(access,
                            space,
                            bodyId,
                            anchorBodyId);
                    }
                });
        }
        if (anchorBodyId != null) {
            resource.destroyBody(anchorBodyId);
        }

        session.deactivate();
        store.removeComponent(controllerRef, PhysicsControlSessionComponent.getComponentType());
    }
}
