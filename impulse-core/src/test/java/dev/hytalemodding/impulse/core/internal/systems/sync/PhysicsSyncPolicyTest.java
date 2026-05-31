package dev.hytalemodding.impulse.core.internal.systems.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsSyncPolicyTest {

    @Test
    void returnsInitialForUninitializedSyncState() {
        PhysicsBodyRuntimeState.BodySyncState syncState = new PhysicsBodyRuntimeState.BodySyncState();

        assertEquals(PhysicsSyncPolicy.SyncDecision.INITIAL,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(),
                new Quaternionf(),
                false,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.NEAR));
    }

    @Test
    void returnsTransitionWhenSleepingStateChanges() {
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(false);

        assertEquals(PhysicsSyncPolicy.SyncDecision.TRANSITION,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(),
                new Quaternionf(),
                true,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.NEAR));
    }

    @Test
    void farRangeFollowersSkipVisualSync() {
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(false);

        assertEquals(PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_RANGE,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(),
                new Quaternionf(),
                false,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.FAR));
    }

    @Test
    void lowSpeedNearBodiesUseVisualDeadzoneBeforeKeepalive() {
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(false);
        syncState.recordSkip(1.0f);

        assertEquals(PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_DEADZONE,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(0.05f, 0.0f, 0.0f),
                new Quaternionf(),
                false,
                true,
                false,
                PhysicsSyncPolicy.SyncRangeTier.NEAR));
    }

    @Test
    void lowSpeedNearBodiesTriggerKeepaliveAtLongerInterval() {
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(false);
        syncState.recordSkip(1.25f);

        assertEquals(PhysicsSyncPolicy.SyncDecision.KEEPALIVE,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(0.05f, 0.0f, 0.0f),
                new Quaternionf(),
                false,
                true,
                false,
                PhysicsSyncPolicy.SyncRangeTier.NEAR));
    }

    @Test
    void midRangeFollowersUseCoarseThresholdAndKeepalive() {
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(false);
        syncState.recordSkip(2.4f);

        assertEquals(PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_RANGE,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(0.2f, 0.0f, 0.0f),
                new Quaternionf(),
                false,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.MID));

        syncState.recordSkip(0.1f);
        assertEquals(PhysicsSyncPolicy.SyncDecision.KEEPALIVE,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(0.2f, 0.0f, 0.0f),
                new Quaternionf(),
                false,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.MID));
    }

    @Test
    void midRangeFollowersRespectConfiguredMinimumInterval() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualMidSyncIntervalTicks(4);
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(false);
        syncState.recordSkip(0.15f);

        assertEquals(PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_RANGE,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                settings,
                new Vector3f(1.0f, 0.0f, 0.0f),
                new Quaternionf(),
                false,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.MID));

        syncState.recordSkip(0.05f);
        assertEquals(PhysicsSyncPolicy.SyncDecision.THRESHOLD,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                settings,
                new Vector3f(1.0f, 0.0f, 0.0f),
                new Quaternionf(),
                false,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.MID));
    }

    @Test
    void farRangeLodUsesConfiguredIntervalWhenCutoffIsDisabled() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualFarSyncCutoffEnabled(false);
        settings.getVisualSyncSettings().setVisualFarSyncIntervalTicks(40);
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(false);
        syncState.recordSkip(1.95f);

        assertEquals(PhysicsSyncPolicy.SyncDecision.SKIP_VISUAL_RANGE,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                settings,
                new Vector3f(),
                new Quaternionf(),
                false,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.FAR));

        syncState.recordSkip(0.05f);
        assertEquals(PhysicsSyncPolicy.SyncDecision.KEEPALIVE,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                settings,
                new Vector3f(),
                new Quaternionf(),
                false,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.FAR));
    }

    @Test
    void controlledBodiesBypassLowSpeedDeadzoneThresholds() {
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(false);

        assertEquals(PhysicsSyncPolicy.SyncDecision.THRESHOLD,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(0.05f, 0.0f, 0.0f),
                new Quaternionf(),
                false,
                true,
                true,
                PhysicsSyncPolicy.SyncRangeTier.NEAR));
    }

    @Test
    void activeBodiesTriggerThresholdOnRotationChange() {
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(false);
        Quaternionf rotated = new Quaternionf().rotateY((float) Math.toRadians(4.0));

        assertEquals(PhysicsSyncPolicy.SyncDecision.THRESHOLD,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(),
                rotated,
                false,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.NEAR));
    }

    @Test
    void bodySyncStateTracksPublishedSnapshotMotion() {
        PhysicsBodyRuntimeState.BodySyncState syncState = new PhysicsBodyRuntimeState.BodySyncState();

        assertTrue(Float.isNaN(syncState.recordSnapshotObservation(new Vector3f(1.0f,
            2.0f,
            3.0f))));
        assertTrue(syncState.isSnapshotObserved());
        assertEquals(0.5f,
            syncState.recordSnapshotObservation(new Vector3f(1.5f, 2.0f, 3.0f)),
            0.0001f);
        assertEquals(1.5f, syncState.getLastObservedSnapshotPosition().x, 0.0001f);
    }

    @Test
    void sleepingBodiesSkipAfterThresholdAndKeepaliveChecks() {
        PhysicsBodyRuntimeState.BodySyncState syncState = initializedState(true);
        syncState.recordSkip(5.0f);

        assertEquals(PhysicsSyncPolicy.SyncDecision.SKIP_SLEEPING,
            PhysicsSyncPolicy.resolveSyncDecision(syncState,
                PhysicsSpaceSettings.defaults(),
                new Vector3f(),
                new Quaternionf(),
                true,
                false,
                false,
                PhysicsSyncPolicy.SyncRangeTier.NEAR));
    }

    @Test
    void rangeTierReturnsNearForNonLimitedOrControlledVisuals() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        List<PhysicsSyncPolicy.PlayerInterest> players =
            interests(new Vector3f(100.0f, 0.0f, 0.0f));

        assertEquals(PhysicsSyncPolicy.SyncRangeTier.NEAR,
            PhysicsSyncPolicy.resolveRangeTier(settings,
                null,
                false,
                false,
                players,
                new Vector3f()));
        assertEquals(PhysicsSyncPolicy.SyncRangeTier.NEAR,
            PhysicsSyncPolicy.resolveRangeTier(settings,
                null,
                true,
                true,
                players,
                new Vector3f()));
    }

    @Test
    void rangeTierReturnsFarWhenNoPlayersAreInterested() {
        assertEquals(PhysicsSyncPolicy.SyncRangeTier.FAR,
            PhysicsSyncPolicy.resolveRangeTier(PhysicsSpaceSettings.defaults(),
                null,
                true,
                false,
                List.of(),
                new Vector3f()));
    }

    @Test
    void rangeTierFallsBackToNearWhenSettingsAreMissing() {
        assertEquals(PhysicsSyncPolicy.SyncRangeTier.NEAR,
            PhysicsSyncPolicy.resolveRangeTier(null,
                null,
                true,
                false,
                interests(new Vector3f(500.0f, 0.0f, 0.0f)),
                new Vector3f()));
    }

    @Test
    void rangeTierDistinguishesNearMidAndFarBands() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getVisualSyncSettings().setVisualFullSyncRadius(10);
        settings.getVisualSyncSettings().setVisualMaxSyncRadius(20);

        List<PhysicsSyncPolicy.PlayerInterest> players = interests(new Vector3f(0.0f, 0.0f, 0.0f));

        assertEquals(PhysicsSyncPolicy.SyncRangeTier.NEAR,
            PhysicsSyncPolicy.resolveRangeTier(settings,
                null,
                true,
                false,
                players,
                new Vector3f(6.0f, 0.0f, 0.0f)));
        assertEquals(PhysicsSyncPolicy.SyncRangeTier.MID,
            PhysicsSyncPolicy.resolveRangeTier(settings,
                null,
                true,
                false,
                players,
                new Vector3f(15.0f, 0.0f, 0.0f)));
        assertEquals(PhysicsSyncPolicy.SyncRangeTier.FAR,
            PhysicsSyncPolicy.resolveRangeTier(settings,
                null,
                true,
                false,
                players,
                new Vector3f(25.0f, 0.0f, 0.0f)));
    }

    private static PhysicsBodyRuntimeState.BodySyncState initializedState(boolean sleeping) {
        PhysicsBodyRuntimeState.BodySyncState syncState = new PhysicsBodyRuntimeState.BodySyncState();
        syncState.recordSync(new Vector3f(), new Quaternionf(), sleeping);
        return syncState;
    }

    private static List<PhysicsSyncPolicy.PlayerInterest> interests(Vector3f... positions) {
        return java.util.Arrays.stream(positions)
            .map(position -> new PhysicsSyncPolicy.PlayerInterest(position, new Vector3f(1.0f, 0.0f, 0.0f)))
            .toList();
    }
}
