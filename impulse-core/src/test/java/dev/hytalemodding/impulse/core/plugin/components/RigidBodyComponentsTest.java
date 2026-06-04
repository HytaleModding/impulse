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
    void rigidBodyAndLifecycleComponentsCloneValueState() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 42L);
        RigidBodyComponent body = new RigidBodyComponent();
        body.setBodyKey(bodyKey);
        body.setSpaceId(new SpaceId(7));
        RigidBodyLifecycleComponent lifecycle = RigidBodyLifecycleComponent.pending(
            bodyKey,
            RigidBodyComponent.Ownership.ENTITY_OWNED);

        RigidBodyComponent bodyCopy = body.clone();
        RigidBodyLifecycleComponent lifecycleCopy = lifecycle.clone();

        assertNotSame(body, bodyCopy);
        assertEquals(bodyKey, bodyCopy.getBodyKey());
        assertEquals(new SpaceId(7), bodyCopy.getSpaceId());
        assertEquals(RigidBodyLifecycleComponent.State.PENDING, lifecycleCopy.getState());
        assertEquals(RigidBodyComponent.Ownership.ENTITY_OWNED, lifecycleCopy.getOwnership());
    }

    @Test
    void spawnIntentCopiesComponentValuesIntoExistingCommandTypes() {
        RigidBodyComponent body = new RigidBodyComponent();
        body.setShapeType(ShapeType.CAPSULE);
        body.setRadius(0.35f);
        body.setHalfHeight(0.8f);
        body.setAxis(PhysicsAxis.Y);
        body.setFriction(0.4f);
        body.setRestitution(0.2f);
        body.setLinearDamping(0.05f);
        body.setAngularDamping(0.1f);
        body.setSensor(false);
        body.setCollisionGroup(PhysicsCollisionFilters.DYNAMIC_BODY);
        body.setCollisionMask(PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);

        PhysicsShapeSpec shapeSpec = RigidBodyComponentValues.toShapeSpec(body);
        RigidBodySpawnSettings settings = RigidBodyComponentValues.toSpawnSettings(body);

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
        RigidBodyComponent body = new RigidBodyComponent();

        assertEquals(PhysicsBodyType.DYNAMIC, body.getBodyType());
        assertEquals(1.0f, body.getMass(), 0.0001f);
        assertEquals(PhysicsBodyPersistenceMode.RUNTIME_ONLY, body.getPersistenceMode());
        assertEquals(RigidBodyComponent.Ownership.ENTITY_OWNED, body.getOwnership());
        assertFalse(RigidBodyComponentValues.hasExplicitSpace(body));
    }
}
