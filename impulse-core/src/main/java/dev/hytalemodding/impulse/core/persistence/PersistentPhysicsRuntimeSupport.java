package dev.hytalemodding.impulse.core.persistence;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Shared helpers for bridging persisted physics state to live runtime objects.
 *
 * <p>Used by the hydration and sync systems to resolve entity UUIDs to runtime
 * bodies, look up the correct space for a persisted body, construct joints from
 * persisted joint definitions, and produce stable keys for deduplicating joints
 * across hydration ticks.</p>
 */
public final class PersistentPhysicsRuntimeSupport {

    private PersistentPhysicsRuntimeSupport() {
    }

    @Nullable
    public static UUID ownerUuid(@Nonnull Store<EntityStore> store, @Nonnull PhysicsBody body,
        @Nonnull PhysicsWorldResource resource) {
        Ref<EntityStore> owner = resource.getBodyOwner(body);
        if (owner == null || !owner.isValid()) {
            return null;
        }

        UUIDComponent uuidComponent = store.getComponent(owner, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }

    @Nullable
    public static PhysicsSpace resolveSpace(@Nonnull PhysicsWorldResource runtimeResource,
        @Nonnull PersistentPhysicsWorldResource persistentWorld,
        @Nonnull PersistentPhysicsBodyComponent persistentBody) {
        int resolvedSpaceId = persistentBody.resolveSpaceId(persistentWorld.getDefaultSpaceIdValue());
        if (resolvedSpaceId <= 0) {
            return null;
        }
        return runtimeResource.getSpace(new SpaceId(resolvedSpaceId));
    }

    @Nullable
    public static PhysicsBody runtimeBody(@Nonnull Store<EntityStore> store, @Nonnull UUID uuid) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(uuid);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        PhysicsBodyComponent component = store.getComponent(ref, PhysicsBodyComponent.getComponentType());
        return component != null ? component.getBody() : null;
    }

    @Nonnull
    public static String jointKey(int spaceId,
        @Nonnull UUID bodyAUuid,
        @Nonnull UUID bodyBUuid,
        @Nonnull PhysicsJoint joint) {
        return PersistentPhysicsJointState.from(spaceId, bodyAUuid, bodyBUuid, joint).key();
    }

    @Nonnull
    public static PhysicsJoint createJoint(@Nonnull PhysicsSpace space,
        @Nonnull PersistentPhysicsJointState state,
        @Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB) {
        Vector3f anchorA = new Vector3f(state.getAnchorA());
        Vector3f anchorB = new Vector3f(state.getAnchorB());
        Vector3f axis = state.getAxis() != null ? new Vector3f(state.getAxis()) : new Vector3f(0.0f, 1.0f, 0.0f);
        PhysicsJoint joint = switch (state.getType()) {
            case FIXED -> space.createFixedJoint(bodyA, bodyB, anchorA, anchorB);
            case POINT -> space.createPointJoint(bodyA, bodyB, anchorA, anchorB);
            case HINGE -> space.createHingeJoint(bodyA, bodyB, anchorA, anchorB, axis);
            case SLIDER -> space.createSliderJoint(bodyA, bodyB, anchorA, anchorB, axis);
            case SPRING -> space.createSpringJoint(bodyA,
                bodyB,
                anchorA,
                anchorB,
                state.getSpringRestLength(),
                state.getSpringStiffness(),
                state.getSpringDamping());
        };
        if (state.getType() == PhysicsJointType.HINGE || state.getType() == PhysicsJointType.SLIDER) {
            joint.setLimits(state.getLowerLimit(), state.getUpperLimit());
            joint.setMotor(state.getMotorTargetVelocity(), state.getMotorMaxForce());
            joint.setMotorEnabled(state.isMotorEnabled());
        }
        joint.setEnabled(state.isEnabled());
        return joint;
    }
}
