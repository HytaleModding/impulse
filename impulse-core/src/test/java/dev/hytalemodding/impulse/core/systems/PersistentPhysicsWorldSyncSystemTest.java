package dev.hytalemodding.impulse.core.systems;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.InMemoryPhysicsBackend;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
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

    private static RuntimeFixture createRuntimeFixture() {
        InMemoryPhysicsBackend backend =
            new InMemoryPhysicsBackend("test:persistence-sync-" + BACKEND_COUNTER.incrementAndGet());
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
