package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsRuntimeSupport;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Second stage of persistence restore: rebuilds live bodies from persisted entity state.
 *
 * <p>Queries for entities that have a {@link PersistentPhysicsBodyComponent} but no
 * live {@link PhysicsBodyComponent} yet (i.e. the {@code needsBodyRebuild} flag is set).
 * For each matching entity, resolves the target space, creates the backend body from
 * the persisted shape description, applies the full dynamic state, and writes the
 * new {@link PhysicsBodyComponent} back onto the entity.</p>
 *
 * <p>Runs after {@link PersistentPhysicsSpaceBootstrapSystem} so that target spaces
 * already exist, and before {@link PhysicsSyncSystem} so that hydrated bodies are
 * available for transform sync.</p>
 *
 * <p>Bodies whose target space never becomes valid are terminally unresolved.
 * Those cases are recorded as skipped restore entries so the overall restore can
 * finish instead of leaving body rebuild pending forever.</p>
 */
public class PersistentPhysicsBodyHydrationSystem extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, PersistentPhysicsBodyComponent>
        PERSISTENT_BODY_TYPE = PersistentPhysicsBodyComponent.getComponentType();
    private static final Query<EntityStore> QUERY = Query.and(
        PERSISTENT_BODY_TYPE,
        Query.not(PhysicsBodyComponent.getComponentType()));
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistentPhysicsSpaceBootstrapSystem.class)
    );
    @Nonnull
    private final SystemGroup<EntityStore> group = ImpulsePlugin.get().getPersistenceRestoreGroup();

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
    public SystemGroup<EntityStore> getGroup() {
        return group;
    }

    @Override
    public void tick(float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PersistentPhysicsBodyComponent persistent = chunk.getComponent(index, PERSISTENT_BODY_TYPE);
        if (persistent == null || !persistent.needsBodyRebuild()) {
            return;
        }

        PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());
        PersistentPhysicsWorldResource persistentWorld = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        if (persistentWorld.hasRuntimeRestoreFailed()) {
            return;
        }

        int resolvedSpaceId = persistent.resolveSpaceId(persistentWorld.getDefaultSpaceIdValue());
        if (resolvedSpaceId <= 0) {
            if (persistentWorld.isRuntimeRestorePending()) {
                persistentWorld.recordRuntimeBodySkipped("no resolved space id");
            }
            persistent.clearBodyRebuildFlag();
            return;
        }

        var space = PersistentPhysicsRuntimeSupport.resolveSpace(runtime, persistentWorld, persistent);
        if (space == null) {
            if (persistentWorld.isRuntimeRestorePending()) {
                persistentWorld.recordRuntimeBodySkipped("missing target space");
            }
            persistent.clearBodyRebuildFlag();
            return;
        }

        PhysicsBody body = persistent.createBody(space);
        persistent.applyToBody(body);
        space.addBody(body);

        commandBuffer.putComponent(chunk.getReferenceTo(index),
            PhysicsBodyComponent.getComponentType(),
            new PhysicsBodyComponent(body, space.getId()));
        if (body.getBodyType() == PhysicsBodyType.DYNAMIC) {
            commandBuffer.putComponent(chunk.getReferenceTo(index),
                ImpulseControllableComponent.getComponentType(),
                new ImpulseControllableComponent());
        }
        runtime.registerBodyOwner(body, chunk.getReferenceTo(index));
        if (persistentWorld.isRuntimeRestorePending()) {
            persistentWorld.recordRuntimeBodyRestored();
        }
    }
}
