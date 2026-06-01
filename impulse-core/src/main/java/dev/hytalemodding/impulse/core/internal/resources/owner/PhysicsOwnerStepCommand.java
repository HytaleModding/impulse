package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBackendBodyActivationEvent;
import dev.hytalemodding.impulse.api.PhysicsBackendContactEvent;
import dev.hytalemodding.impulse.api.PhysicsBackendEvent;
import dev.hytalemodding.impulse.api.PhysicsBackendEventBatch;
import dev.hytalemodding.impulse.api.PhysicsBackendEventBuffer;
import dev.hytalemodding.impulse.api.PhysicsBackendJointBreakEvent;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsStepCountPolicy;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsBodyActivationEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsJointBreakEvent;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.ArrayList;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Owner-lane execution of the world physics step.
 *
 * <p>{@link PhysicsSpace} mutations are owner-lane operations. Callers must
 * only submit this command when the lane is the exclusive owner of backend
 * spaces and the resource until the result has been consumed.</p>
 *
 * <p>{@code stepSequence} and {@code serverTick} are copied into the published
 * snapshot frame for diagnostics and external correlation. They do not control
 * publication freshness; that remains guarded by the resource's frame and world
 * epochs.</p>
 */
public final class PhysicsOwnerStepCommand implements PhysicsOwnerCommand {

    private static final float DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP = 0.45f;
    private static final float MIN_LINEAR_TRAVEL_PER_SUBSTEP = 0.125f;
    private static final float SHAPE_TRAVEL_FRACTION = 0.75f;
    private static final float MAX_ANGULAR_RADIANS_PER_SUBSTEP = (float) Math.toRadians(30.0);

    @Nonnull
    private final PhysicsWorldRuntimeResource resource;
    private final float dt;
    private final boolean profilingEnabled;
    private final long stepSequence;
    private final long serverTick;
    @Nullable
    private RuntimeException failure;
    @Nullable
    private PublishedPhysicsSnapshotFrame publishedFrame;
    @Nullable
    private PhysicsEventFrame eventFrame;

    public PhysicsOwnerStepCommand(@Nonnull PhysicsWorldResource resource,
        float dt,
        boolean profilingEnabled) {
        this(resource, dt, profilingEnabled, 0L, 0L);
    }

    /**
     * Creates an owner step command with snapshot correlation metadata.
     *
     * @param stepSequence monotonic Impulse step-scheduler sequence; not a
     *     Hytale world tick and not guaranteed contiguous in published frames
     * @param serverTick Hytale world tick observed when the step command was
     *     scheduled; not a physics step counter
     */
    public PhysicsOwnerStepCommand(@Nonnull PhysicsWorldResource resource,
        float dt,
        boolean profilingEnabled,
        long stepSequence,
        long serverTick) {
        this.resource = PhysicsWorldRuntimeResource.require(
            Objects.requireNonNull(resource, "resource"));
        this.dt = dt;
        this.profilingEnabled = profilingEnabled;
        this.stepSequence = Math.max(0L, stepSequence);
        this.serverTick = Math.max(0L, serverTick);
    }

    @Nonnull
    @Override
    public PhysicsOwnerSnapshot run() {
        StepExecution result = runStepCycle(resource,
            dt,
            profilingEnabled,
            stepSequence,
            serverTick);
        failure = result.failure();
        publishedFrame = result.frame();
        eventFrame = result.eventFrame();
        return result.snapshot();
    }

    @Nullable
    public RuntimeException failure() {
        return failure;
    }

    @Nullable
    public PublishedPhysicsSnapshotFrame publishedFrame() {
        return publishedFrame;
    }

    @Nullable
    public PhysicsEventFrame eventFrame() {
        return eventFrame;
    }

    @Nonnull
    static PhysicsOwnerSnapshot runStep(@Nonnull PhysicsWorldResource resource,
        float dt,
        boolean profilingEnabled) {
        return runStepCycle(resource, dt, profilingEnabled, 0L, 0L).snapshot();
    }

