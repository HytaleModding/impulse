package dev.hytalemodding.impulse.core.internal.systems.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.control.PhysicsControlJointResolver;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsKinematicControlSystem.ControlAnchorUpdate;
import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsKinematicControlSystem.ControlMutationState;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.joint.PhysicsJointId;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsKinematicControlSystemTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void controlAnchorUpdateCopiesMutableVectors() {
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        PhysicsBodyId anchorBodyId = PhysicsBodyId.random();
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
    void pendingControlMutationBlocksAnotherSubmissionUntilComplete() {
        ControlMutationState state = new ControlMutationState();
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        PhysicsMutationHandle<Void> handle = PhysicsMutationHandle.fromCompletion("test",
            null,
            completion);

        state.trackPendingMutation(bodyId, handle);

        assertTrue(state.hasPendingMutation(bodyId));

        completion.complete(null);

        assertFalse(state.hasPendingMutation(bodyId));
    }

    @Test
    void clearingControlMutationStateAllowsImmediateRetry() {
        ControlMutationState state = new ControlMutationState();
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        PhysicsMutationHandle<Void> handle = PhysicsMutationHandle.fromCompletion("test",
            null,
            completion);

        state.trackPendingMutation(bodyId, handle);
        state.clear(bodyId);

        assertFalse(state.hasPendingMutation(bodyId));

        completion.complete(null);
        assertFalse(state.hasPendingMutation(bodyId));
    }

    @Test
    void controlJointCleanupResolvesJointFromBodyIds() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:control-joint-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId());
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody anchorBody = space.createSphere(0.1f, 1.0f);
        PhysicsBodyId bodyId = resource.addBody(space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PhysicsBodyId anchorBodyId = resource.addBody(space.getId(),
            anchorBody,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        space.createPointJoint(anchorBody, body, new Vector3f(), new Vector3f());

        assertEquals(1, space.jointCount());

        boolean removed = resource.callOnPhysicsOwner("remove control joint",
            access -> PhysicsControlJointResolver.removeControlJoint(access,
                space,
                bodyId,
                anchorBodyId));
        assertTrue(removed);

        assertEquals(0, space.jointCount());
    }

    @Test
    void sessionCleanupDestroysAnchorBodyAndClearsControlledState() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:control-cleanup-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpace space = resource.createLiveSpace(backend.getId());
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody anchorBody = space.createSphere(0.1f, 1.0f);
        PhysicsBodyId bodyId = resource.addBody(space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PhysicsBodyId anchorBodyId = resource.addBody(space.getId(),
            anchorBody,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsJoint controlJoint =
            space.createPointJoint(anchorBody, body, new Vector3f(), new Vector3f());
        PhysicsJointId controlJointId = resource.addJoint(space.getId(), controlJoint);
        resource.markBodyControlled(bodyId);
        PhysicsControlSessionComponent session = new PhysicsControlSessionComponent(bodyId,
            anchorBodyId,
            controlJointId,
            null,
            space.getId(),
            body.getBodyType(),
            4.0f,
            new Vector3f(),
            new Vector3f());

        assertFalse(Arrays.stream(PhysicsControlSessionComponent.class.getDeclaredFields())
            .anyMatch(field -> PhysicsJoint.class.isAssignableFrom(field.getType())));
        assertEquals(controlJointId, session.getControlJointId());
        assertEquals(controlJointId, session.clone().getControlJointId());
        assertSame(controlJoint, resource.getJoint(controlJointId));

        PhysicsControlSessionCleanup.cleanup(resource, session);

        assertFalse(resource.isBodyControlled(bodyId));
        assertNull(resource.getJoint(controlJointId));
        assertEquals(1, space.bodyCount());
        assertEquals(0, space.jointCount());
        assertTrue(space.containsBody(body));
        assertFalse(space.containsBody(anchorBody));
        assertEquals(body, resource.getBody(bodyId));
        assertNull(resource.getBodyRegistrationView(anchorBodyId));
    }
}
