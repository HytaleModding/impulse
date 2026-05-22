package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings.ExecutionMode;
import dev.hytalemodding.impulse.core.plugin.voxel.WorldCollisionMode;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

class PhysicsSpaceSettingsTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void defaultsExposeHeadlessVisualSyncRadii() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        assertEquals(PhysicsSpaceSettings.DEFAULT_VISUAL_FULL_SYNC_RADIUS,
            settings.getVisualFullSyncRadius());
        assertEquals(PhysicsSpaceSettings.DEFAULT_VISUAL_MAX_SYNC_RADIUS,
            settings.getVisualMaxSyncRadius());
        assertEquals(PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS,
            settings.getDetachedVisualInterestRefreshIntervalTicks());
        assertEquals(PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS,
            settings.getDetachedVisualCandidateRefreshIntervalTicks());
        assertEquals(PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS,
            settings.getDetachedVisualVisibilityCheckIntervalTicks());
        assertEquals(PhysicsSpaceSettings.DEFAULT_COLLISION_LOD_NEAR_RADIUS,
            settings.getCollisionLodNearRadius());
        assertEquals(PhysicsSpaceSettings.DEFAULT_COLLISION_LOD_MID_RADIUS,
            settings.getCollisionLodMidRadius());
        assertEquals(PhysicsSpaceSettings.DEFAULT_COLLISION_LOD_REFRESH_INTERVAL_TICKS,
            settings.getCollisionLodRefreshIntervalTicks());
        assertEquals(PhysicsSpaceSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_ENABLED,
            settings.isVisualSnapshotPredictionEnabled());
        assertEquals(PhysicsSpaceSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS,
            settings.getVisualSnapshotPredictionMaxSeconds(),
            0.0001f);
        assertEquals(PhysicsSpaceSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_ENABLED,
            settings.isVisualSnapshotSmoothingEnabled());
        assertEquals(PhysicsSpaceSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_RATE,
            settings.getVisualSnapshotSmoothingRate(),
            0.0001f);
    }

    @Test
    void rejectsVisualFullSyncRadiusAboveMaxRadius() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> settings.setVisualFullSyncRadius(settings.getVisualMaxSyncRadius() + 1));

        assertEquals("Visual full sync radius cannot exceed visual max sync radius",
            exception.getMessage());
    }

    @Test
    void rejectsVisualMaxSyncRadiusBelowFullRadius() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> settings.setVisualMaxSyncRadius(settings.getVisualFullSyncRadius() - 1));

        assertEquals("Visual max sync radius cannot be lower than visual full sync radius",
            exception.getMessage());
    }

    @Test
    void acceptsUpdatedVisualSyncRadiiWhenOrderingStaysValid() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        settings.setVisualMaxSyncRadius(192);
        settings.setVisualFullSyncRadius(96);

        assertEquals(192, settings.getVisualMaxSyncRadius());
        assertEquals(96, settings.getVisualFullSyncRadius());
    }

    @Test
    void rejectsNonPositiveWorldCollisionValues() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        assertEquals("World collision radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_WORLD_COLLISION_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setWorldCollisionRadius(0)).getMessage());
        assertEquals("World collision body radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_WORLD_COLLISION_BODY_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setWorldCollisionBodyRadius(0)).getMessage());
        assertEquals("World collision TTL must be between 1 and "
                + PhysicsSpaceSettings.MAX_WORLD_COLLISION_TTL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setWorldCollisionTtlTicks(0)).getMessage());
        assertEquals("Visual full sync radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_VISUAL_FULL_SYNC_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setVisualFullSyncRadius(0)).getMessage());
        assertEquals("Visual max sync radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_VISUAL_MAX_SYNC_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setVisualMaxSyncRadius(0)).getMessage());
        assertEquals("Detached visual interest refresh interval must be between 1 and "
                + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setDetachedVisualInterestRefreshIntervalTicks(0)).getMessage());
        assertEquals("Detached visual candidate refresh interval must be between 1 and "
                + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setDetachedVisualCandidateRefreshIntervalTicks(0)).getMessage());
        assertEquals("Detached visual visibility check interval must be between 1 and "
                + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setDetachedVisualVisibilityCheckIntervalTicks(0)).getMessage());
        assertEquals("Collision LOD near radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_COLLISION_LOD_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setCollisionLodNearRadius(0)).getMessage());
        assertEquals("Collision LOD mid radius must be between 1 and "
                + PhysicsSpaceSettings.MAX_COLLISION_LOD_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setCollisionLodMidRadius(0)).getMessage());
        assertEquals("Collision LOD refresh interval must be between 1 and "
                + PhysicsSpaceSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setCollisionLodRefreshIntervalTicks(0)).getMessage());
        assertEquals("Visual snapshot prediction max seconds must be between 0 and "
                + PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setVisualSnapshotPredictionMaxSeconds(
                    PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS
                        + 0.01f)).getMessage());
        assertEquals("Visual snapshot smoothing rate must be > 0 and <= "
                + PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE,
            assertThrows(IllegalArgumentException.class,
                () -> settings.setVisualSnapshotSmoothingRate(0.0f)).getMessage());
    }

    @Test
    void rejectsInvalidCollisionLodOrderingAndHysteresis() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        IllegalArgumentException radiusException = assertThrows(IllegalArgumentException.class,
            () -> settings.setCollisionLodRadii(96, 64));
        IllegalArgumentException hysteresisException = assertThrows(IllegalArgumentException.class,
            () -> settings.setCollisionLodHysteresis(
                PhysicsSpaceSettings.MAX_COLLISION_LOD_HYSTERESIS + 1));

        assertEquals("Collision LOD near radius cannot exceed mid radius",
            radiusException.getMessage());
        assertEquals("Collision LOD hysteresis must be between 0 and "
                + PhysicsSpaceSettings.MAX_COLLISION_LOD_HYSTERESIS,
            hysteresisException.getMessage());
    }

    @Test
    void defaultsFactoryReturnsFreshDefaultSettings() {
        PhysicsSpaceSettings first = PhysicsSpaceSettings.defaults();
        PhysicsSpaceSettings second = PhysicsSpaceSettings.defaults();

        assertNotSame(first, second);
        assertEquals(WorldCollisionMode.NONE, first.getWorldCollisionMode());
        assertSame(PhysicsSpaceSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            first.getEntityChunkBoundaryMode());
    }

    @Test
    void streamingWorldCollisionFactoryEnablesStreamingMode() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.streamingWorldCollision();

        assertEquals(WorldCollisionMode.STREAMING, settings.getWorldCollisionMode());
        assertEquals(PhysicsSpaceSettings.DEFAULT_WORLD_COLLISION_RADIUS,
            settings.getWorldCollisionRadius());
    }

    @Test
    void workerExecutionModeRemainsUnavailable() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        assertEquals(ExecutionMode.INLINE, PhysicsSpaceSettings.DEFAULT_EXECUTION_MODE);
        assertEquals(ExecutionMode.INLINE, settings.getExecutionMode());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> settings.setExecutionMode(ExecutionMode.WORKER));

        assertEquals("Worker physics execution is not available yet; use inline execution",
            exception.getMessage());
        assertEquals(ExecutionMode.INLINE, settings.getExecutionMode());
    }

    @Test
    void copyConstructorCopiesValuesWithoutSharingOriginalInstance() {
        PhysicsSpaceSettings original = new PhysicsSpaceSettings();
        original.setWorldCollisionMode(WorldCollisionMode.STREAMING);
        original.setWorldCollisionRadius(12);
        original.setWorldCollisionBodyRadius(6);
        original.setWorldCollisionTtlTicks(180);
        original.setVisualMaxSyncRadius(160);
        original.setVisualFullSyncRadius(80);
        original.setDetachedVisualInterestRefreshIntervalTicks(2);
        original.setDetachedVisualCandidateRefreshIntervalTicks(3);
        original.setDetachedVisualVisibilityCheckIntervalTicks(12);
        original.setVisualSnapshotPredictionEnabled(true);
        original.setVisualSnapshotPredictionMaxSeconds(0.08f);
        original.setVisualSnapshotSmoothingEnabled(true);
        original.setVisualSnapshotSmoothingRate(18.0f);
        original.setCollisionLodEnabled(true);
        original.setCollisionLodRadii(32, 96);
        original.setCollisionLodHysteresis(8);
        original.setCollisionLodRefreshIntervalTicks(6);
        original.setCollisionLodFarSleepEnabled(false);

        PhysicsSpaceSettings copy = new PhysicsSpaceSettings(original);
        original.setWorldCollisionRadius(20);

        assertEquals(WorldCollisionMode.STREAMING, copy.getWorldCollisionMode());
        assertEquals(12, copy.getWorldCollisionRadius());
        assertEquals(6, copy.getWorldCollisionBodyRadius());
        assertEquals(180, copy.getWorldCollisionTtlTicks());
        assertEquals(160, copy.getVisualMaxSyncRadius());
        assertEquals(80, copy.getVisualFullSyncRadius());
        assertEquals(2, copy.getDetachedVisualInterestRefreshIntervalTicks());
        assertEquals(3, copy.getDetachedVisualCandidateRefreshIntervalTicks());
        assertEquals(12, copy.getDetachedVisualVisibilityCheckIntervalTicks());
        assertEquals(true, copy.isVisualSnapshotPredictionEnabled());
        assertEquals(0.08f, copy.getVisualSnapshotPredictionMaxSeconds(), 0.0001f);
        assertEquals(true, copy.isVisualSnapshotSmoothingEnabled());
        assertEquals(18.0f, copy.getVisualSnapshotSmoothingRate(), 0.0001f);
        assertEquals(true, copy.isCollisionLodEnabled());
        assertEquals(32, copy.getCollisionLodNearRadius());
        assertEquals(96, copy.getCollisionLodMidRadius());
        assertEquals(8, copy.getCollisionLodHysteresis());
        assertEquals(6, copy.getCollisionLodRefreshIntervalTicks());
        assertEquals(false, copy.isCollisionLodFarSleepEnabled());
    }

    @Test
    void persistentSpaceStateRoundTripPreservesDetachedVisualCadenceSettings() {
        PhysicsSpaceSettings original = PhysicsSpaceSettings.defaults();
        original.setDetachedVisualInterestRefreshIntervalTicks(7);
        original.setDetachedVisualCandidateRefreshIntervalTicks(9);
        original.setDetachedVisualVisibilityCheckIntervalTicks(11);

        PhysicsSpace space = new FakePhysicsBackend("test:settings-persistence-"
            + BACKEND_COUNTER.incrementAndGet()).createSpace();
        PersistentPhysicsSpaceState state = PersistentPhysicsSpaceState.from(space, original);

        BsonDocument encoded = PersistentPhysicsSpaceState.CODEC.encode(state).asDocument();

        assertTrue(encoded.containsKey("DetachedVisualInterestRefreshIntervalTicks"));
        assertTrue(encoded.containsKey("DetachedVisualCandidateRefreshIntervalTicks"));
        assertTrue(encoded.containsKey("DetachedVisualVisibilityCheckIntervalTicks"));
        assertDetachedVisualCadence(PersistentPhysicsSpaceState.CODEC.decode(encoded).toSettings(),
            7,
            9,
            11);
        assertDetachedVisualCadence(state.copy().toSettings(), 7, 9, 11);
    }

    private static void assertDetachedVisualCadence(PhysicsSpaceSettings settings,
        int interestInterval,
        int candidateInterval,
        int visibilityInterval) {
        assertEquals(interestInterval, settings.getDetachedVisualInterestRefreshIntervalTicks());
        assertEquals(candidateInterval, settings.getDetachedVisualCandidateRefreshIntervalTicks());
        assertEquals(visibilityInterval, settings.getDetachedVisualVisibilityCheckIntervalTicks());
    }
}
