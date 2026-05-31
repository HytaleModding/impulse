package dev.hytalemodding.impulse.core.internal.systems.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsRuntimeSnapshot;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.concurrent.atomic.AtomicInteger;
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
        fixture.runtime.addBody(fixture.space.getId(),
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
        fixture.runtime.addBody(fixture.space.getId(),
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
        RigidBodyKey firstId = fixture.runtime.addBody(fixture.space.getId(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        RigidBodyKey secondId = fixture.runtime.addBody(fixture.space.getId(),
            second,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        fixture.syncPersistentSpaces();
        fixture.persistent.setBodies(new PersistentPhysicsBodyState[] {
            PersistentPhysicsBodyState.from(fixture.runtime.requireBodyRegistration(firstId)),
            PersistentPhysicsBodyState.from(fixture.runtime.requireBodyRegistration(secondId))
        });
        fixture.persistent.markRuntimeSnapshotSynced();

        assertFalse(fixture.persistent.shouldSyncRuntimeSnapshot(20));
        assertFalse(PersistentPhysicsWorldSyncSystem.hasRuntimePersistenceFootprintChanged(
            fixture.persistent,
            PersistentPhysicsRuntimeSnapshot.captureFootprint(fixture.runtime)));

        fixture.space.createFixedJoint(first, second, new Vector3f(), new Vector3f());

        assertTrue(PersistentPhysicsWorldSyncSystem.hasRuntimePersistenceFootprintChanged(
            fixture.persistent,
            PersistentPhysicsRuntimeSnapshot.captureFootprint(fixture.runtime)));
    }

    @Test
    void explicitRuntimeSnapshotSyncCopiesPersistentState() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsWorldSettings settings = fixture.runtime.getWorldSettings();
        settings.setStepSchedulingMode(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT);
        fixture.runtime.setWorldSettings(settings);
        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        fixture.runtime.addBody(fixture.space.getId(),
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
        RigidBodyKey bodyKey = fixture.runtime.addBody(fixture.space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PersistentPhysicsBodyState state =
            PersistentPhysicsBodyState.from(fixture.runtime.requireBodyRegistration(bodyKey));

        PersistentPhysicsBodyHydrationSystem.RestoreBodyResult result =
            PersistentPhysicsBodyHydrationSystem.restoreBodyOnOwner(fixture.runtime, state, bodyKey);

        assertEquals(PersistentPhysicsBodyHydrationSystem.RestoreBodyResult.ALREADY_REGISTERED, result);
        assertEquals(1, fixture.space.bodyCount());
    }

    private static RuntimeFixture createRuntimeFixture() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:persistence-sync-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldRuntimeResource runtime = new PhysicsWorldRuntimeResource();
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        PhysicsSpace space = runtime.createLiveSpace(backend.getId(),
            "test-world",
            settings);
        PersistentPhysicsWorldResource persistent = new PersistentPhysicsWorldResource();
        return new RuntimeFixture(runtime, persistent, space);
    }

    private record RuntimeFixture(PhysicsWorldRuntimeResource runtime,
        PersistentPhysicsWorldResource persistent,
        PhysicsSpace space) {

        private void syncPersistentSpaces() {
            persistent.setSpaces(new PersistentPhysicsSpaceState[] {
                PersistentPhysicsSpaceState.from(space, runtime.getSpaceSettings(space.getId()))
            });
        }
    }
}
