package dev.hytalemodding.impulse.core.internal.systems.step;

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
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class PhysicsKinematicControlSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private static final ComponentType<EntityStore, PhysicsControlSessionComponent> SESSION_TYPE =
        PhysicsControlSessionComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, HeadRotation> HEAD_ROTATION_TYPE =
        HeadRotation.getComponentType();
    private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE =
        ModelComponent.getComponentType();

    private static final Query<EntityStore> QUERY = Query.and(SESSION_TYPE, TRANSFORM_TYPE);

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);
    // Anchor updates are worker-owned; keep at most one queued mutation per session anchor.
    @Nonnull
    private final Map<Store<EntityStore>, ControlMutationState> statesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());

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
        PhysicsControlSessionComponent session = chunk.getComponent(index, SESSION_TYPE);
        TransformComponent transform = chunk.getComponent(index, TRANSFORM_TYPE);
        if (session == null || transform == null || !session.isActive()) {
            return;
        }

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsBodyId bodyId = session.getBodyId();
        PhysicsBodyId anchorBodyId = session.getAnchorBodyId();
        PhysicsBody body = bodyId != null ? resource.getBody(bodyId) : null;
        PhysicsBody anchorBody = anchorBodyId != null ? resource.getBody(anchorBodyId) : null;
        Ref<EntityStore> targetRef = session.getTargetRef();
        if (body == null || anchorBody == null || (targetRef != null && !targetRef.isValid())) {
            stateFor(store).clear(anchorBodyId);
            cleanupSession(store, session);
            commandBuffer.removeComponent(chunk.getReferenceTo(index), SESSION_TYPE);
            return;
        }

        if (session.getSpaceId() != null && resource.getSpace(session.getSpaceId()) == null) {
            stateFor(store).clear(anchorBodyId);
            cleanupSession(store, session);
            commandBuffer.removeComponent(chunk.getReferenceTo(index), SESSION_TYPE);
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
        if (state.hasPendingMutation(anchorBodyId)) {
            return;
        }

        ControlAnchorUpdate update = new ControlAnchorUpdate(bodyId,
            anchorBodyId,
            local.target,
            releaseVelocity);
        PhysicsMutationHandle<Void> handle = PhysicsWorkerAccess.runAsync(store,
            "update kinematic control anchor",
            () -> applyControlUpdate(resource, update));
        state.trackPendingMutation(anchorBodyId, handle);
    }

    static void applyControlUpdate(@Nonnull PhysicsWorldResource resource,
        @Nonnull ControlAnchorUpdate update) {
        PhysicsBody body = resource.getBody(update.bodyId());
        PhysicsBody anchorBody = resource.getBody(update.anchorBodyId());
        if (body == null || anchorBody == null) {
            return;
        }

        if (anchorBody.getBodyType() != PhysicsBodyType.KINEMATIC) {
            anchorBody.setBodyType(PhysicsBodyType.KINEMATIC);
        }
        anchorBody.setPosition(update.target());
        anchorBody.setLinearVelocity(update.releaseVelocity());
        anchorBody.activate();
        body.activate();
    }

    private static void cleanupSession(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsControlSessionComponent session) {
        PhysicsWorkerAccess.run(store, "cleanup kinematic control session", () -> {
            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            if (session.getBodyId() != null) {
                resource.clearControlledBody(session.getBodyId());
            }
            PhysicsSpace space = session.getSpaceId() != null ? resource.getSpace(session.getSpaceId()) : null;
            if (space != null && session.getJoint() != null) {
                PhysicsJoint joint = session.getJoint();
                space.removeJoint(joint);
            }

            if (session.getAnchorBodyId() != null) {
                resource.destroyBody(session.getAnchorBodyId());
            }
        });
    }

    private static float eyeHeight(@Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store) {
        ModelComponent modelComponent = chunk.getComponent(index, MODEL_TYPE);
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
    private static Vector3d lookDirection(@Nonnull ArchetypeChunk<EntityStore> chunk,
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
    private static Rotation3f rotation(@Nonnull ArchetypeChunk<EntityStore> chunk,
        int index,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = chunk.getComponent(index, HEAD_ROTATION_TYPE);
        return headRotation != null ? headRotation.getRotation() : transform.getRotation();
    }

    @Nonnull
    private ControlMutationState stateFor(@Nonnull Store<EntityStore> store) {
        synchronized (statesByStore) {
            return statesByStore.computeIfAbsent(store, ignored -> new ControlMutationState());
        }
    }

    record ControlAnchorUpdate(@Nonnull PhysicsBodyId bodyId,
                               @Nonnull PhysicsBodyId anchorBodyId,
                               @Nonnull Vector3f target,
                               @Nonnull Vector3f releaseVelocity) {

        ControlAnchorUpdate {
            target = new Vector3f(target);
            releaseVelocity = new Vector3f(releaseVelocity);
        }
    }

    static final class ControlMutationState {

        @Nonnull
        private final Object2ObjectMap<PhysicsBodyId, PhysicsMutationHandle<Void>> pendingMutations =
            new Object2ObjectOpenHashMap<>();

        synchronized boolean hasPendingMutation(@Nonnull PhysicsBodyId bodyId) {
            PhysicsMutationHandle<Void> handle = pendingMutations.get(bodyId);
            if (handle == null) {
                return false;
            }
            if (!handle.isDone()) {
                return true;
            }
            pendingMutations.remove(bodyId);
            return false;
        }

        synchronized void trackPendingMutation(@Nonnull PhysicsBodyId bodyId,
            @Nonnull PhysicsMutationHandle<Void> handle) {
            if (handle.isDone()) {
                logImmediateFailure(handle);
                return;
            }
            pendingMutations.put(bodyId, handle);
            handle.completion().whenComplete((ignored, failure) -> clear(bodyId, handle));
        }

        synchronized void clear(@Nullable PhysicsBodyId bodyId) {
            if (bodyId != null) {
                pendingMutations.remove(bodyId);
            }
        }

        private synchronized void clear(@Nonnull PhysicsBodyId bodyId,
            @Nonnull PhysicsMutationHandle<Void> expectedHandle) {
            PhysicsMutationHandle<Void> current = pendingMutations.get(bodyId);
            if (current == expectedHandle) {
                pendingMutations.remove(bodyId);
            }
        }

        private static void logImmediateFailure(@Nonnull PhysicsMutationHandle<Void> handle) {
            Throwable failure = handle.failure();
            if (failure != null) {
                LOGGER.at(Level.WARNING).log(
                    "Kinematic control worker mutation could not be queued: %s",
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
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
