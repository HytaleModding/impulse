package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

class PhysicsSpaceSettingsTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void defaultsExposeHeadlessVisualSyncRadii() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        assertEquals(PhysicsVisualSyncSettings.DEFAULT_VISUAL_FULL_SYNC_RADIUS,
            settings.getVisualSyncSettings().getVisualFullSyncRadius());
        assertEquals(PhysicsVisualSyncSettings.DEFAULT_VISUAL_MAX_SYNC_RADIUS,
            settings.getVisualSyncSettings().getVisualMaxSyncRadius());
        assertEquals(PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS,
            settings.getVisualMaterializationSettings().getDetachedVisualInterestRefreshIntervalTicks());
        assertEquals(PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS,
            settings.getVisualMaterializationSettings().getDetachedVisualCandidateRefreshIntervalTicks());
        assertEquals(PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS,
            settings.getVisualMaterializationSettings().getDetachedVisualVisibilityCheckIntervalTicks());
        assertEquals(PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_NEAR_RADIUS,
            settings.getCollisionLodSettings().getCollisionLodNearRadius());
        assertEquals(PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_MID_RADIUS,
            settings.getCollisionLodSettings().getCollisionLodMidRadius());
        assertEquals(PhysicsCollisionLodSettings.DEFAULT_COLLISION_LOD_REFRESH_INTERVAL_TICKS,
            settings.getCollisionLodSettings().getCollisionLodRefreshIntervalTicks());
        assertEquals(PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_ENABLED,
            settings.getVisualSyncSettings().isVisualSnapshotPredictionEnabled());
        assertEquals(PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS,
            settings.getVisualSyncSettings().getVisualSnapshotPredictionMaxSeconds(),
            0.0001f);
        assertEquals(PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_ENABLED,
            settings.getVisualSyncSettings().isVisualSnapshotSmoothingEnabled());
        assertEquals(PhysicsVisualSyncSettings.DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_RATE,
            settings.getVisualSyncSettings().getVisualSnapshotSmoothingRate(),
            0.0001f);
    }

    @Test
    void rejectsVisualFullSyncRadiusAboveMaxRadius() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> settings.getVisualSyncSettings().setVisualFullSyncRadius(settings.getVisualSyncSettings().getVisualMaxSyncRadius() + 1));

        assertEquals("Visual full sync radius cannot exceed visual max sync radius",
            exception.getMessage());
    }

    @Test
    void rejectsVisualMaxSyncRadiusBelowFullRadius() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> settings.getVisualSyncSettings().setVisualMaxSyncRadius(settings.getVisualSyncSettings().getVisualFullSyncRadius() - 1));

        assertEquals("Visual max sync radius cannot be lower than visual full sync radius",
            exception.getMessage());
    }

    @Test
    void acceptsUpdatedVisualSyncRadiiWhenOrderingStaysValid() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        settings.getVisualSyncSettings().setVisualMaxSyncRadius(192);
        settings.getVisualSyncSettings().setVisualFullSyncRadius(96);

        assertEquals(192, settings.getVisualSyncSettings().getVisualMaxSyncRadius());
        assertEquals(96, settings.getVisualSyncSettings().getVisualFullSyncRadius());
    }

    @Test
    void rejectsNonPositiveWorldCollisionValues() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        assertEquals("World collision radius must be between 1 and "
                + PhysicsWorldCollisionSettings.MAX_WORLD_COLLISION_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getWorldCollisionSettings().setWorldCollisionRadius(0)).getMessage());
        assertEquals("World collision body radius must be between 1 and "
                + PhysicsWorldCollisionSettings.MAX_WORLD_COLLISION_BODY_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getWorldCollisionSettings().setWorldCollisionBodyRadius(0)).getMessage());
        assertEquals("World collision TTL must be between 1 and "
                + PhysicsWorldCollisionSettings.MAX_WORLD_COLLISION_TTL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getWorldCollisionSettings().setWorldCollisionTtlTicks(0)).getMessage());
        assertEquals("Visual full sync radius must be between 1 and "
                + PhysicsVisualSyncSettings.MAX_VISUAL_FULL_SYNC_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getVisualSyncSettings().setVisualFullSyncRadius(0)).getMessage());
        assertEquals("Visual max sync radius must be between 1 and "
                + PhysicsVisualSyncSettings.MAX_VISUAL_MAX_SYNC_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getVisualSyncSettings().setVisualMaxSyncRadius(0)).getMessage());
        assertEquals("Detached visual interest refresh interval must be between 1 and "
                + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getVisualMaterializationSettings().setDetachedVisualInterestRefreshIntervalTicks(0)).getMessage());
        assertEquals("Detached visual candidate refresh interval must be between 1 and "
                + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getVisualMaterializationSettings().setDetachedVisualCandidateRefreshIntervalTicks(0)).getMessage());
        assertEquals("Detached visual visibility check interval must be between 1 and "
                + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getVisualMaterializationSettings().setDetachedVisualVisibilityCheckIntervalTicks(0)).getMessage());
        assertEquals("Collision LOD near radius must be between 1 and "
                + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getCollisionLodSettings().setCollisionLodNearRadius(0)).getMessage());
        assertEquals("Collision LOD mid radius must be between 1 and "
                + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getCollisionLodSettings().setCollisionLodMidRadius(0)).getMessage());
        assertEquals("Collision LOD refresh interval must be between 1 and "
                + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getCollisionLodSettings().setCollisionLodRefreshIntervalTicks(0)).getMessage());
        assertEquals("Visual snapshot prediction max seconds must be between 0 and "
                + PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getVisualSyncSettings().setVisualSnapshotPredictionMaxSeconds(
                    PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS
                        + 0.01f)).getMessage());
        assertEquals("Visual snapshot smoothing rate must be > 0 and <= "
                + PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getVisualSyncSettings().setVisualSnapshotSmoothingRate(0.0f)).getMessage());
    }

    @Test
    void rejectsInvalidCollisionLodOrderingAndHysteresis() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        IllegalArgumentException radiusException = assertThrows(IllegalArgumentException.class,
            () -> settings.getCollisionLodSettings().setCollisionLodRadii(96, 64));
        IllegalArgumentException hysteresisException = assertThrows(IllegalArgumentException.class,
            () -> settings.getCollisionLodSettings().setCollisionLodHysteresis(
                PhysicsCollisionLodSettings.MAX_COLLISION_LOD_HYSTERESIS + 1));

        assertEquals("Collision LOD near radius cannot exceed mid radius",
            radiusException.getMessage());
        assertEquals("Collision LOD hysteresis must be between 0 and "
                + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_HYSTERESIS,
            hysteresisException.getMessage());
    }

    @Test
    void defaultsFactoryReturnsFreshDefaultSettings() {
        PhysicsSpaceSettings first = PhysicsSpaceSettings.defaults();
        PhysicsSpaceSettings second = PhysicsSpaceSettings.defaults();

        assertNotSame(first, second);
        assertEquals(WorldCollisionMode.NONE, first.getWorldCollisionSettings().getWorldCollisionMode());
        assertSame(PhysicsWorldCollisionSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            first.getWorldCollisionSettings().getEntityChunkBoundaryMode());
    }

    @Test
    void groupedAccessorsExposeIndependentDomainState() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        settings.getWorldCollisionSettings().setWorldCollisionRadius(14);
        settings.getVisualSyncSettings().setVisualSyncRadii(36, 144);
        settings.getSolverSettings().setSolverIterations(6);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxMaterialized(96);
        settings.getCollisionLodSettings().setCollisionLodRadii(24, 72);

        assertEquals(14, settings.getWorldCollisionSettings().getWorldCollisionRadius());
        assertEquals(36, settings.getVisualSyncSettings().getVisualFullSyncRadius());
        assertEquals(144, settings.getVisualSyncSettings().getVisualMaxSyncRadius());
        assertEquals(6, settings.getSolverSettings().getSolverIterations());
        assertEquals(96, settings.getVisualMaterializationSettings().getDetachedVisualMaxMaterialized());
        assertEquals(24, settings.getCollisionLodSettings().getCollisionLodNearRadius());
        assertEquals(72, settings.getCollisionLodSettings().getCollisionLodMidRadius());

        settings.getWorldCollisionSettings().setWorldCollisionBodyRadius(5);
        settings.getVisualSyncSettings().setVisualMidSyncIntervalTicks(3);
        settings.getSolverSettings().setInternalPgsIterations(2);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxSpawnsPerTick(16);
        settings.getCollisionLodSettings().setCollisionLodHysteresis(4);

        assertEquals(5, settings.getWorldCollisionSettings().getWorldCollisionBodyRadius());
        assertEquals(3, settings.getVisualSyncSettings().getVisualMidSyncIntervalTicks());
        assertEquals(2, settings.getSolverSettings().getInternalPgsIterations());
        assertEquals(16,
            settings.getVisualMaterializationSettings().getDetachedVisualMaxSpawnsPerTick());
        assertEquals(4, settings.getCollisionLodSettings().getCollisionLodHysteresis());
    }

    @Test
    void streamingWorldCollisionFactoryEnablesStreamingMode() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.streamingWorldCollision();

        assertEquals(WorldCollisionMode.STREAMING, settings.getWorldCollisionSettings().getWorldCollisionMode());
        assertEquals(PhysicsWorldCollisionSettings.DEFAULT_WORLD_COLLISION_RADIUS,
            settings.getWorldCollisionSettings().getWorldCollisionRadius());
    }

    @Test
    void copyConstructorCopiesValuesWithoutSharingOriginalInstance() {
        PhysicsSpaceSettings original = new PhysicsSpaceSettings();
        original.getWorldCollisionSettings().setWorldCollisionMode(WorldCollisionMode.STREAMING);
        original.getWorldCollisionSettings().setWorldCollisionRadius(12);
        original.getWorldCollisionSettings().setWorldCollisionBodyRadius(6);
        original.getWorldCollisionSettings().setWorldCollisionTtlTicks(180);
        original.getVisualSyncSettings().setVisualMaxSyncRadius(160);
        original.getVisualSyncSettings().setVisualFullSyncRadius(80);
        original.getVisualMaterializationSettings().setDetachedVisualInterestRefreshIntervalTicks(2);
        original.getVisualMaterializationSettings().setDetachedVisualCandidateRefreshIntervalTicks(3);
        original.getVisualMaterializationSettings().setDetachedVisualVisibilityCheckIntervalTicks(12);
        original.getVisualSyncSettings().setVisualSnapshotPredictionEnabled(true);
        original.getVisualSyncSettings().setVisualSnapshotPredictionMaxSeconds(0.08f);
        original.getVisualSyncSettings().setVisualSnapshotSmoothingEnabled(true);
        original.getVisualSyncSettings().setVisualSnapshotSmoothingRate(18.0f);
        original.getCollisionLodSettings().setCollisionLodEnabled(true);
        original.getCollisionLodSettings().setCollisionLodRadii(32, 96);
        original.getCollisionLodSettings().setCollisionLodHysteresis(8);
        original.getCollisionLodSettings().setCollisionLodRefreshIntervalTicks(6);
        original.getCollisionLodSettings().setCollisionLodFarSleepEnabled(false);

        PhysicsSpaceSettings copy = new PhysicsSpaceSettings(original);
        original.getWorldCollisionSettings().setWorldCollisionRadius(20);
        original.getVisualSyncSettings().setVisualSyncRadii(96, 192);
        original.getVisualMaterializationSettings().setDetachedVisualMaxMaterialized(128);
        original.getCollisionLodSettings().setCollisionLodRadii(48, 112);

        assertNotSame(original.getWorldCollisionSettings(), copy.getWorldCollisionSettings());
        assertNotSame(original.getVisualSyncSettings(), copy.getVisualSyncSettings());
        assertNotSame(original.getSolverSettings(), copy.getSolverSettings());
        assertNotSame(original.getVisualMaterializationSettings(),
            copy.getVisualMaterializationSettings());
        assertNotSame(original.getCollisionLodSettings(), copy.getCollisionLodSettings());
        assertEquals(WorldCollisionMode.STREAMING, copy.getWorldCollisionSettings().getWorldCollisionMode());
        assertEquals(12, copy.getWorldCollisionSettings().getWorldCollisionRadius());
        assertEquals(6, copy.getWorldCollisionSettings().getWorldCollisionBodyRadius());
        assertEquals(180, copy.getWorldCollisionSettings().getWorldCollisionTtlTicks());
        assertEquals(160, copy.getVisualSyncSettings().getVisualMaxSyncRadius());
        assertEquals(80, copy.getVisualSyncSettings().getVisualFullSyncRadius());
        assertEquals(2, copy.getVisualMaterializationSettings().getDetachedVisualInterestRefreshIntervalTicks());
        assertEquals(3, copy.getVisualMaterializationSettings().getDetachedVisualCandidateRefreshIntervalTicks());
        assertEquals(12, copy.getVisualMaterializationSettings().getDetachedVisualVisibilityCheckIntervalTicks());
        assertEquals(true, copy.getVisualSyncSettings().isVisualSnapshotPredictionEnabled());
        assertEquals(0.08f, copy.getVisualSyncSettings().getVisualSnapshotPredictionMaxSeconds(), 0.0001f);
        assertEquals(true, copy.getVisualSyncSettings().isVisualSnapshotSmoothingEnabled());
        assertEquals(18.0f, copy.getVisualSyncSettings().getVisualSnapshotSmoothingRate(), 0.0001f);
        assertEquals(true, copy.getCollisionLodSettings().isCollisionLodEnabled());
        assertEquals(32, copy.getCollisionLodSettings().getCollisionLodNearRadius());
        assertEquals(96, copy.getCollisionLodSettings().getCollisionLodMidRadius());
        assertEquals(8, copy.getCollisionLodSettings().getCollisionLodHysteresis());
        assertEquals(6, copy.getCollisionLodSettings().getCollisionLodRefreshIntervalTicks());
        assertEquals(false, copy.getCollisionLodSettings().isCollisionLodFarSleepEnabled());
    }

    @Test
    void persistentSpaceStateRoundTripPreservesDetachedVisualCadenceSettings() {
        PhysicsSpaceSettings original = PhysicsSpaceSettings.defaults();
        original.getVisualMaterializationSettings().setDetachedVisualInterestRefreshIntervalTicks(7);
        original.getVisualMaterializationSettings().setDetachedVisualCandidateRefreshIntervalTicks(9);
        original.getVisualMaterializationSettings().setDetachedVisualVisibilityCheckIntervalTicks(11);

        PhysicsSpace space = new FakePhysicsBackend("test:settings-persistence-"
            + BACKEND_COUNTER.incrementAndGet()).createSpace();
        PersistentPhysicsSpaceState state = PersistentPhysicsSpaceState.from(space, original);

        BsonDocument encoded = PersistentPhysicsSpaceState.CODEC.encode(state, new ExtraInfo()).asDocument();

        assertTrue(encoded.containsKey("DetachedVisualInterestRefreshIntervalTicks"));
        assertTrue(encoded.containsKey("DetachedVisualCandidateRefreshIntervalTicks"));
        assertTrue(encoded.containsKey("DetachedVisualVisibilityCheckIntervalTicks"));
        assertDetachedVisualCadence(PersistentPhysicsSpaceState.CODEC.decode(encoded, new ExtraInfo()).toSettings(),
            7,
            9,
            11);
        assertDetachedVisualCadence(state.copy().toSettings(), 7, 9, 11);
    }

    private static void assertDetachedVisualCadence(PhysicsSpaceSettings settings,
        int interestInterval,
        int candidateInterval,
        int visibilityInterval) {
        assertEquals(interestInterval, settings.getVisualMaterializationSettings().getDetachedVisualInterestRefreshIntervalTicks());
        assertEquals(candidateInterval, settings.getVisualMaterializationSettings().getDetachedVisualCandidateRefreshIntervalTicks());
        assertEquals(visibilityInterval, settings.getVisualMaterializationSettings().getDetachedVisualVisibilityCheckIntervalTicks());
    }
}
