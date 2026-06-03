package dev.hytalemodding.impulse.core.internal.modules.control.systems;

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
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.TestPhysicsOwnerLane;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerSnapshot;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsKinematicControlSystem.ControlAnchorUpdate;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsKinematicControlSystem.ControlMutationState;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
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
        ControlAnchorUpdate first = update(bodyId, bodyId, 1.0f);
        ControlAnchorUpdate second = update(bodyId, bodyId, 2.0f);

        state.trackPendingMutation(bodyId, handle, first);

        assertTrue(state.hasPendingMutation(bodyId));
        assertNull(state.selectReadyUpdate(bodyId, second));

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

        state.trackPendingMutation(bodyId, handle, update(bodyId, bodyId, 1.0f));
        state.clear(bodyId);

        assertFalse(state.hasPendingMutation(bodyId));

        completion.complete(null);
        assertFalse(state.hasPendingMutation(bodyId));
    }

    @Test
    void pendingControlMutationCoalescesLatestTargetUntilCompletion() {
        ControlMutationState state = new ControlMutationState();
        RigidBodyKey bodyId = RigidBodyKey.random();
        RigidBodyKey anchorBodyId = RigidBodyKey.random();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        PhysicsMutationHandle<Void> handle = PhysicsMutationHandle.fromCompletion("test",
            null,
            completion);
        ControlAnchorUpdate first = update(bodyId, anchorBodyId, 1.0f);
        ControlAnchorUpdate second = update(bodyId, anchorBodyId, 2.0f);
        ControlAnchorUpdate third = update(bodyId, anchorBodyId, 3.0f);
        ControlAnchorUpdate current = update(bodyId, anchorBodyId, 4.0f);

        state.trackPendingMutation(anchorBodyId, handle, first);

        assertNull(state.selectReadyUpdate(anchorBodyId, second));
        assertNull(state.selectReadyUpdate(anchorBodyId, third));

        completion.complete(null);
        ControlAnchorUpdate ready = state.selectReadyUpdate(anchorBodyId, current);

        assertSame(third, ready);
    }

    @Test
    void pendingAnchorCreationIsUsableBeforePublishedRegistrationViewArrives() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:control-pending-anchor-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("control-pending-anchor");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backend.getId());
            RigidBodyKey anchorBodyId = RigidBodyKey.random();

            resource.submitCommands(10L, commands -> commands
                    .spawnBody(anchorBodyId, spawn -> spawn
                        .space(space.id())
                        .sphere(0.08f)
                        .kinematic()
                        .temporary()
                        .runtimeOnly()))
                .completionSummary()
                .toCompletableFuture()
                .get(2L, TimeUnit.SECONDS);

            assertNull(resource.getBodyRegistrationView(anchorBodyId));
            assertTrue(resource.hasPublishedOrPendingBodyRegistration(anchorBodyId));

            resource.detachOwnerExecutor(owner);
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

    @Test
    void sessionCleanupDestroysAnchorBodyAndClearsControlledState() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:control-cleanup-" + BACKEND_COUNTER.incrementAndGet());
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
        PhysicsJoint controlJoint =
            space.createPointJoint(anchorBody, body, new Vector3f(), new Vector3f());
        JointKey controlJointId = resource.addJoint(space.id(), controlJoint);
        resource.markBodyControlled(bodyId);
        PhysicsControlSessionComponent session = new PhysicsControlSessionComponent(bodyId,
            anchorBodyId,
            controlJointId,
            null,
            space.id(),
            body.getBodyType(),
            4.0f,
            new Vector3f(),
            new Vector3f());

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

    @Test
    void sessionCleanupQueuesOwnerReleaseWithoutBlocking() throws Exception {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:control-cleanup-owner-" + BACKEND_COUNTER.incrementAndGet());
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
        PhysicsJoint controlJoint =
            space.createPointJoint(anchorBody, body, new Vector3f(), new Vector3f());
        JointKey controlJointId = resource.addJoint(space.id(), controlJoint);
        resource.markBodyControlled(bodyId);
        PhysicsControlSessionComponent session = new PhysicsControlSessionComponent(bodyId,
            anchorBodyId,
            controlJointId,
            null,
            space.id(),
            body.getBodyType(),
            4.0f,
            new Vector3f(),
            new Vector3f());

        CountDownLatch mutationStarted = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane(4,
            Duration.ofSeconds(2L))) {
            owner.start("control-cleanup-owner");
            resource.attachOwnerExecutor(owner);
            owner.submitMutation("blocking mutation", () -> {
                mutationStarted.countDown();
                assertTrue(releaseMutation.await(2L, TimeUnit.SECONDS));
                return PhysicsOwnerSnapshot.empty();
            });
            assertTrue(mutationStarted.await(2L, TimeUnit.SECONDS));

            CompletableFuture<Void> cleanup = CompletableFuture.runAsync(
                () -> PhysicsControlSessionCleanup.cleanup(resource, session));
            cleanup.get(200L, TimeUnit.MILLISECONDS);

            assertFalse(resource.isBodyControlled(bodyId));
            assertEquals(2, owner.pendingMutations());

            releaseMutation.countDown();
            pollMutationCompletions(owner, 2);
            resource.detachOwnerExecutor(owner);
        } finally {
            releaseMutation.countDown();
        }
    }

    @Nonnull
    private static ControlAnchorUpdate update(@Nonnull RigidBodyKey bodyId,
        @Nonnull RigidBodyKey anchorBodyId,
        float coordinate) {
        return new ControlAnchorUpdate(bodyId,
            anchorBodyId,
            new Vector3f(coordinate, coordinate, coordinate),
            new Vector3f(coordinate + 1.0f, coordinate + 1.0f, coordinate + 1.0f));
    }

    private static void pollMutationCompletions(@Nonnull TestPhysicsOwnerLane owner,
        int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        int completed = 0;
        while (System.nanoTime() < deadline) {
            completed += owner.pollCompletedMutations(8).size();
            if (completed >= expected) {
                return;
            }
            Thread.sleep(10L);
        }
        assertEquals(expected, completed);
    }
}
