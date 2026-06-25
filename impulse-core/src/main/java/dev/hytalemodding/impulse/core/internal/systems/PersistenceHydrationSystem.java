package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.persistence.PhysicsStoreHolderStorage;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Rehydrates persisted PhysicsStore holder rows before backend mutation is allowed.
 */
public final class PersistenceHydrationSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed() || restore.isHydrated()) {
            return;
        }
        try {
            prepareTransientRestoreState(store);
            PhysicsStoreHolderStorage.load(store);
            restore.markComplete();
            restore.markHydrated();
        } catch (RuntimeException exception) {
            restore.markFailed(exception.getMessage());
        }
    }

    private static void prepareTransientRestoreState(@Nonnull Store<PhysicsStore> store) {
        store.getResource(PhysicsRuntimeResource.getResourceType()).destroyBackendBindings();
        store.getExternalData().clearUuidIndex();
        store.getResource(PhysicsSnapshotResource.getResourceType()).clear();
        store.getResource(PhysicsEventResource.getResourceType()).clear();
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
