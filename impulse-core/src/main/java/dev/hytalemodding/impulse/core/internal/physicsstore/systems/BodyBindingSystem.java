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
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Creates backend bodies from authoritative PhysicsStore body rows.
 */
public final class BodyBindingSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, SpaceBindingSystem.class),
        new SystemDependency<>(Order.AFTER, SpaceSettingsApplicationSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> bindBodies(runtime, identity, restore, chunk);
        store.forEachChunk(systemIndex, collector);
    }

    private static void bindBodies(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body == null) {
                continue;
            }
            UUID bodyUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(bodyUuid)
                || runtime.getBodyHandle(bodyUuid) != null) {
                continue;
            }
            bindBody(runtime,
                identity,
                restore,
                chunk.getReferenceTo(index),
                bodyUuid,
                body,
                chunk.getComponent(index, DynamicsComponent.getComponentType()),
                chunk.getComponent(index, TargetComponent.getComponentType()),
                chunk.getComponent(index, ColliderComponent.getComponentType()),
                chunk.getComponent(index, ShapeComponent.getComponentType()),
                chunk.getComponent(index, MaterialComponent.getComponentType()),
                chunk.getComponent(index, CollisionFilterComponent.getComponentType()));
        }
    }

    private static void bindBody(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyComponent body,
        @Nullable DynamicsComponent dynamics,
        @Nullable TargetComponent target,
        @Nullable ColliderComponent collider,
        @Nullable ShapeComponent shape,
        @Nullable MaterialComponent material,
        @Nullable CollisionFilterComponent filter) {
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(body.getSpaceUuid());
        if (spaceHandle == null) {
            restore.recordSoftSkip("Body references unbound space: " + bodyUuid);
            return;
        }
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, body.getSpaceUuid());
        if (backendRuntime == null) {
            restore.recordSoftSkip("Body references missing backend runtime: " + bodyUuid);
            return;
        }
        if (collider == null || shape == null || material == null || filter == null) {
            restore.recordSoftSkip("Body aggregate is missing collider data: " + bodyUuid);
            return;
        }
        DynamicsComponent bodyDynamics = dynamics != null ? dynamics : new DynamicsComponent();
        TargetComponent initialTarget = target != null ? target : new TargetComponent();
        Vector3f position = target != null ? initialTarget.getPosition() : new Vector3f();
        Quaternionf rotation = target != null ? initialTarget.getRotation() : new Quaternionf();
        PhysicsBodyType bodyType = bodyDynamics.getBodyType();
        float mass = bodyType == PhysicsBodyType.DYNAMIC ? bodyDynamics.getMass() : 0.0f;
        long bodyId = Long.MIN_VALUE;
        try {
            bodyId = backendRuntime.createBody(spaceHandle.value(),
                BackendRuntimeCodes.shapeTypeCode(shape.getShapeType()),
                shape.getHalfExtentX(),
                shape.getHalfExtentY(),
                shape.getHalfExtentZ(),
                shape.getRadius(),
                shape.getHalfHeight(),
                BackendRuntimeCodes.axisCode(shape.getAxis()),
                shape.getGroundY(),
                mass,
                BackendRuntimeCodes.bodyTypeCode(bodyType),
                position.x,
                position.y,
                position.z,
                rotation.x,
                rotation.y,
                rotation.z,
                rotation.w);
            BackendBodyHandle bodyHandle = new BackendBodyHandle(bodyId);
            backendRuntime.setBodyDamping(spaceHandle.value(),
                bodyId,
                bodyDynamics.getLinearDamping(),
                bodyDynamics.getAngularDamping());
            backendRuntime.setBodyFriction(spaceHandle.value(), bodyId, material.getFriction());
            backendRuntime.setBodyRestitution(spaceHandle.value(), bodyId, material.getRestitution());
            backendRuntime.setBodyCollisionFilter(spaceHandle.value(),
                bodyId,
                filter.getCollisionGroup(),
                filter.getCollisionMask());
            backendRuntime.setBodySensor(spaceHandle.value(), bodyId, collider.isSensor());
            if (bodyDynamics.isContinuousCollisionEnabled()
                && backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                backendRuntime.setBodyContinuousCollision(spaceHandle.value(), bodyId, true);
            }
            applyInitialTargetState(backendRuntime, spaceHandle, bodyHandle, bodyType, target);
            runtime.putBodyHandle(bodyUuid, body.getSpaceUuid(), spaceHandle, bodyHandle);
            runtime.putBodyHitMetadata(bodyHandle,
                RigidBodyKey.of(bodyUuid),
                bodyType,
                shape.getShapeType());
            identity.putBodyHandle(bodyHandle, bodyRef);
        } catch (RuntimeException exception) {
            if (bodyId != Long.MIN_VALUE) {
                try {
                    backendRuntime.removeBody(spaceHandle.value(), bodyId);
                } catch (RuntimeException ignored) {
                    // Preserve the original backend failure as the restore status.
                }
            }
            restore.markFailed("PhysicsStore body " + bodyUuid
                + " failed backend binding: " + exception.getMessage());
        }
    }

    private static void applyInitialTargetState(@Nonnull PhysicsBackendRuntime backendRuntime,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle bodyHandle,
        @Nonnull PhysicsBodyType bodyType,
        @Nullable TargetComponent target) {
        if (target == null) {
            return;
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
        } else if (bodyType == PhysicsBodyType.DYNAMIC) {
            backendRuntime.sleepBody(spaceHandle.value(), bodyHandle.value());
        }
    }

    @Nullable
    private static PhysicsBackendRuntime runtimeForSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID spaceUuid) {
        var backendId = runtime.getSpaceBackendId(spaceUuid);
        return backendId != null ? runtime.getRuntime(backendId) : null;
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
