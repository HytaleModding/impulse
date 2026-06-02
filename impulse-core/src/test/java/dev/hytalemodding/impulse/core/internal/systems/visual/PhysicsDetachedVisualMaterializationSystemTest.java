package dev.hytalemodding.impulse.core.internal.systems.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsDetachedVisualMaterializationSystemTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

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

    @Test
    void firstOcclusionFrameKeepsCandidateVisibleWhileRaycastIsPending() {
        BackendId backendId = new BackendId("test:visual-occlusion-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(new FakePhysicsBackend(backendId));
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualOcclusionMode(VisualOcclusionMode.CULL);
        settings.getVisualSyncSettings().setVisualOcclusionRaycastsPerTick(1);
        PhysicsSpace space = resource.createLiveSpace(backendId, "test-world", settings);
        RigidBodyKey bodyKey = RigidBodyKey.random();
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshot.of(0.0f,
            0.0f,
            4.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.0f,
            ShapeType.BOX,
            true,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            PhysicsAxis.Y);
        List<PhysicsVisualRuntime.VisualInterest> interests = List.of(
            new PhysicsVisualRuntime.VisualInterest(new Vector3f(), null));

        DetachedVisualOcclusion.Result result = assertDoesNotThrow(() -> DetachedVisualOcclusion.resolve(resource,
            bodyKey,
            resource.requireSpaceBinding(space.id()),
            snapshot,
            settings,
            interests,
            16.0f,
            1L,
            new DetachedVisualOcclusion.RaycastBudget(),
            null));

        assertTrue(result.shouldMaterialize());
    }
}
