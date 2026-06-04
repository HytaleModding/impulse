package dev.hytalemodding.impulse.core.internal.systems.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKeyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyMassComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyOwnershipComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyPersistenceComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodySpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyTypeComponent;
import org.junit.jupiter.api.Test;

class RigidBodySpawnPlanTest {

    @Test
    void entityOwnedBodiesProduceSpawnPlansWithExplicitSpace() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 81L);

        RigidBodySpawnPlan plan = RigidBodySpawnPlan.create(
            new RigidBodyKeyComponent(bodyKey),
            new RigidBodySpaceComponent(new SpaceId(3)),
            new RigidBodyShapeComponent(ShapeType.SPHERE,
                0.0f,
                0.0f,
                0.0f,
                0.75f,
                0.0f,
                PhysicsAxis.Y,
                0.0f),
            new RigidBodyTypeComponent(PhysicsBodyType.DYNAMIC),
            new RigidBodyMassComponent(2.0f),
            null,
            null,
            new RigidBodyPersistenceComponent(PhysicsBodyPersistenceMode.PERSISTENT),
            new RigidBodyOwnershipComponent(RigidBodyOwnershipComponent.Ownership.ENTITY_OWNED));

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
        RigidBodySpawnPlan plan = RigidBodySpawnPlan.create(
            new RigidBodyKeyComponent(RigidBodyKey.of(0L, 82L)),
            new RigidBodySpaceComponent(new SpaceId(4)),
            new RigidBodyShapeComponent(),
            null,
            null,
            null,
            null,
            null,
            new RigidBodyOwnershipComponent(RigidBodyOwnershipComponent.Ownership.DETACHED_VIEW));

        assertFalse(plan.shouldSpawnBody());
        assertTrue(plan.shouldAttachEntity());
        assertFalse(plan.shouldDestroyOnLifecycleRemoval());
    }

    @Test
    void fullDetachedBodiesSpawnWithoutEntityAttachmentOrDestroyOnRemoval() {
        RigidBodySpawnPlan plan = RigidBodySpawnPlan.create(
            new RigidBodyKeyComponent(RigidBodyKey.of(0L, 83L)),
            new RigidBodySpaceComponent(new SpaceId(5)),
            new RigidBodyShapeComponent(),
            null,
            null,
            null,
            null,
            null,
            new RigidBodyOwnershipComponent(RigidBodyOwnershipComponent.Ownership.FULL_DETACHED));

        assertTrue(plan.shouldSpawnBody());
        assertFalse(plan.shouldAttachEntity());
        assertFalse(plan.shouldDestroyOnLifecycleRemoval());
    }

    @Test
    void missingExplicitSpaceFailsInsteadOfChoosingDefault() {
        RigidBodyKeyComponent key = new RigidBodyKeyComponent(RigidBodyKey.of(0L, 84L));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> RigidBodySpawnPlan.create(key,
                new RigidBodySpaceComponent(),
                new RigidBodyShapeComponent(),
                null,
                null,
                null,
                null,
                null,
                new RigidBodyOwnershipComponent()));

        assertEquals("RigidBodySpaceComponent must hold a positive explicit SpaceId",
            failure.getMessage());
    }
}
