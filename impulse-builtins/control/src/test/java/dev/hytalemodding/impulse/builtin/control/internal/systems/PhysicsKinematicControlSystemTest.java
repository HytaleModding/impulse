package dev.hytalemodding.impulse.builtin.control.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.builtin.control.internal.systems.PhysicsKinematicControlSystem.ControlAnchorUpdate;
import dev.hytalemodding.impulse.builtin.control.internal.systems.PhysicsKinematicControlSystem.ControlMutationState;
import dev.hytalemodding.impulse.builtin.control.internal.testsupport.TestInstanceFactory;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsKinematicControlSystemTest {

    @Test
    void controlAnchorUpdateCopiesMutableVectors() {
        Ref<PhysicsStore> bodyRef = new TestPhysicsRef(1);
        Ref<PhysicsStore> anchorBodyRef = new TestPhysicsRef(2);
        Vector3f target = new Vector3f(1.0f, 2.0f, 3.0f);
        Vector3f releaseVelocity = new Vector3f(4.0f, 5.0f, 6.0f);
        ControlAnchorUpdate update = new ControlAnchorUpdate(bodyRef,
            anchorBodyRef,
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
        Ref<PhysicsStore> bodyRef = new TestPhysicsRef(1);
        Ref<PhysicsStore> anchorBodyRef = new TestPhysicsRef(2);
        ControlAnchorUpdate first = update(bodyRef, anchorBodyRef, 1.0f);
        ControlAnchorUpdate sameTarget = update(bodyRef, anchorBodyRef, 1.0f);
        ControlAnchorUpdate changedTarget = update(bodyRef, anchorBodyRef, 2.0f);

        state.trackSubmittedMutation(anchorBodyRef, first);

        assertNull(state.selectReadyUpdate(anchorBodyRef, sameTarget));
        assertSame(changedTarget, state.selectReadyUpdate(anchorBodyRef, changedTarget));
    }

    @Test
    void clearingControlMutationStateAllowsIdenticalTargetRetry() {
        ControlMutationState state = new ControlMutationState();
        Ref<PhysicsStore> bodyRef = new TestPhysicsRef(1);
        Ref<PhysicsStore> anchorBodyRef = new TestPhysicsRef(2);
        ControlAnchorUpdate first = update(bodyRef, anchorBodyRef, 1.0f);
        ControlAnchorUpdate retry = update(bodyRef, anchorBodyRef, 1.0f);

        state.trackSubmittedMutation(anchorBodyRef, first);
        state.clear(anchorBodyRef);

        assertSame(retry, state.selectReadyUpdate(anchorBodyRef, retry));
    }

    @Test
    void trackingSubmittedControlMutationUpdatesSuppressionTarget() {
        ControlMutationState state = new ControlMutationState();
        Ref<PhysicsStore> bodyRef = new TestPhysicsRef(1);
        Ref<PhysicsStore> anchorBodyRef = new TestPhysicsRef(2);
        ControlAnchorUpdate first = update(bodyRef, anchorBodyRef, 1.0f);
        ControlAnchorUpdate second = update(bodyRef, anchorBodyRef, 2.0f);
        ControlAnchorUpdate sameSecondTarget = update(bodyRef, anchorBodyRef, 2.0f);
        ControlAnchorUpdate third = update(bodyRef, anchorBodyRef, 3.0f);

        state.trackSubmittedMutation(anchorBodyRef, first);
        assertSame(second, state.selectReadyUpdate(anchorBodyRef, second));

        state.trackSubmittedMutation(anchorBodyRef, second);

        assertNull(state.selectReadyUpdate(anchorBodyRef, sameSecondTarget));
        assertSame(third, state.selectReadyUpdate(anchorBodyRef, third));
    }

    @Test
    void clearingSystemMutationStateAfterReleaseAllowsIdenticalTargetRetry() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        Store<EntityStore> store = registry.addStore(
            new EntityStore(TestInstanceFactory.world("control-release-mutation-state-test")),
            EmptyResourceStorage.get());
        try {
            ControlMutationState state = PhysicsKinematicControlSystem.stateFor(store);
            Ref<PhysicsStore> bodyRef = new TestPhysicsRef(1);
            Ref<PhysicsStore> anchorBodyRef = new TestPhysicsRef(2);
            ControlAnchorUpdate first = update(bodyRef, anchorBodyRef, 1.0f);
            ControlAnchorUpdate queued = update(bodyRef, anchorBodyRef, 1.0f);
            ControlAnchorUpdate afterRelease = update(bodyRef, anchorBodyRef, 1.0f);

            state.trackSubmittedMutation(anchorBodyRef, first);
            assertNull(state.selectReadyUpdate(anchorBodyRef, queued));

            PhysicsKinematicControlSystem.clearMutationState(store, anchorBodyRef);

            assertSame(afterRelease, state.selectReadyUpdate(anchorBodyRef, afterRelease));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static ControlAnchorUpdate update(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<PhysicsStore> anchorBodyRef,
        float coordinate) {
        return new ControlAnchorUpdate(bodyRef,
            anchorBodyRef,
            new Vector3f(coordinate, coordinate, coordinate),
            new Vector3f(coordinate + 1.0f, coordinate + 1.0f, coordinate + 1.0f));
    }

    private static final class TestPhysicsRef extends Ref<PhysicsStore> {

        private TestPhysicsRef(int index) {
            super(null, index);
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }
}
