package dev.hytalemodding.impulse.core.internal.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SubPluginLifecycleGateTest {

    @Test
    void gateStartsDisabledAndFailsFastWithConfiguredMessage() {
        SubPluginLifecycleGate gate = new SubPluginLifecycleGate("test module disabled");

        assertFalse(gate.isEnabled());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            gate::requireEnabled);
        assertEquals("test module disabled", exception.getMessage());
    }

    @Test
    void generationChangesOnEnableAndDisableTransitions() {
        SubPluginLifecycleGate gate = new SubPluginLifecycleGate("test module disabled");
        long initialGeneration = gate.generation();

        gate.enable();
        long enabledGeneration = gate.generation();
        gate.enable();

        assertTrue(enabledGeneration > initialGeneration);
        assertEquals(enabledGeneration, gate.generation());

        gate.disable();
        long disabledGeneration = gate.generation();
        gate.disable();

        assertTrue(disabledGeneration > enabledGeneration);
        assertEquals(disabledGeneration, gate.generation());
    }

    @Test
    void disableRunsCleanupCallbacksEvenWhenAlreadyDisabled() {
        SubPluginLifecycleGate gate = new SubPluginLifecycleGate("test module disabled");
        AtomicInteger cleanupCount = new AtomicInteger();
        gate.onDisable(cleanupCount::incrementAndGet);

        gate.disable();
        gate.enable();
        gate.disable();

        assertEquals(2, cleanupCount.get());
    }
}
