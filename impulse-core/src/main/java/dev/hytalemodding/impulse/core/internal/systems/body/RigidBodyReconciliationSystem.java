package dev.hytalemodding.impulse.core.internal.systems.body;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKeyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKinematicTargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyLifecycleComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyMassComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyMaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyOwnershipComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyPersistenceComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodySpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyTypeComponent;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Reconciles ECS rigid-body components into existing physics commands.
 */
public class RigidBodyReconciliationSystem extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, RigidBodyKeyComponent> KEY_TYPE =
        RigidBodyKeyComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodySpaceComponent> SPACE_TYPE =
        RigidBodySpaceComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyShapeComponent> SHAPE_TYPE =
        RigidBodyShapeComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyTypeComponent> BODY_TYPE =
        RigidBodyTypeComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyMassComponent> MASS_TYPE =
        RigidBodyMassComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyMaterialComponent> MATERIAL_TYPE =
        RigidBodyMaterialComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyCollisionComponent> COLLISION_TYPE =
        RigidBodyCollisionComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyPersistenceComponent> PERSISTENCE_TYPE =
        RigidBodyPersistenceComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyOwnershipComponent> OWNERSHIP_TYPE =
        RigidBodyOwnershipComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyKinematicTargetComponent> KINEMATIC_TARGET_TYPE =
        RigidBodyKinematicTargetComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyLifecycleComponent> LIFECYCLE_TYPE =
        RigidBodyLifecycleComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private static final Query<EntityStore> QUERY = KEY_TYPE;

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        RigidBodyKeyComponent key = chunk.getComponent(index, KEY_TYPE);
        if (key == null) {
            return;
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        RigidBodyLifecycleComponent lifecycle = chunk.getComponent(index, LIFECYCLE_TYPE);
        RigidBodyOwnershipComponent ownership = chunk.getComponent(index, OWNERSHIP_TYPE);
        RigidBodyOwnershipComponent.Ownership ownershipValue = ownership != null
            ? ownership.getOwnership()
            : RigidBodyOwnershipComponent.Ownership.ENTITY_OWNED;
        RigidBodyKey bodyKey = key.getBodyKey();
        boolean registeredOrPending = resource.hasPublishedOrPendingBodyRegistration(bodyKey);

        if (registeredOrPending) {
            reconcileExistingBody(ref,
                chunk,
                index,
                commandBuffer,
                resource,
                bodyKey,
                ownershipValue,
                lifecycle);
            return;
        }

        RigidBodySpawnPlan plan;
        try {
            plan = RigidBodySpawnPlan.create(key,
                chunk.getComponent(index, SPACE_TYPE),
                chunk.getComponent(index, SHAPE_TYPE),
                chunk.getComponent(index, BODY_TYPE),
                chunk.getComponent(index, MASS_TYPE),
                chunk.getComponent(index, MATERIAL_TYPE),
                chunk.getComponent(index, COLLISION_TYPE),
                chunk.getComponent(index, PERSISTENCE_TYPE),
                ownership);
        } catch (IllegalArgumentException exception) {
            commandBuffer.putComponent(ref,
                LIFECYCLE_TYPE,
                RigidBodyLifecycleComponent.failed(bodyKey, ownershipValue, exception.getMessage()));
            clearAttachment(ref, chunk.getComponent(index, ATTACHMENT_TYPE), commandBuffer);
            return;
        }

        if (!plan.shouldSpawnBody()) {
            commandBuffer.putComponent(ref,
                LIFECYCLE_TYPE,
                RigidBodyLifecycleComponent.failed(bodyKey,
                    plan.ownership(),
                    "Detached rigid body key is not registered or pending"));
            clearAttachment(ref, chunk.getComponent(index, ATTACHMENT_TYPE), commandBuffer);
            return;
        }

        submitSpawn(resource, chunk, index, plan);
        commandBuffer.putComponent(ref,
            LIFECYCLE_TYPE,
            RigidBodyLifecycleComponent.pending(plan.bodyKey(), plan.ownership()));
        reconcileAttachment(ref,
            plan,
            chunk.getComponent(index, ATTACHMENT_TYPE),
            commandBuffer);
    }

    private static void reconcileExistingBody(@Nonnull Ref<EntityStore> ref,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyOwnershipComponent.Ownership ownership,
        @Nullable RigidBodyLifecycleComponent lifecycle) {
        RigidBodySpaceComponent space = chunk.getComponent(index, SPACE_TYPE);
        if (space == null || space.getSpaceId() == null) {
            commandBuffer.putComponent(ref,
                LIFECYCLE_TYPE,
                RigidBodyLifecycleComponent.failed(bodyKey,
                    ownership,
                    "RigidBodySpaceComponent must hold a positive explicit SpaceId"));
            return;
        }
        RigidBodySpawnPlan plan;
        try {
            plan = RigidBodySpawnPlan.create(
                new RigidBodyKeyComponent(bodyKey),
                space,
                chunk.getComponent(index, SHAPE_TYPE),
                chunk.getComponent(index, BODY_TYPE),
                chunk.getComponent(index, MASS_TYPE),
                chunk.getComponent(index, MATERIAL_TYPE),
                chunk.getComponent(index, COLLISION_TYPE),
                chunk.getComponent(index, PERSISTENCE_TYPE),
                new RigidBodyOwnershipComponent(ownership));
        } catch (IllegalArgumentException exception) {
            commandBuffer.putComponent(ref,
                LIFECYCLE_TYPE,
                RigidBodyLifecycleComponent.failed(bodyKey, ownership, exception.getMessage()));
            return;
        }
        RigidBodyLifecycleComponent.State state = resource.getBodyRegistrationView(bodyKey) != null
            ? RigidBodyLifecycleComponent.State.CREATED
            : RigidBodyLifecycleComponent.State.PENDING;
        if (lifecycle == null
            || lifecycle.getState() != state
            || !bodyKey.equals(lifecycle.getBodyKey())
            || lifecycle.getOwnership() != ownership) {
            RigidBodyLifecycleComponent updated = state == RigidBodyLifecycleComponent.State.CREATED
                ? RigidBodyLifecycleComponent.created(bodyKey, ownership)
                : RigidBodyLifecycleComponent.pending(bodyKey, ownership);
            commandBuffer.putComponent(ref, LIFECYCLE_TYPE, updated);
        }
        reconcileAttachment(ref,
            plan,
            chunk.getComponent(index, ATTACHMENT_TYPE),
            commandBuffer);
        reconcileKinematicTarget(resource, bodyKey, chunk.getComponent(index, KINEMATIC_TARGET_TYPE));
    }

    private static void submitSpawn(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull RigidBodySpawnPlan plan) {
        Vector3f position = spawnPosition(chunk, index);
        resource.submitCommands(0L, 1, commands -> commands.spawnBody(plan.bodyKey(), spawn -> spawn
            .space(plan.spaceId())
            .shape(plan.requireShape())
            .mass(plan.mass())
            .type(plan.bodyType())
            .position(position)
            .settings(plan.settings())
            .kind(PhysicsBodyKind.BODY)
            .persistence(plan.persistenceMode())));
    }

    @Nonnull
    private static Vector3f spawnPosition(@Nonnull ArchetypeChunk<EntityStore> chunk, int index) {
        RigidBodyKinematicTargetComponent target = chunk.getComponent(index, KINEMATIC_TARGET_TYPE);
        if (target != null && target.isTransformEnabled()) {
            return new Vector3f(target.getPosition());
        }
        TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
        if (transform == null) {
            return new Vector3f();
        }
        Vector3d position = transform.getPosition();
        return new Vector3f((float) position.x, (float) position.y, (float) position.z);
    }

    private static void reconcileAttachment(@Nonnull Ref<EntityStore> ref,
        @Nonnull RigidBodySpawnPlan plan,
        @Nullable PhysicsBodyAttachmentComponent current,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!plan.shouldAttachEntity()) {
            clearAttachment(ref, current, commandBuffer);
            return;
        }
        if (current != null
            && current.getBodyKey().equals(plan.bodyKey())
            && plan.spaceId().equals(current.getSpaceId())
            && current.getLifecycle()
                == PhysicsBodyAttachmentComponent.AttachmentLifecycle.EXTERNAL_ENTITY) {
            return;
        }
        commandBuffer.putComponent(ref,
            ATTACHMENT_TYPE,
            PhysicsBodyAttachmentComponent.externalEntity(plan.bodyKey(), plan.spaceId()));
    }

    private static void clearAttachment(@Nonnull Ref<EntityStore> ref,
        @Nullable PhysicsBodyAttachmentComponent current,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (current != null) {
            commandBuffer.removeComponent(ref, ATTACHMENT_TYPE);
        }
    }

    private static void reconcileKinematicTarget(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull RigidBodyKey bodyKey,
        @Nullable RigidBodyKinematicTargetComponent target) {
        if (target == null || (!target.isTransformEnabled() && !target.isVelocityEnabled())) {
            return;
        }
        resource.submitCommands(0L, 2, commands -> {
            if (target.isTransformEnabled()) {
                commands.setBodyTransform(bodyKey,
                    target.getPosition(),
                    target.getRotation(),
                    target.isActivate());
            }
            if (target.isVelocityEnabled()) {
                commands.setBodyVelocity(bodyKey,
                    target.getLinearVelocity(),
                    target.getAngularVelocity(),
                    target.isActivate());
            }
        });
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }
}
