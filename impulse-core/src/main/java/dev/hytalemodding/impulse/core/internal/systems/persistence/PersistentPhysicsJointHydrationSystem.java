package dev.hytalemodding.impulse.core.internal.systems.persistence;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsJointState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsRuntimeSupport;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistration;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerBridge;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Third stage of persistence restore: reconnects joints between hydrated bodies.
 *
 * <p>Runs when {@code runtimeRestorePending} is true.
 * Scans the persisted joint definitions and creates each joint in the target space
 * if both endpoint bodies have already been hydrated by body key.</p>
 *
 * <p>Uses deterministic key strings (from {@link PersistentPhysicsJointState#key()})
 * to avoid duplicating joints that already exist in the runtime space. Clears
 * the {@code runtimeRestorePending} flag once all persisted joints have been
 * restored or terminally skipped.</p>
 *
 * <p>Runs after space bootstrap and body hydration so that both spaces and bodies
 * are available for joint creation.</p>
 */
public class PersistentPhysicsJointHydrationSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistentPhysicsSpaceBootstrapSystem.class),
        new SystemDependency<>(Order.AFTER, PersistentPhysicsBodyHydrationSystem.class)
    );

    @Nonnull
    private final SystemGroup<EntityStore> group = ImpulsePlugin.get().getPersistenceRestoreGroup();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        if (!persistent.isRuntimeRestorePending()) {
            return;
        }

        PhysicsOwnerBridge.run(store, "hydrate persisted physics joints", () -> hydrateJoints(store,
            persistent));

        if (!shouldFinalizeRuntimeRestore(persistent)) {
            if (persistent.hasRuntimeRestoreFailed()) {
                LOGGER.at(Level.SEVERE).log(persistent.runtimeRestoreFailureSummary());
            }
            return;
        }

        World world = store.getExternalData().getWorld();
        PersistentPhysicsRestoreTerrainPrewarm.prewarmRestoredDynamicBodyTerrain(store,
            world,
            PhysicsWorldRuntimeResource.require(store),
            persistent,
            Math.max(0L, world.getTick()));
        persistent.clearRuntimeRestorePending();
        if (persistent.hasRuntimeRestoreSkips()) {
            LOGGER.at(Level.WARNING).log(persistent.runtimeRestoreSummary());
        } else {
            LOGGER.at(Level.INFO).log(persistent.runtimeRestoreSummary());
        }
    }

    static boolean shouldFinalizeRuntimeRestore(@Nonnull PersistentPhysicsWorldResource persistent) {
        return persistent.isRuntimeRestorePending() && !persistent.hasRuntimeRestoreFailed();
    }

    private static void hydrateJoints(@Nonnull Store<EntityStore> store,
        @Nonnull PersistentPhysicsWorldResource persistent) {
        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(store);
        Set<String> existing = new ObjectOpenHashSet<>();
        for (PhysicsJointRegistration joint : runtime.getJointRegistrations()) {
            existing.add(PersistentPhysicsRuntimeSupport.jointKey(joint.spaceId().value(),
                joint.bodyA(),
                joint.bodyB(),
                joint));
        }

        for (PersistentPhysicsJointState state : persistent.getJoints()) {
            String key = state.key();
            RigidBodyKey bodyAKey = state.getBodyAKey();
            RigidBodyKey bodyBKey = state.getBodyBKey();
            if (bodyAKey == null || bodyBKey == null) {
                persistent.recordRuntimeJointSkipped(key, "missing endpoint body key");
                continue;
            }

            if (existing.contains(key)) {
                continue;
            }

            PhysicsSpaceBinding space = runtime.getSpaceBinding(new SpaceId(state.getSpaceId()));
            PhysicsBodyRegistration bodyA = runtime.getRegistration(bodyAKey);
            PhysicsBodyRegistration bodyB = runtime.getRegistration(bodyBKey);
            if (space == null) {
                persistent.recordRuntimeJointSkipped(key, "missing target space");
                continue;
            }
            if (bodyA == null || bodyB == null) {
                if (bodyA == null && bodyB == null) {
                    persistent.recordRuntimeJointSkipped(key, "missing both endpoint bodies");
                } else if (bodyA == null) {
                    persistent.recordRuntimeJointSkipped(key, "missing body A");
                } else {
                    persistent.recordRuntimeJointSkipped(key, "missing body B");
                }
                continue;
            }

            try {
                PersistentPhysicsRuntimeSupport.createJoint(runtime, space, state, bodyAKey, bodyA, bodyBKey, bodyB);
            } catch (RuntimeException exception) {
                persistent.failRuntimeRestore("Failed to hydrate persisted joint in space "
                    + state.getSpaceId()
                    + ": "
                    + exception.getClass().getSimpleName());
                return;
            }
            persistent.recordRuntimeJointRestored();
            existing.add(key);
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
