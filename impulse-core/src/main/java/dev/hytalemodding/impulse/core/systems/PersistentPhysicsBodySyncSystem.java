package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Syncs persisted body components from the live runtime body state.
 *
 * <p>For every entity that has both a {@link PersistentPhysicsBodyComponent} and a
 * {@link PhysicsBodyComponent}, this system copies the current position, rotation,
 * velocities, and other dynamic state from the live body back into the persisted
 * component. Dynamic bodies are updated every tick; sleeping and static bodies
 * use a bounded cadence because their runtime state is stable in steady state.</p>
 *
 * <p>Runs after {@link PhysicsSyncSystem} (which writes live body transforms into
 * Hytale's {@code TransformComponent}) and after hydration (so newly hydrated
 * bodies are not immediately overwritten with stale state).</p>
 */
public class PersistentPhysicsBodySyncSystem extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, PersistentPhysicsBodyComponent>
        PERSISTENT_BODY_TYPE = PersistentPhysicsBodyComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyComponent> PHYSICS_BODY_TYPE =
        PhysicsBodyComponent.getComponentType();
    private static final Query<EntityStore> QUERY = Query.and(PERSISTENT_BODY_TYPE, PHYSICS_BODY_TYPE);
    private static final int SLEEPING_BODY_SYNC_INTERVAL_TICKS = 30;
    private static final int STATIC_BODY_SYNC_INTERVAL_TICKS = 120;
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PhysicsSyncSystem.class),
        new SystemDependency<>(Order.AFTER, PersistentPhysicsBodyHydrationSystem.class)
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PersistentPhysicsBodyComponent persistent = chunk.getComponent(index, PERSISTENT_BODY_TYPE);
        PhysicsBodyComponent runtime = chunk.getComponent(index, PHYSICS_BODY_TYPE);
        if (persistent == null || runtime == null) {
            return;
        }

        if (persistent.shouldDeferSleepingUpdate(runtime.getBody(),
            runtime.getSpaceId(),
            SLEEPING_BODY_SYNC_INTERVAL_TICKS)
            || persistent.shouldDeferStaticUpdate(runtime.getBody(),
                runtime.getSpaceId(),
                STATIC_BODY_SYNC_INTERVAL_TICKS)) {
            persistent.clearBodyRebuildFlag();
            return;
        }

        persistent.updateFromBody(runtime.getBody(), runtime.getSpaceId());
        persistent.clearBodyRebuildFlag();
    }
}
