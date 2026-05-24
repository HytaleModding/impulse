package dev.hytalemodding.impulse.core.internal.systems.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.systems.collision.PhysicsCollisionLodSystem.CollisionLodState;
import dev.hytalemodding.impulse.core.internal.systems.collision.PhysicsCollisionLodSystem.CollisionLodTier;
import dev.hytalemodding.impulse.core.internal.systems.collision.PhysicsCollisionLodSystem.CollisionLodUpdate;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsCollisionLodSystemTest {

    @Test
    void resolvesDistanceTiersAroundInterest() {
        PhysicsSpaceSettings settings = testSettings();
        List<PhysicsWorldResource.VisualInterest> interests = interestsAtOrigin();

        assertEquals(CollisionLodTier.NEAR_FULL,
            PhysicsCollisionLodSystem.resolveTier(settings,
                null,
                new Vector3f(9.0f, 0.0f, 0.0f),
                interests));
        assertEquals(CollisionLodTier.MID_TERRAIN,
            PhysicsCollisionLodSystem.resolveTier(settings,
                null,
                new Vector3f(20.0f, 0.0f, 0.0f),
                interests));
        assertEquals(CollisionLodTier.FAR_SLEEPING,
            PhysicsCollisionLodSystem.resolveTier(settings,
                null,
                new Vector3f(31.0f, 0.0f, 0.0f),
                interests));
    }

    @Test
    void keepsPreviousTierInsideHysteresis() {
        PhysicsSpaceSettings settings = testSettings();
        List<PhysicsWorldResource.VisualInterest> interests = interestsAtOrigin();

        assertEquals(CollisionLodTier.NEAR_FULL,
            PhysicsCollisionLodSystem.resolveTier(settings,
                CollisionLodTier.NEAR_FULL,
                new Vector3f(14.0f, 0.0f, 0.0f),
                interests));
        assertEquals(CollisionLodTier.MID_TERRAIN,
            PhysicsCollisionLodSystem.resolveTier(settings,
                CollisionLodTier.NEAR_FULL,
                new Vector3f(16.0f, 0.0f, 0.0f),
                interests));
        assertEquals(CollisionLodTier.MID_TERRAIN,
            PhysicsCollisionLodSystem.resolveTier(settings,
                CollisionLodTier.MID_TERRAIN,
                new Vector3f(34.0f, 0.0f, 0.0f),
                interests));
        assertEquals(CollisionLodTier.FAR_SLEEPING,
            PhysicsCollisionLodSystem.resolveTier(settings,
                CollisionLodTier.MID_TERRAIN,
                new Vector3f(36.0f, 0.0f, 0.0f),
                interests));
    }

    @Test
    void noInterestsResolveToFarTier() {
        PhysicsSpaceSettings settings = testSettings();

        assertEquals(CollisionLodTier.FAR_SLEEPING,
            PhysicsCollisionLodSystem.resolveTier(settings,
                CollisionLodTier.NEAR_FULL,
                new Vector3f(),
                List.of()));
    }

    @Test
    void persistentBodiesAreNotCollisionLodCandidates() {
        assertFalse(PhysicsCollisionLodSystem.isCollisionLodCandidate(snapshotEntry(
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT,
            PhysicsBodyType.DYNAMIC,
            false)));
    }

    @Test
    void onlyRuntimeDynamicBodiesAreCollisionLodCandidates() {
        assertTrue(PhysicsCollisionLodSystem.isCollisionLodCandidate(snapshotEntry(
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            PhysicsBodyType.DYNAMIC,
            false)));
        assertFalse(PhysicsCollisionLodSystem.isCollisionLodCandidate(snapshotEntry(
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            PhysicsBodyType.STATIC,
            false)));
        assertFalse(PhysicsCollisionLodSystem.isCollisionLodCandidate(snapshotEntry(
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            PhysicsBodyType.DYNAMIC,
            true)));
    }

    @Test
    void tierStateCommitsAfterSuccessfulWorkerMutation() {
        CollisionLodState state = new CollisionLodState();
        SpaceId spaceId = new SpaceId(1);
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        List<CollisionLodUpdate> updates = new ArrayList<>();

        state.recordTier(spaceId, bodyId, CollisionLodTier.MID_TERRAIN, updates);

        assertNull(state.tier(bodyId));
        state.trackPendingMutation(PhysicsMutationHandle.completed("test", null), updates);
        state.refreshPendingMutation();

        assertEquals(CollisionLodTier.MID_TERRAIN, state.tier(bodyId));
        assertFalse(state.hasPendingMutation());
    }

    @Test
    void tierStateRetriesAfterFailedWorkerMutation() {
        CollisionLodState state = new CollisionLodState();
        SpaceId spaceId = new SpaceId(1);
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        List<CollisionLodUpdate> updates = new ArrayList<>();

        assertTrue(state.shouldRefresh(spaceId, 20, 1L));
        state.recordTier(spaceId, bodyId, CollisionLodTier.MID_TERRAIN, updates);
        state.trackPendingMutation(PhysicsMutationHandle.failed("test",
            null,
            new IllegalStateException("boom")),
            updates);
        state.refreshPendingMutation();

        assertNull(state.tier(bodyId));
        assertTrue(state.shouldRefresh(spaceId, 20, 2L));
    }

    @Test
    void restoreClearsTierStateAfterSuccessfulWorkerMutation() {
        CollisionLodState state = new CollisionLodState();
        SpaceId spaceId = new SpaceId(1);
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        List<CollisionLodUpdate> updates = new ArrayList<>();

        state.recordTier(spaceId, bodyId, CollisionLodTier.MID_TERRAIN, updates);
        state.trackPendingMutation(PhysicsMutationHandle.completed("test", null), updates);
        state.refreshPendingMutation();

        updates = new ArrayList<>();
        state.recordRestore(spaceId, bodyId, updates);
        state.trackPendingMutation(PhysicsMutationHandle.completed("test", null), updates);
        state.refreshPendingMutation();

        assertNull(state.tier(bodyId));
    }

    private static PhysicsSpaceSettings testSettings() {
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getCollisionLodSettings().setCollisionLodRadii(10, 30);
        settings.getCollisionLodSettings().setCollisionLodHysteresis(5);
        return settings;
    }

    private static List<PhysicsWorldResource.VisualInterest> interestsAtOrigin() {
        return List.of(new PhysicsWorldResource.VisualInterest(new Vector3f(), null));
    }

    private static PhysicsWorldResource.BodySnapshotEntry snapshotEntry(
        PhysicsBodyKind kind,
        PhysicsBodyPersistenceMode persistenceMode,
        PhysicsBodyType bodyType,
        boolean sensor) {
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        SpaceId spaceId = new SpaceId(1);
        PhysicsBodySnapshot snapshot = new PhysicsBodySnapshot(new Vector3f(),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            bodyType,
            false,
            sensor,
            0.0f,
            ShapeType.BOX,
            new Vector3f(0.5f),
            -1.0f,
            -1.0f,
            PhysicsAxis.Y,
            Float.NaN);
        return new PhysicsWorldResource.BodySnapshotEntry(bodyId, snapshot, spaceId, kind, persistenceMode);
    }
}
