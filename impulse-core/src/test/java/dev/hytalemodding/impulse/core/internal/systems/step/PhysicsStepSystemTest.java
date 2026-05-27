package dev.hytalemodding.impulse.core.internal.systems.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCompletion;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import org.joml.Vector3f;

class PhysicsStepSystemTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void submittedStepKeepsSchedulerSequenceSeparateFromServerTick() throws Exception {
        PhysicsStepSystem system = new PhysicsStepSystem();
        PhysicsStepSystem.StepSchedulerState state = new PhysicsStepSystem.StepSchedulerState();
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            worker.start("step-system-sequence-test");
            resource.attachWorkerResource(worker);

            // Worker steps run detached from the scheduling tick, so the published
            // frame must preserve the scheduling metadata captured at submission.
            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 42L);

            PhysicsWorkerStepCompletion completion = pollCompletedStep(worker);
            PublishedPhysicsSnapshotFrame frame = completion.frame();
            assertNotNull(frame);
            assertEquals(1L, frame.stepSequence());
            assertEquals(42L, frame.serverTick());
        }
    }

    @Test
    void pendingWorkerStepAccumulatesElapsedDtForNextSubmission() throws Exception {
        PhysicsStepSystem system = new PhysicsStepSystem();
        PhysicsStepSystem.StepSchedulerState state = new PhysicsStepSystem.StepSchedulerState();
        RecordingBackend backend = registerBackend();
        PhysicsWorldRuntimeResource resource = fixedStepResource(backend);
        configureWorldSettings(resource,
            settings -> settings.setStepSchedulingMode(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT));
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        RecordingSpace space = backend.space();
        CountDownLatch stepStarted = new CountDownLatch(1);
        CountDownLatch releaseStep = new CountDownLatch(1);

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            worker.start("step-system-accumulated-dt-test");
            resource.attachWorkerResource(worker);
            space.blockNextStep(stepStarted, releaseStep);

            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 1L);
            assertTrue(stepStarted.await(2, TimeUnit.SECONDS));

            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 2L);
            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 3L);

            releaseStep.countDown();
            pollCompletedStep(worker);

            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 4L);
            PhysicsWorkerStepCompletion completion = pollCompletedStep(worker);

            assertEquals(2, space.stepDts.size());
            assertEquals(0.05f, space.stepDts.get(0), 0.00001f);
            assertEquals(0.15f, space.stepDts.get(1), 0.00001f);
            PublishedPhysicsSnapshotFrame frame = completion.frame();
            assertNotNull(frame);
            assertEquals(2L, frame.stepSequence());
            assertEquals(4L, frame.serverTick());
        } finally {
            releaseStep.countDown();
        }
    }

    @Test
    void accumulatedDtIsCappedAndDroppedBacklogIsProfiled() throws Exception {
        PhysicsStepSystem system = new PhysicsStepSystem();
        PhysicsStepSystem.StepSchedulerState state = new PhysicsStepSystem.StepSchedulerState();
        RecordingBackend backend = registerBackend();
        PhysicsWorldRuntimeResource resource = fixedStepResource(backend);
        configureWorldSettings(resource,
            settings -> settings.setStepSchedulingMode(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT));
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        profiling.setEnabled(true);
        RecordingSpace space = backend.space();
        CountDownLatch stepStarted = new CountDownLatch(1);
        CountDownLatch releaseStep = new CountDownLatch(1);

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            worker.start("step-system-capped-dt-test");
            resource.attachWorkerResource(worker);
            space.blockNextStep(stepStarted, releaseStep);

            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 1L);
            assertTrue(stepStarted.await(2, TimeUnit.SECONDS));
            for (int tick = 2; tick < 12; tick++) {
                system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, tick);
            }

            releaseStep.countDown();
            pollCompletedStep(worker);

            system.submitStepIfIdle(state, worker, resource, 0.0f, profiling, 12L);
            pollCompletedStep(worker);

            assertEquals(2, space.stepDts.size());
            assertEquals(0.25f, space.stepDts.get(1), 0.00001f);
            assertTrue(profiling.getCumulativeStep().getDtCapHits() > 0);
            assertTrue(profiling.getCumulativeStep().getDroppedBacklogTicks() > 0);
            assertTrue(profiling.getCumulativeStep().getDroppedBacklogDtNanos() > 0L);
            assertEquals(250_000_000L,
                profiling.getCumulativeStep().getMaxSchedulerBacklogDtNanos());
        } finally {
            releaseStep.countDown();
        }
    }

    @Test
    void nonFinitePendingDtDoesNotPoisonAccumulator() throws Exception {
        PhysicsStepSystem system = new PhysicsStepSystem();
        PhysicsStepSystem.StepSchedulerState state = new PhysicsStepSystem.StepSchedulerState();
        RecordingBackend backend = registerBackend();
        PhysicsWorldRuntimeResource resource = fixedStepResource(backend);
        configureWorldSettings(resource,
            settings -> settings.setStepSchedulingMode(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT));
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        RecordingSpace space = backend.space();
        CountDownLatch stepStarted = new CountDownLatch(1);
        CountDownLatch releaseStep = new CountDownLatch(1);

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            worker.start("step-system-safe-dt-test");
            resource.attachWorkerResource(worker);
            space.blockNextStep(stepStarted, releaseStep);

            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 1L);
            assertTrue(stepStarted.await(2, TimeUnit.SECONDS));
            system.submitStepIfIdle(state, worker, resource, Float.NaN, profiling, 2L);
            system.submitStepIfIdle(state, worker, resource, Float.POSITIVE_INFINITY, profiling, 3L);
            system.submitStepIfIdle(state, worker, resource, -1.0f, profiling, 4L);
            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 5L);

            releaseStep.countDown();
            pollCompletedStep(worker);

            system.submitStepIfIdle(state, worker, resource, 0.0f, profiling, 6L);
            pollCompletedStep(worker);

            assertEquals(2, space.stepDts.size());
            assertEquals(0.05f, space.stepDts.get(1), 0.00001f);
        } finally {
            releaseStep.countDown();
        }
    }

    @Test
    void dropPendingDtPolicySubmitsOnlyCurrentTickDtAfterPendingStep() throws Exception {
        PhysicsStepSystem system = new PhysicsStepSystem();
        PhysicsStepSystem.StepSchedulerState state = new PhysicsStepSystem.StepSchedulerState();
        RecordingBackend backend = registerBackend();
        PhysicsWorldRuntimeResource resource = fixedStepResource(backend);
        configureWorldSettings(resource,
            settings -> settings.setStepSchedulingMode(PhysicsStepSchedulingMode.DROP_PENDING_DT));
        PhysicsRuntimeProfilingResource profiling = new PhysicsRuntimeProfilingResource();
        profiling.setEnabled(true);
        RecordingSpace space = backend.space();
        CountDownLatch stepStarted = new CountDownLatch(1);
        CountDownLatch releaseStep = new CountDownLatch(1);

        try (PhysicsWorldWorkerResource worker = new PhysicsWorldWorkerResource(2,
            Duration.ofSeconds(2L))) {
            worker.start("step-system-drop-pending-dt-test");
            resource.attachWorkerResource(worker);
            space.blockNextStep(stepStarted, releaseStep);

            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 1L);
            assertTrue(stepStarted.await(2, TimeUnit.SECONDS));

            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 2L);
            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 3L);

            releaseStep.countDown();
            pollCompletedStep(worker);

            system.submitStepIfIdle(state, worker, resource, 0.05f, profiling, 4L);
            PhysicsWorkerStepCompletion completion = pollCompletedStep(worker);

            assertEquals(2, space.stepDts.size());
            assertEquals(0.05f, space.stepDts.get(0), 0.00001f);
            assertEquals(0.05f, space.stepDts.get(1), 0.00001f);
            assertEquals(0, profiling.getCumulativeStep().getSchedulerSamples());
            assertEquals(2, profiling.getCumulativeStep().getSkippedPendingSteps());
            PublishedPhysicsSnapshotFrame frame = completion.frame();
            assertNotNull(frame);
            assertEquals(2L, frame.stepSequence());
            assertEquals(4L, frame.serverTick());
        } finally {
            releaseStep.countDown();
        }
    }

    @Nonnull
    private static RecordingBackend registerBackend() {
        RecordingBackend backend = new RecordingBackend("test:step-system-"
            + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        return backend;
    }

    @Nonnull
    private static PhysicsWorldRuntimeResource fixedStepResource(@Nonnull RecordingBackend backend) {
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        configureWorldSettings(resource, settings -> {
            settings.setStepMode(PhysicsStepMode.FIXED);
            settings.setSimulationSteps(1);
        });
        resource.createSpace(backend.getId(),
            "step-system-test",
            PhysicsSpaceSettings.defaults());
        return resource;
    }

    private static void configureWorldSettings(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull Consumer<PhysicsWorldSettings> configurator) {
        PhysicsWorldSettings settings = resource.getWorldSettings();
        configurator.accept(settings);
        resource.setWorldSettings(settings);
    }

    @Nonnull
    private static PhysicsWorkerStepCompletion pollCompletedStep(
        @Nonnull PhysicsWorldWorkerResource worker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (System.nanoTime() < deadline) {
            PhysicsWorkerStepCompletion completion = worker.pollCompletedStep();
            if (completion != null) {
                return completion;
            }
            Thread.sleep(10L);
        }
        fail("Timed out waiting for physics step completion");
        throw new AssertionError();
    }

    private static final class RecordingBackend implements PhysicsBackend {

        @Nonnull
        private final BackendId id;
        @Nonnull
        private RecordingSpace space;

        private RecordingBackend(@Nonnull String id) {
            this.id = new BackendId(id);
        }

        @Nonnull
        @Override
        public BackendId getId() {
            return id;
        }

        @Override
        public void init() {
        }

        @Nonnull
        @Override
        public PhysicsSpace createSpace() {
            return createSpace(SpaceId.next());
        }

        @Nonnull
        @Override
        public PhysicsSpace createSpace(@Nonnull SpaceId spaceId) {
            space = new RecordingSpace(spaceId, id);
            return space;
        }

        @Nonnull
        private RecordingSpace space() {
            return space;
        }
    }

    private static final class RecordingSpace implements PhysicsSpace {

        @Nonnull
        private final SpaceId id;
        @Nonnull
        private final BackendId backendId;
        @Nonnull
        private final Vector3f gravity = new Vector3f();
        @Nonnull
        private final List<Float> stepDts = new ArrayList<>();
        @Nonnull
        private CountDownLatch stepStarted = new CountDownLatch(0);
        @Nonnull
        private CountDownLatch releaseStep = new CountDownLatch(0);

        private RecordingSpace(@Nonnull SpaceId id, @Nonnull BackendId backendId) {
            this.id = id;
            this.backendId = backendId;
        }

        private void blockNextStep(@Nonnull CountDownLatch stepStarted,
            @Nonnull CountDownLatch releaseStep) {
            this.stepStarted = stepStarted;
            this.releaseStep = releaseStep;
        }

        @Nonnull
        @Override
        public SpaceId getId() {
            return id;
        }

        @Nonnull
        @Override
        public BackendId getBackendId() {
            return backendId;
        }

        @Override
        public void step(float dt) {
            stepDts.add(dt);
            stepStarted.countDown();
            try {
                releaseStep.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while blocking physics step", exception);
            } finally {
                stepStarted = new CountDownLatch(0);
                releaseStep = new CountDownLatch(0);
            }
        }

        @Override
        public void setGravity(float x, float y, float z) {
            gravity.set(x, y, z);
        }

        @Nonnull
        @Override
        public Vector3f getGravity() {
            return new Vector3f(gravity);
        }

        @Override
        public void addBody(@Nonnull PhysicsBody body) {
        }

        @Override
        public void removeBody(@Nonnull PhysicsBody body) {
        }

        @Nonnull
        @Override
        public List<PhysicsBody> getBodies() {
            return List.of();
        }

        @Nonnull
        @Override
        public PhysicsBody createStaticPlane(float groundY) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsBody createBox(float halfX, float halfY, float halfZ, float mass) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsBody createBox(@Nonnull Vector3f halfExtents, float mass) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsBody createSphere(float radius, float mass) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsBody createCapsule(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsBody createCylinder(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsBody createCone(float radius,
            float halfHeight,
            @Nonnull PhysicsAxis axis,
            float mass) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public Optional<PhysicsRayHit> raycastClosest(@Nonnull Vector3f from, @Nonnull Vector3f to) {
            return Optional.empty();
        }

        @Nonnull
        @Override
        public List<PhysicsRayHit> raycastAll(@Nonnull Vector3f from, @Nonnull Vector3f to) {
            return List.of();
        }

        @Nonnull
        @Override
        public List<PhysicsContact> getContacts() {
            return List.of();
        }

        @Nonnull
        @Override
        public PhysicsJoint createFixedJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsJoint createPointJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsJoint createHingeJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            @Nonnull Vector3f axis) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsJoint createSliderJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            @Nonnull Vector3f axis) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public PhysicsJoint createSpringJoint(@Nonnull PhysicsBody bodyA,
            @Nonnull PhysicsBody bodyB,
            @Nonnull Vector3f anchorA,
            @Nonnull Vector3f anchorB,
            float restLength,
            float stiffness,
            float damping) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeJoint(@Nonnull PhysicsJoint joint) {
        }

        @Nonnull
        @Override
        public List<PhysicsJoint> getJoints() {
            return List.of();
        }
    }
}
