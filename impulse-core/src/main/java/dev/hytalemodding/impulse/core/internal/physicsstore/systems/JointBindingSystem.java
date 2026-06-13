package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

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
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Binds joint rows once both endpoint bodies are bound.
 */
public final class JointBindingSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, ColliderBindingSystem.class),
        new SystemDependency<>(Order.BEFORE, TerrainColliderBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> bindChunk(runtime, identity, restore, chunk);
        store.forEachChunk(systemIndex, collector);
    }

    private static void bindChunk(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            JointComponent joint = chunk.getComponent(index, JointComponent.getComponentType());
            if (joint == null) {
                continue;
            }
            UUID jointUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(jointUuid)
                || runtime.getJointHandle(jointUuid) != null) {
                continue;
            }
            bindJoint(runtime, identity, restore, chunk.getReferenceTo(index), jointUuid, joint);
        }
    }

    private static void bindJoint(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull UUID jointUuid,
        @Nonnull JointComponent joint) {
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(joint.getSpaceUuid());
        BackendBodyHandle bodyA = runtime.getBodyHandle(joint.getBodyAUuid());
        BackendBodyHandle bodyB = runtime.getBodyHandle(joint.getBodyBUuid());
        var backendId = runtime.getSpaceBackendId(joint.getSpaceUuid());
        PhysicsBackendRuntime backendRuntime = backendId != null ? runtime.getRuntime(backendId) : null;
        if (spaceHandle == null || bodyA == null || bodyB == null || backendRuntime == null) {
            restore.recordSoftSkip("Joint references unbound endpoint: " + jointUuid);
            return;
        }
        Vector3f anchorA = joint.getAnchorA();
        Vector3f anchorB = joint.getAnchorB();
        Vector3f axis = joint.getAxis();
        long jointId = backendRuntime.createJoint(spaceHandle.value(),
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
        runtime.putJointHandle(jointUuid, handle);
        identity.putJointHandle(handle, jointRef);
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.UUID_QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
