package dev.hytalemodding.impulse.core.plugin.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RigidBodySpawnSettingsTest {

    @Test
    void fromOptionalValuesRequiresCollisionFilterPair() {
        assertThrows(IllegalArgumentException.class,
            () -> RigidBodySpawnSettings.fromOptionalValues(null,
                null,
                null,
                null,
                7,
                null,
                null));
        assertThrows(IllegalArgumentException.class,
            () -> RigidBodySpawnSettings.fromOptionalValues(null,
                null,
                null,
                null,
                null,
                11,
                null));
    }

    @Test
    void fromOptionalValuesCopiesPresentCollisionFilterPair() {
        RigidBodySpawnSettings settings = RigidBodySpawnSettings.fromOptionalValues(null,
            null,
            null,
            null,
            7,
            11,
            null);

        assertTrue(settings.hasCollisionFilter());
        assertEquals(7, settings.collisionGroup());
        assertEquals(11, settings.collisionMask());
    }
}
