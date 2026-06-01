package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SpaceSelectionTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void specifiedSpaceIdReturnsExistingExplicitSpace() {
        Fixture fixture = fixture();
        SpaceId spaceId = new SpaceId(12);
        fixture.resource().createSpace(fixture.backend().getId(),
            spaceId,
            "test-world",
            PhysicsSpaceSettings.defaults());

        assertEquals(spaceId, SpaceSelection.specifiedSpaceId(fixture.resource(), 12));
    }

    @Test
    void specifiedSpaceIdRejectsInvalidOrMissingSpace() {
        Fixture fixture = fixture();
        fixture.resource().createSpace(fixture.backend().getId(),
            new SpaceId(7),
            "test-world",
            PhysicsSpaceSettings.defaults());

        assertNull(SpaceSelection.specifiedSpaceId(fixture.resource(), 0));
        assertNull(SpaceSelection.specifiedSpaceId(fixture.resource(), -1));
        assertNull(SpaceSelection.specifiedSpaceId(fixture.resource(), 8));
    }

    @Test
    void firstRegisteredSpaceIdSelectsLowestStableId() {
        Fixture fixture = fixture();
        SpaceId higher = new SpaceId(20);
        SpaceId lower = new SpaceId(5);
        fixture.resource().createSpace(fixture.backend().getId(),
            higher,
            "test-world",
            PhysicsSpaceSettings.defaults());
        fixture.resource().createSpace(fixture.backend().getId(),
            lower,
            "test-world",
            PhysicsSpaceSettings.defaults());

        assertEquals(lower, SpaceSelection.firstRegisteredSpaceId(fixture.resource()));
    }

    @Test
    void firstRegisteredSpaceIdReturnsNullWhenNoSpacesExist() {
        Fixture fixture = fixture();

        assertNull(SpaceSelection.firstRegisteredSpaceId(fixture.resource()));
    }

    private static Fixture fixture() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:space-selection-"
            + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);
        return new Fixture(backend, new LegacyLiveHandleTestResource());
    }

    private record Fixture(FakePhysicsBackend backend, LegacyLiveHandleTestResource resource) {
    }
}
