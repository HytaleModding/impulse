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
 * Reserved for voxel terrain payload binding owned by ChunkStore request producers.
 */
public final class TerrainColliderBindingSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, JointBindingSystem.class),
        new SystemDependency<>(Order.BEFORE, TargetBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        /*
         * Terrain rows currently carry source metadata but not the compact voxel payload. Worker D
         * owns the request producer and payload handoff, so backend terrain creation stays here.
         */
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
