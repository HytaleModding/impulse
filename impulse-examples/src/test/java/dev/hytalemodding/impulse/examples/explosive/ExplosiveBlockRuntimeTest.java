package dev.hytalemodding.impulse.examples.explosive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.server.core.entity.ExplosionConfig;
import java.lang.reflect.Field;
import org.joml.Vector3d;
import org.joml.Vector3i;
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
    }

    @Test
    void chunkBlockCoordinateConvertsWorldCoordinateToLocalCoordinate() {
        assertEquals(0, ExplosiveBlockRuntime.chunkBlockCoordinate(32));
        assertEquals(15, ExplosiveBlockRuntime.chunkBlockCoordinate(47));
        assertEquals(31, ExplosiveBlockRuntime.chunkBlockCoordinate(-1));
    }

    @Test
    void supportBlockPositionChecksBlockBelowBodyCenter() {
        Vector3i block = ExplosiveBlockRuntime.supportBlockPosition(new Vector3d(12.25, 8.5, -2.25));

        assertEquals(new Vector3i(12, 7, -3), block);
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

    private static Field field(String fieldName) throws ReflectiveOperationException {
        Field field = ExplosionConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }
}
