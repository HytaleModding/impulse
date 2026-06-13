package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Captures serializable PhysicsStore rows into compact DTO resources.
 */
public final class PersistenceCaptureSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, CompletedStepPublicationSystem.class),
        new SystemDependency<>(Order.BEFORE, StepSubmissionSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        /*
         * DTO capture is intentionally after completed snapshot publication and before next-step
         * submission. The capture body is filled once Worker D finishes projection/authoring
         * migration so the DTO wire format is not populated from legacy EntityStore state.
         */
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
