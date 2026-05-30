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
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsKinematicControlSystem.ControlAnchorUpdate;
import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsKinematicControlSystem.ControlMutationState;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsKinematicControlSystemTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void controlAnchorUpdateCopiesMutableVectors() {
        RigidBodyKey bodyId = RigidBodyKey.random();
        RigidBodyKey anchorBodyId = RigidBodyKey.random();
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
        RigidBodyKey bodyId = RigidBodyKey.random();
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
        RigidBodyKey bodyId = RigidBodyKey.random();
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
    void pendingAnchorCreationIsUsableBeforePublishedRegistrationViewArrives() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:control-pending-anchor-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(4,
            Duration.ofSeconds(2L))) {
            worker.start("control-pending-anchor");
            resource.attachWorkerResource(worker);
            PhysicsSpace space = resource.createLiveSpace(backend.getId());
            RigidBodyKey anchorBodyId = RigidBodyKey.random();

            resource.submitCommands(10L, commands -> commands
                    .spawnBody(anchorBodyId, spawn -> spawn
                        .space(space.getId())
                        .sphere(0.08f)
                        .kinematic()
                        .temporary()
                        .runtimeOnly()))
                .completionSummary()
                .toCompletableFuture()
                .get(2L, TimeUnit.SECONDS);

            assertNull(resource.getBodyRegistrationView(anchorBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(anchorBodyId));

            resource.detachWorkerResource(worker);
        }
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
        RigidBodyKey bodyId = resource.addBody(space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        RigidBodyKey anchorBodyId = resource.addBody(space.getId(),
            anchorBody,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        space.createPointJoint(anchorBody, body, new Vector3f(), new Vector3f());

        assertEquals(1, space.jointCount());

        boolean removed = resource.callOwner("remove control joint",
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
        RigidBodyKey bodyId = resource.addBody(space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        RigidBodyKey anchorBodyId = resource.addBody(space.getId(),
            anchorBody,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsJoint controlJoint =
            space.createPointJoint(anchorBody, body, new Vector3f(), new Vector3f());
        JointKey controlJointId = resource.addJoint(space.getId(), controlJoint);
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
        assertEquals(controlJointId, session.getControlJointKey());
        assertEquals(controlJointId, session.clone().getControlJointKey());
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
