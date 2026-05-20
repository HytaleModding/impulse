package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Restores schema-v3 world-level body states keyed by {@link PhysicsBodyId}.
 */
public class PersistentPhysicsBodyHydrationSystem extends TickingSystem<EntityStore> {

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistentPhysicsSpaceBootstrapSystem.class)
    );
    @Nonnull
    private final SystemGroup<EntityStore> group = ImpulsePlugin.get().getPersistenceRestoreGroup();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        if (!persistent.isRuntimeRestorePending()
            || !persistent.isRuntimeSpaceBootstrapComplete()
            || persistent.hasRuntimeRestoreFailed()) {
            return;
        }

        PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());
        for (PersistentPhysicsBodyState state : persistent.getBodies()) {
            PhysicsBodyId bodyId = state.getBodyId();
            if (bodyId == null) {
                persistent.recordRuntimeBodySkipped("missing body id");
                continue;
            }
            if (runtime.getRegistration(bodyId) != null) {
                continue;
            }

            String validationFailure = state.restoreValidationFailureReason();
            if (validationFailure != null) {
                persistent.recordRuntimeBodySkipped(validationFailure);
                continue;
            }

            int resolvedSpaceId = state.resolveSpaceId(persistent.getDefaultSpaceIdValue());
            if (resolvedSpaceId <= 0) {
                persistent.recordRuntimeBodySkipped("no resolved space id");
                continue;
            }
            PhysicsSpace space = runtime.getSpace(new SpaceId(resolvedSpaceId));
            if (space == null) {
                persistent.recordRuntimeBodySkipped("missing target space");
                continue;
            }

            try {
                PhysicsBody body = state.createBody(space);
                state.applyToBody(body);
                runtime.addBody(bodyId,
                    space.getId(),
                    body,
                    PhysicsBodyKind.BODY,
                    PhysicsBodyPersistenceMode.PERSISTENT);
                persistent.recordRuntimeBodyRestored();
            } catch (RuntimeException exception) {
                persistent.recordRuntimeBodySkipped("body restore failed: "
                    + exception.getClass().getSimpleName());
            }
        }
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
}
