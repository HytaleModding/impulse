package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Direct PhysicsStore row mutations for kinematic control lifecycle cleanup.
 */
public final class PhysicsStoreControlSessionMutations {

    private static final Vector3f ZERO = new Vector3f();

    private PhysicsStoreControlSessionMutations() {
    }

    public static void applyRelease(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsControlSessionComponent session) {
        Store<PhysicsStore> physicsStore =
            ((PhysicsStoreWorld) store.getExternalData().getWorld()).getPhysicsStore()
                .getStore();
        PhysicsStoreThreading.requireWorldThread(physicsStore,
            "apply PhysicsStore control-session release mutations");

        Ref<PhysicsStore> controlJointRef = session.getControlJointRef();
        if (controlJointRef != null) {
            disableJoint(physicsStore, controlJointRef);
        }

        Ref<PhysicsStore> bodyRef = session.getBodyRef();
        if (bodyRef != null) {
            restoreControlledBody(physicsStore,
                bodyRef,
                session.getOriginalBodyType(),
                releaseVelocity(session));
        }

        Ref<PhysicsStore> anchorBodyRef = session.getAnchorBodyRef();
        if (anchorBodyRef != null) {
            removeRow(physicsStore, anchorBodyRef);
        }
    }

    private static void restoreControlledBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f releaseVelocity) {
        if (!isValidStoreRef(store, bodyRef)
            || store.getComponent(bodyRef, BodyComponent.getComponentType()) == null) {
            return;
        }
        appendBodyCommand(store, bodyRef, BodyCommandComponent.setType(originalBodyType, true));
        appendBodyCommand(store,
            bodyRef,
            BodyCommandComponent.setVelocity(releaseVelocity, ZERO, true));
    }

    private static void appendBodyCommand(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull BodyCommandComponent command) {
        BodyCommandComponent existing = store.getComponent(bodyRef,
            BodyCommandComponent.getComponentType());
        BodyCommandComponent merged = existing != null ? existing.append(command) : command;
        store.putComponent(bodyRef, BodyCommandComponent.getComponentType(), merged);
    }

    private static void disableJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        if (!isValidStoreRef(store, ref)) {
            return;
        }
        JointComponent joint = store.getComponent(ref, JointComponent.getComponentType());
        if (joint == null) {
            return;
        }
        JointComponent disabled = joint.clone();
        disabled.setEnabled(false);
        store.putComponent(ref, JointComponent.getComponentType(), disabled);
    }

    private static void removeRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        if (!isValidStoreRef(store, ref)) {
            return;
        }
        UuidComponent uuid = store.getComponent(ref, UuidComponent.getComponentType());
        if (uuid != null) {
            store.getResource(PhysicsIdentityIndexResource.getResourceType())
                .removeUuid(uuid.getUuid(), ref);
        }
        store.removeEntity(ref, store.getRegistry().newHolder(), RemoveReason.REMOVE);
    }

    private static boolean isValidStoreRef(@Nonnull Store<PhysicsStore> store,
        @Nullable Ref<PhysicsStore> ref) {
        return ref != null && ref.getStore() == store && ref.isValid();
    }

    @Nonnull
    private static Vector3f releaseVelocity(@Nonnull PhysicsControlSessionComponent session) {
        if (session.getOriginalBodyType() == PhysicsBodyType.DYNAMIC) {
            return session.getReleaseVelocity();
        }
        return new Vector3f();
    }
}
