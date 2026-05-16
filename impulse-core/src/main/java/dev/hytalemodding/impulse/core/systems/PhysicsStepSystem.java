package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Collection;
import javax.annotation.Nonnull;

/**
 * Steps the physics world each tick.
 */
public class PhysicsStepSystem extends TickingSystem<ChunkStore> {

    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store) {
        var world = store.getExternalData().getWorld();
        var entityStore = world.getEntityStore().getStore();
        PhysicsWorldResource resource = entityStore.getResource(
            PhysicsWorldResource.getResourceType());

        PhysicsStepMode stepMode = resource.getStepMode();
        int steps = resolveStepCount(dt, resource, stepMode);
        if (stepMode != PhysicsStepMode.CCD) {
            restoreForcedContinuousCollision(resource);
        }

        float stepDt = dt / steps;
        for (PhysicsSpace space : resource.iterateSpaces()) {
            if (stepMode == PhysicsStepMode.CCD && space.supportsContinuousCollision()) {
                forceContinuousCollision(resource, space);
            }
            for (int step = 0; step < steps; step++) {
                space.step(stepDt);
            }
        }
    }

    private static int resolveStepCount(float dt,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsStepMode stepMode) {
        int simulationSteps = resource.getSimulationSteps();
        return switch (stepMode) {
            case FIXED, CCD -> simulationSteps;
            case PROGRESSIVE_REFINEMENT -> {
                float maxStepDt = resource.getMaxStepDt();
                if (maxStepDt <= 0f) {
                    maxStepDt = PhysicsWorldResource.DEFAULT_MAX_STEP_DT;
                }
                int steps = Math.max(simulationSteps, (int) Math.ceil(dt / maxStepDt));
                yield Math.min(steps, PhysicsWorldResource.MAX_SIMULATION_STEPS);
            }
        };
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
