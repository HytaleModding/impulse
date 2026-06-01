package dev.hytalemodding.impulse.core.internal.testsupport;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendJointSpec;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.legacy.LegacyPhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistration;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Test-only bridge for legacy unit tests that still assert live-object behavior.
 */
public class LegacyLiveHandleTestResource extends PhysicsWorldRuntimeResource {

    private static final Field SPACES = field(LegacyPhysicsBackendRuntime.class, "spaces");
    private static final Field NEXT_BODY_ID = field(LegacyPhysicsBackendRuntime.class, "nextBodyId");
    private static final Field NEXT_JOINT_ID = field(LegacyPhysicsBackendRuntime.class, "nextJointId");

    @Nonnull
    public PhysicsSpace createLiveSpace(@Nonnull BackendId backendId) {
        return createLiveSpace(backendId, "test-world", PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    public PhysicsSpace createLiveSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName) {
        return createLiveSpace(backendId, worldName, PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    public PhysicsSpace createLiveSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        SpaceId spaceId = createSpace(backendId, worldName, settings);
        return liveSpace(spaceId);
    }

    @Nonnull
    public RigidBodyKey addBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return addBody(RigidBodyKey.random(), spaceId, body, kind, persistenceMode);
    }

    @Nonnull
    public RigidBodyKey addBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return addBodyOnOwner(bodyKey, spaceId, body, kind, persistenceMode);
    }

    @Nonnull
    public RigidBodyKey addBodyOnOwner(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        RegisteredBody registered = registerLiveBody(spaceId, body);
        try {
            return super.addBodyOnOwner(bodyKey, spaceId, registered.backendBodyId(), kind, persistenceMode);
        } catch (RuntimeException exception) {
            if (registered.created()) {
                unregisterLiveBody(spaceId, registered.backendBodyId(), body);
            }
            throw exception;
        }
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> addBodyAsync(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return enqueueOwnerMutation("add test physics body",
            bodyKey,
            () -> addBodyOnOwner(bodyKey, spaceId, body, kind, persistenceMode));
    }

    @Nullable
    public PhysicsBody getBody(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodyRegistration registration = getRegistration(bodyKey);
        if (registration == null) {
            return null;
        }
        Object state = legacySpaceState(requireSpaceBinding(registration.spaceId()));
        return bodiesById(state).get(registration.backendBodyId());
    }

    @Nonnull
    public PhysicsSpace getLiveSpace(@Nonnull SpaceId spaceId) {
        return liveSpace(spaceId);
    }

    @Nonnull
    public JointKey addJoint(@Nonnull SpaceId spaceId, @Nonnull PhysicsJoint joint) {
        long backendJointId = registerLiveJoint(spaceId, joint);
        PhysicsBodyRegistration bodyA = getRegistrationByLiveBody(spaceId, joint.getBodyA());
        PhysicsBodyRegistration bodyB = getRegistrationByLiveBody(spaceId, joint.getBodyB());
        if (bodyA == null || bodyB == null) {
            throw new IllegalArgumentException("Both joint bodies must be registered before registering the joint");
        }
        JointKey jointKey = JointKey.random();
        BackendJointSpec spec = jointSpec(joint, bodyA.backendBodyId(), bodyB.backendBodyId());
        return super.addJointOnOwner(jointKey,
            spaceId,
            backendJointId,
            bodyA.id(),
            bodyB.id(),
            jointType(joint.getType()),
            spec);
    }

    @Nullable
    public PhysicsJoint getJoint(@Nonnull JointKey jointKey) {
        PhysicsJointRegistration registration = getJointRegistration(jointKey);
        if (registration == null) {
            return null;
        }
        Object state = legacySpaceState(requireSpaceBinding(registration.spaceId()));
        return jointsById(state).get(registration.backendJointId());
    }

    @Nonnull
    private PhysicsSpace liveSpace(@Nonnull SpaceId spaceId) {
        Object state = legacySpaceState(requireSpaceBinding(spaceId));
        return liveSpace(state);
    }

    @Nonnull
    private RegisteredBody registerLiveBody(@Nonnull SpaceId spaceId, @Nonnull PhysicsBody body) {
        Object state = legacySpaceState(requireSpaceBinding(spaceId));
        Map<PhysicsBody, Long> bodyIdsByBody = bodyIdsByBody(state);
        Long existing = bodyIdsByBody.get(body);
        if (existing != null) {
            return new RegisteredBody(existing, false);
        }

        PhysicsSpace space = liveSpace(state);
        if (!space.getBodies().contains(body)) {
            space.addBody(body);
        }

        LegacyPhysicsBackendRuntime runtime = legacyRuntime(requireSpaceBinding(spaceId));
        long bodyId = nextId(runtime, NEXT_BODY_ID);
        bodiesById(state).put(bodyId, body);
        bodyIdsByBody.put(body, bodyId);
        return new RegisteredBody(bodyId, true);
    }

    private void unregisterLiveBody(@Nonnull SpaceId spaceId,
        long backendBodyId,
        @Nonnull PhysicsBody body) {
        Object state = legacySpaceState(requireSpaceBinding(spaceId));
        bodiesById(state).remove(backendBodyId);
        bodyIdsByBody(state).remove(body);
        liveSpace(state).removeBody(body);
    }

    private long registerLiveJoint(@Nonnull SpaceId spaceId, @Nonnull PhysicsJoint joint) {
        Object state = legacySpaceState(requireSpaceBinding(spaceId));
        Map<PhysicsJoint, Long> jointIdsByJoint = jointIdsByJoint(state);
        Long existing = jointIdsByJoint.get(joint);
        if (existing != null) {
            return existing;
        }
        LegacyPhysicsBackendRuntime runtime = legacyRuntime(requireSpaceBinding(spaceId));
        long jointId = nextId(runtime, NEXT_JOINT_ID);
        jointsById(state).put(jointId, joint);
        jointIdsByJoint.put(joint, jointId);
        return jointId;
    }

    @Nullable
    private PhysicsBodyRegistration getRegistrationByLiveBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body) {
        Long backendBodyId = bodyIdsByBody(legacySpaceState(requireSpaceBinding(spaceId))).get(body);
        if (backendBodyId == null) {
            return null;
        }
        RigidBodyKey bodyKey = getBodyKey(spaceId, backendBodyId);
        return bodyKey != null ? getRegistration(bodyKey) : null;
    }

