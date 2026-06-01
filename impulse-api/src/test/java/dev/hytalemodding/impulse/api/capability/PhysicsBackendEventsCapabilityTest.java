package dev.hytalemodding.impulse.api.capability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBackendEventKind;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PhysicsBackendEventsCapabilityTest {

    @Test
    void mapsSupportedContactPhasesThroughBackendEventKinds() {
        PhysicsBackendEventsCapability capability =
            () -> Set.of(PhysicsBackendEventKind.CONTACT_STARTED, PhysicsBackendEventKind.CONTACT_ENDED);

        assertTrue(capability.supportsContactPhase(PhysicsContactPhase.STARTED));
        assertTrue(capability.supportsContactPhase(PhysicsContactPhase.ENDED));
        assertFalse(capability.supportsContactPhase(PhysicsContactPhase.FORCE));
    }
}
