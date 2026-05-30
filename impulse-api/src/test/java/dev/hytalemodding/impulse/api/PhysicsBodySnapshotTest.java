package dev.hytalemodding.impulse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsBodySnapshotTest {

    @Test
    void exposesPrimitivePoseAndVelocityValuesWithoutMutableCopies() {
        Quaternionf rotation = new Quaternionf(0.1f, 0.2f, 0.3f, 0.9f).normalize();
        PhysicsBodySnapshot snapshot = new PhysicsBodySnapshot(new Vector3f(1.0f, 2.0f, 3.0f),
            rotation,
            new Vector3f(4.0f, 5.0f, 6.0f),
            new Vector3f(7.0f, 8.0f, 9.0f),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.25f,
            ShapeType.BOX,
            new Vector3f(0.5f, 0.75f, 1.0f),
            -1.0f,
            -1.0f,
            PhysicsAxis.Y);

        assertEquals(1.0f, snapshot.positionX());
        assertEquals(2.0f, snapshot.positionY());
        assertEquals(3.0f, snapshot.positionZ());
        assertEquals(rotation.x, snapshot.rotationX());
        assertEquals(rotation.y, snapshot.rotationY());
        assertEquals(rotation.z, snapshot.rotationZ());
        assertEquals(rotation.w, snapshot.rotationW());
        assertEquals(4.0f, snapshot.linearVelocityX());
        assertEquals(5.0f, snapshot.linearVelocityY());
        assertEquals(6.0f, snapshot.linearVelocityZ());
        assertEquals(7.0f, snapshot.angularVelocityX());
        assertEquals(8.0f, snapshot.angularVelocityY());
        assertEquals(9.0f, snapshot.angularVelocityZ());
    }

    @Test
    void copiesSnapshotVectorsIntoCallerOwnedTargets() {
        Quaternionf rotation = new Quaternionf().rotateXYZ(0.25f, 0.5f, 0.75f);
        PhysicsBodySnapshot snapshot = new PhysicsBodySnapshot(new Vector3f(1.0f, 2.0f, 3.0f),
            rotation,
            new Vector3f(4.0f, 5.0f, 6.0f),
            new Vector3f(7.0f, 8.0f, 9.0f),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.25f,
            ShapeType.BOX,
            new Vector3f(0.5f, 0.75f, 1.0f),
            -1.0f,
            -1.0f,
            PhysicsAxis.Y);
        Vector3f position = new Vector3f();
        Quaternionf rotationTarget = new Quaternionf();
        Vector3f linearVelocity = new Vector3f();
        Vector3f angularVelocity = new Vector3f();

        assertSame(position, snapshot.copyPositionTo(position));
        assertSame(rotationTarget, snapshot.copyRotationTo(rotationTarget));
        assertSame(linearVelocity, snapshot.copyLinearVelocityTo(linearVelocity));
        assertSame(angularVelocity, snapshot.copyAngularVelocityTo(angularVelocity));

        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), position);
        assertEquals(rotation, rotationTarget);
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), linearVelocity);
        assertEquals(new Vector3f(7.0f, 8.0f, 9.0f), angularVelocity);
    }

    @Test
    void exposesBoxHalfExtentsWithoutMutableCopiesWhenPresent() {
        PhysicsBodySnapshot snapshot = new PhysicsBodySnapshot(new Vector3f(),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.0f,
            ShapeType.BOX,
            new Vector3f(0.5f, 0.75f, 1.0f),
            -1.0f,
            -1.0f,
            PhysicsAxis.Y);
        Vector3f extents = new Vector3f();

        assertTrue(snapshot.hasBoxHalfExtents());
        assertEquals(0.5f, snapshot.boxHalfExtentX());
        assertEquals(0.75f, snapshot.boxHalfExtentY());
        assertEquals(1.0f, snapshot.boxHalfExtentZ());
        assertSame(extents, snapshot.copyBoxHalfExtentsTo(extents));
        assertEquals(new Vector3f(0.5f, 0.75f, 1.0f), extents);
    }

    @Test
    void reportsMissingBoxHalfExtentsWithoutAllocatingFallbackVectors() {
        PhysicsBodySnapshot snapshot = new PhysicsBodySnapshot(new Vector3f(),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.0f,
            ShapeType.SPHERE,
            null,
            0.5f,
            -1.0f,
            PhysicsAxis.Y);
        Vector3f extents = new Vector3f(1.0f, 2.0f, 3.0f);

        assertFalse(snapshot.hasBoxHalfExtents());
        assertEquals(0.0f, snapshot.boxHalfExtentX());
        assertEquals(0.0f, snapshot.boxHalfExtentY());
        assertEquals(0.0f, snapshot.boxHalfExtentZ());
        assertSame(extents, snapshot.copyBoxHalfExtentsTo(extents));
        assertEquals(new Vector3f(), extents);
    }
}
