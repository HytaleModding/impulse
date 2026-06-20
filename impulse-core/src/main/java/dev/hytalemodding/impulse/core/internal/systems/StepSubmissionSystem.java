package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.BackendStepPhaseStatsSink;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource.BodyHitMetadata;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource.BodySnapshotMetadata;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource.CompletedStep;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource.StepInput;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsStepCountPolicy;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Submits the next backend step from PhysicsStore.tick().
 */
public final class StepSubmissionSystem extends TickingSystem<PhysicsStore> {

    private static final float DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP = 0.45f;
    private static final float MIN_LINEAR_TRAVEL_PER_SUBSTEP = 0.125f;
    private static final float SHAPE_TRAVEL_FRACTION = 0.75f;
    private static final float MAX_ANGULAR_RADIANS_PER_SUBSTEP = (float) Math.toRadians(30.0);

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PhysicsStoreQueuedReadSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isPending() || restore.isFailed()) {
            return;
        }
        float safeDt = Float.isFinite(dt) ? Math.max(dt, 0.0f) : 0.0f;
        if (safeDt <= 0.0f) {
            return;
        }
        PhysicsWorldSettingsResource settingsResource = store.getResource(
            PhysicsWorldSettingsResource.getResourceType());
        PhysicsWorldSettings settings = settingsResource.getSettings();
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        PhysicsStepSchedulerResource scheduler = store.getResource(
            PhysicsStepSchedulerResource.getResourceType());
        StepInput input = scheduler.acceptStepInput(safeDt,
            settings.getStepSchedulingMode(),
            maxSubmittedDtSeconds(settings));
        float submittedDt = input.submittedDtSeconds();
        if (submittedDt <= 0.0f) {
            return;
        }
        PhysicsStepMode stepMode = settings.getStepMode();
        float maxStepDt = settings.getMaxStepDt() > 0.0f
            ? settings.getMaxStepDt()
            : PhysicsWorldSettings.DEFAULT_MAX_STEP_DT;
        int steps = stepMode == PhysicsStepMode.ADAPTIVE
            ? resolveAdaptiveStepCount(runtime, submittedDt, settings.getSimulationSteps(), maxStepDt)
            : PhysicsStepCountPolicy.resolveStepCount(submittedDt,
                settings.getSimulationSteps(),
                maxStepDt,
                stepMode);
        float stepDt = submittedDt / steps;
        boolean ccdMode = stepMode == PhysicsStepMode.CCD;
        if (ccdMode || settingsResource.isCcdStepModeActive()) {
            syncContinuousCollisionMode(store, runtime, ccdMode);
        }
        settingsResource.setCcdStepModeActive(ccdMode);
        PhysicsProfilingResource profiling = store.getResource(PhysicsProfilingResource.getResourceType());
        boolean profilingEnabled = profiling.isEnabled();
        if (profilingEnabled) {
            resetStepPhaseStats(runtime);
        }
        List<RuntimeStepBinding> bindings = runtimeStepBindings(runtime);
        boolean collectBackendEvents = settings.getEventCollectionMode().collectsBackendEvents();
        boolean submitted = scheduler.submitStep(input,
            () -> runOwnerStep(runtime,
                compatibility,
                bindings,
                steps,
                stepDt,
                profilingEnabled,
                collectBackendEvents),
            System.nanoTime());
        if (!submitted) {
            throw new IllegalStateException("PhysicsStore owner-lane scheduler refused a submitted step");
        }
    }

    @Nonnull
    private static CompletedStep runOwnerStep(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull List<RuntimeStepBinding> bindings,
        int steps,
        float stepDt,
        boolean profilingEnabled,
        boolean collectBackendEvents) {
        long stepStartNanos = profilingEnabled ? System.nanoTime() : 0L;
        StepCounters counters = new StepCounters();
        for (RuntimeStepBinding binding : bindings) {
            counters.spaceCount++;
            for (int step = 0; step < steps; step++) {
                binding.backendRuntime().step(binding.spaceHandle().value(), stepDt);
                counters.substeps++;
            }
        }
        long stepNanos = profilingEnabled ? System.nanoTime() - stepStartNanos : 0L;
        PhysicsStepPhaseStats nativePhaseStats = profilingEnabled
            ? collectStepPhaseStats(bindings)
            : PhysicsStepPhaseStats.unavailable();
        long snapshotStartNanos = profilingEnabled ? System.nanoTime() : 0L;
        List<PhysicsBodySnapshot> bodySnapshots = collectOwnerLaneSnapshots(runtime,
            bindings);
        long snapshotNanos = profilingEnabled ? System.nanoTime() - snapshotStartNanos : 0L;
        StepBackendEvents backendEvents = collectOwnerLaneBackendEvents(runtime,
            compatibility,
            bindings,
            collectBackendEvents);
        return new CompletedStep(counters.spaceCount,
            counters.substeps,
            stepNanos,
            snapshotNanos,
            nativePhaseStats,
            bodySnapshots,
            backendEvents.physicsEvents(),
            backendEvents.droppedBackendEventCount());
    }

    @Nonnull
    private static List<RuntimeStepBinding> runtimeStepBindings(
        @Nonnull PhysicsRuntimeResource runtime) {
        List<RuntimeStepBinding> bindings = new ArrayList<>();
        runtime.forEachRuntimeSpaceBinding((spaceRef, backendId, spaceHandle, backendRuntime) ->
            bindings.add(new RuntimeStepBinding(spaceRef, backendId, spaceHandle, backendRuntime)));
        return bindings;
    }

    @Nonnull
    private static List<PhysicsBodySnapshot> collectOwnerLaneSnapshots(
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull List<RuntimeStepBinding> bindings) {
        List<PhysicsBodySnapshot> snapshots = new ArrayList<>(runtimeBodyHandleCount(runtime,
            bindings));
        for (RuntimeStepBinding binding : bindings) {
            binding.backendRuntime().snapshotBodies(binding.spaceHandle().value(),
                bodyIds -> runtime.forEachBodyHandle(binding.backendId(),
                    binding.spaceHandle(),
                    bodyIds),
                (bodyId,
                    _,
                    bodyTypeCode,
                    positionX,
                    positionY,
                    positionZ,
                    rotationX,
                    rotationY,
                    rotationZ,
                    rotationW,
                    linearVelocityX,
                    linearVelocityY,
                    linearVelocityZ,
                    angularVelocityX,
                    angularVelocityY,
                    angularVelocityZ,
                    sleeping,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    centerOfMassOffsetY,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _,
                    _) -> collectOwnerLaneSnapshot(runtime,
                        snapshots,
                        binding.backendId(),
                        binding.spaceHandle(),
                        bodyId,
                        bodyTypeCode,
                        positionX,
                        positionY,
                        positionZ,
                        rotationX,
                        rotationY,
                        rotationZ,
                        rotationW,
                        linearVelocityX,
                        linearVelocityY,
                        linearVelocityZ,
                        angularVelocityX,
                        angularVelocityY,
                        angularVelocityZ,
                        centerOfMassOffsetY,
                        sleeping));
        }
        return snapshots;
    }

    private static int runtimeBodyHandleCount(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull List<RuntimeStepBinding> bindings) {
        int bodyCount = 0;
        for (RuntimeStepBinding binding : bindings) {
            bodyCount += runtime.bodyHandleCount(binding.backendId(), binding.spaceHandle());
        }
        return bodyCount;
    }

    private static void collectOwnerLaneSnapshot(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull List<PhysicsBodySnapshot> snapshots,
        @Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        long bodyId,
        int bodyTypeCode,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        float linearVelocityX,
        float linearVelocityY,
        float linearVelocityZ,
        float angularVelocityX,
        float angularVelocityY,
        float angularVelocityZ,
        float centerOfMassOffsetY,
        boolean sleeping) {
        BodySnapshotMetadata metadata = runtime.getBodySnapshotMetadata(backendId,
            spaceHandle,
            bodyId);
        if (metadata == null) {
            return;
        }
        snapshots.add(PhysicsBodySnapshot.of(metadata.bodyRef(),
            metadata.bodyUuid(),
            metadata.spaceUuid(),
            BackendRuntimeCodes.bodyType(bodyTypeCode),
            positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW,
            linearVelocityX,
            linearVelocityY,
            linearVelocityZ,
            angularVelocityX,
            angularVelocityY,
            angularVelocityZ,
            centerOfMassOffsetY,
            sleeping));
    }

    @Nonnull
    private static StepBackendEvents collectOwnerLaneBackendEvents(
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull List<RuntimeStepBinding> bindings,
        boolean collectBackendEvents) {
        if (!collectBackendEvents) {
            return StepBackendEvents.EMPTY;
        }
        StepBackendEvents backendEvents = new StepBackendEvents();
        for (RuntimeStepBinding binding : bindings) {
            UUID spaceUuid = runtime.getSpaceUuid(binding.spaceRef());
            if (spaceUuid == null) {
                backendEvents.addDropped(
                    binding.backendRuntime().contactCount(binding.spaceHandle().value()));
                continue;
            }
            SpaceId spaceId = compatibility.getSpaceId(spaceUuid);
            if (spaceId == null) {
                backendEvents.addDropped(
                    binding.backendRuntime().contactCount(binding.spaceHandle().value()));
                continue;
            }
            binding.backendRuntime().contacts(binding.spaceHandle().value(), (bodyAId,
                bodyBId,
                pointAX,
                pointAY,
                pointAZ,
                pointBX,
                pointBY,
                pointBZ,
                normalBX,
                normalBY,
                normalBZ,
                distance,
                impulse) -> collectOwnerLaneContactEvent(runtime,
                    backendEvents,
                    binding.backendId(),
                    binding.spaceHandle(),
                    spaceId,
                    bodyAId,
                    bodyBId,
                    pointAX,
                    pointAY,
                    pointAZ,
                    pointBX,
                    pointBY,
                    pointBZ,
                    normalBX,
                    normalBY,
                    normalBZ,
                    distance,
                    impulse));
        }
        return backendEvents;
    }

    private static void collectOwnerLaneContactEvent(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull StepBackendEvents backendEvents,
        @Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull SpaceId spaceId,
        long bodyAId,
        long bodyBId,
        float pointAX,
        float pointAY,
        float pointAZ,
        float pointBX,
        float pointBY,
        float pointBZ,
        float normalBX,
        float normalBY,
        float normalBZ,
        float distance,
        float impulse) {
        BodyHitMetadata bodyA = runtime.getBodyHitMetadata(backendId, spaceHandle, bodyAId);
        BodyHitMetadata bodyB = runtime.getBodyHitMetadata(backendId, spaceHandle, bodyBId);
        if (bodyA == null
            || bodyA.bodyRef() == null
            || bodyB == null
            || bodyB.bodyRef() == null
            || PhysicsStoreSystemSupport.isNil(bodyA.bodyUuid())
            || PhysicsStoreSystemSupport.isNil(bodyB.bodyUuid())) {
            backendEvents.addDropped(1);
            return;
        }
        backendEvents.add(new PhysicsContactEvent(spaceId,
            PhysicsContactPhase.OBSERVED,
            bodyA.bodyUuid(),
            bodyB.bodyUuid(),
            new Vector3f(pointAX, pointAY, pointAZ),
            new Vector3f(pointBX, pointBY, pointBZ),
            new Vector3f(normalBX, normalBY, normalBZ),
            distance,
            impulse));
    }

    private static int resolveAdaptiveStepCount(@Nonnull PhysicsRuntimeResource runtime,
        float dt,
        int simulationSteps,
        float maxStepDt) {
        int minimumSteps = PhysicsStepCountPolicy.resolveMaxStepCount(dt,
            simulationSteps,
            maxStepDt);
        StepRisk risk = new StepRisk(dt, minimumSteps);
        runtime.forEachRuntimeSpaceBinding((_, backendId, spaceHandle, backendRuntime) ->
            backendRuntime.snapshotBodies(spaceHandle.value(),
                bodyIds -> runtime.forEachBodyHandle(backendId, spaceHandle, bodyIds),
                risk));
        return risk.steps();
    }

    private static float maxSubmittedDtSeconds(@Nonnull PhysicsWorldSettings settings) {
        float maxStepDt = settings.getMaxStepDt() > 0.0f
            ? settings.getMaxStepDt()
            : PhysicsWorldSettings.DEFAULT_MAX_STEP_DT;
        int maxSteps = switch (settings.getStepMode()) {
            case FIXED, CCD -> settings.getSimulationSteps();
            case ADAPTIVE, PROGRESSIVE_REFINEMENT -> PhysicsWorldSettings.MAX_SIMULATION_STEPS;
        };
        return maxStepDt * maxSteps;
    }

    private static void syncContinuousCollisionMode(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        boolean forceDynamicBodies) {
        runtime.forEachRuntimeSpaceBinding((_, backendId, spaceHandle, backendRuntime) -> {
            if (!backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                return;
            }
            runtime.forEachBodyHandle(backendId, spaceHandle, bodyId -> {
                BodySnapshotMetadata metadata = runtime.getBodySnapshotMetadata(backendId,
                    spaceHandle,
                    bodyId);
                boolean authoredCcd = metadata != null
                    && authoredContinuousCollision(store, metadata);
                backendRuntime.bodySnapshot(spaceHandle.value(),
                    bodyId,
                    new ContinuousCollisionSync(backendRuntime,
                        spaceHandle,
                        bodyId,
                        forceDynamicBodies || authoredCcd));
            });
        });
    }

    private static boolean authoredContinuousCollision(@Nonnull Store<PhysicsStore> store,
        @Nonnull BodySnapshotMetadata metadata) {
        DynamicsComponent dynamics = PhysicsStoreSystemSupport.component(store,
            metadata.bodyRef(),
            DynamicsComponent.getComponentType());
        return dynamics != null && dynamics.isContinuousCollisionEnabled();
    }

    private static void resetStepPhaseStats(@Nonnull PhysicsRuntimeResource runtime) {
        runtime.forEachRuntimeSpaceBinding((_, _, spaceHandle, backendRuntime) ->
            backendRuntime.resetStepPhaseStats(spaceHandle.value()));
    }

    @Nonnull
    private static PhysicsStepPhaseStats collectStepPhaseStats(
        @Nonnull List<RuntimeStepBinding> bindings) {
        StepPhaseStatsAccumulator stats = new StepPhaseStatsAccumulator();
        StepPhaseStatsCapture capture = new StepPhaseStatsCapture();
        for (RuntimeStepBinding binding : bindings) {
            capture.reset();
            binding.backendRuntime().stepPhaseStats(binding.spaceHandle().value(), capture);
            stats.add(capture.value());
        }
        return stats.value();
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private record ContinuousCollisionSync(@Nonnull PhysicsBackendRuntime backendRuntime,
                                           @Nonnull BackendSpaceHandle spaceHandle, long bodyId,
                                           boolean targetEnabled) implements
        BackendBodySnapshotSink {

        @Override
            public void accept(long bodyId,
                int shapeTypeCode,
                int bodyTypeCode,
                float positionX,
                float positionY,
                float positionZ,
                float rotationX,
                float rotationY,
                float rotationZ,
                float rotationW,
                float linearVelocityX,
                float linearVelocityY,
                float linearVelocityZ,
                float angularVelocityX,
                float angularVelocityY,
                float angularVelocityZ,
                boolean sleeping,
                boolean sensor,
                float mass,
                float friction,
                float restitution,
                float linearDamping,
                float angularDamping,
                int collisionGroup,
                int collisionMask,
                boolean continuousCollisionEnabled,
                float centerOfMassOffsetY,
                boolean hasBoxHalfExtents,
                float halfExtentX,
                float halfExtentY,
                float halfExtentZ,
                float radius,
                float halfHeight,
                int axisCode) {
                if (BackendRuntimeCodes.bodyType(bodyTypeCode) != PhysicsBodyType.DYNAMIC
                    || continuousCollisionEnabled == targetEnabled) {
                    return;
                }
                backendRuntime.setBodyContinuousCollision(spaceHandle.value(),
                    this.bodyId,
                    targetEnabled);
            }
        }

    private static final class StepRisk implements BackendBodySnapshotSink {

        private final float dt;
        private int steps;

        private StepRisk(float dt, int minimumSteps) {
            this.dt = dt;
            steps = minimumSteps;
        }

        @Override
        public void accept(long bodyId,
            int shapeTypeCode,
            int bodyTypeCode,
            float positionX,
            float positionY,
            float positionZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW,
            float linearVelocityX,
            float linearVelocityY,
            float linearVelocityZ,
            float angularVelocityX,
            float angularVelocityY,
            float angularVelocityZ,
            boolean sleeping,
            boolean sensor,
            float mass,
            float friction,
            float restitution,
            float linearDamping,
            float angularDamping,
            int collisionGroup,
            int collisionMask,
            boolean continuousCollisionEnabled,
            float centerOfMassOffsetY,
            boolean hasBoxHalfExtents,
            float halfExtentX,
            float halfExtentY,
            float halfExtentZ,
            float radius,
            float halfHeight,
            int axisCode) {
            if (steps >= PhysicsWorldSettings.MAX_SIMULATION_STEPS
                || sleeping
                || sensor) {
                return;
            }
            PhysicsBodyType bodyType = BackendRuntimeCodes.bodyType(bodyTypeCode);
            if (bodyType != PhysicsBodyType.DYNAMIC && bodyType != PhysicsBodyType.KINEMATIC) {
                return;
            }

            ShapeType shapeType = BackendRuntimeCodes.shapeType(shapeTypeCode);
            float linearTravel = vectorLength(linearVelocityX,
                linearVelocityY,
                linearVelocityZ) * dt;
            float shapeRadius = approximateShapeRadius(shapeType,
                hasBoxHalfExtents,
                halfExtentX,
                halfExtentY,
                halfExtentZ,
                radius,
                halfHeight);
            float angularSurfaceTravel = vectorLength(angularVelocityX,
                angularVelocityY,
                angularVelocityZ) * shapeRadius * dt;
            int requiredSteps = Math.max(
                requiredSteps(linearTravel,
                    safeLinearTravel(shapeType,
                        hasBoxHalfExtents,
                        halfExtentX,
                        halfExtentY,
                        halfExtentZ,
                        radius)),
                requiredSteps(angularSurfaceTravel, safeAngularTravel(shapeRadius)));
            steps = Math.clamp(steps,
                Math.min(requiredSteps, PhysicsWorldSettings.MAX_SIMULATION_STEPS),
                PhysicsWorldSettings.MAX_SIMULATION_STEPS);
        }

        private int steps() {
            return steps;
        }
    }

    private static final class StepPhaseStatsCapture implements BackendStepPhaseStatsSink {

        @Nonnull
        private PhysicsStepPhaseStats value = PhysicsStepPhaseStats.unavailable();

        @Override
        public void accept(long stepNanos,
            long broadPhaseNanos,
            long narrowPhaseNanos,
            long solverNanos,
            long continuousCollisionNanos,
            long snapshotNanos,
            boolean available) {
            value = available
                ? PhysicsStepPhaseStats.available(stepNanos,
                    broadPhaseNanos,
                    narrowPhaseNanos,
                    solverNanos,
                    continuousCollisionNanos,
                    snapshotNanos)
                : PhysicsStepPhaseStats.unavailable();
        }

        private void reset() {
            value = PhysicsStepPhaseStats.unavailable();
        }

        @Nonnull
        private PhysicsStepPhaseStats value() {
            return value;
        }
    }

    private static final class StepPhaseStatsAccumulator {

        @Nonnull
        private PhysicsStepPhaseStats value = PhysicsStepPhaseStats.unavailable();

        private void add(@Nonnull PhysicsStepPhaseStats stats) {
            value = value.add(stats);
        }

        @Nonnull
        private PhysicsStepPhaseStats value() {
            return value;
        }
    }

    private static final class StepCounters {

        private int spaceCount;
        private int substeps;
    }

    private record RuntimeStepBinding(@Nonnull Ref<PhysicsStore> spaceRef,
                                      @Nonnull BackendId backendId,
                                      @Nonnull BackendSpaceHandle spaceHandle,
                                      @Nonnull PhysicsBackendRuntime backendRuntime) {
    }

    private static final class StepBackendEvents {

        @Nonnull
        private static final StepBackendEvents EMPTY = new StepBackendEvents(List.of(), 0);

        @Nonnull
        private final List<PhysicsFrameEvent> physicsEvents;
        private int droppedBackendEventCount;

        private StepBackendEvents() {
            this(new ArrayList<>(), 0);
        }

        private StepBackendEvents(@Nonnull List<PhysicsFrameEvent> physicsEvents,
            int droppedBackendEventCount) {
            this.physicsEvents = physicsEvents;
            this.droppedBackendEventCount = Math.max(0, droppedBackendEventCount);
        }

        private void add(@Nonnull PhysicsFrameEvent event) {
            physicsEvents.add(event);
        }

        private void addDropped(int count) {
            droppedBackendEventCount += Math.max(0, count);
        }

        @Nonnull
        private List<PhysicsFrameEvent> physicsEvents() {
            return physicsEvents;
        }

        private int droppedBackendEventCount() {
            return droppedBackendEventCount;
        }
    }

    private static int requiredSteps(float travel, float safeTravel) {
        if (travel <= safeTravel) {
            return 1;
        }
        return (int) Math.ceil(travel / safeTravel);
    }

    private static float safeLinearTravel(@Nonnull ShapeType shapeType,
        boolean hasBoxHalfExtents,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius) {
        return Math.clamp(
            approximateMinimumExtent(shapeType,
                hasBoxHalfExtents,
                halfExtentX,
                halfExtentY,
                halfExtentZ,
                radius) * SHAPE_TRAVEL_FRACTION,
            MIN_LINEAR_TRAVEL_PER_SUBSTEP,
            DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP);
    }

    private static float safeAngularTravel(float shapeRadius) {
        return Math.clamp(shapeRadius * MAX_ANGULAR_RADIANS_PER_SUBSTEP,
            MIN_LINEAR_TRAVEL_PER_SUBSTEP,
            DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP);
    }

    private static float approximateMinimumExtent(@Nonnull ShapeType shapeType,
        boolean hasBoxHalfExtents,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius) {
        if (shapeType == ShapeType.BOX && hasBoxHalfExtents) {
            return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP,
                Math.min(halfExtentX, Math.min(halfExtentY, halfExtentZ)));
        }
        if (shapeType == ShapeType.SPHERE
            || shapeType == ShapeType.CAPSULE
            || shapeType == ShapeType.CYLINDER
            || shapeType == ShapeType.CONE) {
            return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP, radius);
        }
        return DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP;
    }

    private static float approximateShapeRadius(@Nonnull ShapeType shapeType,
        boolean hasBoxHalfExtents,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight) {
        if (shapeType == ShapeType.BOX && hasBoxHalfExtents) {
            return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP,
                (float) Math.sqrt(halfExtentX * halfExtentX
                    + halfExtentY * halfExtentY
                    + halfExtentZ * halfExtentZ));
        }
        if (shapeType == ShapeType.SPHERE) {
            return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP, radius);
        }
        if (shapeType == ShapeType.CAPSULE
            || shapeType == ShapeType.CYLINDER
            || shapeType == ShapeType.CONE) {
            return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP, radius + halfHeight);
        }
        return DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP;
    }

    private static float vectorLength(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }
}
