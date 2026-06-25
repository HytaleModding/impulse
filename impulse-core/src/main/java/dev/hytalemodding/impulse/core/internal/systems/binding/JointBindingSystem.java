package dev.hytalemodding.impulse.core.internal.systems.binding;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsStoreSystemSupport;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Binds joint entities once both endpoint bodies are bound.
 */
public final class JointBindingSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, ColliderBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> bindChunk(store, runtime, restore, chunk);
        store.forEachChunk(systemIndex, collector);
    }

    private static void bindChunk(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            JointComponent joint = chunk.getComponent(index, JointComponent.getComponentType());
            if (joint == null) {
                continue;
            }
            UUID jointUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(jointUuid)) {
                continue;
            }
            Ref<PhysicsStore> jointRef = chunk.getReferenceTo(index);
            if (!joint.isEnabled()) {
                removeJoint(runtime, jointRef);
                continue;
            }
            BackendJointHandle existing = runtime.getJointHandle(jointRef);
            if (existing != null) {
                if (!endpointsBound(store, runtime, joint)) {
                    removeJoint(runtime, jointRef);
                }
                continue;
            }
            bindJoint(store, runtime, restore, jointRef, jointUuid, joint);
        }
    }

    private static void bindJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull UUID jointUuid,
        @Nonnull JointComponent joint) {
        Ref<PhysicsStore> spaceRef = resolveSpaceRef(store, joint);
        Ref<PhysicsStore> bodyARef = resolveBodyARef(store, joint);
        Ref<PhysicsStore> bodyBRef = resolveBodyBRef(store, joint);
        BackendSpaceHandle spaceHandle = spaceRef != null ? runtime.getSpaceHandle(spaceRef) : null;
        BackendBodyHandle bodyA = bodyARef != null ? runtime.getBodyHandle(bodyARef) : null;
        BackendBodyHandle bodyB = bodyBRef != null ? runtime.getBodyHandle(bodyBRef) : null;
        BackendSpaceHandle bodyASpace = bodyARef != null ? runtime.getBodySpaceHandle(bodyARef) : null;
        BackendSpaceHandle bodyBSpace = bodyBRef != null ? runtime.getBodySpaceHandle(bodyBRef) : null;
        BackendId backendId = spaceRef != null ? runtime.getSpaceBackendId(spaceRef) : null;
        BackendId bodyABackendId = bodyARef != null ? runtime.getBodyBackendId(bodyARef) : null;
        BackendId bodyBBackendId = bodyBRef != null ? runtime.getBodyBackendId(bodyBRef) : null;
        PhysicsBackendRuntime backendRuntime = backendId != null ? runtime.getRuntime(backendId) : null;
        if (spaceHandle == null || bodyA == null || bodyB == null || backendRuntime == null) {
            restore.recordSoftSkip("Joint references unbound endpoint: " + jointUuid);
            return;
        }
        if (bodyASpace == null
            || bodyBSpace == null
            || bodyABackendId == null
            || bodyBBackendId == null
            || !bodyABackendId.equals(backendId)
            || !bodyBBackendId.equals(backendId)
            || bodyASpace.value() != spaceHandle.value()
            || bodyBSpace.value() != spaceHandle.value()) {
            restore.recordSoftSkip("Joint endpoints are not in the joint space: " + jointUuid);
            return;
        }
        Vector3f anchorA = joint.getAnchorA();
        Vector3f anchorB = joint.getAnchorB();
        Vector3f axis = joint.getAxis();
        long jointId = Long.MIN_VALUE;
        try {
            jointId = backendRuntime.createJoint(spaceHandle.value(),
                BackendRuntimeCodes.jointTypeCode(BackendJointType.valueOf(joint.getType().name())),
                bodyA.value(),
                bodyB.value(),
                anchorA.x,
                anchorA.y,
                anchorA.z,
                anchorB.x,
                anchorB.y,
                anchorB.z,
                axis.x,
                axis.y,
                axis.z,
                joint.getSpringRestLength(),
                joint.getSpringStiffness(),
                joint.getSpringDamping(),
                joint.getLowerLimit(),
                joint.getUpperLimit(),
                joint.isMotorEnabled(),
                joint.getMotorTargetVelocity(),
                joint.getMotorMaxForce());
            BackendJointHandle handle = new BackendJointHandle(jointId);
            runtime.putJointHandle(jointRef, spaceRef, spaceHandle, handle);
            runtime.putJointMetadata(backendId, spaceHandle, handle, jointUuid, jointRef);
        } catch (RuntimeException exception) {
            if (jointId != Long.MIN_VALUE) {
                try {
                    backendRuntime.removeJoint(spaceHandle.value(), jointId);
                } catch (RuntimeException ignored) {
                    // Preserve the original backend failure as the restore status.
                }
            }
            restore.markFailed("PhysicsStore joint " + jointUuid
                + " failed backend binding: " + exception.getMessage());
        }
    }

    private static boolean endpointsBound(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull JointComponent joint) {
        Ref<PhysicsStore> spaceRef = resolveSpaceRef(store, joint);
        Ref<PhysicsStore> bodyARef = resolveBodyARef(store, joint);
        Ref<PhysicsStore> bodyBRef = resolveBodyBRef(store, joint);
        BackendSpaceHandle spaceHandle = spaceRef != null ? runtime.getSpaceHandle(spaceRef) : null;
        BackendSpaceHandle bodyASpace = bodyARef != null ? runtime.getBodySpaceHandle(bodyARef) : null;
        BackendSpaceHandle bodyBSpace = bodyBRef != null ? runtime.getBodySpaceHandle(bodyBRef) : null;
        BackendId backendId = spaceRef != null ? runtime.getSpaceBackendId(spaceRef) : null;
        BackendId bodyABackendId = bodyARef != null ? runtime.getBodyBackendId(bodyARef) : null;
        BackendId bodyBBackendId = bodyBRef != null ? runtime.getBodyBackendId(bodyBRef) : null;
        return spaceHandle != null
            && backendId != null
            && bodyARef != null
            && bodyBRef != null
            && runtime.getBodyHandle(bodyARef) != null
            && runtime.getBodyHandle(bodyBRef) != null
            && bodyASpace != null
            && bodyBSpace != null
            && backendId.equals(bodyABackendId)
            && backendId.equals(bodyBBackendId)
            && bodyASpace.value() == spaceHandle.value()
            && bodyBSpace.value() == spaceHandle.value();
    }

    @Nullable
    private static Ref<PhysicsStore> resolveSpaceRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull JointComponent joint) {
        Ref<PhysicsStore> spaceRef = resolveRef(store, joint.getSpaceUuid(), joint.getSpaceRef());
        joint.setSpaceRef(spaceRef);
        return spaceRef;
    }

    @Nullable
    private static Ref<PhysicsStore> resolveBodyARef(@Nonnull Store<PhysicsStore> store,
        @Nonnull JointComponent joint) {
        Ref<PhysicsStore> bodyRef = resolveRef(store, joint.getBodyAUuid(), joint.getBodyARef());
        joint.setBodyARef(bodyRef);
        return bodyRef;
    }

    @Nullable
    private static Ref<PhysicsStore> resolveBodyBRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull JointComponent joint) {
        Ref<PhysicsStore> bodyRef = resolveRef(store, joint.getBodyBUuid(), joint.getBodyBRef());
        joint.setBodyBRef(bodyRef);
        return bodyRef;
    }

    @Nullable
    private static Ref<PhysicsStore> resolveRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID uuid,
        @Nullable Ref<PhysicsStore> current) {
        if (PhysicsStoreSystemSupport.isNil(uuid)) {
            return null;
        }
        UuidComponent currentUuid = PhysicsStoreSystemSupport.component(store,
            current,
            UuidComponent.getComponentType());
        Ref<PhysicsStore> resolved = currentUuid != null && uuid.equals(currentUuid.getUuid())
            ? current
            : store.getExternalData().getRefFromUUID(uuid);
        return resolved != null && resolved.getStore() == store && resolved.isValid()
            ? resolved
            : null;
    }

    private static void removeJoint(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Ref<PhysicsStore> jointRef) {
        BackendJointHandle handle = runtime.getJointHandle(jointRef);
        if (handle == null) {
            return;
        }
        BackendSpaceHandle spaceHandle = runtime.getJointSpaceHandle(jointRef);
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForJointRef(jointRef);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeJoint(spaceHandle.value(), handle.value());
        }
        runtime.removeJointHandle(jointRef);
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.uuidQuery();
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
