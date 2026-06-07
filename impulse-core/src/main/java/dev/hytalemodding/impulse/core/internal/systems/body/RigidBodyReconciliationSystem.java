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
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKinematicTargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyLifecycleComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Reconciles ECS rigid-body components into existing physics commands.
 */
public class RigidBodyReconciliationSystem extends TickingSystem<EntityStore>
    implements QuerySystem<EntityStore> {

    private static final ComponentType<EntityStore, RigidBodyComponent> BODY_TYPE =
        RigidBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyKinematicTargetComponent> KINEMATIC_TARGET_TYPE =
        RigidBodyKinematicTargetComponent.getComponentType();
    private static final ComponentType<EntityStore, RigidBodyLifecycleComponent> LIFECYCLE_TYPE =
        RigidBodyLifecycleComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private static final Query<EntityStore> QUERY = BODY_TYPE;

    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemGroupDependency<>(Order.AFTER, ImpulsePlugin.get().getPersistenceRestoreGroup()),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );
    @Nonnull
    private final Map<Store<EntityStore>, RigidBodyKinematicTargetState> kinematicStatesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        RigidBodyCommandBatch physicsBatch = new RigidBodyCommandBatch();
        RigidBodyKinematicTargetState kinematicState = kinematicStateFor(store);
        PersistentPhysicsWorldResource persistent = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());

        kinematicState.beginTick();
        try {
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> collector =
                (chunk, commandBuffer) -> reconcileChunk(chunk,
                    commandBuffer,
                    resource,
                    persistent,
                    physicsBatch,
                    kinematicState);
            store.forEachChunk(systemIndex, collector);
        } finally {
            kinematicState.finishTick();
        }

        submitBatch(physicsBatch, resource, kinematicState);
    }

    private static void reconcileChunk(@Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nullable PersistentPhysicsWorldResource persistent,
        @Nonnull RigidBodyCommandBatch physicsBatch,
        @Nonnull RigidBodyKinematicTargetState kinematicState) {
        for (int index = 0; index < chunk.size(); index++) {
            reconcileEntity(index,
                chunk,
                commandBuffer,
                resource,
                persistent,
                physicsBatch,
                kinematicState);
        }
    }

    private static void reconcileEntity(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nullable PersistentPhysicsWorldResource persistent,
        @Nonnull RigidBodyCommandBatch physicsBatch,
        @Nonnull RigidBodyKinematicTargetState kinematicState) {
        RigidBodyComponent body = chunk.getComponent(index, BODY_TYPE);
        if (body == null) {
            return;
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        RigidBodyLifecycleComponent lifecycle = chunk.getComponent(index, LIFECYCLE_TYPE);
        RigidBodyComponent.Ownership ownershipValue = body.getOwnership();
        RigidBodyKey bodyKey = body.getBodyKey();
        if (RigidBodyReconciliationPolicy.shouldSuppressBodyReconciliation(lifecycle, persistent, bodyKey)) {
            kinematicState.clear(bodyKey);
            clearAttachment(ref, chunk.getComponent(index, ATTACHMENT_TYPE), commandBuffer);
            if (lifecycle == null || lifecycle.getState() != RigidBodyLifecycleComponent.State.DESTROYED) {
                commandBuffer.putComponent(ref,
                    LIFECYCLE_TYPE,
                    RigidBodyLifecycleComponent.failed(bodyKey,
                        ownershipValue,
                        "Persisted body restore skipped"));
            }
            return;
        }

        boolean registeredOrPending = resource.hasPublishedOrPendingBodyRegistration(bodyKey)
            || physicsBatch.hasPendingBody(bodyKey);

        if (registeredOrPending) {
            reconcileExistingBody(ref,
                chunk,
                index,
                commandBuffer,
                resource,
                physicsBatch,
                kinematicState,
                body,
                bodyKey,
                ownershipValue,
                lifecycle);
            return;
        }

        RigidBodySpawnPlan plan;
        try {
            plan = RigidBodySpawnPlan.create(body);
        } catch (IllegalArgumentException exception) {
            kinematicState.clear(bodyKey);
            commandBuffer.putComponent(ref,
                LIFECYCLE_TYPE,
                RigidBodyLifecycleComponent.failed(bodyKey, ownershipValue, exception.getMessage()));
            clearAttachment(ref, chunk.getComponent(index, ATTACHMENT_TYPE), commandBuffer);
            return;
        }

        if (!plan.shouldSpawnBody()) {
            kinematicState.clear(bodyKey);
            commandBuffer.putComponent(ref,
                LIFECYCLE_TYPE,
                RigidBodyLifecycleComponent.failed(bodyKey,
                    plan.ownership(),
                    "Detached rigid body key is not registered or pending"));
            clearAttachment(ref, chunk.getComponent(index, ATTACHMENT_TYPE), commandBuffer);
            return;
        }

        physicsBatch.addSpawn(plan, spawnPosition(chunk, index));
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
        @Nonnull RigidBodyCommandBatch physicsBatch,
        @Nonnull RigidBodyKinematicTargetState kinematicState,
        @Nonnull RigidBodyComponent body,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyComponent.Ownership ownership,
        @Nullable RigidBodyLifecycleComponent lifecycle) {
        RigidBodySpawnPlan plan;
        try {
            plan = RigidBodySpawnPlan.create(body);
        } catch (IllegalArgumentException exception) {
            commandBuffer.putComponent(ref,
                LIFECYCLE_TYPE,
                RigidBodyLifecycleComponent.failed(bodyKey, ownership, exception.getMessage()));
            kinematicState.clear(bodyKey);
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
        reconcileKinematicTarget(physicsBatch,
            kinematicState,
            bodyKey,
            chunk.getComponent(index, KINEMATIC_TARGET_TYPE));
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

    private static void reconcileKinematicTarget(@Nonnull RigidBodyCommandBatch physicsBatch,
        @Nonnull RigidBodyKinematicTargetState kinematicState,
        @Nonnull RigidBodyKey bodyKey,
        @Nullable RigidBodyKinematicTargetComponent target) {
        if (target == null || (!target.isTransformEnabled() && !target.isVelocityEnabled())) {
            kinematicState.clear(bodyKey);
            return;
        }
        if (kinematicState.shouldSubmit(bodyKey, target)) {
            physicsBatch.addKinematicTarget(bodyKey, target);
        }
    }

    private static void submitBatch(@Nonnull RigidBodyCommandBatch physicsBatch,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull RigidBodyKinematicTargetState kinematicState) {
        boolean hasKinematicTargets = physicsBatch.hasKinematicTargets();
        List<RigidBodyKey> kinematicTargetKeys = hasKinematicTargets
            ? physicsBatch.kinematicTargetKeys()
            : List.of();
        PhysicsCommandHandle handle = physicsBatch.submit(resource);
        if (handle == null || !hasKinematicTargets) {
            return;
        }
        handle.completionSummary().whenComplete((completion, failure) -> {
            if (failure != null || !completion.allApplied()) {
                kinematicState.clearAll(kinematicTargetKeys);
            }
        });
    }

    @Nonnull
    private RigidBodyKinematicTargetState kinematicStateFor(@Nonnull Store<EntityStore> store) {
        synchronized (kinematicStatesByStore) {
            RigidBodyKinematicTargetState state = kinematicStatesByStore.get(store);
            if (state == null) {
                state = new RigidBodyKinematicTargetState();
                kinematicStatesByStore.put(store, state);
            }
            return state;
        }
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
