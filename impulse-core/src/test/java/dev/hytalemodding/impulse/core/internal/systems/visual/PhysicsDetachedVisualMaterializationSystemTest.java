package dev.hytalemodding.impulse.core.internal.systems.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.core.internal.resources.visual.PhysicsVisualRuntime;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsDetachedVisualMaterializationSystemTest {

    @Test
    void scalarVisibleDistanceRespectsRadiusAndViewCone() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualVisibilityCullingEnabled(true);
        List<PhysicsVisualRuntime.VisualInterest> interests = List.of(
            new PhysicsVisualRuntime.VisualInterest(new Vector3f(), new Vector3f(1.0f, 0.0f, 0.0f)));

        assertEquals(9.0f,
            DetachedVisualGeometry.visibleDistanceSquared(3.0f,
                0.0f,
                0.0f,
                settings,
                interests,
                10.0f));
        assertEquals(Float.POSITIVE_INFINITY,
            DetachedVisualGeometry.visibleDistanceSquared(-9.0f,
                0.0f,
                0.0f,
                settings,
                interests,
                10.0f));
        assertEquals(16.0f,
            DetachedVisualGeometry.visibleDistanceSquared(-4.0f,
                0.0f,
                0.0f,
                settings,
                interests,
                10.0f));
        assertEquals(Float.POSITIVE_INFINITY,
            DetachedVisualGeometry.visibleDistanceSquared(11.0f,
                0.0f,
                0.0f,
                settings,
                interests,
                10.0f));
    }
}
