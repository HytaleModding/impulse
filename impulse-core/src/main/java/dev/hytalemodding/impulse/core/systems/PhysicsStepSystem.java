package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource;
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
        PhysicsRuntimeProfilingResource profiling = entityStore.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());

        PhysicsStepMode stepMode = resource.getStepMode();
        int steps = PhysicsStepCountPolicy.resolveStepCount(dt,
            resource.getSimulationSteps(),
            resource.getMaxStepDt(),
            stepMode);
        if (stepMode != PhysicsStepMode.CCD) {
            restoreForcedContinuousCollision(resource);
        }

        long startNanos = profiling.isEnabled() ? System.nanoTime() : 0L;
        float stepDt = dt / steps;
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
        if (profiling.isEnabled()) {
            profiling.recordStep(spaceCount, substeps, System.nanoTime() - startNanos);
        }
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
