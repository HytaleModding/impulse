package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStorePreflight;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Preflights persisted PhysicsStore DTOs before backend mutation is allowed.
 */
public final class PersistenceHydrationSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, RequestDrainSystem.class),
        new SystemDependency<>(Order.BEFORE, IdentityIndexSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PersistentPhysicsStoreResource persistent = store.getResource(
            PersistentPhysicsStoreResource.getResourceType());
        PersistentPhysicsStorePreflight.Result result = persistent.preflight();
        if (!result.valid()) {
            restore.markFailed(String.join("; ", result.errors()));
            return;
        }
        restore.markComplete();
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
