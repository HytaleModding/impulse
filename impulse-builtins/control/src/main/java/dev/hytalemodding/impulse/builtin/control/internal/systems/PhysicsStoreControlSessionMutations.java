package dev.hytalemodding.impulse.builtin.control.internal.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.builtin.control.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreRowCleanup;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Direct PhysicsStore entity mutations for kinematic control lifecycle cleanup.
 */
public final class PhysicsStoreControlSessionMutations {

    private static final Vector3f ZERO = new Vector3f();

    private PhysicsStoreControlSessionMutations() {
    }

    public static void applyRelease(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsControlSessionComponent session) {
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(
            store.getExternalData().getWorld());
        PhysicsThreading.requireWorldThread(physicsStore,
            "apply PhysicsStore control-session release mutations");

        Ref<PhysicsStore> controlJointRef = session.getControlJointRef();
        if (controlJointRef != null) {
            removeJointRow(physicsStore, controlJointRef);
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
            removeBodyRow(physicsStore, anchorBodyRef);
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
        PhysicsBodies.appendCommand(store,
            bodyRef,
            BodyCommandComponent.setType(originalBodyType, true));
        PhysicsBodies.appendCommand(store,
            bodyRef,
            BodyCommandComponent.setVelocity(releaseVelocity, ZERO, true));
    }

    private static void removeJointRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        if (!isValidStoreRef(store, ref)) {
            return;
        }
        UuidComponent uuid = store.getComponent(ref, UuidComponent.getComponentType());
        if (uuid == null) {
            removeRow(store, ref);
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsStoreRowCleanup.removeRuntimeJoint(store, runtime, uuid.getUuid(), ref);
        PhysicsStoreRowCleanup.removeJointEntity(store, uuid.getUuid(), ref);
    }

    private static void removeBodyRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        if (!isValidStoreRef(store, ref)) {
            return;
        }
        UuidComponent uuid = store.getComponent(ref, UuidComponent.getComponentType());
        if (uuid == null) {
            removeRow(store, ref);
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsStoreRowCleanup.removeRuntimeBody(store, runtime, uuid.getUuid(), ref, null);
        PhysicsStoreRowCleanup.removeBodyEntity(store, uuid.getUuid(), ref);
    }

    private static void removeRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        if (!isValidStoreRef(store, ref)) {
            return;
        }
        UuidComponent uuid = store.getComponent(ref, UuidComponent.getComponentType());
        if (uuid != null) {
            store.getExternalData().removeRefForUUID(uuid.getUuid(), ref);
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
