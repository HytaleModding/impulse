package dev.hytalemodding.impulse.core.internal.systems.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyLifecycleComponent;
import org.junit.jupiter.api.Test;

class RigidBodySpawnPlanTest {

    @Test
    void entityOwnedBodiesProduceSpawnPlansWithExplicitSpace() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 81L);

        RigidBodySpawnPlan plan = RigidBodySpawnPlan.create(sphere(bodyKey,
            new SpaceId(3),
            RigidBodyComponent.Ownership.ENTITY_OWNED));

        assertTrue(plan.shouldSpawnBody());
        assertTrue(plan.shouldAttachEntity());
        assertTrue(plan.shouldDestroyOnLifecycleRemoval());
        assertEquals(bodyKey, plan.bodyKey());
        assertEquals(new SpaceId(3), plan.spaceId());
        assertEquals(PhysicsBodyType.DYNAMIC, plan.bodyType());
        assertEquals(2.0f, plan.mass(), 0.0001f);
        assertEquals(PhysicsBodyPersistenceMode.PERSISTENT, plan.persistenceMode());
    }

    @Test
    void detachedViewsAttachWithoutSpawningOrDestroying() {
        RigidBodySpawnPlan plan = RigidBodySpawnPlan.create(sphere(RigidBodyKey.of(0L, 82L),
            new SpaceId(4),
            RigidBodyComponent.Ownership.DETACHED_VIEW));

        assertFalse(plan.shouldSpawnBody());
        assertTrue(plan.shouldAttachEntity());
        assertFalse(plan.shouldDestroyOnLifecycleRemoval());
    }

    @Test
    void fullDetachedBodiesSpawnWithoutEntityAttachmentOrDestroyOnRemoval() {
        RigidBodySpawnPlan plan = RigidBodySpawnPlan.create(sphere(RigidBodyKey.of(0L, 83L),
            new SpaceId(5),
            RigidBodyComponent.Ownership.FULL_DETACHED));

        assertTrue(plan.shouldSpawnBody());
        assertFalse(plan.shouldAttachEntity());
        assertFalse(plan.shouldDestroyOnLifecycleRemoval());
    }

    @Test
    void missingExplicitSpaceFailsInsteadOfChoosingDefault() {
        RigidBodyComponent body = sphere(RigidBodyKey.of(0L, 84L),
            null,
            RigidBodyComponent.Ownership.ENTITY_OWNED);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> RigidBodySpawnPlan.create(body));

        assertEquals("RigidBodyComponent must hold a positive explicit SpaceId",
            failure.getMessage());
    }

    @Test
    void destroyedLifecycleSuppressesEntityAuthoredRespawn() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 85L);
        RigidBodyLifecycleComponent lifecycle = RigidBodyLifecycleComponent.destroyed(bodyKey,
            RigidBodyComponent.Ownership.ENTITY_OWNED);

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

    private static RigidBodyComponent sphere(RigidBodyKey bodyKey,
        SpaceId spaceId,
        RigidBodyComponent.Ownership ownership) {
        RigidBodyComponent body = new RigidBodyComponent();
        body.setBodyKey(bodyKey);
        body.setSpaceId(spaceId);
        body.setShapeType(ShapeType.SPHERE);
        body.setRadius(0.75f);
        body.setAxis(PhysicsAxis.Y);
        body.setBodyType(PhysicsBodyType.DYNAMIC);
        body.setMass(2.0f);
        body.setPersistenceMode(PhysicsBodyPersistenceMode.PERSISTENT);
        body.setOwnership(ownership);
        return body;
    }
}
