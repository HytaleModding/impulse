package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorldCollisionLifecycleTest {

    @BeforeEach
    @AfterEach
    void disableLifecycle() {
        WorldCollisionLifecycle.disable();
    }

    @Test
    void lifecycleStartsDisabled() {
        assertFalse(WorldCollisionLifecycle.isEnabled());
    }

    @Test
    void lifecycleGenerationChangesWhenLifecycleIsDisabled() {
        WorldCollisionLifecycle.enable();
        long enabledGeneration = WorldCollisionLifecycle.generation();

        WorldCollisionLifecycle.disable();

        assertFalse(WorldCollisionLifecycle.isEnabled());
        assertTrue(WorldCollisionLifecycle.generation() > enabledGeneration);
    }
}
