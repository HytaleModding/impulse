package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhysicsDebugResourceTest {

    @Test
    void contactsDefaultDisabledWhileOtherOverlayFlagsRemainEnabled() {
        PhysicsDebugResource resource = new PhysicsDebugResource();

        assertTrue(resource.isDebugShapesEnabled());
        assertTrue(resource.isDebugMotionEnabled());
        assertFalse(resource.isDebugContactsEnabled());
        assertTrue(resource.isDebugJointsEnabled());
    }

    @Test
    void clonePreservesExplicitContactFlag() {
        PhysicsDebugResource resource = new PhysicsDebugResource();
        resource.setDebugContactsEnabled(true);

        PhysicsDebugResource copy = resource.clone();

        assertTrue(copy.isDebugContactsEnabled());
    }
}
