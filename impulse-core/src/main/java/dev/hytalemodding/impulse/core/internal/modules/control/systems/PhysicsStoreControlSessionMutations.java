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
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import java.util.UUID;
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
        PhysicsIdentityIndexResource identity = physicsStore.getResource(
            PhysicsIdentityIndexResource.getResourceType());

        JointKey controlJointKey = session.getControlJointKey();
        if (controlJointKey != null) {
            disableJoint(physicsStore, identity, controlJointKey.value());
        }

        UUID bodyUuid = session.getBodyUuid();
        if (bodyUuid != null) {
            restoreControlledBody(physicsStore,
                identity,
                bodyUuid,
                session.getOriginalBodyType(),
                releaseVelocity(session));
        }

        UUID anchorBodyUuid = session.getAnchorBodyUuid();
        if (anchorBodyUuid != null) {
            removeRow(physicsStore, identity, anchorBodyUuid, refForUuid(identity, anchorBodyUuid));
        }
    }

    private static void restoreControlledBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID bodyUuid,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f releaseVelocity) {
        Ref<PhysicsStore> bodyRef = refForUuid(identity, bodyUuid);
        if (bodyRef == null || store.getComponent(bodyRef, BodyComponent.getComponentType()) == null) {
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

    @Nullable
    private static Ref<PhysicsStore> refForUuid(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID uuid) {
        Ref<PhysicsStore> ref = identity.getByUuid(uuid);
        return ref != null && ref.isValid() ? ref : null;
    }

    private static void disableJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID jointUuid) {
        Ref<PhysicsStore> ref = refForUuid(identity, jointUuid);
        JointComponent joint = ref != null
            ? store.getComponent(ref, JointComponent.getComponentType())
            : null;
        if (ref == null || joint == null) {
            return;
        }
        JointComponent disabled = joint.clone();
        disabled.setEnabled(false);
        store.putComponent(ref, JointComponent.getComponentType(), disabled);
    }

    private static void removeRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID uuid,
        @Nullable Ref<PhysicsStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        identity.removeUuid(uuid, ref);
        store.removeEntity(ref, store.getRegistry().newHolder(), RemoveReason.REMOVE);
    }

    @Nonnull
    private static Vector3f releaseVelocity(@Nonnull PhysicsControlSessionComponent session) {
        if (session.getOriginalBodyType() == PhysicsBodyType.DYNAMIC) {
            return session.getReleaseVelocity();
        }
        return new Vector3f();
    }
}
