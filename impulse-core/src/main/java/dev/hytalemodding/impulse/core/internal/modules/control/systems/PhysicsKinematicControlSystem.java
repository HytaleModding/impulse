package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAccess;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTargetRequest;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class PhysicsKinematicControlSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    @Nonnull
    private final ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType;
    @Nonnull
    private final Query<EntityStore> query;
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);
    private static final Vector3f ZERO_VELOCITY = new Vector3f();
    private static final Quaternionf IDENTITY_ROTATION = new Quaternionf();
    // Anchor updates are copied commands; keep one owner command in flight and at most one latest
    // queued target per session anchor.
    @Nonnull
    private static final Map<Store<EntityStore>, ControlMutationState> STATES_BY_STORE =
        Collections.synchronizedMap(new WeakHashMap<>());

    public PhysicsKinematicControlSystem() {
        this(PhysicsControlSessionComponent.getComponentType());
    }

    PhysicsKinematicControlSystem(
        @Nonnull ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType) {
        this.sessionType = Objects.requireNonNull(sessionType, "sessionType");
        this.query = Query.and(sessionType, TransformComponent.getComponentType());
    }

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
        if (!ControlLifecycle.isEnabled()) {
            return;
        }
        ControlLifecycle.registerStore(store);
        PhysicsControlSessionComponent session = chunk.getComponent(index, sessionType);
        TransformComponent transform = chunk.getComponent(index,
            TransformComponent.getComponentType());
        if (session == null || transform == null || !session.isActive()) {
            return;
        }

        RigidBodyKey bodyKey = session.getBodyKey();
        RigidBodyKey anchorBodyKey = session.getAnchorBodyKey();
        Ref<EntityStore> targetRef = session.getTargetRef();
        if (bodyKey == null || anchorBodyKey == null || (targetRef != null && !targetRef.isValid())) {
            stateFor(store).clear(anchorBodyKey);
            PhysicsControlSessionCleanup.cleanup(PhysicsWorldRuntimeResource.require(store), session);
            commandBuffer.removeComponent(chunk.getReferenceTo(index), sessionType);
            return;
        }

        Scratch local = scratch.get();
        Vector3d eye = local.eye;
        eye.set(transform.getPosition());
        eye.y += eyeHeight(chunk, index, chunk.getReferenceTo(index), store);

        Vector3d direction = lookDirection(chunk, index, transform, local.direction);
        Vector3f viewOffset = session.getViewOffset();
        local.right.set(Vector3dUtil.RIGHT);
        local.up.set(Vector3dUtil.UP);
        local.rotation.identity();
        rotation(chunk, index, transform).getQuaternion(local.rotation);
        local.rotation.transform(local.right);
        local.rotation.transform(local.up);
        local.target.set(
            (float) (eye.x
                + direction.x * session.getGrabDistance()
                + local.right.x * viewOffset.x
                + local.up.x * viewOffset.y
                + direction.x * viewOffset.z),
            (float) (eye.y
                + direction.y * session.getGrabDistance()
                + local.right.y * viewOffset.x
                + local.up.y * viewOffset.y
                + direction.y * viewOffset.z),
            (float) (eye.z
                + direction.z * session.getGrabDistance()
                + local.right.z * viewOffset.x
                + local.up.z * viewOffset.y
                + direction.z * viewOffset.z)
        );

        Vector3f previousTarget = session.getPreviousTarget();
        Vector3f releaseVelocity = session.getReleaseVelocity();
        float safeDt = dt > 0.0f ? dt : 1.0f / 20.0f;
        releaseVelocity.set(local.target).sub(previousTarget).div(safeDt);
        previousTarget.set(local.target);

        ControlMutationState state = stateFor(store);
        ControlAnchorUpdate update = new ControlAnchorUpdate(bodyKey,
            anchorBodyKey,
            local.target,
            releaseVelocity);
        ControlAnchorUpdate readyUpdate = state.selectReadyUpdate(anchorBodyKey, update);
        if (readyUpdate == null) {
            return;
        }

        PhysicsStoreControlTargets physicsStoreTargets =
            resolvePhysicsStoreTargets(store, bodyKey, anchorBodyKey);
        if (physicsStoreTargets != null) {
            physicsStoreTargets.enqueue(readyUpdate);
            state.trackSubmittedRequest(anchorBodyKey, readyUpdate);
            return;
        }

        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        if (!resource.hasPublishedOrPendingBodyRegistration(bodyKey)
            || !resource.hasPublishedOrPendingBodyRegistration(anchorBodyKey)) {
            stateFor(store).clear(anchorBodyKey);
            PhysicsControlSessionCleanup.cleanup(resource, session);
            commandBuffer.removeComponent(chunk.getReferenceTo(index), sessionType);
            return;
        }

        if (session.getSpaceId() != null && !resource.hasSpace(session.getSpaceId())) {
            stateFor(store).clear(anchorBodyKey);
            PhysicsControlSessionCleanup.cleanup(resource, session);
            commandBuffer.removeComponent(chunk.getReferenceTo(index), sessionType);
            return;
        }

        PhysicsMutationHandle<Void> handle = PhysicsMutationHandle.fromCompletion(
            "update kinematic control anchor",
            null,
            resource.submitCommands(0L, 4, commands -> commands
                    .setBodyType(readyUpdate.anchorBodyKey(), PhysicsBodyType.KINEMATIC)
                    .setBodyPosition(readyUpdate.anchorBodyKey(),
                        readyUpdate.target().x,
                        readyUpdate.target().y,
                        readyUpdate.target().z,
                        false)
                    .setBodyVelocity(readyUpdate.anchorBodyKey(),
                        readyUpdate.releaseVelocity().x,
                        readyUpdate.releaseVelocity().y,
                        readyUpdate.releaseVelocity().z,
                        0.0f,
                        0.0f,
                        0.0f,
                        true)
                    .activateBody(readyUpdate.bodyKey()))
                .completionSummary());
        state.trackPendingMutation(anchorBodyKey, handle, readyUpdate);
    }

    @Nullable
    private static PhysicsStoreControlTargets resolvePhysicsStoreTargets(
        @Nonnull Store<EntityStore> store,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyKey anchorBodyKey) {
        PhysicsStore physicsStore = PhysicsStoreAccess.require(store.getExternalData().getWorld());
        Store<PhysicsStore> physics = physicsStore.getStore();
        PhysicsIdentityIndexResource identity = physics.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        UUID bodyUuid = bodyKey.value();
        UUID anchorBodyUuid = anchorBodyKey.value();
        if (!hasPhysicsStoreBody(physics, identity, bodyUuid)
            || !hasPhysicsStoreBody(physics, identity, anchorBodyUuid)) {
            return null;
        }
        return new PhysicsStoreControlTargets(
            physics.getResource(PhysicsRequestQueueResource.getResourceType()),
            bodyUuid,
            anchorBodyUuid);
    }

    private static boolean hasPhysicsStoreBody(@Nonnull Store<PhysicsStore> physics,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID bodyUuid) {
        Ref<PhysicsStore> ref = identity.getByUuid(bodyUuid);
        return ref != null
            && ref.isValid()
            && physics.getComponent(ref, BodyComponent.getComponentType()) != null;
    }

    private float eyeHeight(@Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store) {
        ModelComponent modelComponent = chunk.getComponent(index,
            ModelComponent.getComponentType());
        if (modelComponent == null) {
            return 1.6f;
        }

        Model model = modelComponent.getModel();
        if (model == null) {
            return 1.6f;
        }
        return model.getEyeHeight(ref, store);
    }

    @Nonnull
    private Vector3d lookDirection(@Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull TransformComponent transform,
        @Nonnull Vector3d out) {
        Rotation3f rotation = rotation(chunk, index, transform);
        Quaterniond quaternion = rotation.getQuaternion(new Quaterniond());
        out.set(Vector3dUtil.FORWARD);
        quaternion.transform(out);
        if (out.lengthSquared() == 0.0) {
            out.set(Vector3dUtil.FORWARD);
        } else {
            out.normalize();
        }
        return out;
    }

    @Nonnull
    private Rotation3f rotation(@Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = chunk.getComponent(index, HeadRotation.getComponentType());
        return headRotation != null ? headRotation.getRotation() : transform.getRotation();
    }

    @Nonnull
    static ControlMutationState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (STATES_BY_STORE) {
            return STATES_BY_STORE.computeIfAbsent(store, _ -> new ControlMutationState());
        }
    }

    public static void clearMutationState(@Nonnull Store<EntityStore> store,
        @Nullable RigidBodyKey anchorBodyKey) {
        if (anchorBodyKey != null) {
            stateFor(store).clear(anchorBodyKey);
        }
    }

    record ControlAnchorUpdate(@Nonnull RigidBodyKey bodyKey,
                               @Nonnull RigidBodyKey anchorBodyKey,
                               @Nonnull Vector3f target,
                               @Nonnull Vector3f releaseVelocity) {

        ControlAnchorUpdate {
            target = new Vector3f(target);
            releaseVelocity = new Vector3f(releaseVelocity);
        }
    }

    private record PhysicsStoreControlTargets(@Nonnull PhysicsRequestQueueResource queue,
                                              @Nonnull UUID bodyUuid,
                                              @Nonnull UUID anchorBodyUuid) {

        private void enqueue(@Nonnull ControlAnchorUpdate update) {
            queue.enqueue(BodyTargetRequest.of(anchorBodyUuid,
                update.target(),
                IDENTITY_ROTATION,
                update.releaseVelocity(),
                ZERO_VELOCITY,
                true,
                true,
                true));
            queue.enqueue(BodyTargetRequest.of(bodyUuid,
                update.target(),
                IDENTITY_ROTATION,
                ZERO_VELOCITY,
                ZERO_VELOCITY,
                false,
                false,
                true));
        }
    }

    static final class ControlMutationState {

        /*
         * Coalescing happens on the tick thread: completion callbacks only clear the in-flight
         * marker. The next tick decides whether the latest queued target still belongs to an active
         * session and is different enough to submit.
         *
         * FIXME: Replace this coalescing workaround when strict control scheduling lands.
         */
        @Nonnull
        private final Object2ObjectMap<RigidBodyKey, PhysicsMutationHandle<Void>> pendingMutations =
            new Object2ObjectOpenHashMap<>();
        @Nonnull
        private final Object2ObjectMap<RigidBodyKey, ControlAnchorUpdate> queuedUpdates =
            new Object2ObjectOpenHashMap<>();
        @Nonnull
        private final Object2ObjectMap<RigidBodyKey, ControlAnchorUpdate> submittedUpdates =
            new Object2ObjectOpenHashMap<>();

        synchronized boolean hasPendingMutation(@Nonnull RigidBodyKey bodyKey) {
            PhysicsMutationHandle<Void> handle = pendingMutations.get(bodyKey);
            if (handle == null) {
                return false;
            }
            if (!handle.isDone()) {
                return true;
            }
            pendingMutations.remove(bodyKey);
            return false;
        }

        @Nullable
        synchronized ControlAnchorUpdate selectReadyUpdate(@Nonnull RigidBodyKey bodyKey,
            @Nonnull ControlAnchorUpdate currentUpdate) {
            PhysicsMutationHandle<Void> handle = pendingMutations.get(bodyKey);
            if (handle != null && !handle.isDone()) {
                queuedUpdates.put(bodyKey, currentUpdate);
                return null;
            }
            if (handle != null) {
                pendingMutations.remove(bodyKey);
            }

            ControlAnchorUpdate queuedUpdate = queuedUpdates.remove(bodyKey);
            ControlAnchorUpdate submittedUpdate = submittedUpdates.get(bodyKey);
            if (queuedUpdate != null) {
                if (!sameTarget(queuedUpdate, submittedUpdate)) {
                    return queuedUpdate;
                }
                if (sameTarget(currentUpdate, submittedUpdate)) {
                    return null;
                }
            }
            return currentUpdate;
        }

        synchronized void trackPendingMutation(@Nonnull RigidBodyKey bodyKey,
            @Nonnull PhysicsMutationHandle<Void> handle,
            @Nonnull ControlAnchorUpdate submittedUpdate) {
            submittedUpdates.put(bodyKey, submittedUpdate);
            if (handle.isDone()) {
                logImmediateFailure(handle);
                return;
            }
            pendingMutations.put(bodyKey, handle);
            handle.completion().whenComplete((ignored, _) -> clear(bodyKey, handle));
        }

        synchronized void trackSubmittedRequest(@Nonnull RigidBodyKey bodyKey,
            @Nonnull ControlAnchorUpdate submittedUpdate) {
            pendingMutations.remove(bodyKey);
            queuedUpdates.remove(bodyKey);
            submittedUpdates.put(bodyKey, submittedUpdate);
        }

        synchronized void clear(@Nullable RigidBodyKey bodyKey) {
            if (bodyKey != null) {
                pendingMutations.remove(bodyKey);
                queuedUpdates.remove(bodyKey);
                submittedUpdates.remove(bodyKey);
            }
        }

        private synchronized void clear(@Nonnull RigidBodyKey bodyKey,
            @Nonnull PhysicsMutationHandle<Void> expectedHandle) {
            PhysicsMutationHandle<Void> current = pendingMutations.get(bodyKey);
            if (current == expectedHandle) {
                pendingMutations.remove(bodyKey);
            }
        }

        private static boolean sameTarget(@Nonnull ControlAnchorUpdate first,
            @Nullable ControlAnchorUpdate second) {
            return second != null
                && Float.compare(first.target().x, second.target().x) == 0
                && Float.compare(first.target().y, second.target().y) == 0
                && Float.compare(first.target().z, second.target().z) == 0;
        }

        private static void logImmediateFailure(@Nonnull PhysicsMutationHandle<Void> handle) {
            Throwable failure = handle.failure();
            if (failure != null) {
                LOGGER.at(Level.WARNING).log(
                    "Kinematic control anchor update could not be queued: %s",
                    failure.getMessage());
            }
        }
    }

    private static final class Scratch {

        private final Vector3d eye = new Vector3d();
        private final Vector3d direction = new Vector3d();
        private final Vector3d right = new Vector3d();
        private final Vector3d up = new Vector3d();
        private final Quaterniond rotation = new Quaterniond();
        private final Vector3f target = new Vector3f();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
