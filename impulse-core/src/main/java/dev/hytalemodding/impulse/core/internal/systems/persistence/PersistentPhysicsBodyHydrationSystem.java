package dev.hytalemodding.impulse.core.internal.systems.persistence;

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
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Restores world-level body states keyed by {@link RigidBodyKey}.
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

        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(store);
        for (PersistentPhysicsBodyState state : persistent.getBodies()) {
            RigidBodyKey bodyKey = state.getBodyKey();
            if (bodyKey == null) {
                persistent.recordRuntimeBodySkipped("missing body key");
                continue;
            }
            if (runtime.getBodyRegistrationView(bodyKey) != null) {
                continue;
            }

            String validationFailure = state.restoreValidationFailureReason();
            if (validationFailure != null) {
                persistent.recordRuntimeBodySkipped(validationFailure);
                continue;
            }

            int resolvedSpaceId = state.resolveSpaceId();
            if (resolvedSpaceId <= 0) {
                persistent.recordRuntimeBodySkipped("no resolved space id");
                continue;
            }

            try {
                boolean restored = PhysicsWorkerAccess.call(store, "hydrate persisted physics body", () -> {
                    PhysicsSpace space = runtime.getSpace(new SpaceId(resolvedSpaceId));
                    if (space == null) {
                        return false;
                    }
                    PhysicsBody body = state.createBody(space);
                    state.applyToBody(body);
                    runtime.addBody(bodyKey,
                        space.getId(),
                        body,
                        PhysicsBodyKind.BODY,
                        PhysicsBodyPersistenceMode.PERSISTENT);
                    return true;
                });
                if (restored) {
                    persistent.recordRuntimeBodyRestored();
                } else {
                    persistent.recordRuntimeBodySkipped("missing target space");
                }
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

    @Nonnull
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return group;
    }
}
