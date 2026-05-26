package dev.hytalemodding.impulse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import java.util.List;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class ImpulsePluginBackendSelectionTest {

    @Test
    void singleDiscoveredBackendIsDefaultBackend() {
        BackendId backendId = new BackendId("impulse:rapier");

        assertEquals(backendId, ImpulsePlugin.selectDefaultBackendId(List.of(
            new TestBackend(backendId))));
    }

    @Test
    void multipleDiscoveredBackendsRequireExplicitSelection() {
        assertNull(ImpulsePlugin.selectDefaultBackendId(List.of(
            new TestBackend(new BackendId("impulse:bullet")),
            new TestBackend(new BackendId("impulse:rapier")))));
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
