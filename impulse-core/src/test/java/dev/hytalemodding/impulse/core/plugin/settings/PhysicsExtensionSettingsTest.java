package dev.hytalemodding.impulse.core.plugin.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhysicsExtensionSettingsTest {

    private static final PhysicsBackendExtensionId EXTENSION_ID =
        new PhysicsBackendExtensionId("test:extension");

    @Test
    void malformedTypedNumericValuesAreIgnoredByTypedGetters() {
        PhysicsExtensionSettings settings = new PhysicsExtensionSettings();
        settings.set(EXTENSION_ID,
            "bad-int",
            new PhysicsExtensionSettingValue(PhysicsExtensionSettingValue.Kind.INTEGER, "not-an-int"));
        settings.set(EXTENSION_ID,
            "bad-float",
            new PhysicsExtensionSettingValue(PhysicsExtensionSettingValue.Kind.FLOAT, "not-a-float"));
        settings.set(EXTENSION_ID,
            "nan-float",
            new PhysicsExtensionSettingValue(PhysicsExtensionSettingValue.Kind.FLOAT, "NaN"));

        assertTrue(settings.getInt(EXTENSION_ID, "bad-int").isEmpty());
        assertTrue(settings.getFloat(EXTENSION_ID, "bad-float").isEmpty());
        assertTrue(settings.getFloat(EXTENSION_ID, "nan-float").isEmpty());
    }

    @Test
    void typedNumericGettersParseValidPersistedValues() {
        PhysicsExtensionSettings settings = new PhysicsExtensionSettings();
        settings.set(EXTENSION_ID,
            "int",
            new PhysicsExtensionSettingValue(PhysicsExtensionSettingValue.Kind.INTEGER, "42"));
        settings.set(EXTENSION_ID,
            "float",
            new PhysicsExtensionSettingValue(PhysicsExtensionSettingValue.Kind.FLOAT, "0.25"));

        assertEquals(42, settings.getInt(EXTENSION_ID, "int").orElseThrow());
        assertEquals(0.25f, settings.getFloat(EXTENSION_ID, "float").orElseThrow(), 0.0001f);
    }
}
