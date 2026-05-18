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
 * Syncs the persisted world resource from the current runtime state.
 *
 * <p>Mirrors the live spaces, their settings, and all entity-backed joints back
 * into {@link PersistentPhysicsWorldResource} on a bounded cadence. Scalar
 * world settings are synchronized immediately when they diverge. Skipped while a restore
 * is in progress, or after a hard restore failure, to avoid overwriting
 * deserialized data before hydration finishes or before the failure is resolved.</p>
 *
 * <p>Runs after joint hydration and body sync to ensure both sides are settled
 * before copying. Only joints whose endpoints resolve to entity UUIDs are part
 * of the persisted contract; runtime helper-body joints are intentionally ignored.</p>
 */
public class PersistentPhysicsWorldSyncSystem extends TickingSystem<EntityStore> {

    private static final int WORLD_SYNC_INTERVAL_TICKS = 20;
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
        if (persistent.isRuntimeRestorePending() || persistent.hasRuntimeRestoreFailed()) {
            return;
        }

        PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());
        if (!hasScalarWorldStateChanged(persistent, runtime)
            && !persistent.shouldSyncRuntimeSnapshot(WORLD_SYNC_INTERVAL_TICKS)) {
            return;
        }

        persistent.setSimulationSteps(runtime.getSimulationSteps());
        persistent.setStepMode(runtime.getStepMode());
        persistent.setMaxStepDt(runtime.getMaxStepDt());
        SpaceId defaultSpaceId = runtime.getDefaultSpaceId();
        persistent.setDefaultSpaceId(defaultSpaceId != null
            ? defaultSpaceId.value()
            : PersistentPhysicsBodyComponent.DEFAULT_SPACE_ID);

        List<PersistentPhysicsSpaceState> spaces = new ArrayList<>();
        List<PersistentPhysicsJointState> joints = new ArrayList<>();
        for (PhysicsSpace space : runtime.getSpaces()) {
            PhysicsSpaceSettings settings = runtime.getSpaceSettings(space.getId());
            spaces.add(PersistentPhysicsSpaceState.from(space, settings));
            space.forEachJoint(joint -> {
                UUID bodyAUuid = PersistentPhysicsRuntimeSupport.ownerUuid(store, joint.getBodyA(), runtime);
                UUID bodyBUuid = PersistentPhysicsRuntimeSupport.ownerUuid(store, joint.getBodyB(), runtime);
                if (bodyAUuid == null || bodyBUuid == null) {
                    return;
                }
                joints.add(PersistentPhysicsJointState.from(space.getId().value(),
                    bodyAUuid,
                    bodyBUuid,
                    joint));
            });
        }

        persistent.setSpaces(spaces.toArray(PersistentPhysicsSpaceState[]::new));
        persistent.setJoints(joints.toArray(PersistentPhysicsJointState[]::new));
        persistent.markRuntimeSnapshotSynced();
    }

    private static boolean hasScalarWorldStateChanged(@Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsWorldResource runtime) {
        SpaceId defaultSpaceId = runtime.getDefaultSpaceId();
        int resolvedDefaultSpaceId = defaultSpaceId != null
            ? defaultSpaceId.value()
            : PersistentPhysicsBodyComponent.DEFAULT_SPACE_ID;
        return persistent.getSimulationSteps() != runtime.getSimulationSteps()
            || persistent.getStepMode() != runtime.getStepMode()
            || Float.compare(persistent.getMaxStepDt(), runtime.getMaxStepDt()) != 0
            || persistent.getDefaultSpaceId() != resolvedDefaultSpaceId;
    }
}