    @Nonnull
    static StepExecution runStepCycle(@Nonnull PhysicsWorldResource resource,
        float dt,
        boolean profilingEnabled,
        long stepSequence,
        long serverTick) {
        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(
            Objects.requireNonNull(resource, "resource"));
        float safeDt = Float.isFinite(dt) ? Math.max(dt, 0.0f) : 0.0f;
        PhysicsWorldSettings settings = runtime.getWorldSettings();
        PhysicsStepMode stepMode = settings.getStepMode();
        int simulationSteps = settings.getSimulationSteps();
        float configuredMaxStepDt = settings.getMaxStepDt();
        float maxStepDt = configuredMaxStepDt > 0f
            ? configuredMaxStepDt
            : PhysicsWorldSettings.DEFAULT_MAX_STEP_DT;
        int steps = stepMode == PhysicsStepMode.ADAPTIVE
            ? resolveAdaptiveStepCount(safeDt, simulationSteps, maxStepDt, runtime)
            : PhysicsStepCountPolicy.resolveStepCount(safeDt,
                simulationSteps,
                maxStepDt,
                stepMode);

        long startNanos = profilingEnabled ? System.nanoTime() : 0L;
        StepCounters counters = new StepCounters();
        StepBackendEvents backendEvents = new StepBackendEvents();
        RuntimeException stepFailure = null;
        try {
            if (profilingEnabled) {
                resetStepPhaseStats(runtime);
            }
            if (stepMode != PhysicsStepMode.CCD) {
                restoreForcedContinuousCollision(runtime);
            }
            executeSteps(runtime, safeDt, stepMode, steps, counters, backendEvents);
        } catch (RuntimeException exception) {
            stepFailure = exception;
        }
        long stepNanos = profilingEnabled ? System.nanoTime() - startNanos : 0L;

        PublishedPhysicsSnapshotFrame frame;
        try {
            frame = runtime.capturePublishedSnapshotFrame(stepSequence,
                serverTick,
                stepFailure == null
                    ? PublishedPhysicsSnapshotFrame.Status.COMPLETE
                    : PublishedPhysicsSnapshotFrame.Status.PARTIAL,
                stepNanos,
                profilingEnabled,
                backendEvents.physicsEvents,
                backendEvents.droppedBackendEventCount);
        } catch (RuntimeException exception) {
            if (stepFailure != null) {
                stepFailure.addSuppressed(exception);
                throw stepFailure;
            }
            throw exception;
        }
        PhysicsEventFrame eventFrame = runtime.getLatestEventFrame();

        PhysicsStepPhaseStats nativePhaseStats = profilingEnabled
            ? collectStepPhaseStats(runtime)
            : PhysicsStepPhaseStats.unavailable();
        return new StepExecution(new PhysicsOwnerSnapshot(counters.spaceCount(),
            counters.substeps(),
            frame.bodyCount(),
            frame.spatialIndexCellCount(),
            stepNanos,
            frame.snapshotNanos(),
            nativePhaseStats), stepFailure, frame, eventFrame);
    }

    private static void resetStepPhaseStats(@Nonnull PhysicsWorldRuntimeResource resource) {
        for (PhysicsSpace space : resource.iterateSpaces()) {
            space.resetStepPhaseStats();
        }
    }

    @Nonnull
    private static PhysicsStepPhaseStats collectStepPhaseStats(
        @Nonnull PhysicsWorldRuntimeResource resource) {
        PhysicsStepPhaseStats stats = PhysicsStepPhaseStats.unavailable();
        for (PhysicsSpace space : resource.iterateSpaces()) {
            stats = stats.add(space.getStepPhaseStats());
        }
        return stats;
    }

    private static int resolveAdaptiveStepCount(float dt,
        int simulationSteps,
        float maxStepDt,
        @Nonnull PhysicsWorldRuntimeResource resource) {
        float sampledDt = Math.max(dt, 0.0f);
        if (sampledDt <= 0.0f) {
            return simulationSteps;
        }

        int minimumSteps = PhysicsStepCountPolicy.resolveMaxStepCount(sampledDt,
            simulationSteps,
            maxStepDt);
        StepRisk risk = new StepRisk(sampledDt, minimumSteps);
        for (PhysicsSpace space : resource.iterateSpaces()) {
            space.forEachBody(risk::inspect);
        }
        return risk.steps();
    }

