package dev.hytalemodding.impulse.core.internal.physics.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhysicsDebugResourceTest {

    @Test
    void contactsDefaultDisabledWhileOtherPhysicsFlagsRemainEnabled() {
        PhysicsDebugResource resource = new PhysicsDebugResource();

        assertTrue(resource.isDebugShapesEnabled());
        assertTrue(resource.isDebugMotionEnabled());
        assertFalse(resource.isDebugContactsEnabled());
        assertTrue(resource.isDebugJointsEnabled());
        assertFalse(resource.isDebugPhysicsChunkCollisionEnabled());
    }

    @Test
    void clonePreservesExplicitPhysicsFlags() {
        PhysicsDebugResource resource = new PhysicsDebugResource();
        resource.setDebugContactsEnabled(true);
        resource.setDebugPhysicsChunkCollisionEnabled(true);

        PhysicsDebugResource copy = resource.clone();

        assertTrue(copy.isDebugContactsEnabled());
        assertTrue(copy.isDebugPhysicsChunkCollisionEnabled());
    }
}
