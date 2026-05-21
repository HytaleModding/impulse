package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsJointState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsRuntimeSupport;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 * <p>Runs after restore hydration to ensure both sides are settled before copying.
 * Only persistent bodies and joints whose endpoints are persistent body ids are
 * part of the persisted contract; runtime helper-body joints are ignored.</p>
 */
public class PersistentPhysicsWorldSyncSystem extends TickingSystem<EntityStore> {

    private static final int WORLD_SYNC_INTERVAL_TICKS = 20;
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistentPhysicsJointHydrationSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        if (persistent.isRuntimeRestorePending() || persistent.hasRuntimeRestoreFailed()) {
            return;
        }

        PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());
        if (!hasScalarWorldStateChanged(persistent, runtime)
            && !hasRuntimePersistenceFootprintChanged(persistent, runtime)
            && !persistent.shouldSyncRuntimeSnapshot(WORLD_SYNC_INTERVAL_TICKS)) {
            return;
        }

        syncRuntimeSnapshot(persistent, runtime);
    }

    @Nonnull
    public static SyncResult syncRuntimeSnapshot(@Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsWorldResource runtime) {
        if (persistent.isRuntimeRestorePending()) {
            return SyncResult.skipped("restore pending");
        }
        if (persistent.hasRuntimeRestoreFailed()) {
            return SyncResult.skipped("restore failed");
        }

        persistent.setSchemaVersion(PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION);
        persistent.setSimulationSteps(runtime.getSimulationSteps());
        persistent.setStepMode(runtime.getStepMode());
        persistent.setMaxStepDt(runtime.getMaxStepDt());
        SpaceId defaultSpaceId = runtime.getDefaultSpaceId();
        persistent.setDefaultSpaceId(defaultSpaceId != null
            ? defaultSpaceId.value()
            : PersistentPhysicsBodyState.DEFAULT_SPACE_ID);

        List<PersistentPhysicsSpaceState> spaces = new ArrayList<>();
        List<PersistentPhysicsBodyState> bodies = new ArrayList<>();
        List<PersistentPhysicsJointState> joints = new ArrayList<>();
        for (PhysicsWorldResource.BodyRegistration registration : runtime.getBodyRegistrations()) {
            if (registration.persistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT) {
                bodies.add(PersistentPhysicsBodyState.from(registration));
            }
        }
        for (PhysicsSpace space : runtime.getSpaces()) {
            PhysicsSpaceSettings settings = runtime.getSpaceSettings(space.getId());
            spaces.add(PersistentPhysicsSpaceState.from(space, settings));
            space.forEachJoint(joint -> {
                PhysicsBodyId bodyAId = runtime.getBodyId(joint.getBodyA());
                PhysicsBodyId bodyBId = runtime.getBodyId(joint.getBodyB());
                if (bodyAId == null || bodyBId == null) {
                    return;
                }
                PhysicsWorldResource.BodyRegistration bodyA = runtime.getRegistration(bodyAId);
                PhysicsWorldResource.BodyRegistration bodyB = runtime.getRegistration(bodyBId);
                if (bodyA == null
                    || bodyB == null
                    || bodyA.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT
                    || bodyB.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT) {
                    return;
                }
                joints.add(PersistentPhysicsJointState.from(space.getId().value(),
                    bodyAId,
                    bodyBId,
                    joint));
            });
        }

        persistent.setSpaces(spaces.toArray(PersistentPhysicsSpaceState[]::new));
        persistent.setBodies(bodies.toArray(PersistentPhysicsBodyState[]::new));
        persistent.setJoints(joints.toArray(PersistentPhysicsJointState[]::new));
        persistent.markRuntimeSnapshotSynced();
        return SyncResult.synced(spaces.size(), bodies.size(), joints.size());
    }

    private static boolean hasScalarWorldStateChanged(@Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsWorldResource runtime) {
        SpaceId defaultSpaceId = runtime.getDefaultSpaceId();
        int resolvedDefaultSpaceId = defaultSpaceId != null
            ? defaultSpaceId.value()
            : PersistentPhysicsBodyState.DEFAULT_SPACE_ID;
        return persistent.getSimulationSteps() != runtime.getSimulationSteps()
            || persistent.getStepMode() != runtime.getStepMode()
            || Float.compare(persistent.getMaxStepDt(), runtime.getMaxStepDt()) != 0
            || persistent.getDefaultSpaceId() != resolvedDefaultSpaceId;
    }

    static boolean hasRuntimePersistenceFootprintChanged(@Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsWorldResource runtime) {
        return persistent.getSpaceCount() != runtime.getSpaceCount()
            || persistent.getBodyCount() != runtime.getBodyRegistrationCount(PhysicsBodyPersistenceMode.PERSISTENT)
            || persistent.getJointCount() != countPersistentJoints(runtime);
    }

    private static int countPersistentJoints(@Nonnull PhysicsWorldResource runtime) {
        int[] count = new int[1];
        for (PhysicsSpace space : runtime.iterateSpaces()) {
            space.forEachJoint(joint -> {
                PhysicsBodyId bodyAId = runtime.getBodyId(joint.getBodyA());
                PhysicsBodyId bodyBId = runtime.getBodyId(joint.getBodyB());
                if (bodyAId == null || bodyBId == null) {
                    return;
                }
                PhysicsWorldResource.BodyRegistration bodyA = runtime.getRegistration(bodyAId);
                PhysicsWorldResource.BodyRegistration bodyB = runtime.getRegistration(bodyBId);
                if (bodyA == null
                    || bodyB == null
                    || bodyA.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT
                    || bodyB.persistenceMode() != PhysicsBodyPersistenceMode.PERSISTENT) {
                    return;
                }
                count[0]++;
            });
        }
        return count[0];
    }

    public record SyncResult(boolean synced,
        int spaces,
        int bodies,
        int joints,
        @Nonnull String skippedReason) {

        @Nonnull
        private static SyncResult synced(int spaces, int bodies, int joints) {
            return new SyncResult(true, spaces, bodies, joints, "");
        }

        @Nonnull
        private static SyncResult skipped(@Nonnull String reason) {
            return new SyncResult(false, 0, 0, 0, reason);
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

}