    private static void executeSteps(@Nonnull PhysicsWorldRuntimeResource resource,
        float safeDt,
        @Nonnull PhysicsStepMode stepMode,
        int steps,
        @Nonnull StepCounters counters,
        @Nonnull StepBackendEvents backendEvents) {
        float stepDt = safeDt / steps;
        PhysicsBackendEventBuffer eventBuffer = new PhysicsBackendEventBuffer();
        for (PhysicsSpace space : resource.iterateSpaces()) {
            counters.spaceCount++;
            if (stepMode == PhysicsStepMode.CCD && supportsContinuousCollision(space)) {
                forceContinuousCollision(resource, space);
            }
            for (int step = 0; step < steps; step++) {
                space.step(stepDt, eventBuffer);
                drainBackendEvents(resource, space, eventBuffer, backendEvents);
                counters.substeps++;
            }
        }
    }

    private static void drainBackendEvents(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBackendEventBuffer eventBuffer,
        @Nonnull StepBackendEvents backendEvents) {
        PhysicsBackendEventBatch batch = eventBuffer.drain();
        if (batch.isEmpty()) {
            return;
        }
        backendEvents.droppedBackendEventCount += batch.droppedEventCount();
        for (PhysicsBackendEvent event : batch.events()) {
            translateBackendEvent(resource, space, event, backendEvents);
        }
    }

    private static void translateBackendEvent(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBackendEvent event,
        @Nonnull StepBackendEvents backendEvents) {
        if (event instanceof PhysicsBackendContactEvent contactEvent) {
            translateContactEvent(resource, space, contactEvent, backendEvents);
        } else if (event instanceof PhysicsBackendBodyActivationEvent activationEvent) {
            translateBodyActivationEvent(resource, space, activationEvent, backendEvents);
        } else if (event instanceof PhysicsBackendJointBreakEvent jointBreakEvent) {
            translateJointBreakEvent(resource, space, jointBreakEvent, backendEvents);
        }
    }

    private static void translateContactEvent(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBackendContactEvent event,
        @Nonnull StepBackendEvents backendEvents) {
        RigidBodyKey bodyAKey = resource.getBodyKey(event.bodyA());
        RigidBodyKey bodyBKey = resource.getBodyKey(event.bodyB());
        if (bodyAKey == null || bodyBKey == null) {
            return;
        }
        backendEvents.physicsEvents.add(new PhysicsContactEvent(space.id(),
            event.phase(),
            bodyAKey,
            bodyBKey,
            event.pointOnA(),
            event.pointOnB(),
            event.normalOnB(),
            event.distance(),
            event.impulse()));
    }

    private static void translateBodyActivationEvent(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBackendBodyActivationEvent event,
        @Nonnull StepBackendEvents backendEvents) {
        RigidBodyKey bodyKey = resource.getBodyKey(event.body());
        if (bodyKey == null) {
            return;
        }
        backendEvents.physicsEvents.add(new PhysicsBodyActivationEvent(space.id(),
            event.phase(),
            bodyKey));
    }

    private static void translateJointBreakEvent(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBackendJointBreakEvent event,
        @Nonnull StepBackendEvents backendEvents) {
        JointKey jointKey = resource.getJointKey(event.joint());
        if (jointKey == null) {
            return;
        }
        RigidBodyKey bodyAKey = resource.getBodyKey(event.joint().getBodyA());
        RigidBodyKey bodyBKey = resource.getBodyKey(event.joint().getBodyB());
        backendEvents.physicsEvents.add(new PhysicsJointBreakEvent(space.id(),
            jointKey,
            bodyAKey,
            bodyBKey));
    }

    private static boolean supportsContinuousCollision(@Nonnull PhysicsSpace space) {
        return space.getCapability(PhysicsContinuousCollisionCapability.class).isPresent();
    }

    private static int requiredSteps(float travel, float safeTravel) {
        if (travel <= safeTravel) {
            return 1;
        }
        return (int) Math.ceil(travel / safeTravel);
    }

    private static float safeLinearTravel(@Nonnull PhysicsBody body) {
        return Math.clamp(
            approximateMinimumExtent(body) * SHAPE_TRAVEL_FRACTION,
            MIN_LINEAR_TRAVEL_PER_SUBSTEP,
            DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP);
    }

    private static float safeAngularTravel(@Nonnull PhysicsBody body) {
        return Math.clamp(
            approximateShapeRadius(body) * MAX_ANGULAR_RADIANS_PER_SUBSTEP,
            MIN_LINEAR_TRAVEL_PER_SUBSTEP,
            DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP);
    }

