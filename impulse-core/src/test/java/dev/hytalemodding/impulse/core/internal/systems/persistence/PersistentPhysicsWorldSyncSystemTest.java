package dev.hytalemodding.impulse.core.internal.systems.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsRuntimeSnapshot;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PersistentPhysicsWorldSyncSystemTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @Test
    void persistentBodyCountChangeForcesSnapshotBeforeCadence() {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.syncPersistentSpaces();
        fixture.persistent.markRuntimeSnapshotSynced();

        assertFalse(fixture.persistent.shouldSyncRuntimeSnapshot(20));
        assertFalse(PersistentPhysicsWorldSyncSystem.hasRuntimePersistenceFootprintChanged(
            fixture.persistent,
            PersistentPhysicsRuntimeSnapshot.captureFootprint(fixture.runtime)));

        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        fixture.runtime.addBody(fixture.space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        assertTrue(PersistentPhysicsWorldSyncSystem.hasRuntimePersistenceFootprintChanged(
            fixture.persistent,
            PersistentPhysicsRuntimeSnapshot.captureFootprint(fixture.runtime)));
    }

    @Test
    void runtimeOnlyBodiesDoNotChangePersistentFootprint() {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.syncPersistentSpaces();
        fixture.persistent.markRuntimeSnapshotSynced();

        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        fixture.runtime.addBody(fixture.space.id(),
            body,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);

        assertFalse(PersistentPhysicsWorldSyncSystem.hasRuntimePersistenceFootprintChanged(
            fixture.persistent,
            PersistentPhysicsRuntimeSnapshot.captureFootprint(fixture.runtime)));
    }

    @Test
    void persistentJointCountChangeForcesSnapshotBeforeCadence() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsBody first = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey firstId = fixture.runtime.addBody(fixture.space.id(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        RigidBodyKey secondId = fixture.runtime.addBody(fixture.space.id(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        fixture.syncPersistentSpaces();
        fixture.persistent.setBodies(new PersistentPhysicsBodyState[] {
            fixture.bodyState(firstId),
            fixture.bodyState(secondId)
        });
        fixture.persistent.markRuntimeSnapshotSynced();

        assertFalse(fixture.persistent.shouldSyncRuntimeSnapshot(20));
        assertFalse(PersistentPhysicsWorldSyncSystem.hasRuntimePersistenceFootprintChanged(
            fixture.persistent,
            PersistentPhysicsRuntimeSnapshot.captureFootprint(fixture.runtime)));

        PhysicsJoint joint = fixture.space.createFixedJoint(first, second, new Vector3f(), new Vector3f());
        fixture.runtime.addJoint(fixture.space.id(), joint);

        assertTrue(PersistentPhysicsWorldSyncSystem.hasRuntimePersistenceFootprintChanged(
            fixture.persistent,
            PersistentPhysicsRuntimeSnapshot.captureFootprint(fixture.runtime)));
    }

    @Test
    void explicitRuntimeSnapshotSyncCopiesPersistentState() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsWorldSettings settings = fixture.runtime.getWorldSettings();
        settings.setStepSchedulingMode(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT);
        settings.setEventCollectionMode(PhysicsEventCollectionMode.CONTACTS);
        fixture.runtime.setWorldSettings(settings);
        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        fixture.runtime.addBody(fixture.space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        PersistentPhysicsWorldSyncSystem.SyncResult result =
            PersistentPhysicsWorldSyncSystem.syncRuntimeSnapshot(fixture.persistent,
                PersistentPhysicsRuntimeSnapshot.capture(fixture.runtime));

        assertTrue(result.synced());
        assertEquals(1, result.spaces());
        assertEquals(1, result.bodies());
        assertEquals(0, result.joints());
        assertEquals(1, fixture.persistent.getSpaceCount());
        assertEquals(1, fixture.persistent.getBodyCount());
        assertEquals(0, fixture.persistent.getJointCount());
        assertEquals(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT,
            fixture.persistent.getWorldSettings().getStepSchedulingMode());
        assertEquals(PhysicsEventCollectionMode.CONTACTS,
            fixture.persistent.getWorldSettings().getEventCollectionMode());
    }

    @Test
    void explicitRuntimeSnapshotSyncSkipsPendingRestore() {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.persistent.markRuntimeRestorePending();

        PersistentPhysicsWorldSyncSystem.SyncResult result =
            PersistentPhysicsWorldSyncSystem.syncRuntimeSnapshot(fixture.persistent,
                PersistentPhysicsRuntimeSnapshot.capture(fixture.runtime));

        assertFalse(result.synced());
        assertEquals("restore pending", result.skippedReason());
    }

    @Test
    void explicitRuntimeSnapshotSyncSkipsFailedRestore() {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.persistent.failRuntimeRestore("bad persisted state");

        PersistentPhysicsWorldSyncSystem.SyncResult result =
            PersistentPhysicsWorldSyncSystem.syncRuntimeSnapshot(fixture.persistent,
                PersistentPhysicsRuntimeSnapshot.capture(fixture.runtime));

        assertFalse(result.synced());
        assertEquals("restore failed", result.skippedReason());
    }

    @Test
    void bodyHydrationChecksLiveRegistrationBeforeCreatingBackendBody() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        RigidBodyKey bodyKey = fixture.runtime.addBody(fixture.space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PersistentPhysicsBodyState state = fixture.bodyState(bodyKey);

        PersistentPhysicsBodyHydrationSystem.RestoreBodyResult result =
            PersistentPhysicsBodyHydrationSystem.restoreBodyOnOwner(fixture.runtime, state, bodyKey);

        assertEquals(PersistentPhysicsBodyHydrationSystem.RestoreBodyResult.ALREADY_REGISTERED, result);
        assertEquals(1, fixture.space.bodyCount());
    }

    @Test
    void persistentBodyStateCapturesRuntimeMaterialAndCollisionSettings() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsBody body = fixture.space.createBox(1.0f, 2.0f, 3.0f, 7.0f);
        body.setFriction(0.65f);
        body.setRestitution(0.15f);
        body.setDamping(0.2f, 0.3f);
        body.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
        body.setContinuousCollisionEnabled(true);
        RigidBodyKey bodyKey = fixture.runtime.addBody(fixture.space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        PersistentPhysicsBodyState state = fixture.bodyState(bodyKey);

        assertEquals(7.0f, state.getMass(), 0.0001f);
        assertEquals(0.65f, state.getFriction(), 0.0001f);
        assertEquals(0.15f, state.getRestitution(), 0.0001f);
        assertEquals(0.2f, state.getLinearDamping(), 0.0001f);
        assertEquals(0.3f, state.getAngularDamping(), 0.0001f);
        assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY, state.getCollisionGroup());
        assertEquals(PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY,
            state.getCollisionMask());
        assertTrue(state.isContinuousCollisionEnabled());
    }

    @Test
    void restoreTerrainPrewarmTargetsOnlyRestoredDynamicBodies() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsBody fallingBody = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        fallingBody.setPosition(12.0f, 70.0f, -4.0f);
        PhysicsBody staticBody = fixture.space.createBox(0.5f, 0.5f, 0.5f, 0.0f);
        staticBody.setBodyType(PhysicsBodyType.STATIC);
        staticBody.setPosition(40.0f, 64.0f, 40.0f);
        RigidBodyKey fallingBodyKey = fixture.runtime.addBody(fixture.space.id(),
            fallingBody,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        RigidBodyKey staticBodyKey = fixture.runtime.addBody(fixture.space.id(),
            staticBody,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PersistentPhysicsBodyState fallingState = fixture.bodyState(fallingBodyKey);
        PersistentPhysicsBodyState staticState = fixture.bodyState(staticBodyKey);
        assertEquals(PhysicsBodyType.DYNAMIC, fallingState.getBodyType());
        assertEquals(PhysicsBodyType.STATIC, staticState.getBodyType());

        Map<Integer, List<Vector3d>> targets =
            PersistentPhysicsRestoreTerrainPrewarm.dynamicPrewarmTargetsBySpace(
                new PersistentPhysicsBodyState[] {
                    fallingState,
                    staticState
                },
                4);

        assertEquals(List.of(new Vector3d(12.0, 70.0, -4.0)),
            targets.get(fixture.space.id().value()));
    }

    @Test
    void restoreTerrainPrewarmExpandsDownwardForFallingBodies() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsBody fallingBody = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        fallingBody.setPosition(12.0f, 20.0f, -4.0f);
        fallingBody.setLinearVelocity(0.0f, -12.0f, 0.0f);
        RigidBodyKey fallingBodyKey = fixture.runtime.addBody(fixture.space.id(),
            fallingBody,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PersistentPhysicsBodyState fallingState = fixture.bodyState(fallingBodyKey);

        Map<Integer, List<Vector3d>> targets =
            PersistentPhysicsRestoreTerrainPrewarm.dynamicPrewarmTargetsBySpace(
                new PersistentPhysicsBodyState[] {fallingState},
                4);

        assertEquals(List.of(new Vector3d(12.0, 20.0, -4.0),
                new Vector3d(12.0, 12.0, -4.0),
                new Vector3d(12.0, 4.0, -4.0)),
            targets.get(fixture.space.id().value()));
    }

    private static RuntimeFixture createRuntimeFixture() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:persistence-sync-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        LegacyLiveHandleTestResource runtime = new LegacyLiveHandleTestResource();
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        PhysicsSpace space = runtime.createLiveSpace(backend.getId(),
            "test-world",
            settings);
        PersistentPhysicsWorldResource persistent = new PersistentPhysicsWorldResource();
        return new RuntimeFixture(runtime, persistent, space);
    }

    private record RuntimeFixture(LegacyLiveHandleTestResource runtime,
        PersistentPhysicsWorldResource persistent,
        PhysicsSpace space) {

        private void syncPersistentSpaces() {
            persistent.setSpaces(new PersistentPhysicsSpaceState[] {
                PersistentPhysicsSpaceState.from(runtime.requireSpaceBinding(space.id()),
                    runtime.getSpaceSettings(space.id()))
            });
        }

        private PersistentPhysicsBodyState bodyState(RigidBodyKey bodyKey) {
            return PersistentPhysicsBodyState.from(runtime.requireBodyRegistration(bodyKey),
                runtime.getBodySnapshot(bodyKey));
        }
    }
}
