package dev.hytalemodding.impulse.examples.explosive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.entity.ExplosionConfig;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ExplosiveBlockRuntimeTest {

    @Test
    void explosionConfigDamagesEntitiesWithoutConsumingTerrain() throws ReflectiveOperationException {
        ExplosionConfig config = ExplosiveBlockRuntime.explosionConfig(4);

        assertFalse(booleanField(config, "damageBlocks"));
        assertTrue(booleanField(config, "damageEntities"));
        assertEquals(0, intField(config, "blockDamageRadius"));
        assertEquals(4.0f, floatField(config, "entityDamageRadius"), 0.0001f);
        assertEquals(0.0f, floatField(config, "blockDropChance"), 0.0001f);
        assertEquals("SFX_Goblin_Lobber_Bomb_Death", objectField(config, "soundEventId"));

        ModelParticle[] particles = (ModelParticle[]) objectField(config, "particles");
        assertEquals(1, particles.length);
        assertEquals("Explosion_Medium", particles[0].getSystemId());
        assertEquals(EntityPart.Entity, particles[0].getTargetEntityPart());
    }

    @Test
    void chunkBlockCoordinateConvertsWorldCoordinateToLocalCoordinate() {
        assertEquals(0, ExplosiveBlockRuntime.chunkBlockCoordinate(32));
        assertEquals(15, ExplosiveBlockRuntime.chunkBlockCoordinate(47));
        assertEquals(31, ExplosiveBlockRuntime.chunkBlockCoordinate(-1));
    }

    @Test
    void sourceExplosionCenterBiasesAbovePhysicsBodyCenter() {
        Vector3d center = ExplosiveBlockRuntime.sourceExplosionCenter(
            new Vector3d(12.5, 40.5, -3.5));

        assertEquals(new Vector3d(12.5, 41.5, -3.5), center);
    }

    @Test
    void contactExplosionCenterStartsAboveTerrainContactPoint() {
        Vector3d center = ExplosiveBlockRuntime.contactExplosionCenter(
            new Vector3d(12.5, 41.0, -3.5));

        assertEquals(new Vector3d(12.5, 41.5, -3.5), center);
    }

    @Test
    void fragmentOffsetsAreNearestFirstSphereInsteadOfBottomFirstScan() {
        List<ExplosiveBlockRuntime.FragmentOffset> offsets =
            ExplosiveBlockRuntime.sphericalFragmentOffsets(2);

        assertEquals(new ExplosiveBlockRuntime.FragmentOffset(0, 0, 0, 0), offsets.getFirst());
        for (int i = 1; i < offsets.size(); i++) {
            assertTrue(offsets.get(i - 1).distanceSquared() <= offsets.get(i).distanceSquared());
        }

        List<ExplosiveBlockRuntime.FragmentOffset> firstSeven = offsets.subList(0, 7);
        assertTrue(firstSeven.stream().anyMatch(offset -> offset.dy() > 0));
        assertTrue(firstSeven.stream().anyMatch(offset -> offset.dy() < 0));
        assertTrue(firstSeven.stream().noneMatch(offset -> offset.dy() < -1));
    }

    @Test
    void largeSphereHasEnoughOffsetsForBigExplosionFragmentCap() {
        List<ExplosiveBlockRuntime.FragmentOffset> offsets =
            ExplosiveBlockRuntime.sphericalFragmentOffsets(8);

        assertTrue(offsets.size() >= 1024);
    }

    @Test
    void faceConnectedFragmentsBecomeOneAabbGroup() {
        List<ExplosiveBlockRuntime.FragmentGroup> groups = ExplosiveBlockRuntime.groupFragments(
            List.of(
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 0, 10, 0),
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 1, 10, 0),
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 2, 10, 0)
            ),
            new Vector3d(1.5, 10.5, 0.5),
            8);

        assertEquals(1, groups.size());
        ExplosiveBlockRuntime.FragmentGroup group = groups.getFirst();
        assertEquals("Hytale:block/stone", group.blockType());
        assertEquals(3, group.blockCount());
        assertEquals(new Vector3d(1.5, 10.5, 0.5), group.center());
        assertEquals(1.5f, group.halfExtentX(), 0.0001f);
        assertEquals(0.5f, group.halfExtentY(), 0.0001f);
        assertEquals(0.5f, group.halfExtentZ(), 0.0001f);
        assertEquals(3.0f, group.mass(), 0.0001f);
    }

    @Test
    void groupedFragmentVisualsUseBlockBasePositionsAndSyncOffsets() {
        List<ExplosiveBlockRuntime.FragmentGroup> groups = ExplosiveBlockRuntime.groupFragments(
            List.of(
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 0, 10, 0),
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 1, 10, 0),
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 2, 10, 0)
            ),
            new Vector3d(1.5, 10.5, 0.5),
            8);

        List<ExplosiveBlockRuntime.FragmentVisual> visuals = groups.getFirst().visualBlocks();

        assertEquals(3, visuals.size());
        assertVectorEquals(new Vector3d(1.5, 10.0, 0.5), visuals.getFirst().position());
        assertVectorEquals(new Vector3f(0.0f, 0.0f, 0.0f), visuals.get(0).localPositionOffset());
        assertVisualSyncsToSpawnPosition(groups.getFirst(), visuals.get(0));
        assertVectorEquals(new Vector3d(0.5, 10.0, 0.5), visuals.get(1).position());
        assertVectorEquals(new Vector3f(-1.0f, 0.0f, 0.0f), visuals.get(1).localPositionOffset());
        assertVisualSyncsToSpawnPosition(groups.getFirst(), visuals.get(1));
        assertVectorEquals(new Vector3d(2.5, 10.0, 0.5), visuals.get(2).position());
        assertVectorEquals(new Vector3f(1.0f, 0.0f, 0.0f), visuals.get(2).localPositionOffset());
        assertVisualSyncsToSpawnPosition(groups.getFirst(), visuals.get(2));
    }

    @Test
    void groupedFragmentVisualsUseBlockTypeCenterForVisualBase() {
        List<ExplosiveBlockRuntime.FragmentGroup> groups = ExplosiveBlockRuntime.groupFragments(
            List.of(new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/offset",
                4,
                10,
                -2,
                new Vector3d(0.25, 0.5, 0.75))),
            new Vector3d(4.5, 10.5, -1.5),
            8);

        List<ExplosiveBlockRuntime.FragmentVisual> visuals = groups.getFirst().visualBlocks();

        assertEquals(1, visuals.size());
        assertVectorEquals(new Vector3d(4.25, 10.0, -1.25), visuals.getFirst().position());
        assertVectorEquals(new Vector3f(-0.25f, 0.0f, 0.25f),
            visuals.getFirst().localPositionOffset());
    }

    @Test
    void blockRotationUsesHytaleRotationTupleIndex() {
        RotationTuple rotationTuple = RotationTuple.of(Rotation.Ninety,
            Rotation.OneEighty,
            Rotation.TwoSeventy);
        Rotation3f hytaleRotation = new Rotation3f();
        rotationTuple.applyRotationTo(hytaleRotation);
        Quaterniond expected = hytaleRotation.getQuaternion(new Quaterniond());

        assertQuaternionEquals(expected, ExplosiveBlockRuntime.blockRotation(rotationTuple.index()));
    }

    @Test
    void groupedFragmentVisualsPreserveBlockLocalRotation() {
        Quaternionf localRotation = new Quaternionf().rotateXYZ(0.25f, 0.5f, 0.75f);
        List<ExplosiveBlockRuntime.FragmentGroup> groups = ExplosiveBlockRuntime.groupFragments(
            List.of(new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone",
                0,
                10,
                0,
                new Vector3d(0.5, 0.5, 0.5),
                localRotation)),
            new Vector3d(0.5, 10.5, 0.5),
            8);

        ExplosiveBlockRuntime.FragmentVisual visual = groups.getFirst().visualBlocks().getFirst();

        assertQuaternionEquals(localRotation, visual.localRotationOffset());
        visual.localRotationOffset().identity();
        assertQuaternionEquals(localRotation, visual.localRotationOffset());
    }

    @Test
    void verticalGroupedFragmentsKeepBlockHeightVisualSpacing() {
        List<ExplosiveBlockRuntime.FragmentGroup> groups = ExplosiveBlockRuntime.groupFragments(
            List.of(
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 0, 10, 0),
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 0, 11, 0)
            ),
            new Vector3d(0.5, 10.5, 0.5),
            8);

        List<ExplosiveBlockRuntime.FragmentVisual> visuals = groups.getFirst().visualBlocks();

        assertEquals(2, visuals.size());
        assertVectorEquals(new Vector3d(0.5, 10.0, 0.5), visuals.getFirst().position());
        assertVectorEquals(new Vector3f(0.0f, -0.5f, 0.0f), visuals.get(0).localPositionOffset());
        assertVisualSyncsToSpawnPosition(groups.getFirst(), visuals.get(0));
        assertVectorEquals(new Vector3d(0.5, 11.0, 0.5), visuals.get(1).position());
        assertVectorEquals(new Vector3f(0.0f, 0.5f, 0.0f), visuals.get(1).localPositionOffset());
        assertVisualSyncsToSpawnPosition(groups.getFirst(), visuals.get(1));
    }

    @Test
    void verticalGroupedFragmentVisualCentersRotateAroundGroupCenter() {
        ExplosiveBlockRuntime.FragmentGroup group = ExplosiveBlockRuntime.groupFragments(
            List.of(
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 0, 10, 0),
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 0, 11, 0)
            ),
            new Vector3d(0.5, 10.5, 0.5),
            8).getFirst();
        List<ExplosiveBlockRuntime.FragmentVisual> visuals = group.visualBlocks();
        Quaternionf rotation = new Quaternionf().rotateZ((float) (Math.PI / 2.0));

        assertVisualCenterAfterSyncEqualsRotatedLocalCenter(group,
            visuals.get(0),
            rotation,
            new Vector3d(1.0, 11.0, 0.5));
        assertVisualCenterAfterSyncEqualsRotatedLocalCenter(group,
            visuals.get(1),
            rotation,
            new Vector3d(0.0, 11.0, 0.5));
    }

    @Test
    void disconnectedFragmentsRemainSeparateGroups() {
        List<ExplosiveBlockRuntime.FragmentGroup> groups = ExplosiveBlockRuntime.groupFragments(
            List.of(
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 0, 10, 0),
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/dirt", 4, 10, 0)
            ),
            new Vector3d(0.5, 10.5, 0.5),
            8);

        assertEquals(2, groups.size());
        assertEquals(new Vector3d(0.5, 10.5, 0.5), groups.get(0).center());
        assertEquals(new Vector3d(4.5, 10.5, 0.5), groups.get(1).center());
    }

    @Test
    void largeConnectedComponentsSplitIntoBoundedGroups() {
        List<ExplosiveBlockRuntime.FragmentBlock> fragments = new ArrayList<>();
        for (int x = 0; x < 40; x++) {
            fragments.add(new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", x, 10, 0));
        }

        List<ExplosiveBlockRuntime.FragmentGroup> groups = ExplosiveBlockRuntime.groupFragments(
            fragments,
            new Vector3d(0.5, 10.5, 0.5),
            8);

        assertTrue(groups.size() > 1);
        assertEquals(40, groups.stream().mapToInt(ExplosiveBlockRuntime.FragmentGroup::blockCount).sum());
        assertTrue(groups.stream()
            .allMatch(group -> group.blockCount() <= ExplosiveBlockRuntime.MAX_BLOCKS_PER_FRAGMENT_GROUP));
    }

    @Test
    void irregularComponentsSplitIntoSolidAabbGroups() {
        List<ExplosiveBlockRuntime.FragmentGroup> groups = ExplosiveBlockRuntime.groupFragments(
            List.of(
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 0, 10, 0),
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 1, 10, 0),
                new ExplosiveBlockRuntime.FragmentBlock("Hytale:block/stone", 0, 10, 1)
            ),
            new Vector3d(0.5, 10.5, 0.5),
            8);

        assertEquals(3, groups.stream().mapToInt(ExplosiveBlockRuntime.FragmentGroup::blockCount).sum());
        assertTrue(groups.size() > 1);
        assertTrue(groups.stream()
            .allMatch(group -> group.aabbBlockVolume() == group.blockCount()));
    }

    @Test
    void terrainFragmentsDoNotCarryLandingExplosionState() {
        assertNull(ExplosiveBlockRuntime.fragmentLandingExplosionState());
    }

    private static boolean booleanField(ExplosionConfig config, String fieldName)
        throws ReflectiveOperationException {
        Field field = field(fieldName);
        return field.getBoolean(config);
    }

    private static int intField(ExplosionConfig config, String fieldName)
        throws ReflectiveOperationException {
        Field field = field(fieldName);
        return field.getInt(config);
    }

    private static float floatField(ExplosionConfig config, String fieldName)
        throws ReflectiveOperationException {
        Field field = field(fieldName);
        return field.getFloat(config);
    }

    private static Object objectField(ExplosionConfig config, String fieldName)
        throws ReflectiveOperationException {
        Field field = field(fieldName);
        return field.get(config);
    }

    private static Field field(String fieldName) throws ReflectiveOperationException {
        Field field = ExplosionConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private static void assertVectorEquals(@Nonnull Vector3d expected, @Nonnull Vector3d actual) {
        assertEquals(expected.x, actual.x, 0.0001);
        assertEquals(expected.y, actual.y, 0.0001);
        assertEquals(expected.z, actual.z, 0.0001);
    }

    private static void assertVectorEquals(@Nonnull Vector3f expected, @Nonnull Vector3f actual) {
        assertEquals(expected.x, actual.x, 0.0001f);
        assertEquals(expected.y, actual.y, 0.0001f);
        assertEquals(expected.z, actual.z, 0.0001f);
    }

    private static void assertQuaternionEquals(@Nonnull Quaterniond expected,
        @Nonnull Quaternionf actual) {
        assertEquals(expected.x, actual.x, 0.0001f);
        assertEquals(expected.y, actual.y, 0.0001f);
        assertEquals(expected.z, actual.z, 0.0001f);
        assertEquals(expected.w, actual.w, 0.0001f);
    }

    private static void assertQuaternionEquals(@Nonnull Quaternionf expected,
        @Nonnull Quaternionf actual) {
        assertEquals(expected.x, actual.x, 0.0001f);
        assertEquals(expected.y, actual.y, 0.0001f);
        assertEquals(expected.z, actual.z, 0.0001f);
        assertEquals(expected.w, actual.w, 0.0001f);
    }

    private static void assertVisualSyncsToSpawnPosition(
        ExplosiveBlockRuntime.FragmentGroup group,
        ExplosiveBlockRuntime.FragmentVisual visual) {
        Vector3d center = group.center();
        Vector3f offset = visual.localPositionOffset();
        assertVectorEquals(visual.position(), new Vector3d(
            center.x + offset.x,
            center.y - visual.visualOriginOffsetY() + offset.y,
            center.z + offset.z));
    }

    private static void assertVisualCenterAfterSyncEqualsRotatedLocalCenter(
        ExplosiveBlockRuntime.FragmentGroup group,
        ExplosiveBlockRuntime.FragmentVisual visual,
        Quaternionf rotation,
        Vector3d expectedCenter) {
        Vector3f rotatedOffset = rotation.transform(visual.localPositionOffset(), new Vector3f());
        Vector3d syncedBasePosition = new Vector3d(group.center())
            .add(rotatedOffset.x, rotatedOffset.y, rotatedOffset.z)
            .sub(0.0, visual.visualOriginOffsetY(), 0.0);
        assertVectorEquals(expectedCenter, syncedBasePosition.add(0.0, visual.visualOriginOffsetY(), 0.0));
    }

}
