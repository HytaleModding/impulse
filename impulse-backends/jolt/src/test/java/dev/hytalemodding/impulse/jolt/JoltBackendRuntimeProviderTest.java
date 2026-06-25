package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.hytalemodding.impulse.api.BackendId;
import org.junit.jupiter.api.Test;

class JoltBackendRuntimeProviderTest {

    @Test
    void providerExposesJoltBackendIdAndCreatesRuntime() {
        JoltBackendRuntimeProvider provider = new JoltBackendRuntimeProvider();

        assertEquals(new BackendId("impulse:jolt"), provider.getId());
        assertNotNull(provider.createRuntime());
    }
}
