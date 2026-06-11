package dev.hytalemodding.impulse.core.plugin.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsBodyComponentsTest {

    @Test
    void splitBodyComponentsCloneValueState() {
        RigidBodyKey bodyKey = RigidBodyKey.of(0L, 42L);
        PhysicsBodyIdentityComponent identity = new PhysicsBodyIdentityComponent(
            bodyKey,
            new SpaceId(7),
            PhysicsBodyPersistenceMode.PERSISTENT);
        PhysicsBodyShapeComponent shape = PhysicsBodyShapeComponent.capsule(0.35f,
            0.8f,
            PhysicsAxis.Y);
        PhysicsBodyDynamicsComponent dynamics = new PhysicsBodyDynamicsComponent(
            PhysicsBodyType.KINEMATIC,
            3.0f,
            0.05f,
            0.1f);
        PhysicsBodyMaterialComponent material = new PhysicsBodyMaterialComponent(0.4f,
            0.2f);
        PhysicsBodyCollisionComponent collision = new PhysicsBodyCollisionComponent(
            false,
            PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
        PhysicsBodyLifecycleComponent lifecycle = PhysicsBodyLifecycleComponent.pending(bodyKey);

        PhysicsBodyIdentityComponent identityCopy = identity.clone();
        PhysicsBodyShapeComponent shapeCopy = shape.clone();
        PhysicsBodyDynamicsComponent dynamicsCopy = dynamics.clone();
        PhysicsBodyMaterialComponent materialCopy = material.clone();
        PhysicsBodyCollisionComponent collisionCopy = collision.clone();
        PhysicsBodyLifecycleComponent lifecycleCopy = lifecycle.clone();

        assertNotSame(identity, identityCopy);
        assertEquals(bodyKey, identityCopy.getBodyKey());
        assertEquals(new SpaceId(7), identityCopy.getSpaceId());
        assertEquals(PhysicsBodyPersistenceMode.PERSISTENT, identityCopy.getPersistenceMode());
        assertEquals(ShapeType.CAPSULE, shapeCopy.getShapeType());
        assertEquals(0.35f, shapeCopy.getRadius(), 0.0001f);
        assertEquals(PhysicsBodyType.KINEMATIC, dynamicsCopy.getBodyType());
        assertEquals(3.0f, dynamicsCopy.getMass(), 0.0001f);
        assertEquals(0.4f, materialCopy.getFriction(), 0.0001f);
        assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY, collisionCopy.getCollisionGroup());
        assertEquals(PhysicsBodyLifecycleComponent.State.PENDING, lifecycleCopy.getState());
        assertEquals(bodyKey, lifecycleCopy.getBodyKey());
    }

    @Test
    void spawnIntentCopiesSplitComponentValuesIntoExistingCommandTypes() {
        PhysicsBodyShapeComponent shape = PhysicsBodyShapeComponent.capsule(0.35f,
            0.8f,
            PhysicsAxis.Y);
        PhysicsBodyDynamicsComponent dynamics = new PhysicsBodyDynamicsComponent(
            PhysicsBodyType.DYNAMIC,
            1.0f,
            0.05f,
            0.1f);
        PhysicsBodyMaterialComponent material = new PhysicsBodyMaterialComponent(0.4f,
            0.2f);
        PhysicsBodyCollisionComponent collision = new PhysicsBodyCollisionComponent(
            false,
            PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);

        PhysicsShapeSpec shapeSpec = PhysicsBodyComponentValues.toShapeSpec(shape);
        RigidBodySpawnSettings settings = PhysicsBodyComponentValues.toSpawnSettings(
            dynamics,
            material,
            collision);

        assertEquals(ShapeType.CAPSULE, shapeSpec.type());
        assertEquals(0.35f, shapeSpec.radius(), 0.0001f);
        assertEquals(0.8f, shapeSpec.halfHeight(), 0.0001f);
        assertEquals(PhysicsAxis.Y, shapeSpec.axis());
        assertTrue(settings.hasFriction());
        assertEquals(0.4f, settings.friction(), 0.0001f);
        assertTrue(settings.hasLinearDamping());
        assertEquals(0.05f, settings.linearDamping(), 0.0001f);
        assertTrue(settings.hasCollisionFilter());
        assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY, settings.collisionGroup());
        assertFalse(settings.sensor());
    }

    @Test
    void defaultIntentValuesStayFriendlyButExplicitSpaceIsRequired() {
        PhysicsBodyIdentityComponent identity = new PhysicsBodyIdentityComponent();
        PhysicsBodyShapeComponent shape = new PhysicsBodyShapeComponent();
        PhysicsBodyDynamicsComponent dynamics = new PhysicsBodyDynamicsComponent();
        PhysicsBodyMaterialComponent material = new PhysicsBodyMaterialComponent();
        PhysicsBodyCollisionComponent collision = new PhysicsBodyCollisionComponent();

        assertFalse(PhysicsBodyComponentValues.hasExplicitSpace(identity));
        assertEquals(ShapeType.BOX, shape.getShapeType());
        assertEquals(0.5f, shape.getHalfExtentX(), 0.0001f);
        assertEquals(PhysicsBodyType.DYNAMIC, dynamics.getBodyType());
        assertEquals(1.0f, dynamics.getMass(), 0.0001f);
        assertEquals(0.5f, material.getFriction(), 0.0001f);
        assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY, collision.getCollisionGroup());
        assertEquals(PhysicsCollisionFilters.ALL, collision.getCollisionMask());
        assertEquals(PhysicsBodyPersistenceMode.RUNTIME_ONLY, identity.getPersistenceMode());
    }

    @Test
    void kinematicTargetCopiesValueState() {
        PhysicsBodyKinematicTargetComponent target = new PhysicsBodyKinematicTargetComponent(
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Quaternionf(),
            new Vector3f(0.25f, 0.0f, 0.0f),
            new Vector3f(0.0f, 0.5f, 0.0f),
            true,
            true,
            false);

        PhysicsBodyKinematicTargetComponent copy = target.clone();

        assertNotSame(target, copy);
        assertEquals(1.0f, copy.getPosition().x, 0.0001f);
        assertEquals(0.25f, copy.getLinearVelocity().x, 0.0001f);
        assertTrue(copy.isVelocityEnabled());
        assertFalse(copy.isActivate());
    }

    @Test
    void nonNullPublicComponentFieldsRejectNulls() {
        assertThrows(NullPointerException.class, () -> new PhysicsBodyIdentityComponent(
            null,
            new SpaceId(1),
            PhysicsBodyPersistenceMode.PERSISTENT));
        assertThrows(NullPointerException.class,
            () -> PhysicsBodyShapeComponent.capsule(0.25f, 0.5f, null));
        assertThrows(NullPointerException.class,
            () -> new PhysicsBodyDynamicsComponent(null, 1.0f, 0.0f, 0.0f));
        assertThrows(NullPointerException.class, () -> new PhysicsBodyKinematicTargetComponent(
            null,
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            true,
            false,
            true));
        assertThrows(NullPointerException.class,
            () -> new PhysicsBodyLifecycleComponent(null, null, null));
    }
}
