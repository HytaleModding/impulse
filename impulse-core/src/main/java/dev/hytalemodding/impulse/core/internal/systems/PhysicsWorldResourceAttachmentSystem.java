package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.debug.PhysicsDebugSystem;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsStoreEventPublicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsGeneratedProxyCleanupSystem;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Attaches the concrete physics runtime resource to its owning EntityStore.
 */
public final class PhysicsWorldResourceAttachmentSystem extends TickingSystem<EntityStore> {

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsStoreEventPublicationSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsGeneratedProxyCleanupSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsDebugSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsWorldRuntimeResource.require(store);
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
