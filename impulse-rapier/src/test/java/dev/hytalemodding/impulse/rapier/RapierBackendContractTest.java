package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsRuntimeStats;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsVoxelTerrainCapability;
import dev.hytalemodding.impulse.api.testsupport.PhysicsBackendContractTest;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
    void rapierSupportsVoxelTerrainMetadataAndBodyTypeSwitching() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsVoxelTerrainCapability voxelTerrain = requiredCapability(space, PhysicsVoxelTerrainCapability.class);

        assertTrue(space.getCapability(PhysicsContinuousCollisionCapability.class).isPresent());

        RapierBody terrain = (RapierBody) voxelTerrain.createVoxelTerrain(1.0f,
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
    void rapierTuningCapabilitiesApplyAndGuardClosedSpace() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsSolverTuningCapability solverTuning = requiredCapability(space, PhysicsSolverTuningCapability.class);
        PhysicsActivationTuningCapability activationTuning = requiredCapability(space,
            PhysicsActivationTuningCapability.class);

        solverTuning.setSolverTuning(new PhysicsSolverTuning(1, 1));
        activationTuning.setActivationTuning(new PhysicsActivationTuning(0.01f, 0.01f, 0.1f));

        space.close();

        assertThrows(IllegalStateException.class,
            () -> solverTuning.setSolverTuning(new PhysicsSolverTuning(1, 1)));
        assertThrows(IllegalStateException.class,
            () -> activationTuning.setActivationTuning(new PhysicsActivationTuning(0.01f,
                0.01f,
                0.1f)));
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
    void rapierJointStoresGeometryAsPrimitiveFields() {
        int floatFields = 0;
        for (Field field : RapierJoint.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertNotSame(Vector3f.class, field.getType(),
                "Rapier joints should not retain Vector3f wrappers on native joint paths");
            if (field.getType() == float.class) {
                floatFields++;
            }
        }

        assertTrue(floatFields >= 9,
            "Rapier joints should retain anchors and axis as primitive floats");
    }

    @Test
    void raycastSeesStaticPlaneImmediatelyAfterAddBody() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsBody plane = space.createStaticPlane(0.0f);
        space.addBody(plane);

        var hits = space.raycastAll(new Vector3f(0.0f, 10.0f, 0.0f),
            new Vector3f(0.0f, -10.0f, 0.0f));

        assertFalse(hits.isEmpty());
        assertSame(plane, hits.getFirst().body());
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
        PhysicsBody representativeBody = null;
        for (int i = 0; i < count; i++) {
            PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
            if (i == 0) {
                representativeBody = body;
            }
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
        PhysicsBody representative = Objects.requireNonNull(representativeBody, "representativeBody");
        float startY = representative.getPosition().y;

        stepSpace(space, 30);

        assertTrue(representative.getPosition().y < startY - 0.01f,
            "Representative dynamic body should move under gravity");
        List<PhysicsBodySnapshot> snapshots = new ArrayList<>();
        space.snapshotBodies(snapshots::add);
        assertEquals(count, snapshots.size());
        assertEquals((long) count, countSnapshotsOfShape(snapshots, ShapeType.BOX));
        assertDynamicSnapshotValues(snapshots, count);
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
        List<PhysicsBody> snapshotBodies = new ArrayList<>();
        space.snapshotBodies(selectedBodies, _ -> null, (body, snapshot) -> {
            snapshotBodies.add(body);
            snapshots.add(snapshot);
        });

        assertEquals(count, snapshots.size());
        for (int i = 0; i < count; i++) {
            assertSame(selectedBodies.get(i), snapshotBodies.get(i));
        }
    }

    @Test
    void nativeStepRejectsStaleSpaceHandle() {
        RapierNative.load(null);
        long handle = RapierNative.createSpaceNative();
        assertTrue(handle > 0L);

        RapierNative.destroySpaceNative(handle);

        assertThrows(IllegalStateException.class, () -> RapierNative.stepNative(handle, 1.0f / 60.0f));
    }

    @Test
    void nativeSnapshotRejectsStaleBodyHandleWithoutWritingZeros() {
        RapierNative.load(null);
        long handle = RapierNative.createSpaceNative();
        assertTrue(handle > 0L);
        float[] out = new float[16];
        float[] expected = new float[16];
        Arrays.fill(out, -7.0f);
        Arrays.fill(expected, -7.0f);

        try {
            assertThrows(IllegalStateException.class,
                () -> RapierNative.snapshotBodiesNative(handle, new long[]{123L}, 1, out));
            assertArrayEquals(expected, out, 0.0f);
        } finally {
            RapierNative.destroySpaceNative(handle);
        }
    }

    @Test
    void nativeAttachedBodyMutatorsRejectStaleBodyHandle() {
        RapierNative.load(null);
        long handle = RapierNative.createSpaceNative();
        assertTrue(handle > 0L);

        try {
            assertThrows(IllegalStateException.class,
                () -> RapierNative.setBodyPositionNative(handle, 123L, 1.0f, 2.0f, 3.0f));
            assertThrows(IllegalStateException.class,
                () -> RapierNative.setBodyFrictionNative(handle, 123L, 0.5f));
        } finally {
            RapierNative.destroySpaceNative(handle);
        }
    }

    @Test
    void tenThousandDynamicBodyPileWithBodyContactsStepsAndSnapshots() {
        PhysicsSpace space = createHeadlessSpace();
        requiredCapability(space, PhysicsSolverTuningCapability.class)
            .setSolverTuning(new PhysicsSolverTuning(1, 1));

        PhysicsBody plane = space.createStaticPlane(0.0f);
        plane.setCollisionFilter(PhysicsCollisionFilters.TERRAIN, PhysicsCollisionFilters.ALL);
        space.addBody(plane);

        int count = 10_000;
        PhysicsBody contactSentinelA = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        contactSentinelA.setPosition(0.0f, 0.45f, 0.0f);
        contactSentinelA.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
        space.addBody(contactSentinelA);

        PhysicsBody contactSentinelB = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        contactSentinelB.setPosition(0.85f, 0.45f, 0.0f);
        contactSentinelB.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
        space.addBody(contactSentinelB);

        int side = (int) Math.ceil(Math.cbrt(count));
        PhysicsBody representativeBody = null;
        for (int i = 0; i < count - 2; i++) {
            PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
            if (i == 0) {
                representativeBody = body;
            }
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

        PhysicsBody representative = Objects.requireNonNull(representativeBody, "representativeBody");
        float startY = representative.getPosition().y;
        stepSpace(space, 10);

        assertTrue(representative.getPosition().y < startY - 0.01f,
            "Representative dynamic body should move under gravity");
        PhysicsRuntimeStats stats = space.getRuntimeStats();
        assertTrue(stats.available());
        assertTrue(stats.terrainContactPairCount() >= 1);
        assertTrue(stats.dynamicDynamicContactPairCount() >= 1);
        assertTrue(stats.contactPointCount() >= 1);
        List<PhysicsBodySnapshot> snapshots = new ArrayList<>();
        space.snapshotBodies(snapshots::add);
        assertEquals(count + 1, snapshots.size());
        assertEquals((long) count, countSnapshotsOfShape(snapshots, ShapeType.BOX));
        assertEquals(1L, countSnapshotsOfShape(snapshots, ShapeType.PLANE));
        assertDynamicSnapshotValues(snapshots, count);
    }

    @Test
    void tenThousandDynamicBodyPileOverVoxelTerrainStepsAndSnapshots() {
        PhysicsSpace space = createHeadlessSpace();
        requiredCapability(space, PhysicsSolverTuningCapability.class)
            .setSolverTuning(new PhysicsSolverTuning(1, 1));
        PhysicsVoxelTerrainCapability voxelTerrain = requiredCapability(space, PhysicsVoxelTerrainCapability.class);

        int[] floorVoxels = floorSectionVoxels();
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 2; z++) {
                PhysicsBody terrain = voxelTerrain.createVoxelTerrain(1.0f, 1.0f, 1.0f, floorVoxels);
                terrain.setPosition(x * 16.0f, 0.0f, z * 16.0f);
                terrain.setCollisionFilter(PhysicsCollisionFilters.TERRAIN, PhysicsCollisionFilters.ALL);
                space.addBody(terrain);
            }
        }

        var terrainHit = space.raycastClosest(new Vector3f(0.5f, 10.0f, 0.5f),
            new Vector3f(0.0f, -20.0f, 0.0f));
        assertTrue(terrainHit.isPresent());
        assertEquals(ShapeType.VOXELS, terrainHit.orElseThrow().body().getShapeType());

        int count = 10_000;
        PhysicsBody contactSentinel = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        contactSentinel.setPosition(0.5f, 0.45f, 0.5f);
        contactSentinel.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
        space.addBody(contactSentinel);

        int side = (int) Math.ceil(Math.cbrt(count));
        PhysicsBody representativeBody = null;
        for (int i = 0; i < count - 1; i++) {
            PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
            if (i == 0) {
                representativeBody = body;
            }
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

        PhysicsBody representative = Objects.requireNonNull(representativeBody, "representativeBody");
        float startY = representative.getPosition().y;
        stepSpace(space, 10);

        assertTrue(representative.getPosition().y < startY - 0.01f,
            "Representative dynamic body should move under gravity");
        PhysicsRuntimeStats stats = space.getRuntimeStats();
        assertTrue(stats.available());
        assertTrue(stats.terrainContactPairCount() >= 1);
        assertTrue(stats.contactPointCount() >= 1);
        List<PhysicsBodySnapshot> snapshots = new ArrayList<>();
        space.snapshotBodies(snapshots::add);
        assertEquals(count + 16, snapshots.size());
        assertEquals((long) count, countSnapshotsOfShape(snapshots, ShapeType.BOX));
        assertEquals(16L, countSnapshotsOfShape(snapshots, ShapeType.VOXELS));
        assertDynamicSnapshotValues(snapshots, count);
    }

    @Test
    void adjacentDenseVoxelTerrainSectionsCanCombineAndStep() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsVoxelTerrainCapability voxelTerrain = requiredCapability(space, PhysicsVoxelTerrainCapability.class);
        PhysicsBody first = voxelTerrain.createVoxelTerrain(1.0f, 1.0f, 1.0f, fullSectionVoxels());
        PhysicsBody second = voxelTerrain.createVoxelTerrain(1.0f, 1.0f, 1.0f, fullSectionVoxels());
        first.setPosition(0.0f, 0.0f, 0.0f);
        second.setPosition(16.0f, 0.0f, 0.0f);
        space.addBody(first);
        space.addBody(second);

        voxelTerrain.combineVoxelTerrains(first, second, 16, 0, 0);
        space.step(1.0f / 60.0f);

        assertEquals(2, space.bodyCount());
    }

    @Test
    void denseVoxelTerrainGridCanStepWithoutCombiningSections() {
        PhysicsSpace space = createHeadlessSpace();
        PhysicsVoxelTerrainCapability voxelTerrain = requiredCapability(space, PhysicsVoxelTerrainCapability.class);
        int[] voxels = fullSectionVoxels();
        int sectionsPerAxis = 3;
        for (int x = 0; x < sectionsPerAxis; x++) {
            for (int y = 0; y < sectionsPerAxis; y++) {
                for (int z = 0; z < sectionsPerAxis; z++) {
                    PhysicsBody body = voxelTerrain.createVoxelTerrain(1.0f, 1.0f, 1.0f, voxels);
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

    private static long countSnapshotsOfShape(@Nonnull List<PhysicsBodySnapshot> snapshots,
        @Nonnull ShapeType shapeType) {
        return snapshots.stream()
            .filter(snapshot -> snapshot.shapeType() == shapeType)
            .count();
    }

    private static void assertDynamicSnapshotValues(@Nonnull List<PhysicsBodySnapshot> snapshots,
        int expectedDynamicCount) {
        long dynamicSnapshots = snapshots.stream()
            .filter(snapshot -> snapshot.bodyType() == PhysicsBodyType.DYNAMIC)
            .count();
        assertEquals((long) expectedDynamicCount, dynamicSnapshots);

        long finiteDynamicSnapshots = snapshots.stream()
            .filter(snapshot -> snapshot.bodyType() == PhysicsBodyType.DYNAMIC)
            .filter(snapshot -> isFinite(snapshot.position()))
            .filter(snapshot -> isFinite(snapshot.linearVelocity()))
            .count();
        assertEquals((long) expectedDynamicCount, finiteDynamicSnapshots);

        long fallingDynamicSnapshots = snapshots.stream()
            .filter(snapshot -> snapshot.bodyType() == PhysicsBodyType.DYNAMIC)
            .filter(snapshot -> snapshot.linearVelocity().y < -0.001f)
            .count();
        assertTrue(fallingDynamicSnapshots > expectedDynamicCount * 9L / 10L);
    }

    private static boolean isFinite(@Nonnull Vector3f vector) {
        return Float.isFinite(vector.x) && Float.isFinite(vector.y) && Float.isFinite(vector.z);
    }

    @Nonnull
    private static <T extends PhysicsCapability> T requiredCapability(@Nonnull PhysicsSpace space,
        @Nonnull Class<T> capabilityType) {
        return space.getCapability(capabilityType)
            .orElseThrow(() -> new AssertionError("Missing capability: " + capabilityType.getSimpleName()));
    }
}
