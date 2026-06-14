package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshotSink;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.BackendStepPhaseStatsSink;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.BodySnapshotMetadata;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.systems.step.PhysicsStepCountPolicy;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Submits the next backend step from PhysicsStore.tick().
 */
public final class StepSubmissionSystem extends TickingSystem<PhysicsStore> {

    private static final float DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP = 0.45f;
    private static final float MIN_LINEAR_TRAVEL_PER_SUBSTEP = 0.125f;
    private static final float SHAPE_TRAVEL_FRACTION = 0.75f;
    private static final float MAX_ANGULAR_RADIANS_PER_SUBSTEP = (float) Math.toRadians(30.0);

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistenceCaptureSystem.class)
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
        PhysicsStepMode stepMode = settings.getStepMode();
        float maxStepDt = settings.getMaxStepDt() > 0.0f
            ? settings.getMaxStepDt()
            : PhysicsWorldSettings.DEFAULT_MAX_STEP_DT;
        int steps = stepMode == PhysicsStepMode.ADAPTIVE
            ? resolveAdaptiveStepCount(runtime, safeDt, settings.getSimulationSteps(), maxStepDt)
            : PhysicsStepCountPolicy.resolveStepCount(safeDt,
                settings.getSimulationSteps(),
                maxStepDt,
                stepMode);
        float stepDt = safeDt / steps;
        boolean ccdMode = stepMode == PhysicsStepMode.CCD;
        if (ccdMode || settingsResource.isCcdStepModeActive()) {
            syncContinuousCollisionMode(store,
                runtime,
                store.getResource(PhysicsIdentityIndexResource.getResourceType()),
                ccdMode);
        }
        settingsResource.setCcdStepModeActive(ccdMode);
        PhysicsProfilingResource profiling = store.getResource(PhysicsProfilingResource.getResourceType());
        boolean profilingEnabled = profiling.isEnabled();
        if (profilingEnabled) {
            resetStepPhaseStats(runtime);
        }
        long stepStartNanos = profilingEnabled ? System.nanoTime() : 0L;
        StepCounters counters = new StepCounters();
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) -> {
            counters.spaceCount++;
            for (int step = 0; step < steps; step++) {
                backendRuntime.step(spaceHandle.value(), stepDt);
                counters.substeps++;
            }
        });
        long stepNanos = profilingEnabled ? System.nanoTime() - stepStartNanos : 0L;
        PhysicsStepPhaseStats nativePhaseStats = profilingEnabled
            ? collectStepPhaseStats(runtime)
            : PhysicsStepPhaseStats.unavailable();
        profiling.recordStep(stepNanos,
            counters.spaceCount,
            counters.substeps,
            nativePhaseStats);
    }

    private static int resolveAdaptiveStepCount(@Nonnull PhysicsRuntimeResource runtime,
        float dt,
        int simulationSteps,
        float maxStepDt) {
        int minimumSteps = PhysicsStepCountPolicy.resolveMaxStepCount(dt,
            simulationSteps,
            maxStepDt);
        StepRisk risk = new StepRisk(dt, minimumSteps);
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) ->
            backendRuntime.snapshotBodies(spaceHandle.value(),
                bodyIds -> runtime.forEachBodyHandle(spaceHandle, bodyIds::accept),
                risk));
        return risk.steps();
    }

    private static void syncContinuousCollisionMode(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        boolean forceDynamicBodies) {
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) -> {
            if (!backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                return;
            }
            runtime.forEachBodyHandle(spaceHandle, bodyId -> {
                BodySnapshotMetadata metadata = runtime.getBodySnapshotMetadata(bodyId);
                boolean authoredCcd = metadata != null
                    && authoredContinuousCollision(store, identity, metadata);
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
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull BodySnapshotMetadata metadata) {
        Ref<PhysicsStore> ref = PhysicsStoreSystemSupport.refForUuid(identity, metadata.bodyUuid());
        DynamicsComponent dynamics = PhysicsStoreSystemSupport.component(store,
            ref,
            DynamicsComponent.getComponentType());
        return dynamics != null && dynamics.isContinuousCollisionEnabled();
    }

    private static void resetStepPhaseStats(@Nonnull PhysicsRuntimeResource runtime) {
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) ->
            backendRuntime.resetStepPhaseStats(spaceHandle.value()));
    }

    @Nonnull
    private static PhysicsStepPhaseStats collectStepPhaseStats(@Nonnull PhysicsRuntimeResource runtime) {
        PhysicsStepPhaseStats[] stats = {PhysicsStepPhaseStats.unavailable()};
        StepPhaseStatsCapture capture = new StepPhaseStatsCapture();
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) -> {
            capture.reset();
            backendRuntime.stepPhaseStats(spaceHandle.value(), capture);
            stats[0] = stats[0].add(capture.value());
        });
        return stats[0];
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private static final class ContinuousCollisionSync implements BackendBodySnapshotSink {

        @Nonnull
        private final PhysicsBackendRuntime backendRuntime;
        @Nonnull
        private final BackendSpaceHandle spaceHandle;
        private final long bodyId;
        private final boolean targetEnabled;

        private ContinuousCollisionSync(@Nonnull PhysicsBackendRuntime backendRuntime,
            @Nonnull BackendSpaceHandle spaceHandle,
            long bodyId,
            boolean targetEnabled) {
            this.backendRuntime = backendRuntime;
            this.spaceHandle = spaceHandle;
            this.bodyId = bodyId;
            this.targetEnabled = targetEnabled;
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

    private static final class StepCounters {

        private int spaceCount;
        private int substeps;
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
