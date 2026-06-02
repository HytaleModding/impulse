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
        BackendId bullet = new BackendId("impulse:bullet");
        BackendId rapier = new BackendId("impulse:rapier");

        assertEquals(bullet, CrucibleBackends.selectBackendId(List.of(
            provider(bullet),
            provider(rapier)), "impulse:bullet"));
    }

    @Test
    void rapierIsPreferredWhenMultipleBackendsAreRegistered() {
        BackendId bullet = new BackendId("impulse:bullet");
        BackendId rapier = new BackendId("impulse:rapier");

        assertEquals(rapier, CrucibleBackends.selectBackendId(List.of(
            provider(bullet),
            provider(rapier)), null));
    }

    @Test
    void singleBackendIsSelectedWhenRapierIsUnavailable() {
        BackendId bullet = new BackendId("impulse:bullet");

        assertEquals(bullet, CrucibleBackends.selectBackendId(List.of(
            provider(bullet)), null));
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