    private static float approximateMinimumExtent(@Nonnull PhysicsBody body) {
        ShapeType shapeType = body.getShapeType();
        if (shapeType == ShapeType.BOX) {
            Vector3f halfExtents = body.getBoxHalfExtents();
            if (halfExtents != null) {
                return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP,
                    Math.min(halfExtents.x, Math.min(halfExtents.y, halfExtents.z)));
            }
        }
        if (shapeType == ShapeType.SPHERE) {
            return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP, body.getSphereRadius());
        }
        if (shapeType == ShapeType.CAPSULE
            || shapeType == ShapeType.CYLINDER
            || shapeType == ShapeType.CONE) {
            return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP, body.getSphereRadius());
        }
        return DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP;
    }

    private static float approximateShapeRadius(@Nonnull PhysicsBody body) {
        ShapeType shapeType = body.getShapeType();
        if (shapeType == ShapeType.BOX) {
            Vector3f halfExtents = body.getBoxHalfExtents();
            if (halfExtents != null) {
                return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP,
                    (float) Math.sqrt(halfExtents.x * halfExtents.x
                        + halfExtents.y * halfExtents.y
                        + halfExtents.z * halfExtents.z));
            }
        }
        if (shapeType == ShapeType.SPHERE) {
            return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP, body.getSphereRadius());
        }
        if (shapeType == ShapeType.CAPSULE
            || shapeType == ShapeType.CYLINDER
            || shapeType == ShapeType.CONE) {
            return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP,
                body.getSphereRadius() + body.getHalfHeight());
        }
        return DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP;
    }

    private static void forceContinuousCollision(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpace space) {
        space.forEachBody(body -> {
            if (!body.isDynamic() || body.isContinuousCollisionEnabled()) {
                return;
            }

            RigidBodyKey bodyKey = resource.getBodyKey(body);
            if (bodyKey == null) {
                return;
            }
            body.setContinuousCollisionEnabled(true);
            resource.markContinuousCollisionForced(bodyKey);
        });
    }

    private static void restoreForcedContinuousCollision(@Nonnull PhysicsWorldRuntimeResource resource) {
        if (!resource.hasForcedContinuousCollisionBodies()) {
            return;
        }

        resource.forEachForcedContinuousCollisionBody(bodyKey -> {
            PhysicsBody body = resource.getBody(bodyKey);
            if (body != null) {
                body.setContinuousCollisionEnabled(false);
            }
        });
        resource.clearForcedContinuousCollisionBodies();
    }

    private static final class StepRisk {

        private final float dt;
        private int steps;
        @Nonnull
        private final Vector3f linearVelocity = new Vector3f();
        @Nonnull
        private final Vector3f angularVelocity = new Vector3f();

        private StepRisk(float dt, int minimumSteps) {
            this.dt = dt;
            steps = minimumSteps;
        }

        private void inspect(@Nonnull PhysicsBody body) {
            if (steps >= PhysicsWorldSettings.MAX_SIMULATION_STEPS
                || body.isStatic()
                || body.isSleeping()
                || body.isSensor()
                || (!body.isDynamic() && !body.isKinematic())) {
                return;
            }

            body.getLinearVelocity(linearVelocity);
            body.getAngularVelocity(angularVelocity);
            float linearTravel = linearVelocity.length() * dt;
            float angularSurfaceTravel = angularVelocity.length()
                * approximateShapeRadius(body)
                * dt;
            float safeLinearTravel = safeLinearTravel(body);
            int requiredSteps = Math.max(
                requiredSteps(linearTravel, safeLinearTravel),
                requiredSteps(angularSurfaceTravel, safeAngularTravel(body)));
            steps = Math.clamp(steps,
                Math.min(requiredSteps, PhysicsWorldSettings.MAX_SIMULATION_STEPS),
                PhysicsWorldSettings.MAX_SIMULATION_STEPS);
        }

        private int steps() {
            return steps;
        }
    }

    record StepExecution(@Nonnull PhysicsOwnerSnapshot snapshot,
                         @Nullable RuntimeException failure,
                         @Nonnull PublishedPhysicsSnapshotFrame frame,
                         @Nonnull PhysicsEventFrame eventFrame) {
    }

    private static final class StepBackendEvents {

        @Nonnull
        private final ArrayList<PhysicsFrameEvent> physicsEvents = new ArrayList<>();
        private int droppedBackendEventCount;
    }

    private static final class StepCounters {

        private int spaceCount;
        private int substeps;

        private int spaceCount() {
            return spaceCount;
        }

        private int substeps() {
            return substeps;
        }
    }
}
