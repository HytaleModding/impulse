package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.control.PhysicsControlRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkBoundaryRuntime;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldLifecycleState;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import java.util.ArrayList;
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
    private final PhysicsChunkBoundaryRuntime chunkRuntime;
    @Nonnull
    private final PhysicsVisualRuntime visualRuntime;
    @Nonnull
    private final PhysicsWorldLifecycleState lifecycleState;
    @Nonnull
    private final Runnable worldChangedMarker;
    @Nonnull
    private final PhysicsBodyCreationTracker creationTracker = new PhysicsBodyCreationTracker();

    public PhysicsBodyRuntime(@Nonnull PhysicsSpaceRuntime spaceRuntime,
        @Nonnull PhysicsBodyRegistry bodyRegistry,
        @Nonnull PhysicsBodyRuntimeState runtimeState,
        @Nonnull PhysicsControlRuntimeState controlRuntime,
        @Nonnull PhysicsJointRegistry jointRegistry,
        @Nonnull PhysicsChunkBoundaryRuntime chunkRuntime,
        @Nonnull PhysicsVisualRuntime visualRuntime,
        @Nonnull PhysicsWorldLifecycleState lifecycleState,
        @Nonnull Runnable worldChangedMarker) {
        this.spaceRuntime = spaceRuntime;
        this.bodyRegistry = bodyRegistry;
        this.runtimeState = runtimeState;
        this.controlRuntime = controlRuntime;
        this.jointRegistry = jointRegistry;
        this.chunkRuntime = chunkRuntime;
        this.visualRuntime = visualRuntime;
        this.lifecycleState = lifecycleState;
        this.worldChangedMarker = worldChangedMarker;
    }

    public void markBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        creationTracker.markPending(bodyKey);
    }

    public void clearBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        creationTracker.clearPending(bodyKey);
    }

    public boolean isBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        return creationTracker.isPending(bodyKey);
    }

    @Nonnull
    public RigidBodyKey addBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsSpace space = spaceRuntime.getSpace(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        bodyRegistry.validateRegisterable(bodyKey, body);
        boolean addedToSpace = false;
        if (!containsBody(space, body)) {
            space.addBody(body);
            addedToSpace = true;
        }
        PhysicsBodyRegistration registration;
        try {
            registration = bodyRegistry.registerBody(bodyKey, body, spaceId, kind, persistenceMode);
        } catch (RuntimeException exception) {
            if (addedToSpace) {
                space.removeBody(body);
            }
            throw exception;
        }
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshot.from(body);
        lifecycleState.putBodySnapshot(bodyKey,
            snapshot,
            spaceId,
            registration.kind(),
            registration.persistenceMode());
        worldChangedMarker.run();
        return bodyKey;
    }

    public void destroyBody(@Nonnull RigidBodyKey bodyKey, boolean removeFromSpace) {
        PhysicsBodyRegistration registration = bodyRegistry.getRegistration(bodyKey);
        if (removeFromSpace && registration != null) {
            removeBodyFromSpace(registration.body(), registration);
        }
        if (registration != null) {
            bodyRegistry.unregisterBody(bodyKey);
            clearBodyRuntimeState(bodyKey);
        } else {
            clearBodyRuntimeState(bodyKey);
        }
        worldChangedMarker.run();
    }

    public void destroyBody(@Nonnull PhysicsBody body) {
        RigidBodyKey bodyKey = bodyRegistry.getBodyKey(body);
        if (bodyKey != null) {
            destroyBody(bodyKey, true);
        } else {
            removeBodyFromSpace(body, null);
            worldChangedMarker.run();
        }
    }

    public void destroyRegisteredBodies() {
        RuntimeException failure = null;
        boolean bodyFailure = false;
        for (PhysicsSpace space : spaceRuntime.iterateSpaces()) {
            for (PhysicsJoint joint : new ArrayList<>(space.getJoints())) {
                try {
                    space.removeJoint(joint);
                } catch (RuntimeException exception) {
                    failure = collectFailure(failure, exception);
                }
            }
        }
        for (PhysicsBodyRegistration registration : new ArrayList<>(bodyRegistry.getRegistrations())) {
            try {
                destroyBody(registration.id(), true);
            } catch (RuntimeException exception) {
                bodyFailure = true;
                failure = collectFailure(failure, exception);
            }
        }
        if (bodyFailure) {
            throw failure;
        }
        clearBodyState();
        if (failure != null) {
            throw failure;
        }
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
        chunkRuntime.clear();
        visualRuntime.clear();
        lifecycleState.clearBodySnapshots();
        creationTracker.clear();
    }

    public void clearBodyRuntimeState(@Nonnull RigidBodyKey bodyKey) {
        visualRuntime.clearBodyRuntimeState(bodyKey);
        controlRuntime.clearBody(bodyKey);
        chunkRuntime.clearBody(bodyKey);
        lifecycleState.removeBodySnapshot(bodyKey);
    }

    private void removeBodyFromSpace(@Nonnull PhysicsBody body, @Nullable PhysicsBodyRegistration registration) {
        jointRegistry.unregisterJointsForBody(body);
        if (registration != null) {
            PhysicsSpace space = spaceRuntime.getSpace(registration.spaceId());
            if (space != null) {
                space.removeBody(body);
                return;
            }
        }

        for (PhysicsSpace space : spaceRuntime.iterateSpaces()) {
            space.removeBody(body);
        }
    }

    private static boolean containsBody(@Nonnull PhysicsSpace space, @Nonnull PhysicsBody body) {
        for (PhysicsBody existing : space.getBodies()) {
            if (existing == body) {
                return true;
            }
        }
        return false;
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
