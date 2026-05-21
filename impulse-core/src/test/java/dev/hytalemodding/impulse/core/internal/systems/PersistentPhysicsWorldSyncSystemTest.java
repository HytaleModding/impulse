package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
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
            fixture.runtime));

        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        fixture.runtime.addBody(fixture.space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        assertTrue(PersistentPhysicsWorldSyncSystem.hasRuntimePersistenceFootprintChanged(
            fixture.persistent,
            fixture.runtime));
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
            fixture.runtime));
    }

    @Test
    void persistentJointCountChangeForcesSnapshotBeforeCadence() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsBody first = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBodyId firstId = fixture.runtime.addBody(fixture.space.getId(),
            first,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PhysicsBodyId secondId = fixture.runtime.addBody(fixture.space.getId(),
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
            fixture.runtime));

        fixture.space.createFixedJoint(first, second, new Vector3f(), new Vector3f());

        assertTrue(PersistentPhysicsWorldSyncSystem.hasRuntimePersistenceFootprintChanged(
            fixture.persistent,
            fixture.runtime));
    }

    @Test
    void explicitRuntimeSnapshotSyncCopiesPersistentState() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        fixture.runtime.addBody(fixture.space.getId(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        PersistentPhysicsWorldSyncSystem.SyncResult result =
            PersistentPhysicsWorldSyncSystem.syncRuntimeSnapshot(fixture.persistent, fixture.runtime);

        assertTrue(result.synced());
        assertEquals(1, result.spaces());
        assertEquals(1, result.bodies());
        assertEquals(0, result.joints());
        assertEquals(1, fixture.persistent.getSpaceCount());
        assertEquals(1, fixture.persistent.getBodyCount());
        assertEquals(0, fixture.persistent.getJointCount());
    }

    @Test
    void explicitRuntimeSnapshotSyncSkipsPendingRestore() {
        RuntimeFixture fixture = createRuntimeFixture();
        fixture.persistent.markRuntimeRestorePending();

        PersistentPhysicsWorldSyncSystem.SyncResult result =
            PersistentPhysicsWorldSyncSystem.syncRuntimeSnapshot(fixture.persistent, fixture.runtime);

        assertFalse(result.synced());
        assertEquals("restore pending", result.skippedReason());
    }

    private static RuntimeFixture createRuntimeFixture() {
        FakePhysicsBackend backend =
            new FakePhysicsBackend("test:persistence-sync-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(backend);

        PhysicsWorldResource runtime = new PhysicsWorldResource();
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        PhysicsSpace space = runtime.createSpace(backend.getId(),
            "test-world",
            settings,
            true);
        PersistentPhysicsWorldResource persistent = new PersistentPhysicsWorldResource();
        persistent.setDefaultSpaceId(space.getId().value());
        return new RuntimeFixture(runtime, persistent, space);
    }

    private record RuntimeFixture(PhysicsWorldResource runtime,
        PersistentPhysicsWorldResource persistent,
        PhysicsSpace space) {

        private void syncPersistentSpaces() {
            persistent.setSpaces(new PersistentPhysicsSpaceState[] {
                PersistentPhysicsSpaceState.from(space, runtime.getSpaceSettings(space.getId()))
            });
        }
    }
}
