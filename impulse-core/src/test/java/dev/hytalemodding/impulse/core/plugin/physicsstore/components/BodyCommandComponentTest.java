package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class BodyCommandComponentTest {

    @Test
    void appendPreservesOrderAndCopiesEntries() {
        BodyCommandComponent first = BodyCommandComponent.wake();
        BodyCommandComponent second = BodyCommandComponent.setVelocity(new Vector3f(1.0f,
                2.0f,
                3.0f),
            new Vector3f(0.1f, 0.2f, 0.3f),
            true);

        BodyCommandComponent merged = first.append(second);

        BodyCommandComponent.Entry[] entries = merged.entries();
        assertEquals(2, entries.length);
        assertEquals(BodyCommandComponent.Kind.WAKE, entries[0].getKind());
        assertEquals(BodyCommandComponent.Kind.SET_VELOCITY, entries[1].getKind());
        assertEquals(1.0f, entries[1].getX(), 0.0001f);
        assertEquals(0.3f, entries[1].getAngularZ(), 0.0001f);
        assertNotSame(entries[0], merged.entries()[0]);
    }

    @Test
    void factoriesStoreCommandSpecificFields() {
        BodyCommandComponent type = BodyCommandComponent.setType(PhysicsBodyType.KINEMATIC, true);
        BodyCommandComponent filter = BodyCommandComponent.setCollisionFilter(
            PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN,
            false);
        BodyCommandComponent impulse = BodyCommandComponent.vector(
            BodyCommandComponent.Kind.IMPULSE,
            0.0f,
            6.0f,
            0.0f,
            true,
            0.0f,
            -0.5f,
            0.0f);

        assertEquals(PhysicsBodyType.KINEMATIC, type.entries()[0].getBodyType());
        assertTrue(type.entries()[0].isActivate());
        assertEquals(PhysicsCollisionFilters.TERRAIN, filter.entries()[0].getCollisionMask());
        assertEquals(BodyCommandComponent.Kind.IMPULSE, impulse.entries()[0].getKind());
        assertTrue(impulse.entries()[0].hasOffset());
        assertEquals(-0.5f, impulse.entries()[0].getOffsetY(), 0.0001f);
    }

    @Test
    void vectorRejectsNonVectorKind() {
        assertThrows(IllegalArgumentException.class,
            () -> BodyCommandComponent.vector(BodyCommandComponent.Kind.SET_TYPE,
                0.0f,
                0.0f,
                0.0f,
                false,
                0.0f,
                0.0f,
                0.0f));
    }
}
