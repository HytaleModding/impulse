package dev.hytalemodding.impulse.core.internal.crucible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrucibleBackendsTest {

    @Test
    void configuredBackendWinsWhenRegistered() {
        BackendId jolt = new BackendId("impulse:jolt");
        BackendId rapier = new BackendId("impulse:rapier");

        assertEquals(jolt, CrucibleBackends.selectBackendId(List.of(
            provider(jolt),
            provider(rapier)), "impulse:jolt"));
    }

    @Test
    void rapierIsPreferredWhenMultipleBackendsAreRegistered() {
        BackendId jolt = new BackendId("impulse:jolt");
        BackendId rapier = new BackendId("impulse:rapier");

        assertEquals(rapier, CrucibleBackends.selectBackendId(List.of(
            provider(jolt),
            provider(rapier)), null));
    }

    @Test
    void singleBackendIsSelectedWhenRapierIsUnavailable() {
        BackendId jolt = new BackendId("impulse:jolt");

        assertEquals(jolt, CrucibleBackends.selectBackendId(List.of(
            provider(jolt)), null));
    }

    @Test
    void multipleNonRapierBackendsRequireExplicitConfiguration() {
        assertThrows(IllegalStateException.class, () -> CrucibleBackends.selectBackendId(List.of(
            provider(new BackendId("impulse:alpha")),
            provider(new BackendId("impulse:beta"))), null));
    }

    @Test
    void configuredBackendMustBeRegistered() {
        assertThrows(IllegalStateException.class, () -> CrucibleBackends.selectBackendId(List.of(
            provider(new BackendId("impulse:rapier"))), "impulse:missing"));
    }

    private static FakePhysicsBackendRuntimeProvider provider(BackendId id) {
        return new FakePhysicsBackendRuntimeProvider(id, false, false);
    }
}
