package dev.hytalemodding.impulse.core.internal.systems.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyDynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyIdentityComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyLifecycleComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyMaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyShapeComponent;
import org.junit.jupiter.api.Test;

class RigidBodySpawnPlanTest {

    @Test
    void entityAuthoredBodiesProduceSpawnPlansWithExplicitSpace() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 81L);

        RigidBodySpawnPlan plan = RigidBodySpawnPlan.create(
            identity(bodyKey, new SpaceId(3)),
            shape(),
            dynamics(),
            material(),
            collision());

        assertEquals(bodyKey, plan.bodyKey());
        assertEquals(new SpaceId(3), plan.spaceId());
        assertEquals(PhysicsBodyType.DYNAMIC, plan.bodyType());
        assertEquals(2.0f, plan.mass(), 0.0001f);
        assertEquals(PhysicsBodyPersistenceMode.PERSISTENT, plan.persistenceMode());
    }

    @Test
    void missingExplicitSpaceFailsInsteadOfChoosingDefault() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> RigidBodySpawnPlan.create(identity(RigidBodyKey.of(0L, 84L), null),
                shape(),
                dynamics(),
                material(),
                collision()));

        assertEquals("PhysicsBodyIdentityComponent must hold a positive explicit SpaceId",
            failure.getMessage());
    }

    @Test
    void missingShapeFailsInsteadOfSilentlySkippingTheEntity() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> RigidBodySpawnPlan.create(identity(RigidBodyKey.of(0L, 82L), new SpaceId(4)),
                null,
                dynamics(),
                material(),
                collision()));

        assertEquals("PhysicsBodyShapeComponent is required", failure.getMessage());
    }

    @Test
    void missingDynamicsFailsInsteadOfApplyingImplicitDefaults() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> RigidBodySpawnPlan.create(identity(RigidBodyKey.of(0L, 83L), new SpaceId(5)),
                shape(),
                null,
                material(),
                collision()));

        assertEquals("PhysicsBodyDynamicsComponent is required", failure.getMessage());
    }

    @Test
    void destroyedLifecycleSuppressesEntityAuthoredRespawn() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 85L);
        PhysicsBodyLifecycleComponent lifecycle = PhysicsBodyLifecycleComponent.destroyed(bodyKey);

        assertTrue(RigidBodyReconciliationPolicy.shouldSuppressBodyReconciliation(lifecycle,
            null,
            bodyKey));
    }

    @Test
    void skippedRestoreKeySuppressesEntityAuthoredRespawn() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 86L);
        PersistentPhysicsWorldResource persistent = new PersistentPhysicsWorldResource();
        persistent.recordRuntimeBodySkipped(bodyKey, "invalid position");

        assertTrue(RigidBodyReconciliationPolicy.shouldSuppressBodyReconciliation(null,
            persistent,
            bodyKey));
        assertFalse(RigidBodyReconciliationPolicy.shouldSuppressBodyReconciliation(null,
            persistent,
            RigidBodyKey.of(0L, 87L)));
    }

    private static PhysicsBodyIdentityComponent identity(RigidBodyKey bodyKey, SpaceId spaceId) {
        return new PhysicsBodyIdentityComponent(bodyKey,
            spaceId,
            PhysicsBodyPersistenceMode.PERSISTENT);
    }

    private static PhysicsBodyShapeComponent shape() {
        return PhysicsBodyShapeComponent.capsule(0.75f, 0.5f, PhysicsAxis.Y);
    }

    private static PhysicsBodyDynamicsComponent dynamics() {
        return new PhysicsBodyDynamicsComponent(PhysicsBodyType.DYNAMIC,
            2.0f,
            0.0f,
            0.0f);
    }

    private static PhysicsBodyMaterialComponent material() {
        return new PhysicsBodyMaterialComponent();
    }

    private static PhysicsBodyCollisionComponent collision() {
        return new PhysicsBodyCollisionComponent();
    }
}
