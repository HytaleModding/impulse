package dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStoreTypes;
import dev.hytalemodding.impulse.core.internal.systems.SpaceSettingsApplicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.BodyBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.SpaceBindingSystem;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Requests PhysicsChunk collision support for unbound restored bodies before body binding.
 */
public final class ChunkCollisionRestorePrewarmSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, SpaceBindingSystem.class),
        new SystemDependency<>(Order.AFTER, SpaceSettingsApplicationSystem.class),
        new SystemDependency<>(Order.AFTER, PhysicsChunkSettingsIndexSystem.class),
        new SystemDependency<>(Order.BEFORE, ChunkCollisionMutationDrainSystem.class),
        new SystemDependency<>(Order.BEFORE, BodyBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsChunkStoreTypes.prewarmRestoreDependencies(store);
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
