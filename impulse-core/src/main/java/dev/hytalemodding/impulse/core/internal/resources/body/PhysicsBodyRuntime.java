package dev.hytalemodding.impulse.core.internal.resources.body;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.control.PhysicsControlRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.chunk.PhysicsChunkBoundaryRuntime;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistry;
import dev.hytalemodding.impulse.core.internal.resources.snapshot.PhysicsWorldSnapshotState;
import dev.hytalemodding.impulse.core.internal.resources.space.PhysicsSpaceRuntime;
import dev.hytalemodding.impulse.core.internal.resources.visual.PhysicsVisualRuntime;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
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
    private final PhysicsWorldSnapshotState snapshotState;
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
        @Nonnull PhysicsWorldSnapshotState snapshotState,
        @Nonnull Runnable worldChangedMarker) {
        this.spaceRuntime = spaceRuntime;
        this.bodyRegistry = bodyRegistry;
        this.runtimeState = runtimeState;
        this.controlRuntime = controlRuntime;
        this.jointRegistry = jointRegistry;
        this.chunkRuntime = chunkRuntime;
        this.visualRuntime = visualRuntime;
        this.snapshotState = snapshotState;
        this.worldChangedMarker = worldChangedMarker;
    }

    public void markBodyCreationPending(@Nonnull PhysicsBodyId bodyId) {
        creationTracker.markPending(bodyId);
    }

    public void clearBodyCreationPending(@Nonnull PhysicsBodyId bodyId) {
        creationTracker.clearPending(bodyId);
    }

    public boolean isBodyCreationPending(@Nonnull PhysicsBodyId bodyId) {
        return creationTracker.isPending(bodyId);
    }

    @Nonnull
    public PhysicsBodyId addBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsSpace space = spaceRuntime.getSpace(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        if (!containsBody(space, body)) {
            space.addBody(body);
        }
        PhysicsBodyRegistration registration = bodyRegistry.registerBody(bodyId, body, spaceId, kind, persistenceMode);
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshot.from(body);
        snapshotState.putBodySnapshot(bodyId,
            snapshot,
            spaceId,
            registration.kind(),
            registration.persistenceMode());
        worldChangedMarker.run();
        return bodyId;
    }

    public void destroyBody(@Nonnull PhysicsBodyId bodyId, boolean removeFromSpace) {
        PhysicsBodyRegistration registration = bodyRegistry.getRegistration(bodyId);
        if (removeFromSpace && registration != null) {
            removeBodyFromSpace(registration.body(), registration);
        }
        if (registration != null) {
            bodyRegistry.unregisterBody(bodyId);
            clearBodyRuntimeState(bodyId);
        } else {
            clearBodyRuntimeState(bodyId);
        }
        worldChangedMarker.run();
    }

    public void destroyBody(@Nonnull PhysicsBody body) {
        PhysicsBodyId bodyId = bodyRegistry.getBodyId(body);
        if (bodyId != null) {
            destroyBody(bodyId, true);
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
        bodyRegistry.clear();
        runtimeState.clear();
        controlRuntime.clear();
        jointRegistry.clear();
        chunkRuntime.clear();
        visualRuntime.clear();
        snapshotState.clearBodySnapshots();
        worldChangedMarker.run();
    }

    public void clearBodyRuntimeState(@Nonnull PhysicsBodyId bodyId) {
        visualRuntime.clearBodyRuntimeState(bodyId);
        controlRuntime.clearBody(bodyId);
        chunkRuntime.clearBody(bodyId);
        snapshotState.removeBodySnapshot(bodyId);
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
