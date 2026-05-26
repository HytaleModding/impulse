package dev.hytalemodding.impulse.core.internal.systems.persistence;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsJointState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsRuntimeSupport;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Third stage of persistence restore: reconnects joints between hydrated bodies.
 *
 * <p>Runs when {@code runtimeRestorePending} is true.
 * Scans the persisted joint definitions and creates each joint in the target space
 * if both endpoint bodies have already been hydrated by body id.</p>
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

        PhysicsWorkerAccess.run(store, "hydrate persisted physics joints", () -> hydrateJoints(store,
            persistent));

        persistent.clearRuntimeRestorePending();
        if (persistent.hasRuntimeRestoreSkips()) {
            LOGGER.at(Level.WARNING).log(persistent.runtimeRestoreSummary());
        } else {
            LOGGER.at(Level.INFO).log(persistent.runtimeRestoreSummary());
        }
    }

    private static void hydrateJoints(@Nonnull Store<EntityStore> store,
        @Nonnull PersistentPhysicsWorldResource persistent) {
        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(store);
        Set<String> existing = new ObjectOpenHashSet<>();
        for (PhysicsSpace space : runtime.getSpaces()) {
            space.forEachJoint(joint -> {
                PhysicsBodyId bodyAId = runtime.getBodyId(joint.getBodyA());
                PhysicsBodyId bodyBId = runtime.getBodyId(joint.getBodyB());
                if (bodyAId == null || bodyBId == null) {
                    return;
                }
                existing.add(PersistentPhysicsRuntimeSupport.jointKey(space.getId().value(),
                    bodyAId,
                    bodyBId,
                    joint));
            });
        }

        for (PersistentPhysicsJointState state : persistent.getJoints()) {
            String key = state.key();
            PhysicsBodyId bodyAId = state.getBodyAId();
            PhysicsBodyId bodyBId = state.getBodyBId();
            if (bodyAId == null || bodyBId == null) {
                persistent.recordRuntimeJointSkipped(key, "missing endpoint body id");
                continue;
            }

            if (existing.contains(key)) {
                continue;
            }

            PhysicsSpace space = runtime.getSpace(new SpaceId(state.getSpaceId()));
            PhysicsBody bodyA = runtime.getBody(bodyAId);
            PhysicsBody bodyB = runtime.getBody(bodyBId);
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
                PersistentPhysicsRuntimeSupport.createJoint(space, state, bodyA, bodyB);
            } catch (RuntimeException exception) {
                persistent.recordRuntimeJointSkipped(key, "joint creation failed");
                LOGGER.at(Level.WARNING).log(
                    "Skipping persisted joint in space %s because creation failed: %s",
                    state.getSpaceId(),
                    exception.getMessage());
                continue;
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
