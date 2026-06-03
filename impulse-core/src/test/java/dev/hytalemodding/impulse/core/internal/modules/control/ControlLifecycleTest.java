package dev.hytalemodding.impulse.core.internal.modules.control;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ControlLifecycleTest {

    @BeforeEach
    @AfterEach
    void disableLifecycle() {
        ControlLifecycle.disable();
    }

    @Test
    void lifecycleStartsDisabled() {
        assertFalse(ControlLifecycle.isEnabled());
    }

    @Test
    void lifecycleGenerationChangesWhenLifecycleIsDisabled() {
        ControlLifecycle.enable();
        long enabledGeneration = ControlLifecycle.generation();

        ControlLifecycle.disable();

        assertTrue(ControlLifecycle.generation() > enabledGeneration);
    }

    @Test
    void disablingLifecycleClearsRegisteredControlledBodies() {
        ControlLifecycle.enable();
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        RigidBodyKey bodyKey = RigidBodyKey.random();
        resource.markBodyControlled(bodyKey);

        assertTrue(resource.isBodyControlled(bodyKey));

        ControlLifecycle.disable();

        assertFalse(resource.isBodyControlled(bodyKey));
    }
}
