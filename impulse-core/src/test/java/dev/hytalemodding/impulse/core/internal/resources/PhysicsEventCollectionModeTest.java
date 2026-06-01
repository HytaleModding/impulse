package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import org.junit.jupiter.api.Test;

class PhysicsEventCollectionModeTest {

    @Test
    void parsesSerializedNamesCaseInsensitively() {
        assertEquals(PhysicsEventCollectionMode.DISABLED,
            PhysicsEventCollectionMode.parse(" disabled "));
        assertEquals(PhysicsEventCollectionMode.CONTACTS,
            PhysicsEventCollectionMode.parse("CONTACTS"));
    }

    @Test
    void rejectsUnknownSerializedNames() {
        assertThrows(IllegalArgumentException.class,
            () -> PhysicsEventCollectionMode.parse("forces"));
    }
}
