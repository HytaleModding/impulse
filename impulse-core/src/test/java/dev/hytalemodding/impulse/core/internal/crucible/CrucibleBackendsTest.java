package dev.hytalemodding.impulse.core.internal.crucible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import java.util.List;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class CrucibleBackendsTest {

    @Test
    void configuredBackendWinsWhenRegistered() {
        BackendId bullet = new BackendId("impulse:bullet");
        BackendId rapier = new BackendId("impulse:rapier");

        assertEquals(bullet, CrucibleBackends.selectBackendId(List.of(
            new TestBackend(bullet),
            new TestBackend(rapier)), "impulse:bullet"));
    }

    @Test
    void rapierIsPreferredWhenMultipleBackendsAreRegistered() {
        BackendId bullet = new BackendId("impulse:bullet");
        BackendId rapier = new BackendId("impulse:rapier");

        assertEquals(rapier, CrucibleBackends.selectBackendId(List.of(
            new TestBackend(bullet),
            new TestBackend(rapier)), null));
    }

    @Test
    void singleBackendIsSelectedWhenRapierIsUnavailable() {
        BackendId bullet = new BackendId("impulse:bullet");

        assertEquals(bullet, CrucibleBackends.selectBackendId(List.of(
            new TestBackend(bullet)), null));
    }

    @Test
    void multipleNonRapierBackendsRequireExplicitConfiguration() {
        assertThrows(IllegalStateException.class, () -> CrucibleBackends.selectBackendId(List.of(
            new TestBackend(new BackendId("impulse:alpha")),
            new TestBackend(new BackendId("impulse:beta"))), null));
    }

    @Test
    void configuredBackendMustBeRegistered() {
        assertThrows(IllegalStateException.class, () -> CrucibleBackends.selectBackendId(List.of(
            new TestBackend(new BackendId("impulse:rapier"))), "impulse:missing"));
    }

    private record TestBackend(@Nonnull BackendId id) implements PhysicsBackend {

        @Nonnull
        @Override
        public BackendId getId() {
            return id;
        }

        @Override
        public void init() {
        }

        @Nonnull
        @Override
        public PhysicsSpace createSpace() {
            throw new UnsupportedOperationException("not used");
        }
    }
}
