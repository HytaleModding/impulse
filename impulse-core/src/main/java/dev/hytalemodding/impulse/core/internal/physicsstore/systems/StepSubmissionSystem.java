package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Submits the next backend step from PhysicsStore.tick().
 */
public final class StepSubmissionSystem extends TickingSystem<PhysicsStore> {

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
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) ->
            backendRuntime.step(spaceHandle.value(), safeDt));
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
