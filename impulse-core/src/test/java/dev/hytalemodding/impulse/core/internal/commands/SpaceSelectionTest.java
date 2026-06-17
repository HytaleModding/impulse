package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpaceSelectionTest {

    @Test
    void specifiedSpaceIdReturnsExistingExplicitSpace() {
        PhysicsSpaceCompatibilityIndexResource compatibility = new PhysicsSpaceCompatibilityIndexResource();
        SpaceId spaceId = new SpaceId(12);
        compatibility.putSpace(spaceId, UUID.randomUUID());

        assertEquals(spaceId, SpaceSelection.specifiedSpaceId(compatibility, 12));
    }

    @Test
    void specifiedSpaceIdRejectsInvalidOrMissingSpace() {
        PhysicsSpaceCompatibilityIndexResource compatibility = new PhysicsSpaceCompatibilityIndexResource();
        compatibility.putSpace(new SpaceId(7), UUID.randomUUID());

        assertNull(SpaceSelection.specifiedSpaceId(compatibility, 0));
        assertNull(SpaceSelection.specifiedSpaceId(compatibility, -1));
        assertNull(SpaceSelection.specifiedSpaceId(compatibility, 8));
    }

    @Test
    void firstRegisteredSpaceIdSelectsLowestStableId() {
        PhysicsSpaceCompatibilityIndexResource compatibility = new PhysicsSpaceCompatibilityIndexResource();
        SpaceId higher = new SpaceId(20);
        SpaceId lower = new SpaceId(5);
        compatibility.putSpace(higher, UUID.randomUUID());
        compatibility.putSpace(lower, UUID.randomUUID());

        assertEquals(lower, SpaceSelection.firstRegisteredSpaceId(compatibility));
    }

    @Test
    void firstRegisteredSpaceIdReturnsNullWhenNoSpacesExist() {
        assertNull(SpaceSelection.firstRegisteredSpaceId(new PhysicsSpaceCompatibilityIndexResource()));
    }
}
