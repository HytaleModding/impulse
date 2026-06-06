package dev.hytalemodding.impulse.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsKinematicControlSystem;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsChunkBoundarySystem;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsCollisionLodSystem;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsWorldCollisionStreamingSystem;
import dev.hytalemodding.impulse.core.internal.systems.body.RigidBodyReconciliationSystem;
import dev.hytalemodding.impulse.core.internal.systems.debug.PhysicsDebugSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsBodyHydrationSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsJointHydrationSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsSpaceBootstrapSystem;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsWorldSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsSnapshotPublicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsDetachedVisualMaterializationSystem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImpulsePluginSystemRegistrationOrderTest {

    @Test
    void registersSystemDependencyTargetsBeforeDependents() {
        List<Class<? extends ISystem<EntityStore>>> registrationOrder =
            ImpulsePlugin.entitySystemRegistrationTypesForTesting();
        Map<Class<? extends ISystem<EntityStore>>, List<Class<? extends ISystem<EntityStore>>>>
            dependencies = Map.of(
                PersistentPhysicsBodyHydrationSystem.class,
                List.of(PersistentPhysicsSpaceBootstrapSystem.class),
                PersistentPhysicsJointHydrationSystem.class,
                List.of(PersistentPhysicsSpaceBootstrapSystem.class,
                    PersistentPhysicsBodyHydrationSystem.class),
                RigidBodyReconciliationSystem.class,
                List.of(PhysicsSyncSystem.class),
                PhysicsChunkBoundarySystem.class,
                List.of(PhysicsWorldCollisionStreamingSystem.class, PhysicsSyncSystem.class),
                PhysicsDetachedVisualMaterializationSystem.class,
                List.of(PhysicsSyncSystem.class),
                PhysicsSnapshotPublicationSystem.class,
                List.of(PhysicsCollisionLodSystem.class,
                    PhysicsChunkBoundarySystem.class,
                    PhysicsWorldCollisionStreamingSystem.class,
                    PhysicsDetachedVisualMaterializationSystem.class,
                    PhysicsSyncSystem.class),
                PersistentPhysicsWorldSyncSystem.class,
                List.of(PersistentPhysicsJointHydrationSystem.class,
                    PhysicsSnapshotPublicationSystem.class),
                PhysicsDebugSystem.class,
                List.of(PhysicsSyncSystem.class),
                PhysicsKinematicControlSystem.class,
                List.of(PhysicsSyncSystem.class));

        dependencies.forEach((systemClass, dependencyClasses) -> {
            int systemIndex = registrationOrder.indexOf(systemClass);
            assertTrue(systemIndex >= 0, () -> systemClass.getSimpleName()
                + " must be in the entity system registration order");
            for (Class<? extends ISystem<EntityStore>> dependencyClass : dependencyClasses) {
                int dependencyIndex = registrationOrder.indexOf(dependencyClass);
                assertTrue(dependencyIndex >= 0, () -> dependencyClass.getSimpleName()
                    + " must be in the entity system registration order");
                assertTrue(dependencyIndex < systemIndex, () -> dependencyClass.getSimpleName()
                    + " must be registered before " + systemClass.getSimpleName());
            }
        });
    }
}
