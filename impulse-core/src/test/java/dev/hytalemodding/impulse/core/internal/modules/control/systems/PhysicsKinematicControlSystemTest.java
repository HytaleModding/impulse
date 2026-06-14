package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsKinematicControlSystem.ControlAnchorUpdate;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsKinematicControlSystem.ControlMutationState;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsKinematicControlSystemTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void controlAnchorUpdateCopiesMutableVectors() {
        UUID bodyId = UUID.randomUUID();
        UUID anchorBodyId = UUID.randomUUID();
        Vector3f target = new Vector3f(1.0f, 2.0f, 3.0f);
        Vector3f releaseVelocity = new Vector3f(4.0f, 5.0f, 6.0f);
        ControlAnchorUpdate update = new ControlAnchorUpdate(bodyId,
            anchorBodyId,
            target,
            releaseVelocity);
        target.zero();
        releaseVelocity.zero();

        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), update.target());
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), update.releaseVelocity());
    }

    @Test
    void submittedControlMutationSuppressesIdenticalTarget() {
        ControlMutationState state = new ControlMutationState();
        UUID bodyId = UUID.randomUUID();
        ControlAnchorUpdate first = update(bodyId, bodyId, 1.0f);
        ControlAnchorUpdate sameTarget = update(bodyId, bodyId, 1.0f);
        ControlAnchorUpdate changedTarget = update(bodyId, bodyId, 2.0f);

        state.trackSubmittedMutation(bodyId, first);

        assertNull(state.selectReadyUpdate(bodyId, sameTarget));
        assertSame(changedTarget, state.selectReadyUpdate(bodyId, changedTarget));
    }

    @Test
    void clearingControlMutationStateAllowsIdenticalTargetRetry() {
        ControlMutationState state = new ControlMutationState();
        UUID bodyId = UUID.randomUUID();
        ControlAnchorUpdate first = update(bodyId, bodyId, 1.0f);
        ControlAnchorUpdate retry = update(bodyId, bodyId, 1.0f);

        state.trackSubmittedMutation(bodyId, first);
        state.clear(bodyId);

        assertSame(retry, state.selectReadyUpdate(bodyId, retry));
    }

    @Test
    void trackingSubmittedControlMutationUpdatesSuppressionTarget() {
        ControlMutationState state = new ControlMutationState();
        UUID bodyId = UUID.randomUUID();
        UUID anchorBodyId = UUID.randomUUID();
        ControlAnchorUpdate first = update(bodyId, anchorBodyId, 1.0f);
        ControlAnchorUpdate second = update(bodyId, anchorBodyId, 2.0f);
        ControlAnchorUpdate sameSecondTarget = update(bodyId, anchorBodyId, 2.0f);
        ControlAnchorUpdate third = update(bodyId, anchorBodyId, 3.0f);

        state.trackSubmittedMutation(anchorBodyId, first);
        assertSame(second, state.selectReadyUpdate(anchorBodyId, second));

        state.trackSubmittedMutation(anchorBodyId, second);

        assertNull(state.selectReadyUpdate(anchorBodyId, sameSecondTarget));
        assertSame(third, state.selectReadyUpdate(anchorBodyId, third));
    }

    @Test
    void clearingSystemMutationStateAfterReleaseAllowsIdenticalTargetRetry() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        Store<EntityStore> store = registry.addStore(
            new EntityStore(TestInstanceFactory.world("control-release-mutation-state-test")),
            EmptyResourceStorage.get());
        try {
            ControlMutationState state = PhysicsKinematicControlSystem.stateFor(store);
            UUID bodyId = UUID.randomUUID();
            UUID anchorBodyId = UUID.randomUUID();
            ControlAnchorUpdate first = update(bodyId, anchorBodyId, 1.0f);
            ControlAnchorUpdate queued = update(bodyId, anchorBodyId, 1.0f);
            ControlAnchorUpdate afterRelease = update(bodyId, anchorBodyId, 1.0f);

            state.trackSubmittedMutation(anchorBodyId, first);
            assertNull(state.selectReadyUpdate(anchorBodyId, queued));

            PhysicsKinematicControlSystem.clearMutationState(store, anchorBodyId);

            assertSame(afterRelease, state.selectReadyUpdate(anchorBodyId, afterRelease));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void controlJointCleanupResolvesJointFromBodyIds() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:control-joint-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId());
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody anchorBody = space.createSphere(0.1f, 1.0f);
        RigidBodyKey bodyId = resource.addBody(space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        RigidBodyKey anchorBodyId = resource.addBody(space.id(),
            anchorBody,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        space.createPointJoint(anchorBody, body, new Vector3f(), new Vector3f());

        assertEquals(1, space.jointCount());

        boolean removed = resource.callOwner("remove control joint", () -> {
            if (space.getJoints().isEmpty()) {
                return false;
            }
            space.removeJoint(space.getJoints().getFirst());
            return true;
        });
        assertTrue(removed);

        assertEquals(0, space.jointCount());
    }

    @Nonnull
    private static ControlAnchorUpdate update(@Nonnull UUID bodyId,
        @Nonnull UUID anchorBodyId,
        float coordinate) {
        return new ControlAnchorUpdate(bodyId,
            anchorBodyId,
            new Vector3f(coordinate, coordinate, coordinate),
            new Vector3f(coordinate + 1.0f, coordinate + 1.0f, coordinate + 1.0f));
    }

}
