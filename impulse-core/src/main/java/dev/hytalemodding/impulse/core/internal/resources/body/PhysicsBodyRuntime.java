package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.modules.control.PhysicsControlRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldLifecycleState;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistry;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistration;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Body lifecycle orchestration for one physics world.
 */
public final class PhysicsBodyRuntime {

    @Nonnull
    private final PhysicsSpaceRuntime spaceRuntime;
    @Nonnull
    private final PhysicsBodyRegistry bodyRegistry;
    @Nonnull
    private final PhysicsBodyRuntimeState runtimeState;
    @Nonnull
    private final PhysicsControlRuntimeState controlRuntime;
    @Nonnull
    private final PhysicsJointRegistry jointRegistry;
    @Nonnull
    private final PhysicsVisualRuntime visualRuntime;
    @Nonnull
    private final PhysicsWorldLifecycleState lifecycleState;
    @Nonnull
    private final Runnable worldChangedMarker;

    public PhysicsBodyRuntime(@Nonnull PhysicsSpaceRuntime spaceRuntime,
        @Nonnull PhysicsBodyRegistry bodyRegistry,
        @Nonnull PhysicsBodyRuntimeState runtimeState,
        @Nonnull PhysicsControlRuntimeState controlRuntime,
        @Nonnull PhysicsJointRegistry jointRegistry,
        @Nonnull PhysicsVisualRuntime visualRuntime,
        @Nonnull PhysicsWorldLifecycleState lifecycleState,
        @Nonnull Runnable worldChangedMarker) {
        this.spaceRuntime = spaceRuntime;
        this.bodyRegistry = bodyRegistry;
        this.runtimeState = runtimeState;
        this.controlRuntime = controlRuntime;
        this.jointRegistry = jointRegistry;
        this.visualRuntime = visualRuntime;
        this.lifecycleState = lifecycleState;
        this.worldChangedMarker = worldChangedMarker;
    }

    @Nonnull
    public RigidBodyKey addBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendBodyHandle backendBodyHandle,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsSpaceBinding binding = spaceRuntime.requireBinding(spaceId);
        long backendBodyId = backendBodyHandle.value();
        if (!binding.runtime().containsBody(binding.backendSpaceHandle().value(), backendBodyId)) {
            throw new IllegalArgumentException("Physics backend body id=" + backendBodyId
                + " is not registered in space " + spaceId);
        }
        bodyRegistry.validateRegisterable(bodyKey, backendBodyHandle, spaceId);
        PhysicsBodyRegistration registration =
            bodyRegistry.registerBody(bodyKey, backendBodyHandle, spaceId, kind, persistenceMode);
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshots.read(binding, backendBodyId);
        if (snapshot != null) {
            lifecycleState.putBodySnapshot(bodyKey,
                snapshot,
                spaceId,
                registration.kind(),
                registration.persistenceMode());
        }
        worldChangedMarker.run();
        return bodyKey;
    }

    public void destroyBody(@Nonnull RigidBodyKey bodyKey, boolean removeFromSpace) {
        destroyBody(Objects.requireNonNull(bodyKey, "bodyKey").value(), removeFromSpace);
    }

    public void destroyBody(@Nonnull UUID bodyUuid, boolean removeFromSpace) {
        PhysicsBodyRegistration registration = bodyRegistry.getRegistration(bodyUuid);
        if (registration != null) {
            if (removeFromSpace) {
                removeBodyFromSpace(registration);
            }
            bodyRegistry.unregisterBody(bodyUuid);
            clearBodyRuntimeState(bodyUuid);
        } else {
            clearBodyRuntimeState(bodyUuid);
        }
        worldChangedMarker.run();
    }

    public void destroyRegisteredBodies() {
        RuntimeException failure = null;
        boolean bodyFailure = false;
        for (PhysicsBodyRegistration registration : new ArrayList<>(bodyRegistry.getRegistrations())) {
            try {
                destroyBody(registration.bodyUuid(), true);
            } catch (RuntimeException exception) {
                bodyFailure = true;
                failure = collectFailure(failure, exception);
            }
        }
        if (bodyFailure) {
            throw failure;
        }
        clearBodyState();
    }

    public void clearBodyState() {
        clearBodyStateWithoutMarkingWorldChanged();
        worldChangedMarker.run();
    }

    public void clearBodyStateWithoutMarkingWorldChanged() {
        bodyRegistry.clear();
        runtimeState.clear();
        controlRuntime.clear();
        jointRegistry.clear();
        visualRuntime.clear();
        lifecycleState.clearBodySnapshots();
    }

    public void clearBodyRuntimeState(@Nonnull RigidBodyKey bodyKey) {
        clearBodyRuntimeState(Objects.requireNonNull(bodyKey, "bodyKey").value());
    }

    public void clearBodyRuntimeState(@Nonnull UUID bodyUuid) {
        visualRuntime.clearBodyRuntimeState(bodyUuid, null);
        lifecycleState.removeBodySnapshot(bodyUuid);
    }

    private void removeBodyFromSpace(@Nonnull PhysicsBodyRegistration registration) {
        for (PhysicsJointRegistration joint : jointRegistry.unregisterJointsForBody(registration.bodyUuid())) {
            PhysicsSpaceBinding jointSpace = spaceRuntime.getBinding(joint.spaceId());
            if (jointSpace != null) {
                jointSpace.runtime().removeJoint(jointSpace.backendSpaceHandle().value(), joint.backendJointHandle().value());
            }
        }
        PhysicsSpaceBinding binding = spaceRuntime.getBinding(registration.spaceId());
        if (binding != null) {
            binding.runtime().removeBody(binding.backendSpaceHandle().value(), registration.backendBodyHandle().value());
        }
    }

    @Nonnull
    private static RuntimeException collectFailure(@Nullable RuntimeException failure,
        @Nonnull RuntimeException exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }
}
