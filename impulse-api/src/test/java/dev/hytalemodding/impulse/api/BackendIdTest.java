package dev.hytalemodding.impulse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BackendIdTest {

    @Test
    void rejectsBlankValues() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new BackendId("   "));

        assertEquals("BackendId value cannot be blank", exception.getMessage());
    }

    @Test
    void toStringReturnsUnderlyingValue() {
        BackendId backendId = new BackendId("impulse:rapier");

        assertEquals("impulse:rapier", backendId.toString());
    }
}
