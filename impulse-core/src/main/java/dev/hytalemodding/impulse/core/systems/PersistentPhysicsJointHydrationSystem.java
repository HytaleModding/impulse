package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsJointState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsRuntimeSupport;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Third stage of persistence restore: reconnects joints between hydrated bodies.
 *
 * <p>Runs when {@link PersistentPhysicsWorldResource#isRuntimeRestorePending()} is true.
 * Scans the persisted joint definitions and creates each joint in the target space
 * if both endpoint bodies have already been hydrated (looked up by entity UUID).
 * If any endpoint body is still missing, hydration is deferred to the next tick.</p>
 *
 * <p>Uses deterministic key strings (from {@link PersistentPhysicsJointState#key()})
 * to avoid duplicating joints that already exist in the runtime space. Clears
 * the {@code runtimeRestorePending} flag once all persisted joints have been
 * successfully restored.</p>
 *
 * <p>Runs after space bootstrap and body hydration so that both spaces and bodies
 * are available for joint creation.</p>
 */
public class PersistentPhysicsJointHydrationSystem extends TickingSystem<EntityStore> {

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistentPhysicsSpaceBootstrapSystem.class),
        new SystemDependency<>(Order.AFTER, PersistentPhysicsBodyHydrationSystem.class)
    );

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        if (!persistent.isRuntimeRestorePending()) {
            return;
        }

        PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());
        Set<String> existing = new HashSet<>();
        for (PhysicsSpace space : runtime.getSpaces()) {
            for (PhysicsJoint joint : space.getJoints()) {
                UUID bodyAUuid = PersistentPhysicsRuntimeSupport.ownerUuid(store, joint.getBodyA(), runtime);
                UUID bodyBUuid = PersistentPhysicsRuntimeSupport.ownerUuid(store, joint.getBodyB(), runtime);
                if (bodyAUuid == null || bodyBUuid == null) {
                    continue;
                }
                existing.add(PersistentPhysicsRuntimeSupport.jointKey(space.getId().value(),
                    bodyAUuid,
                    bodyBUuid,
                    joint));
            }
        }

        boolean complete = true;
        for (PersistentPhysicsJointState state : persistent.getJoints()) {
            if (state.getBodyAUuid() == null || state.getBodyBUuid() == null) {
                continue;
            }

            String key = state.key();
            if (existing.contains(key)) {
                continue;
            }

            PhysicsSpace space = runtime.getSpace(new SpaceId(state.getSpaceId()));
            PhysicsBody bodyA = PersistentPhysicsRuntimeSupport.runtimeBody(store, state.getBodyAUuid());
            PhysicsBody bodyB = PersistentPhysicsRuntimeSupport.runtimeBody(store, state.getBodyBUuid());
            if (space == null || bodyA == null || bodyB == null) {
                complete = false;
                continue;
            }

            PersistentPhysicsRuntimeSupport.createJoint(space, state, bodyA, bodyB);
            existing.add(key);
        }

        if (complete) {
            persistent.clearRuntimeRestorePending();
        }
    }
}
