package dev.hytalemodding.impulse.core.plugin.modules.control;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.PhysicsControlRuntimeStates;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsKinematicControlSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsStoreControlSessionMutations;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import java.util.UUID;
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
     * Returns whether the control subplugin is loaded and its component types are registered.
     */
    public static boolean isAvailable() {
        return ControlLifecycle.isEnabled()
            && ImpulseControllableComponent.isComponentTypeRegistered()
            && PhysicsControlSessionComponent.isComponentTypeRegistered();
    }

    /**
     * Returns whether the controller entity currently has an active Impulse control session.
     */
    public static boolean hasSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef) {
        if (!isAvailable()) {
            return false;
        }
        PhysicsControlSessionComponent session =
            store.getComponent(controllerRef, PhysicsControlSessionComponent.getComponentType());
        return session != null && session.isActive();
    }

    /**
     * Starts or replaces the controller entity's Impulse control session from durable entity UUIDs.
     * Prefer the ref overload when the caller already has live PhysicsStore entity refs.
     */
    public static void startSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID anchorBodyUuid,
        @Nullable UUID controlJointUuid,
        @Nullable Ref<EntityStore> targetRef,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        Store<PhysicsStore> physicsStore = physicsStore(store);
        PhysicsThreading.requireWorldThread(physicsStore,
            "resolve PhysicsStore control-session UUIDs");
        Ref<PhysicsStore> bodyRef = requireRef(physicsStore, bodyUuid, "body");
        Ref<PhysicsStore> anchorBodyRef = requireRef(physicsStore, anchorBodyUuid, "anchor body");
        Ref<PhysicsStore> controlJointRef = controlJointUuid != null
            ? requireRef(physicsStore, controlJointUuid, "control joint")
            : null;
        startSession(store,
            controllerRef,
            bodyRef,
            anchorBodyRef,
            controlJointRef,
            targetRef,
            originalBodyType,
            grabDistance,
            viewOffset,
            previousTarget);
    }

    /**
     * Starts or replaces the controller entity's Impulse control session with live PhysicsStore
     * entity refs.
     */
    public static void startSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<PhysicsStore> anchorBodyRef,
        @Nullable Ref<PhysicsStore> controlJointRef,
        @Nullable Ref<EntityStore> targetRef,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        requireAvailable();
        ControlLifecycle.registerStore(store);
        validateControlRefs(bodyRef, anchorBodyRef, controlJointRef);
        ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType =
            PhysicsControlSessionComponent.getComponentType();
        releaseSession(store, controllerRef, sessionType);
        store.putComponent(controllerRef,
            sessionType,
            new PhysicsControlSessionComponent(bodyRef,
                anchorBodyRef,
                controlJointRef,
                targetRef,
                originalBodyType,
                grabDistance,
                viewOffset,
                previousTarget));
        PhysicsControlRuntimeStates.markControlled(bodyRef);
    }

    /**
     * Releases and removes an active Impulse control session.
     *
     * @return false when no session existed
     */
    public static boolean releaseSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef) {
        if (!isAvailable()) {
            return false;
        }
        return releaseSession(store,
            controllerRef,
            PhysicsControlSessionComponent.getComponentType());
    }

    private static boolean releaseSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef,
        @Nonnull ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        PhysicsControlSessionComponent session =
            store.getComponent(controllerRef, sessionType);
        if (session == null) {
            return false;
        }

        releaseSession(store, controllerRef, sessionType, session);
        return true;
    }

    private static void releaseSession(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> controllerRef,
        @Nonnull ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType,
        @Nonnull PhysicsControlSessionComponent session) {
        Ref<PhysicsStore> bodyRef = session.getBodyRef();
        PhysicsKinematicControlSystem.clearMutationState(store, session.getAnchorBodyRef());
        if (bodyRef != null) {
            PhysicsControlRuntimeStates.clearControlled(bodyRef);
        }
        PhysicsStoreControlSessionMutations.applyRelease(store, session);

        session.deactivate();
        store.removeComponent(controllerRef, sessionType);
    }

    @Nonnull
    private static Store<PhysicsStore> physicsStore(@Nonnull Store<EntityStore> store) {
        return PhysicsThreading.store(store.getExternalData().getWorld());
    }

    @Nonnull
    private static Ref<PhysicsStore> requireRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID uuid,
        @Nonnull String role) {
        Ref<PhysicsStore> ref = PhysicsEntities.resolveRef(store, uuid);
        if (ref == null) {
            throw new IllegalArgumentException("PhysicsStore " + role
                + " entity is not loaded for uuid=" + uuid);
        }
        return ref;
    }

    private static void validateControlRefs(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<PhysicsStore> anchorBodyRef,
        @Nullable Ref<PhysicsStore> controlJointRef) {
        Store<PhysicsStore> store = bodyRef.getStore();
        PhysicsThreading.requireWorldThread(store,
            "start PhysicsStore control session");
        requireValidRef(bodyRef, "body");
        requireValidRef(anchorBodyRef, "anchor body");
        if (anchorBodyRef.getStore() != store
            || (controlJointRef != null && controlJointRef.getStore() != store)) {
            throw new IllegalArgumentException("PhysicsStore control-session refs must belong "
                + "to the same PhysicsStore");
        }
        if (controlJointRef != null) {
            requireValidRef(controlJointRef, "control joint");
        }
    }

    private static void requireValidRef(@Nonnull Ref<PhysicsStore> ref,
        @Nonnull String role) {
        if (!ref.isValid()) {
            throw new IllegalArgumentException("PhysicsStore control-session " + role
                + " ref is not valid");
        }
    }

    private static void requireAvailable() {
        ControlLifecycle.requireEnabled();
        if (!ImpulseControllableComponent.isComponentTypeRegistered()
            || !PhysicsControlSessionComponent.isComponentTypeRegistered()) {
            throw new IllegalStateException(
                "Impulse control is disabled. Enable HytaleModding:ImpulseControl to start control sessions.");
        }
    }

}
