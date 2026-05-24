package dev.hytalemodding.impulse.core.internal.systems.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import org.junit.jupiter.api.Test;

class PhysicsSyncSystemTest {

    @Test
    void visualPredictionSecondsClampToConfiguredWindow() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualSnapshotPredictionEnabled(true);
        settings.getVisualSyncSettings().setVisualSnapshotPredictionMaxSeconds(0.05f);

        assertEquals(0.05f,
            PhysicsSyncPolicy.visualPredictionSeconds(settings,
                1_100_000_000L,
                1_000_000_000L),
            0.0001f);
    }

    @Test
    void visualPredictionSecondsStayZeroWhenDisabledOrMissingFrame() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualSnapshotPredictionEnabled(true);

        assertEquals(0.0f,
            PhysicsSyncPolicy.visualPredictionSeconds(settings, 1_100_000_000L, 0L),
            0.0001f);
        settings.getVisualSyncSettings().setVisualSnapshotPredictionEnabled(false);
        assertEquals(0.0f,
            PhysicsSyncPolicy.visualPredictionSeconds(settings,
                1_100_000_000L,
                1_000_000_000L),
            0.0001f);
    }
}
