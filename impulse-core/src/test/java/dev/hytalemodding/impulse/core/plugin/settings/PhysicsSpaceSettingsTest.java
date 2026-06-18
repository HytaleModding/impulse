package dev.hytalemodding.impulse.core.plugin.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings;
import org.junit.jupiter.api.Test;

class PhysicsSpaceSettingsTest {

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
    void rejectsNonPositivePhysicsChunkTerrainValues() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        assertEquals("PhysicsChunk terrain radius must be between 1 and "
                + PhysicsChunkTerrainSettings.MAX_TERRAIN_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getPhysicsChunkTerrainSettings().setTerrainRadius(0)).getMessage());
        assertEquals("PhysicsChunk terrain body radius must be between 1 and "
                + PhysicsChunkTerrainSettings.MAX_BODY_TERRAIN_RADIUS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getPhysicsChunkTerrainSettings().setBodyTerrainRadius(0)).getMessage());
        assertEquals("PhysicsChunk terrain TTL must be between 1 and "
                + PhysicsChunkTerrainSettings.MAX_TERRAIN_TTL_TICKS,
            assertThrows(IllegalArgumentException.class,
                () -> settings.getPhysicsChunkTerrainSettings().setTerrainTtlTicks(0)).getMessage());
        assertEquals("Terrain friction must be finite and >= 0.0",
            assertThrows(IllegalArgumentException.class,
                () -> settings.getPhysicsChunkTerrainSettings().setTerrainFriction(-0.1f)).getMessage());
        assertEquals("Terrain restitution must be finite and >= 0.0",
            assertThrows(IllegalArgumentException.class,
                () -> settings.getPhysicsChunkTerrainSettings().setTerrainRestitution(Float.NaN)).getMessage());
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
        assertEquals(PhysicsChunkTerrainMode.NONE, first.getPhysicsChunkTerrainSettings().getTerrainMode());
        assertSame(PhysicsChunkTerrainSettings.DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE,
            first.getPhysicsChunkTerrainSettings().getEntityChunkBoundaryMode());
        assertFalse(first.getPhysicsChunkTerrainSettings().isNativeVoxelTerrainEnabled());
        assertEquals(PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_FRICTION,
            first.getPhysicsChunkTerrainSettings().getTerrainFriction(),
            0.0001f);
        assertEquals(PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RESTITUTION,
            first.getPhysicsChunkTerrainSettings().getTerrainRestitution(),
            0.0001f);
    }

    @Test
    void groupedAccessorsExposeIndependentDomainState() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();

        settings.getPhysicsChunkTerrainSettings().setTerrainRadius(14);
        settings.getVisualSyncSettings().setVisualSyncRadii(36, 144);
        settings.getSolverSettings().setSolverIterations(6);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxMaterialized(96);
        settings.getCollisionLodSettings().setCollisionLodRadii(24, 72);

        assertEquals(14, settings.getPhysicsChunkTerrainSettings().getTerrainRadius());
        assertEquals(36, settings.getVisualSyncSettings().getVisualFullSyncRadius());
        assertEquals(144, settings.getVisualSyncSettings().getVisualMaxSyncRadius());
        assertEquals(6, settings.getSolverSettings().getSolverIterations());
        assertEquals(96, settings.getVisualMaterializationSettings().getDetachedVisualMaxMaterialized());
        assertEquals(24, settings.getCollisionLodSettings().getCollisionLodNearRadius());
        assertEquals(72, settings.getCollisionLodSettings().getCollisionLodMidRadius());

        settings.getPhysicsChunkTerrainSettings().setBodyTerrainRadius(5);
        settings.getVisualSyncSettings().setVisualMidSyncIntervalTicks(3);
        settings.getSolverSettings().setDynamicSleepLinearThreshold(0.45f);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxSpawnsPerTick(16);
        settings.getCollisionLodSettings().setCollisionLodHysteresis(4);

        assertEquals(5, settings.getPhysicsChunkTerrainSettings().getBodyTerrainRadius());
        assertEquals(3, settings.getVisualSyncSettings().getVisualMidSyncIntervalTicks());
        assertEquals(0.45f, settings.getSolverSettings().getDynamicSleepLinearThreshold(), 0.0001f);
        assertEquals(16,
            settings.getVisualMaterializationSettings().getDetachedVisualMaxSpawnsPerTick());
        assertEquals(4, settings.getCollisionLodSettings().getCollisionLodHysteresis());
    }

    @Test
    void collisionLodSettingsCopyConstructorCopiesValues() {
        PhysicsCollisionLodSettings canonical = new PhysicsCollisionLodSettings();

        canonical.setCollisionLodEnabled(true);
        canonical.setCollisionLodRadii(24, 96);
        canonical.setCollisionLodHysteresis(6);
        canonical.setCollisionLodRefreshIntervalTicks(8);
        canonical.setCollisionLodFarSleepEnabled(false);

        PhysicsCollisionLodSettings canonicalCopy =
            new PhysicsCollisionLodSettings(canonical);
        PhysicsCollisionLodSettings secondCopy =
            new PhysicsCollisionLodSettings(canonicalCopy);
        canonical.setCollisionLodRadii(32, 128);
        canonicalCopy.setCollisionLodRadii(40, 160);

        assertTrue(canonicalCopy.isCollisionLodEnabled());
        assertEquals(40, canonicalCopy.getCollisionLodNearRadius());
        assertEquals(160, canonicalCopy.getCollisionLodMidRadius());
        assertEquals(6, canonicalCopy.getCollisionLodHysteresis());
        assertEquals(8, canonicalCopy.getCollisionLodRefreshIntervalTicks());
        assertFalse(canonicalCopy.isCollisionLodFarSleepEnabled());
        assertTrue(secondCopy.isCollisionLodEnabled());
        assertEquals(24, secondCopy.getCollisionLodNearRadius());
        assertEquals(96, secondCopy.getCollisionLodMidRadius());
        assertEquals(6, secondCopy.getCollisionLodHysteresis());
        assertEquals(8, secondCopy.getCollisionLodRefreshIntervalTicks());
        assertFalse(secondCopy.isCollisionLodFarSleepEnabled());
    }

    @Test
    void terrainSettingsCopyConstructorCopiesValues() {
        PhysicsChunkTerrainSettings canonical = new PhysicsChunkTerrainSettings();

        canonical.setTerrainMode(PhysicsChunkTerrainMode.STREAMING);
        canonical.setEntityChunkBoundaryMode(EntityChunkBoundaryMode.LOAD_TICKING_CHUNK);
        canonical.setNativeVoxelTerrainEnabled(true);
        canonical.setTerrainRadius(18);
        canonical.setBodyTerrainRadius(7);
        canonical.setTerrainTtlTicks(240);
        canonical.setTerrainMaterial(0.85f, 0.2f);

        PhysicsChunkTerrainSettings canonicalCopy =
            new PhysicsChunkTerrainSettings(canonical);
        PhysicsChunkTerrainSettings secondCopy =
            new PhysicsChunkTerrainSettings(canonicalCopy);
        canonical.setTerrainRadius(24);
        canonicalCopy.setTerrainRadius(30);

        assertEquals(PhysicsChunkTerrainMode.STREAMING, canonicalCopy.getTerrainMode());
        assertEquals(EntityChunkBoundaryMode.LOAD_TICKING_CHUNK,
            canonicalCopy.getEntityChunkBoundaryMode());
        assertTrue(canonicalCopy.isNativeVoxelTerrainEnabled());
        assertEquals(30, canonicalCopy.getTerrainRadius());
        assertEquals(7, canonicalCopy.getBodyTerrainRadius());
        assertEquals(240, canonicalCopy.getTerrainTtlTicks());
        assertEquals(0.85f, canonicalCopy.getTerrainFriction(), 0.0001f);
        assertEquals(0.2f, canonicalCopy.getTerrainRestitution(), 0.0001f);
        assertEquals(18, secondCopy.getTerrainRadius());
        assertEquals(7, secondCopy.getBodyTerrainRadius());
        assertEquals(240, secondCopy.getTerrainTtlTicks());
    }

    @Test
    void extensionSettingsAreTypedAndCopyIsolated() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        PhysicsBackendExtensionId extensionId = new PhysicsBackendExtensionId("test:extension");

        settings.getExtensionSettings().setInt(extensionId, "iterations", 7);
        settings.getExtensionSettings().setFloat(extensionId, "scale", 1.5f);
        settings.getExtensionSettings().setBoolean(extensionId, "enabled", true);
        settings.getExtensionSettings().setString(extensionId, "mode", "stable");

        PhysicsSpaceSettings copy = new PhysicsSpaceSettings(settings);
        settings.getExtensionSettings().setInt(extensionId, "iterations", 11);
        copy.getExtensionSettings().setString(extensionId, "mode", "copy");

        assertEquals(11, settings.getExtensionSettings().getInt(extensionId, "iterations").orElseThrow());
        assertEquals("stable", settings.getExtensionSettings().getString(extensionId, "mode").orElseThrow());
        assertEquals(7, copy.getExtensionSettings().getInt(extensionId, "iterations").orElseThrow());
        assertEquals(1.5f, copy.getExtensionSettings().getFloat(extensionId, "scale").orElseThrow(), 0.0001f);
        assertTrue(copy.getExtensionSettings().getBoolean(extensionId, "enabled").orElseThrow());
        assertEquals("copy", copy.getExtensionSettings().getString(extensionId, "mode").orElseThrow());
    }

    @Test
    void defaultsDoNotCarryBackendExtensionValues() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();

        assertTrue(settings.getExtensionSettings().isEmpty());
    }

    @Test
    void streamingPhysicsChunkFactoryEnablesStreamingMode() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.streamingPhysicsChunk();

        assertEquals(PhysicsChunkTerrainMode.STREAMING, settings.getPhysicsChunkTerrainSettings().getTerrainMode());
        assertEquals(PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RADIUS,
            settings.getPhysicsChunkTerrainSettings().getTerrainRadius());
    }

    @Test
    void copyConstructorCopiesValuesWithoutSharingOriginalInstance() {
        PhysicsSpaceSettings original = new PhysicsSpaceSettings();
        original.getPhysicsChunkTerrainSettings().setTerrainMode(PhysicsChunkTerrainMode.STREAMING);
        original.getPhysicsChunkTerrainSettings().setTerrainRadius(12);
        original.getPhysicsChunkTerrainSettings().setBodyTerrainRadius(6);
        original.getPhysicsChunkTerrainSettings().setTerrainTtlTicks(180);
        original.getPhysicsChunkTerrainSettings().setNativeVoxelTerrainEnabled(true);
        original.getPhysicsChunkTerrainSettings().setTerrainMaterial(0.9f, 0.15f);
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
        original.getPhysicsChunkTerrainSettings().setTerrainRadius(20);
        original.getVisualSyncSettings().setVisualSyncRadii(96, 192);
        original.getVisualMaterializationSettings().setDetachedVisualMaxMaterialized(128);
        original.getCollisionLodSettings().setCollisionLodRadii(48, 112);

        assertNotSame(original.getPhysicsChunkTerrainSettings(), copy.getPhysicsChunkTerrainSettings());
        assertNotSame(original.getVisualSyncSettings(), copy.getVisualSyncSettings());
        assertNotSame(original.getSolverSettings(), copy.getSolverSettings());
        assertNotSame(original.getVisualMaterializationSettings(),
            copy.getVisualMaterializationSettings());
        assertNotSame(original.getCollisionLodSettings(), copy.getCollisionLodSettings());
        assertEquals(PhysicsChunkTerrainMode.STREAMING, copy.getPhysicsChunkTerrainSettings().getTerrainMode());
        assertEquals(12, copy.getPhysicsChunkTerrainSettings().getTerrainRadius());
        assertEquals(6, copy.getPhysicsChunkTerrainSettings().getBodyTerrainRadius());
        assertEquals(180, copy.getPhysicsChunkTerrainSettings().getTerrainTtlTicks());
        assertTrue(copy.getPhysicsChunkTerrainSettings().isNativeVoxelTerrainEnabled());
        assertEquals(0.9f, copy.getPhysicsChunkTerrainSettings().getTerrainFriction(), 0.0001f);
        assertEquals(0.15f, copy.getPhysicsChunkTerrainSettings().getTerrainRestitution(), 0.0001f);
        assertEquals(160, copy.getVisualSyncSettings().getVisualMaxSyncRadius());
        assertEquals(80, copy.getVisualSyncSettings().getVisualFullSyncRadius());
        assertEquals(2, copy.getVisualMaterializationSettings().getDetachedVisualInterestRefreshIntervalTicks());
        assertEquals(3, copy.getVisualMaterializationSettings().getDetachedVisualCandidateRefreshIntervalTicks());
        assertEquals(12, copy.getVisualMaterializationSettings().getDetachedVisualVisibilityCheckIntervalTicks());
        assertTrue(copy.getVisualSyncSettings().isVisualSnapshotPredictionEnabled());
        assertEquals(0.08f, copy.getVisualSyncSettings().getVisualSnapshotPredictionMaxSeconds(), 0.0001f);
        assertTrue(copy.getVisualSyncSettings().isVisualSnapshotSmoothingEnabled());
        assertEquals(18.0f, copy.getVisualSyncSettings().getVisualSnapshotSmoothingRate(), 0.0001f);
        assertTrue(copy.getCollisionLodSettings().isCollisionLodEnabled());
        assertEquals(32, copy.getCollisionLodSettings().getCollisionLodNearRadius());
        assertEquals(96, copy.getCollisionLodSettings().getCollisionLodMidRadius());
        assertEquals(8, copy.getCollisionLodSettings().getCollisionLodHysteresis());
        assertEquals(6, copy.getCollisionLodSettings().getCollisionLodRefreshIntervalTicks());
        assertFalse(copy.getCollisionLodSettings().isCollisionLodFarSleepEnabled());
    }

}
