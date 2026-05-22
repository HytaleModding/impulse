package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsRuntimeStats;
import dev.hytalemodding.impulse.api.PhysicsSolverTuning;
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
    void runtimeStatsExposeNativeSpaceCounters() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody plane = space.createStaticPlane(0.0f);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(0.0f, 4.0f, 0.0f);

        space.addBody(plane);
        space.addBody(body);
        space.step(1.0f / 60.0f);

        PhysicsRuntimeStats stats = space.getRuntimeStats();
        assertTrue(stats.available());
        assertEquals(2, stats.bodyCount());
        assertEquals(2, stats.colliderCount());
        assertTrue(stats.activeBodyCount() >= 1);
        assertEquals(0, stats.jointCount());
    }

    @Test
    void runtimeStatsExposeContactPressureCounters() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody plane = space.createStaticPlane(0.0f);
        plane.setCollisionFilter(PhysicsCollisionFilters.TERRAIN, PhysicsCollisionFilters.ALL);
        space.addBody(plane);

        PhysicsBody bodyA = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        bodyA.setPosition(0.0f, 0.45f, 0.0f);
        bodyA.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
        space.addBody(bodyA);

        PhysicsBody bodyB = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        bodyB.setPosition(0.85f, 0.45f, 0.0f);
        bodyB.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
        space.addBody(bodyB);

        space.step(1.0f / 60.0f);

        PhysicsRuntimeStats stats = space.getRuntimeStats();
        assertTrue(stats.available());
        assertEquals(3, stats.bodyCount());
        assertTrue(stats.contactPairCount() >= 2);
        assertTrue(stats.contactManifoldCount() >= stats.contactPairCount());
        assertTrue(stats.contactPointCount() >= 1);
        assertTrue(stats.dynamicDynamicContactPairCount() >= 1);
        assertTrue(stats.terrainContactPairCount() >= 1);
        assertTrue(stats.activeIslandCount() >= 1);
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

    @Test
    void selectedSnapshotBodiesPreserveCollectedBodiesWhenBufferGrows() {
        PhysicsSpace space = createHeadlessSpace();
        List<PhysicsBody> selectedBodies = new ArrayList<>();
        int count = 128;
        for (int i = 0; i < count; i++) {
            PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
            body.setPosition(i, 5.0f, 0.0f);
            space.addBody(body);
            selectedBodies.add(body);
        }

        List<PhysicsBodySnapshot> snapshots = new ArrayList<>();
        space.snapshotBodies(selectedBodies, _ -> null, snapshots::add);

        assertEquals(count, snapshots.size());
        for (int i = 0; i < count; i++) {
            assertSame(selectedBodies.get(i), snapshots.get(i).body());
        }
    }

    @Test
    void tenThousandDynamicBodyPileWithBodyContactsStepsAndSnapshots() {
        PhysicsSpace space = createHeadlessSpace();
        if (space instanceof PhysicsSolverTuning tuning) {
            tuning.setSolverTuning(1, 1, 1, 1);
        }

        PhysicsBody plane = space.createStaticPlane(0.0f);
        plane.setCollisionFilter(PhysicsCollisionFilters.TERRAIN, PhysicsCollisionFilters.ALL);
        space.addBody(plane);

        int count = 10_000;
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

        stepSpace(space, 10);

        List<PhysicsBodySnapshot> snapshots = new ArrayList<>();
        space.snapshotBodies(snapshots::add);
        assertEquals(count + 1, snapshots.size());
    }

    @Test
    void tenThousandDynamicBodyPileOverVoxelTerrainStepsAndSnapshots() {
        PhysicsSpace space = createHeadlessSpace();
        if (space instanceof PhysicsSolverTuning tuning) {
            tuning.setSolverTuning(1, 1, 1, 1);
        }

        int[] floorVoxels = floorSectionVoxels();
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 2; z++) {
                PhysicsBody terrain = space.createVoxelTerrain(1.0f, 1.0f, 1.0f, floorVoxels);
                terrain.setPosition(x * 16.0f, 0.0f, z * 16.0f);
                terrain.setCollisionFilter(PhysicsCollisionFilters.TERRAIN, PhysicsCollisionFilters.ALL);
                space.addBody(terrain);
            }
        }

        int count = 10_000;
        int side = (int) Math.ceil(Math.cbrt(count));
        for (int i = 0; i < count; i++) {
            PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
            int x = i % side;
            int z = (i / side) % side;
            int y = i / (side * side);
            body.setPosition(-side * 1.05f * 0.5f + x * 1.05f,
                5.0f + y * 1.05f,
                -side * 1.05f * 0.5f + z * 1.05f);
            body.setFriction(0.45f);
            body.setRestitution(0.0f);
            body.setDamping(0.02f, 0.25f);
            body.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
            space.addBody(body);
        }

        stepSpace(space, 10);

        List<PhysicsBodySnapshot> snapshots = new ArrayList<>();
        space.snapshotBodies(snapshots::add);
        assertEquals(count + 16, snapshots.size());
    }

    @Test
    void adjacentDenseVoxelTerrainSectionsCanCombineAndStep() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody first = space.createVoxelTerrain(1.0f, 1.0f, 1.0f, fullSectionVoxels());
        PhysicsBody second = space.createVoxelTerrain(1.0f, 1.0f, 1.0f, fullSectionVoxels());
        first.setPosition(0.0f, 0.0f, 0.0f);
        second.setPosition(16.0f, 0.0f, 0.0f);
        space.addBody(first);
        space.addBody(second);

        space.combineVoxelTerrains(first, second, 16, 0, 0);
        space.step(1.0f / 60.0f);

        assertEquals(2, space.bodyCount());
    }

    @Test
    void denseVoxelTerrainGridCanStepWithoutCombiningSections() {
        PhysicsSpace space = createHeadlessSpace();
        int[] voxels = fullSectionVoxels();
        int sectionsPerAxis = 3;
        for (int x = 0; x < sectionsPerAxis; x++) {
            for (int y = 0; y < sectionsPerAxis; y++) {
                for (int z = 0; z < sectionsPerAxis; z++) {
                    PhysicsBody body = space.createVoxelTerrain(1.0f, 1.0f, 1.0f, voxels);
                    body.setPosition(x * 16.0f, y * 16.0f, z * 16.0f);
                    space.addBody(body);
                }
            }
        }

        space.step(1.0f / 60.0f);

        assertEquals(sectionsPerAxis * sectionsPerAxis * sectionsPerAxis, space.bodyCount());
    }

    @Nonnull
    private static int[] fullSectionVoxels() {
        int[] voxels = new int[16 * 16 * 16 * 3];
        int index = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    voxels[index++] = x;
                    voxels[index++] = y;
                    voxels[index++] = z;
                }
            }
        }
        return voxels;
    }

    @Nonnull
    private static int[] floorSectionVoxels() {
        int[] voxels = new int[16 * 16 * 3];
        int index = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                voxels[index++] = x;
                voxels[index++] = 0;
                voxels[index++] = z;
            }
        }
        return voxels;
    }
}
