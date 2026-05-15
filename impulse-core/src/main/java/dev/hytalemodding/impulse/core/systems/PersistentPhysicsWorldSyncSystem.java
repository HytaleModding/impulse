package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsJointState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsRuntimeSupport;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Syncs the persisted world resource from the current runtime state each tick.
 *
 * <p>Mirrors the live spaces, their settings, and all entity-backed joints back
 * into {@link PersistentPhysicsWorldResource} so that Hytale's serialization
 * captures the latest state on the next world save. Skipped while a restore
 * is in progress to avoid overwriting deserialized data before hydration finishes.</p>
 *
 * <p>Runs after joint hydration and body sync to ensure both sides are settled
 * before copying.</p>
 */
public class PersistentPhysicsWorldSyncSystem extends TickingSystem<EntityStore> {

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistentPhysicsJointHydrationSystem.class),
        new SystemDependency<>(Order.AFTER, PersistentPhysicsBodySyncSystem.class)
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
        if (persistent.isRuntimeRestorePending()) {
            return;
        }

        PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());
        persistent.setSimulationSteps(runtime.getSimulationSteps());
        SpaceId defaultSpaceId = runtime.getDefaultSpaceId();
        persistent.setDefaultSpaceId(defaultSpaceId != null
            ? defaultSpaceId.value()
            : PersistentPhysicsBodyComponent.DEFAULT_SPACE_ID);

        List<PersistentPhysicsSpaceState> spaces = new ArrayList<>();
        List<PersistentPhysicsJointState> joints = new ArrayList<>();
        for (PhysicsSpace space : runtime.getSpaces()) {
            PhysicsSpaceSettings settings = runtime.getSpaceSettings(space.getId());
            spaces.add(PersistentPhysicsSpaceState.from(space, settings));
            for (PhysicsJoint joint : space.getJoints()) {
                UUID bodyAUuid = PersistentPhysicsRuntimeSupport.ownerUuid(store, joint.getBodyA(), runtime);
                UUID bodyBUuid = PersistentPhysicsRuntimeSupport.ownerUuid(store, joint.getBodyB(), runtime);
                if (bodyAUuid == null || bodyBUuid == null) {
                    continue;
                }
                joints.add(PersistentPhysicsJointState.from(space.getId().value(),
                    bodyAUuid,
                    bodyBUuid,
                    joint));
            }
        }

        persistent.setSpaces(spaces.toArray(PersistentPhysicsSpaceState[]::new));
        persistent.setJoints(joints.toArray(PersistentPhysicsJointState[]::new));
    }
}
