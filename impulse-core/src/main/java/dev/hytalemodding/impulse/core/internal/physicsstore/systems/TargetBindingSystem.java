package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.PendingBodyOperation;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Applies copied kinematic target state to bound backend bodies before step submission.
 */
public final class TargetBindingSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, TerrainColliderBindingSystem.class),
        new SystemDependency<>(Order.AFTER, BodyCommandApplicationSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> applyTargets(runtime, chunk);
        store.forEachChunk(systemIndex, collector);
        applyPendingBodyOperations(runtime, restore);
    }

    private static void applyTargets(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            TargetComponent target = chunk.getComponent(index, TargetComponent.getComponentType());
            if (target == null || !target.isActive()) {
                continue;
            }
            UUID bodyUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyUuid);
            BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(bodyUuid);
            if (bodyHandle == null || spaceHandle == null) {
                continue;
            }
            PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, spaceHandle);
            if (backendRuntime == null) {
                continue;
            }
            if (target.isTransformEnabled()) {
                Vector3f position = target.getPosition();
                Quaternionf rotation = target.getRotation();
                backendRuntime.setBodyTransform(spaceHandle.value(),
                    bodyHandle.value(),
                    position.x,
                    position.y,
                    position.z,
                    rotation.x,
                    rotation.y,
                    rotation.z,
                    rotation.w);
            }
            if (target.isVelocityEnabled()) {
                Vector3f linearVelocity = target.getLinearVelocity();
                Vector3f angularVelocity = target.getAngularVelocity();
                backendRuntime.setBodyVelocity(spaceHandle.value(),
                    bodyHandle.value(),
                    linearVelocity.x,
                    linearVelocity.y,
                    linearVelocity.z,
                    angularVelocity.x,
                    angularVelocity.y,
                    angularVelocity.z);
            }
            if (target.isActivate()) {
                backendRuntime.activateBody(spaceHandle.value(), bodyHandle.value());
            }
        }
    }

    private static void applyPendingBodyOperations(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore) {
        for (PendingBodyOperation operation : runtime.drainPendingBodyOperations()) {
            BackendSpaceHandle spaceHandle = operation.spaceHandle();
            BackendBodyHandle bodyHandle = operation.bodyHandle();
            if (spaceHandle == null || bodyHandle == null) {
                spaceHandle = runtime.getBodySpaceHandle(operation.bodyUuid());
                bodyHandle = runtime.getBodyHandle(operation.bodyUuid());
            }
            if (spaceHandle == null || bodyHandle == null) {
                restore.recordSoftSkip("Pending body operation body is unbound: "
                    + operation.bodyUuid());
                continue;
            }
            PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, spaceHandle);
            if (backendRuntime == null) {
                restore.recordSoftSkip("Pending body operation backend runtime is missing: "
                    + operation.bodyUuid());
                continue;
            }
            int spaceId = spaceHandle.value();
            long bodyId = bodyHandle.value();
            switch (operation.kind()) {
                case WAKE -> backendRuntime.activateBody(spaceId, bodyId);
                case SLEEP -> backendRuntime.sleepBody(spaceId, bodyId);
                case IMPULSE -> applyImpulse(backendRuntime, spaceId, bodyId, operation, false);
                case TORQUE_IMPULSE -> applyImpulse(backendRuntime, spaceId, bodyId, operation, true);
                case FORCE -> applyForce(backendRuntime, spaceId, bodyId, operation, false);
                case TORQUE -> applyForce(backendRuntime, spaceId, bodyId, operation, true);
            }
        }
    }

    private static void applyImpulse(@Nonnull PhysicsBackendRuntime backendRuntime,
        int spaceId,
        long bodyId,
        @Nonnull PendingBodyOperation operation,
        boolean torque) {
        backendRuntime.applyBodyImpulse(spaceId,
            bodyId,
            operation.x(),
            operation.y(),
            operation.z(),
            operation.hasOffset(),
            operation.offsetX(),
            operation.offsetY(),
            operation.offsetZ(),
            torque);
        backendRuntime.activateBody(spaceId, bodyId);
    }

    private static void applyForce(@Nonnull PhysicsBackendRuntime backendRuntime,
        int spaceId,
        long bodyId,
        @Nonnull PendingBodyOperation operation,
        boolean torque) {
        backendRuntime.applyBodyForce(spaceId,
            bodyId,
            operation.x(),
            operation.y(),
            operation.z(),
            operation.hasOffset(),
            operation.offsetX(),
            operation.offsetY(),
            operation.offsetZ(),
            torque);
        backendRuntime.activateBody(spaceId, bodyId);
    }

    private static PhysicsBackendRuntime runtimeForSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull BackendSpaceHandle spaceHandle) {
        final PhysicsBackendRuntime[] resolved = new PhysicsBackendRuntime[1];
        runtime.forEachSpaceBinding((_, _, handle, backendRuntime) -> {
            if (handle.value() == spaceHandle.value()) {
                resolved[0] = backendRuntime;
            }
        });
        return resolved[0];
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
