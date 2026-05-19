package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Steps the physics world each tick.
 */
public class PhysicsStepSystem extends TickingSystem<ChunkStore> {

    private static final float DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP = 0.45f;
    private static final float MIN_LINEAR_TRAVEL_PER_SUBSTEP = 0.125f;
    private static final float SHAPE_TRAVEL_FRACTION = 0.75f;
    private static final float MAX_ANGULAR_RADIANS_PER_SUBSTEP = (float) Math.toRadians(30.0);

    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store) {
        var world = store.getExternalData().getWorld();
        var entityStore = world.getEntityStore().getStore();
        PhysicsWorldResource resource = entityStore.getResource(
            PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource profiling = entityStore.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());

        float safeDt = Math.max(dt, 0.0f);
        PhysicsStepMode stepMode = resource.getStepMode();
        float maxStepDt = resource.getMaxStepDt() > 0f
            ? resource.getMaxStepDt()
            : PhysicsWorldResource.DEFAULT_MAX_STEP_DT;
        int steps = stepMode == PhysicsStepMode.ADAPTIVE
            ? resolveAdaptiveStepCount(safeDt, maxStepDt, resource)
            : PhysicsStepCountPolicy.resolveStepCount(safeDt,
            resource.getSimulationSteps(),
            maxStepDt,
            stepMode);
        if (stepMode != PhysicsStepMode.CCD) {
            restoreForcedContinuousCollision(resource);
        }

        long startNanos = profiling.isEnabled() ? System.nanoTime() : 0L;
        float stepDt = safeDt / steps;
        int spaceCount = 0;
        int substeps = 0;
        for (PhysicsSpace space : resource.iterateSpaces()) {
            spaceCount++;
            if (stepMode == PhysicsStepMode.CCD && space.supportsContinuousCollision()) {
                forceContinuousCollision(resource, space);
            }
            for (int step = 0; step < steps; step++) {
                space.step(stepDt);
                substeps++;
            }
        }
        long stepNanos = profiling.isEnabled() ? System.nanoTime() - startNanos : 0L;
        long snapshotStartNanos = profiling.isEnabled() ? System.nanoTime() : 0L;
        int bodySnapshots = resource.refreshBodySnapshots();
        long snapshotNanos = profiling.isEnabled() ? System.nanoTime() - snapshotStartNanos : 0L;
        if (profiling.isEnabled()) {
            profiling.recordStep(spaceCount,
                substeps,
                stepNanos,
                bodySnapshots,
                resource.getBodySnapshotCellCount(),
                snapshotNanos);
        }
    }

    private static int resolveAdaptiveStepCount(float dt,
        float maxStepDt,
        @Nonnull PhysicsWorldResource resource) {
        float sampledDt = Math.max(dt, 0.0f);
        if (sampledDt <= 0.0f) {
            return resource.getSimulationSteps();
        }

        int minimumSteps = PhysicsStepCountPolicy.resolveMaxStepCount(sampledDt,
            resource.getSimulationSteps(),
            maxStepDt);
        StepRisk risk = new StepRisk(sampledDt, minimumSteps);
        for (PhysicsSpace space : resource.iterateSpaces()) {
            space.forEachBody(risk::inspect);
        }
        return risk.steps();
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
            if (steps >= PhysicsWorldResource.MAX_SIMULATION_STEPS
                || body.isStatic()
                || body.isSleeping()
                || body.isSensor()
                || (!body.isDynamic() && !body.isKinematic())) {
                return;
            }

            body.getLinearVelocity(linearVelocity);
            body.getAngularVelocity(angularVelocity);
            float linearTravel = linearVelocity.length() * dt;
            float angularSurfaceTravel = angularVelocity.length() * approximateShapeRadius(body) * dt;
            float safeLinearTravel = safeLinearTravel(body);
            int requiredSteps = Math.max(
                requiredSteps(linearTravel, safeLinearTravel),
                requiredSteps(angularSurfaceTravel, safeAngularTravel(body)));
            steps = Math.min(PhysicsWorldResource.MAX_SIMULATION_STEPS,
                Math.max(steps, requiredSteps));
        }

        private int steps() {
            return steps;
        }
    }

    private static int requiredSteps(float travel, float safeTravel) {
        if (travel <= safeTravel) {
            return 1;
        }
        return (int) Math.ceil(travel / safeTravel);
    }

    private static float safeLinearTravel(@Nonnull PhysicsBody body) {
        return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP,
            Math.min(DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP,
                approximateMinimumExtent(body) * SHAPE_TRAVEL_FRACTION));
    }

    private static float safeAngularTravel(@Nonnull PhysicsBody body) {
        return Math.max(MIN_LINEAR_TRAVEL_PER_SUBSTEP,
            Math.min(DEFAULT_LINEAR_TRAVEL_PER_SUBSTEP,
                approximateShapeRadius(body) * MAX_ANGULAR_RADIANS_PER_SUBSTEP));
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

    private static void forceContinuousCollision(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space) {
        space.forEachBody(body -> {
            if (!body.isDynamic() || body.isContinuousCollisionEnabled()) {
                return;
            }

            body.setContinuousCollisionEnabled(true);
            resource.markContinuousCollisionForced(body);
        });
    }

    private static void restoreForcedContinuousCollision(@Nonnull PhysicsWorldResource resource) {
        Collection<PhysicsBody> forcedBodies = resource.getForcedContinuousCollisionBodies();
        if (forcedBodies.isEmpty()) {
            return;
        }

        for (PhysicsBody body : forcedBodies) {
            body.setContinuousCollisionEnabled(false);
        }
        resource.clearForcedContinuousCollisionBodies();
    }
}
