package dev.hytalemodding.impulse.core.internal.systems.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsJointState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsRuntimeSnapshot;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsRuntimeSupport;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsSpaceState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
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
    void runtimeSnapshotCaptureUsesLiveBodyPoseWhenReaderSnapshotIsStale() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(1.0f, 2.0f, 3.0f);
        RigidBodyKey bodyKey = fixture.runtime.addBody(fixture.space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        fixture.runtime.refreshBodySnapshots();

        body.setPosition(9.0f, 8.0f, 7.0f);

        PhysicsBodySnapshot readerSnapshot = fixture.runtime.getBodySnapshot(bodyKey);
        assertEquals(1.0f, readerSnapshot.positionX(), 0.0001f);
        assertEquals(2.0f, readerSnapshot.positionY(), 0.0001f);
        assertEquals(3.0f, readerSnapshot.positionZ(), 0.0001f);

        PersistentPhysicsRuntimeSnapshot snapshot =
            PersistentPhysicsRuntimeSnapshot.capture(fixture.runtime);
        PersistentPhysicsBodyState state = snapshot.getBodies()[0];

        assertEquals(9.0f, state.getPosition().x, 0.0001f);
        assertEquals(8.0f, state.getPosition().y, 0.0001f);
        assertEquals(7.0f, state.getPosition().z, 0.0001f);
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
    void softRestoreSkipsDoNotBlockRuntimeSnapshotTickPolicy() {
        RuntimeFixture fixture = createRuntimeFixture();

        fixture.persistent.recordRuntimeBodySkipped("missing entity");

        assertFalse(PersistentPhysicsWorldSyncSystem.shouldSkipRuntimeSnapshotTick(fixture.persistent));

        fixture.persistent.markRuntimeRestorePending();

        assertTrue(PersistentPhysicsWorldSyncSystem.shouldSkipRuntimeSnapshotTick(fixture.persistent));
    }

    @Test
    void runtimeSnapshotSyncSkipsChangedRestoreGeneration() {
        RuntimeFixture fixture = createRuntimeFixture();
        PhysicsBody body = fixture.space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        fixture.runtime.addBody(fixture.space.id(),
            body,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PersistentPhysicsRuntimeSnapshot snapshot =
            PersistentPhysicsRuntimeSnapshot.capture(fixture.runtime);
        long capturedGeneration = fixture.persistent.runtimeRestoreGeneration();

        fixture.persistent.markRuntimeRestorePending();
        fixture.persistent.clearRuntimeRestorePending();

        PersistentPhysicsWorldSyncSystem.SyncResult result =
            PersistentPhysicsWorldSyncSystem.syncRuntimeSnapshot(fixture.persistent,
                snapshot,
                capturedGeneration);

        assertFalse(result.synced());
        assertEquals("restore generation changed", result.skippedReason());
        assertEquals(0, fixture.persistent.getBodyCount());
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
    void bodyHydrationRemovesBackendBodyWhenRegistrationFails() {
        BackendRuntimeFixture fixture = createBackendRuntimeFixture("body-cleanup");
        FailingRegistrationResource runtime = fixture.runtime();
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000101"));
        PersistentPhysicsBodyState state = persistentBodyState(fixture.spaceId(), bodyKey);
        runtime.failBodyRegistration = true;

        assertThrows(IllegalStateException.class,
            () -> PersistentPhysicsBodyHydrationSystem.restoreBodyOnOwner(runtime, state, bodyKey));

        assertEquals(0, fixture.backendRuntime().bodyCount(fixture.spaceId().value()));
    }

    @Test
    void jointHydrationRemovesBackendJointWhenRegistrationFails() {
        BackendRuntimeFixture fixture = createBackendRuntimeFixture("joint-cleanup");
        FailingRegistrationResource runtime = fixture.runtime();
        RigidBodyKey bodyAKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000102"));
        RigidBodyKey bodyBKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000103"));
        PhysicsSpaceBinding binding = runtime.requireSpaceBinding(fixture.spaceId());
        BackendBodyHandle bodyAHandle = createBackendBox(binding);
        BackendBodyHandle bodyBHandle = createBackendBox(binding);
        runtime.addBodyOnOwner(bodyAKey,
            fixture.spaceId(),
            bodyAHandle,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        runtime.addBodyOnOwner(bodyBKey,
            fixture.spaceId(),
            bodyBHandle,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        PersistentPhysicsJointState state = new PersistentPhysicsJointState();
        state.setSpaceId(fixture.spaceId().value());
        state.setBodyAKey(bodyAKey);
        state.setBodyBKey(bodyBKey);
        state.setType(PhysicsJointType.FIXED);
        runtime.failJointRegistration = true;

        assertThrows(IllegalStateException.class,
            () -> PersistentPhysicsRuntimeSupport.createJoint(runtime,
                binding,
                state,
                bodyAKey,
                runtime.requireBodyRegistration(bodyAKey),
                bodyBKey,
                runtime.requireBodyRegistration(bodyBKey)));

        assertEquals(0, fixture.backendRuntime().jointCount(fixture.spaceId().value()));
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

    private static BackendRuntimeFixture createBackendRuntimeFixture(@Nonnull String name) {
        BackendId backendId =
            new BackendId("test:persistence-" + name + "-" + BACKEND_COUNTER.incrementAndGet());
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(backendId, false, false);
        Impulse.registerRuntimeProvider(provider);
        FailingRegistrationResource runtime = new FailingRegistrationResource();
        SpaceId spaceId = runtime.createSpace(backendId,
            "test-world",
            PhysicsSpaceSettings.defaults());
        return new BackendRuntimeFixture(runtime, provider.createdRuntimes().getFirst(), spaceId);
    }

    @Nonnull
    private static PersistentPhysicsBodyState persistentBodyState(@Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyKey) {
        PhysicsBodyRegistration registration = new PhysicsBodyRegistration(bodyKey,
            new BackendBodyHandle(1L),
            spaceId,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT);
        return PersistentPhysicsBodyState.from(registration,
            new PhysicsBodySnapshot(new Vector3f(),
                new Quaternionf(),
                new Vector3f(),
                new Vector3f(),
                PhysicsBodyType.DYNAMIC,
                false,
                false,
                1.0f,
                ShapeType.BOX,
                new Vector3f(0.5f, 0.5f, 0.5f),
                0.0f,
                0.0f,
                PhysicsAxis.Y));
    }

    @Nonnull
    private static BackendBodyHandle createBackendBox(@Nonnull PhysicsSpaceBinding binding) {
        return new BackendBodyHandle(binding.runtime().createBody(binding.backendSpaceHandle().value(),
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.BODY_DYNAMIC,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f));
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

    private record BackendRuntimeFixture(@Nonnull FailingRegistrationResource runtime,
                                         @Nonnull FakePhysicsBackendRuntime backendRuntime,
                                         @Nonnull SpaceId spaceId) {
    }

    private static final class FailingRegistrationResource extends PhysicsWorldRuntimeResource {

        private boolean failBodyRegistration;
        private boolean failJointRegistration;

        @Nonnull
        @Override
        public RigidBodyKey addBodyOnOwner(@Nonnull RigidBodyKey bodyKey,
            @Nonnull SpaceId spaceId,
            @Nonnull BackendBodyHandle backendBodyHandle,
            @Nonnull PhysicsBodyKind kind,
            @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
            if (failBodyRegistration) {
                throw new IllegalStateException("forced body registration failure");
            }
            return super.addBodyOnOwner(bodyKey, spaceId, backendBodyHandle, kind, persistenceMode);
        }

        @Nonnull
        @Override
        public JointKey addJointOnOwner(@Nonnull JointKey jointKey,
            @Nonnull SpaceId spaceId,
            @Nonnull BackendJointHandle backendJointHandle,
            @Nonnull RigidBodyKey bodyA,
            @Nonnull RigidBodyKey bodyB,
            @Nonnull JointType type,
            float anchorAX,
            float anchorAY,
            float anchorAZ,
            float anchorBX,
            float anchorBY,
            float anchorBZ,
            float axisX,
            float axisY,
            float axisZ,
            float restLength,
            float stiffness,
            float damping,
            float lowerLimit,
            float upperLimit,
            boolean motorEnabled,
            float motorTargetVelocity,
            float motorMaxForce) {
            if (failJointRegistration) {
                throw new IllegalStateException("forced joint registration failure");
            }
            return super.addJointOnOwner(jointKey,
                spaceId,
                backendJointHandle,
                bodyA,
                bodyB,
                type,
                anchorAX,
                anchorAY,
                anchorAZ,
                anchorBX,
                anchorBY,
                anchorBZ,
                axisX,
                axisY,
                axisZ,
                restLength,
                stiffness,
                damping,
                lowerLimit,
                upperLimit,
                motorEnabled,
                motorTargetVelocity,
                motorMaxForce);
        }
    }
}
