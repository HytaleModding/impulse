package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhysicsChunkLifecycleTest {

    @BeforeEach
    @AfterEach
    void disableLifecycle() {
        PhysicsChunkLifecycle.disable();
    }

    @Test
    void lifecycleStartsDisabled() {
        assertFalse(PhysicsChunkLifecycle.isEnabled());
    }

    @Test
    void lifecycleGenerationChangesWhenLifecycleIsDisabled() {
        PhysicsChunkLifecycle.enable();
        long enabledGeneration = PhysicsChunkLifecycle.generation();

        PhysicsChunkLifecycle.disable();

        assertFalse(PhysicsChunkLifecycle.isEnabled());
        assertTrue(PhysicsChunkLifecycle.generation() > enabledGeneration);
    }
}
