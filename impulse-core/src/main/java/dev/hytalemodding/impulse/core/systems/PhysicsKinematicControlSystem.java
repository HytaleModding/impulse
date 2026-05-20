package dev.hytalemodding.impulse.core.systems;

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
import dev.hytalemodding.impulse.core.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class PhysicsKinematicControlSystem extends EntityTickingSystem<EntityStore> {

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
            cleanupSession(store, session);
            commandBuffer.removeComponent(chunk.getReferenceTo(index), SESSION_TYPE);
            return;
        }

        if (session.getSpaceId() != null && resource.getSpace(session.getSpaceId()) == null) {
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

        if (anchorBody.getBodyType() != PhysicsBodyType.KINEMATIC) {
            anchorBody.setBodyType(PhysicsBodyType.KINEMATIC);
        }
        anchorBody.setPosition(local.target);
        anchorBody.setLinearVelocity(releaseVelocity);
        anchorBody.activate();
        body.activate();
    }

    private static void cleanupSession(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsControlSessionComponent session) {
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

    private static final class Scratch {

        private final Vector3d eye = new Vector3d();
        private final Vector3d direction = new Vector3d();
        private final Vector3d right = new Vector3d();
        private final Vector3d up = new Vector3d();
        private final Quaterniond rotation = new Quaterniond();
        private final Vector3f target = new Vector3f();
    }
}
