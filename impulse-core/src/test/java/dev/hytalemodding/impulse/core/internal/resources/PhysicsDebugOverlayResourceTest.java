package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import dev.hytalemodding.impulse.core.internal.modules.physicsentity.resources.PhysicsDebugOverlayResource;
import org.junit.jupiter.api.Test;

class PhysicsDebugOverlayResourceTest {

    @Test
    void clonePreservesSubscribersCadenceAndBudgets() {
        UUID subscriberUuid = UUID.randomUUID();
        PhysicsDebugOverlayResource resource = new PhysicsDebugOverlayResource();
        resource.addSubscriber(subscriberUuid);
        resource.setOverlayRefreshSeconds(0.25f);
        resource.setPhysicsChunkRefreshSeconds(0.5f);
        resource.setViewRadius(48.0);
        resource.setMaxBodies(32);
        resource.setMaxContacts(16);
        resource.setMaxJoints(12);
        resource.setMaxPhysicsChunkSections(8);
        resource.setMaxPhysicsChunkBoxes(64);

        PhysicsDebugOverlayResource copy = resource.clone();

        assertTrue(copy.getSubscriberUuids().contains(subscriberUuid));
        assertEquals(0.25f, copy.getOverlayRefreshSeconds());
        assertEquals(0.5f, copy.getPhysicsChunkRefreshSeconds());
        assertEquals(48.0, copy.getViewRadius());
        assertEquals(32, copy.getMaxBodies());
        assertEquals(16, copy.getMaxContacts());
        assertEquals(12, copy.getMaxJoints());
        assertEquals(8, copy.getMaxPhysicsChunkSections());
        assertEquals(64, copy.getMaxPhysicsChunkBoxes());
    }
}