    @Nonnull
    private static BackendJointSpec jointSpec(@Nonnull PhysicsJoint joint,
        long bodyAId,
        long bodyBId) {
        Vector3f anchorA = joint.getAnchorA();
        Vector3f anchorB = joint.getAnchorB();
        Vector3f axis = joint.getAxis();
        if (axis == null) {
            axis = new Vector3f(0.0f, 1.0f, 0.0f);
        }
        return new BackendJointSpec(backendJointType(joint.getType()),
            bodyAId,
            bodyBId,
            anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            axis.x,
            axis.y,
            axis.z,
            0.0f,
            0.0f,
            0.0f,
            joint.getLowerLimit(),
            joint.getUpperLimit(),
            joint.isMotorEnabled(),
            joint.getMotorTargetVelocity(),
            joint.getMotorMaxForce());
    }

    @Nonnull
    private static JointType jointType(@Nonnull PhysicsJointType type) {
        return switch (type) {
            case FIXED -> JointType.FIXED;
            case POINT -> JointType.POINT;
            case HINGE -> JointType.HINGE;
            case SLIDER -> JointType.SLIDER;
            case SPRING -> JointType.SPRING;
        };
    }

    @Nonnull
    private static BackendJointType backendJointType(@Nonnull PhysicsJointType type) {
        return switch (type) {
            case FIXED -> BackendJointType.FIXED;
            case POINT -> BackendJointType.POINT;
            case HINGE -> BackendJointType.HINGE;
            case SLIDER -> BackendJointType.SLIDER;
            case SPRING -> BackendJointType.SPRING;
        };
    }

    @Nonnull
    private static Object legacySpaceState(@Nonnull PhysicsSpaceBinding binding) {
        LegacyPhysicsBackendRuntime runtime = legacyRuntime(binding);
        Map<Integer, Object> spaces = spaces(runtime);
        Object state = spaces.get(binding.backendSpaceId());
        if (state == null) {
            throw new IllegalStateException("Missing legacy test space state for " + binding.spaceId());
        }
        return state;
    }

    @Nonnull
    private static LegacyPhysicsBackendRuntime legacyRuntime(@Nonnull PhysicsSpaceBinding binding) {
        if (binding.runtime() instanceof LegacyPhysicsBackendRuntime runtime) {
            return runtime;
        }
        throw new IllegalStateException("Legacy live-handle test support requires the legacy runtime adapter");
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static Map<Integer, Object> spaces(@Nonnull LegacyPhysicsBackendRuntime runtime) {
        return (Map<Integer, Object>) get(SPACES, runtime);
    }

    @Nonnull
    private static PhysicsSpace liveSpace(@Nonnull Object state) {
        return (PhysicsSpace) get(field(state.getClass(), "space"), state);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static Map<Long, PhysicsBody> bodiesById(@Nonnull Object state) {
        return (Map<Long, PhysicsBody>) get(field(state.getClass(), "bodiesById"), state);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static Map<PhysicsBody, Long> bodyIdsByBody(@Nonnull Object state) {
        return (Map<PhysicsBody, Long>) get(field(state.getClass(), "bodyIdsByBody"), state);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static Map<Long, PhysicsJoint> jointsById(@Nonnull Object state) {
        return (Map<Long, PhysicsJoint>) get(field(state.getClass(), "jointsById"), state);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static Map<PhysicsJoint, Long> jointIdsByJoint(@Nonnull Object state) {
        return (Map<PhysicsJoint, Long>) get(field(state.getClass(), "jointIdsByJoint"), state);
    }

    private static long nextId(@Nonnull LegacyPhysicsBackendRuntime runtime, @Nonnull Field field) {
        try {
            long id = field.getLong(runtime);
            field.setLong(runtime, id + 1L);
            return id;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot update legacy runtime id counter", exception);
        }
    }

    private record RegisteredBody(long backendBodyId, boolean created) {
    }

    @Nonnull
    private static Field field(@Nonnull Class<?> owner, @Nonnull String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot access " + owner.getName() + "." + name, exception);
        }
    }

    @Nonnull
    private static Object get(@Nonnull Field field, @Nonnull Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read " + field.getName(), exception);
        }
    }
}
