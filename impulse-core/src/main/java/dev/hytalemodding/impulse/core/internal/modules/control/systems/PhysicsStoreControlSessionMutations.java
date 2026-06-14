package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Direct PhysicsStore row mutations for kinematic control lifecycle cleanup.
 */
public final class PhysicsStoreControlSessionMutations {

    private static final Quaternionf IDENTITY_ROTATION = new Quaternionf();
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
        PhysicsRuntimeResource runtime = physicsStore.getResource(PhysicsRuntimeResource.getResourceType());

        JointKey controlJointKey = session.getControlJointKey();
        if (controlJointKey != null) {
            removeJoint(physicsStore, identity, runtime, controlJointKey.value());
        }

        RigidBodyKey bodyKey = session.getBodyKey();
        if (bodyKey != null) {
            restoreControlledBody(physicsStore,
                identity,
                bodyKey.value(),
                session.getOriginalBodyType(),
                releaseVelocity(session));
        }

        RigidBodyKey anchorBodyKey = session.getAnchorBodyKey();
        if (anchorBodyKey != null) {
            removeBody(physicsStore, identity, runtime, anchorBodyKey.value());
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
        store.putComponent(bodyRef, TargetComponent.getComponentType(), releaseTarget(releaseVelocity));
    }

    @Nonnull
    private static TargetComponent releaseTarget(@Nonnull Vector3f releaseVelocity) {
        TargetComponent target = new TargetComponent();
        target.setActive(true);
        target.setPosition(ZERO);
        target.setRotation(IDENTITY_ROTATION);
        target.setLinearVelocity(releaseVelocity);
        target.setAngularVelocity(ZERO);
        target.setTransformEnabled(false);
        target.setVelocityEnabled(true);
        target.setActivate(true);
        return target;
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

    private static void removeBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID bodyUuid) {
        Ref<PhysicsStore> ref = refForUuid(identity, bodyUuid);
        removeBodyBackend(identity, runtime, bodyUuid);
        removeRow(store, identity, bodyUuid, ref);
    }

    private static void removeJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID jointUuid) {
        Ref<PhysicsStore> ref = refForUuid(identity, jointUuid);
        JointComponent joint = ref != null
            ? store.getComponent(ref, JointComponent.getComponentType())
            : null;
        removeJointBackend(identity, runtime, jointUuid, joint);
        removeRow(store, identity, jointUuid, ref);
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

    private static void removeBodyBackend(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID bodyUuid) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyUuid);
        if (bodyHandle == null) {
            return;
        }
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(bodyUuid);
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, spaceHandle);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeBody(spaceHandle.value(), bodyHandle.value());
        }
        identity.removeBodyHandle(bodyHandle);
        runtime.removeBodyHandle(bodyUuid);
    }

    private static void removeJointBackend(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID jointUuid,
        @Nullable JointComponent joint) {
        BackendJointHandle jointHandle = runtime.getJointHandle(jointUuid);
        if (jointHandle == null) {
            return;
        }
        BackendSpaceHandle spaceHandle = runtime.getJointSpaceHandle(jointUuid);
        if (spaceHandle == null && joint != null) {
            spaceHandle = runtime.getSpaceHandle(joint.getSpaceUuid());
        }
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, spaceHandle);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeJoint(spaceHandle.value(), jointHandle.value());
        }
        identity.removeJointHandle(jointHandle);
        runtime.removeJointHandle(jointUuid);
    }

    @Nullable
    private static PhysicsBackendRuntime runtimeForSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nullable BackendSpaceHandle spaceHandle) {
        if (spaceHandle == null) {
            return null;
        }
        final PhysicsBackendRuntime[] resolved = new PhysicsBackendRuntime[1];
        runtime.forEachSpaceBinding((_, _, handle, backendRuntime) -> {
            if (handle.value() == spaceHandle.value()) {
                resolved[0] = backendRuntime;
            }
        });
        return resolved[0];
    }

    @Nonnull
    private static Vector3f releaseVelocity(@Nonnull PhysicsControlSessionComponent session) {
        if (session.getOriginalBodyType() == PhysicsBodyType.DYNAMIC) {
            return session.getReleaseVelocity();
        }
        return new Vector3f();
    }
}
