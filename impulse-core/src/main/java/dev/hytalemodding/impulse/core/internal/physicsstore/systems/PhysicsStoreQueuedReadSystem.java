package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStoreReadQueueResource;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Resolves queued live backend reads on the PhysicsStore owner lane.
 */
public final class PhysicsStoreQueuedReadSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, CompletedStepPublicationSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsStoreReadQueueResource queue = store.getResource(
            PhysicsStoreReadQueueResource.getResourceType());
        List<PhysicsStoreReadQueueResource.QueuedRead<?>> reads = queue.drain();
        if (reads.isEmpty()) {
            return;
        }
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            IllegalStateException failure = new IllegalStateException(
                "PhysicsStore restore failed: " + restore.getFailureMessage());
            for (PhysicsStoreReadQueueResource.QueuedRead<?> read : reads) {
                read.fail(failure);
            }
            return;
        }
        for (PhysicsStoreReadQueueResource.QueuedRead<?> read : reads) {
            read.complete(store);
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
