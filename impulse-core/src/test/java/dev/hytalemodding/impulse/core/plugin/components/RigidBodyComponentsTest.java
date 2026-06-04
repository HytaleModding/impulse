package dev.hytalemodding.impulse.core.plugin.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import org.junit.jupiter.api.Test;

class RigidBodyComponentsTest {

    @Test
    void keySpaceAndLifecycleComponentsCloneValueState() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 42L);
        RigidBodyKeyComponent key = new RigidBodyKeyComponent(bodyKey);
        RigidBodySpaceComponent space = new RigidBodySpaceComponent(new SpaceId(7));
        RigidBodyLifecycleComponent lifecycle = RigidBodyLifecycleComponent.pending(
            bodyKey,
            RigidBodyOwnershipComponent.Ownership.ENTITY_OWNED);

        RigidBodyKeyComponent keyCopy = key.clone();
        RigidBodySpaceComponent spaceCopy = space.clone();
        RigidBodyLifecycleComponent lifecycleCopy = lifecycle.clone();

        assertNotSame(key, keyCopy);
        assertEquals(bodyKey, keyCopy.getBodyKey());
        assertEquals(new SpaceId(7), spaceCopy.getSpaceId());
        assertEquals(RigidBodyLifecycleComponent.State.PENDING, lifecycleCopy.getState());
        assertEquals(RigidBodyOwnershipComponent.Ownership.ENTITY_OWNED, lifecycleCopy.getOwnership());
    }

    @Test
    void spawnIntentCopiesComponentValuesIntoExistingCommandTypes() {
        RigidBodyShapeComponent shape = new RigidBodyShapeComponent(
            ShapeType.CAPSULE,
            0.0f,
            0.0f,
            0.0f,
            0.35f,
            0.8f,
            PhysicsAxis.Y,
            0.0f);
        RigidBodyMaterialComponent material = new RigidBodyMaterialComponent(0.4f,
            0.2f,
            0.05f,
            0.1f);
        RigidBodyCollisionComponent collision = new RigidBodyCollisionComponent(false,
            PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);

        PhysicsShapeSpec shapeSpec = RigidBodyComponentValues.toShapeSpec(shape);
        RigidBodySpawnSettings settings = RigidBodyComponentValues.toSpawnSettings(material, collision);

        assertEquals(ShapeType.CAPSULE, shapeSpec.type());
        assertEquals(0.35f, shapeSpec.radius(), 0.0001f);
        assertEquals(0.8f, shapeSpec.halfHeight(), 0.0001f);
        assertEquals(PhysicsAxis.Y, shapeSpec.axis());
        assertTrue(settings.hasFriction());
        assertEquals(0.4f, settings.friction(), 0.0001f);
        assertTrue(settings.hasCollisionFilter());
        assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY, settings.collisionGroup());
        assertFalse(settings.sensor());
    }

    @Test
    void defaultIntentValuesStayFriendlyButExplicitSpaceIsRequired() {
        RigidBodyTypeComponent type = new RigidBodyTypeComponent();
        RigidBodyMassComponent mass = new RigidBodyMassComponent();
        RigidBodyPersistenceComponent persistence = new RigidBodyPersistenceComponent();
        RigidBodyOwnershipComponent ownership = new RigidBodyOwnershipComponent();
        RigidBodySpaceComponent space = new RigidBodySpaceComponent();

        assertEquals(PhysicsBodyType.DYNAMIC, type.getBodyType());
        assertEquals(1.0f, mass.getMass(), 0.0001f);
        assertEquals(PhysicsBodyPersistenceMode.RUNTIME_ONLY, persistence.getPersistenceMode());
        assertEquals(RigidBodyOwnershipComponent.Ownership.ENTITY_OWNED, ownership.getOwnership());
        assertFalse(RigidBodyComponentValues.hasExplicitSpace(space));
    }
}
