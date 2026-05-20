package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.testsupport.PhysicsBackendContractTest;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RapierBackendContractTest extends PhysicsBackendContractTest {

    @Nonnull
    @Override
    protected PhysicsBackend createBackend() {
        return new RapierBackend();
    }

    @Test
    void shapeFactoriesExposeExpectedStoredMetadata() {
        PhysicsSpace space = createHeadlessSpace();

        PhysicsBody box = space.createBox(0.5f, 1.5f, 2.5f, 3.0f);
        PhysicsBody sphere = space.createSphere(1.25f, 2.0f);
        PhysicsBody capsule = space.createCapsule(0.75f, 1.5f, PhysicsAxis.Z, 4.0f);
        PhysicsBody cylinder = space.createCylinder(0.8f, 1.2f, PhysicsAxis.X, 5.0f);
        PhysicsBody cone = space.createCone(0.6f, 0.9f, PhysicsAxis.Y, 6.0f);
        PhysicsBody plane = space.createStaticPlane(12.0f);

        assertEquals(ShapeType.BOX, box.getShapeType());
        assertVectorNear(new Vector3f(0.5f, 1.5f, 2.5f), box.getBoxHalfExtents(), 0.0001f);
        assertEquals(3.0f, box.getMass(), 0.0001f);
        assertEquals(PhysicsBodyType.DYNAMIC, box.getBodyType());

        assertEquals(ShapeType.SPHERE, sphere.getShapeType());
        assertEquals(1.25f, sphere.getSphereRadius(), 0.0001f);

        assertEquals(ShapeType.CAPSULE, capsule.getShapeType());
        assertEquals(0.75f, capsule.getSphereRadius(), 0.0001f);
        assertEquals(1.5f, capsule.getHalfHeight(), 0.0001f);
        assertEquals(PhysicsAxis.Z, capsule.getShapeAxis());

        assertEquals(ShapeType.CYLINDER, cylinder.getShapeType());
        assertEquals(0.8f, cylinder.getSphereRadius(), 0.0001f);
        assertEquals(1.2f, cylinder.getHalfHeight(), 0.0001f);
        assertEquals(PhysicsAxis.X, cylinder.getShapeAxis());

        assertEquals(ShapeType.CONE, cone.getShapeType());
        assertEquals(0.6f, cone.getSphereRadius(), 0.0001f);
        assertEquals(0.9f, cone.getHalfHeight(), 0.0001f);
        assertEquals(PhysicsAxis.Y, cone.getShapeAxis());

        assertEquals(ShapeType.PLANE, plane.getShapeType());
        assertEquals(PhysicsBodyType.STATIC, plane.getBodyType());
        assertEquals(12.0f, plane.getPlaneGroundY(), 0.0001f);
    }

    @Test
    void rapierSupportsVoxelTerrainMetadataAndBodyTypeSwitching() {
        PhysicsSpace space = createHeadlessSpace();

        assertTrue(space.supportsVoxelTerrain());
        assertTrue(space.supportsContinuousCollision());

        RapierBody terrain = (RapierBody) space.createVoxelTerrain(1.0f,
            2.0f,
            3.0f,
            new int[]{0, 0, 0, 1, 0, 0});

        assertEquals(ShapeType.VOXELS, terrain.getShapeType());
        assertVectorNear(new Vector3f(1.0f, 2.0f, 3.0f), terrain.getVoxelSize(), 0.0001f);
        assertArrayEquals(new int[]{0, 0, 0, 1, 0, 0}, terrain.getVoxelCoordinates());
        assertEquals(PhysicsBodyType.STATIC, terrain.getBodyType());

        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        assertFalse(body.isContinuousCollisionEnabled());
        body.setContinuousCollisionEnabled(true);
        assertTrue(body.isContinuousCollisionEnabled());
        body.setBodyType(PhysicsBodyType.KINEMATIC);
        assertEquals(PhysicsBodyType.KINEMATIC, body.getBodyType());
        body.setKinematic(false);
        assertEquals(PhysicsBodyType.DYNAMIC, body.getBodyType());
    }

    @Test
    void denseDynamicBodyPileStepsAndSnapshots() {
        PhysicsSpace space = createHeadlessSpace();
        int count = 1_000;
        int side = (int) Math.ceil(Math.cbrt(count));
        for (int i = 0; i < count; i++) {
            PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
            int x = i % side;
            int z = (i / side) % side;
            int y = i / (side * side);
            body.setPosition(x * 1.05f, 5.0f + y * 1.05f, z * 1.05f);
            body.setFriction(0.45f);
            body.setRestitution(0.0f);
            body.setDamping(0.02f, 0.25f);
            body.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
            space.addBody(body);
        }

        assertEquals(count, space.bodyCount());

        stepSpace(space, 30);

        List<PhysicsBodySnapshot> snapshots = new ArrayList<>();
        space.snapshotBodies(snapshots::add);
        assertEquals(count, snapshots.size());
    }
}
