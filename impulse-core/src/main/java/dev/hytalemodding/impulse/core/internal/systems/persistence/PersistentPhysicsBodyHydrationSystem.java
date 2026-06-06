package dev.hytalemodding.impulse.core.internal.systems.persistence;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerBridge;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
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

            String validationFailure = state.restoreValidationFailureReason();
            if (validationFailure != null) {
                persistent.recordRuntimeBodySkipped(bodyKey, validationFailure);
                continue;
            }

            int resolvedSpaceId = state.resolveSpaceId();
            if (resolvedSpaceId <= 0) {
                persistent.recordRuntimeBodySkipped(bodyKey, "no resolved space id");
                continue;
            }

            try {
                RestoreBodyResult result = PhysicsOwnerBridge.call(store,
                    "hydrate persisted physics body",
                    () -> restoreBodyOnOwner(runtime, state, bodyKey));
                if (result == RestoreBodyResult.RESTORED) {
                    persistent.recordRuntimeBodyRestored();
                } else if (result == RestoreBodyResult.MISSING_SPACE) {
                    persistent.recordRuntimeBodySkipped(bodyKey, "missing target space");
                }
            } catch (RuntimeException exception) {
                persistent.recordRuntimeBodySkipped(bodyKey, "body restore failed: "
                    + exception.getClass().getSimpleName());
            }
        }
    }

    static RestoreBodyResult restoreBodyOnOwner(@Nonnull PhysicsWorldRuntimeResource runtime,
        @Nonnull PersistentPhysicsBodyState state,
        @Nonnull RigidBodyKey bodyKey) {
        if (runtime.getRegistration(bodyKey) != null) {
            return RestoreBodyResult.ALREADY_REGISTERED;
        }

        PhysicsSpaceBinding space = runtime.getSpaceBinding(new SpaceId(state.resolveSpaceId()));
        if (space == null) {
            return RestoreBodyResult.MISSING_SPACE;
        }

        BackendBodyHandle backendBodyHandle = state.createBackendBody(space);
        state.applyToBody(space, backendBodyHandle);
        runtime.addBodyOnOwner(bodyKey,
            space.spaceId(),
            backendBodyHandle,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        return RestoreBodyResult.RESTORED;
    }

    enum RestoreBodyResult {
        RESTORED,
        ALREADY_REGISTERED,
        MISSING_SPACE
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
