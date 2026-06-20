package dev.hytalemodding.impulse.core.internal.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreRowCleanup.BodyEntityRemoval;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsStoreRowCleanupTest {

    @Test
    void clearBodyCopiedStateRemovesMultipleBodiesWithoutRemovingRows() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("row-cleanup-batch-copied-state")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(1);
            UUID firstBodyUuid = uuid(2);
            UUID secondBodyUuid = uuid(3);
            UUID retainedBodyUuid = uuid(4);
            Ref<PhysicsStore> firstBodyRef = addIdentityRow(store, firstBodyUuid);
            Ref<PhysicsStore> secondBodyRef = addIdentityRow(store, secondBodyUuid);
            Ref<PhysicsStore> retainedBodyRef = addIdentityRow(store, retainedBodyUuid);
            publishCopiedState(store,
                spaceUuid,
                firstBodyUuid,
                firstBodyRef,
                secondBodyUuid,
                secondBodyRef,
                retainedBodyUuid,
                retainedBodyRef);

            PhysicsStoreRowCleanup.clearBodyCopiedState(store,
                List.of(new BodyEntityRemoval(firstBodyUuid, firstBodyRef, null),
                    new BodyEntityRemoval(secondBodyUuid, secondBodyRef, null)));

            PhysicsSnapshotResource snapshots =
                store.getResource(PhysicsSnapshotResource.getResourceType());
            assertNull(snapshots.getBody(firstBodyUuid));
            assertNull(snapshots.getBody(secondBodyUuid));
            assertNotNull(snapshots.getBody(retainedBodyUuid));
            assertNull(PhysicsBodies.spaceId(store, firstBodyUuid));
            assertNull(PhysicsBodies.spaceId(store, secondBodyUuid));
            assertEquals(new SpaceId(42), PhysicsBodies.spaceId(store, retainedBodyUuid));
            assertEquals(1, PhysicsBodies.registrationCount(store));
            assertNotNull(store.getComponent(firstBodyRef, UuidComponent.getComponentType()));
            assertNotNull(store.getComponent(secondBodyRef, UuidComponent.getComponentType()));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void refreshIdentityAndRuntimeRefsRebuildsLargeUuidIndexWithoutParallelMapWrites() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("row-cleanup-refresh-large-index")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            List<UUID> bodyUuids = new ArrayList<>();
            for (int index = 0; index < 6000; index++) {
                UUID bodyUuid = uuid(1000L + index);
                bodyUuids.add(bodyUuid);
                addIdentityRow(store, bodyUuid);
            }

            for (int index = 0; index < 20; index++) {
                PhysicsStoreRowCleanup.refreshIdentityAndRuntimeRefs(store);
            }

            PhysicsIdentityIndexResource identity = store.getResource(
                PhysicsIdentityIndexResource.getResourceType());
            for (UUID bodyUuid : bodyUuids) {
                assertNotNull(identity.getByUuid(bodyUuid));
            }
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void removeRuntimeBodyRejectsStaleRefThatNoLongerMatchesUuid() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("row-cleanup-stale-body-ref")),
            EmptyResourceStorage.get());
        try {
            RuntimeFixture fixture = addBoundSpace(store,
                uuid(30),
                new BackendId("test:row-cleanup-body"));
            UUID firstBodyUuid = uuid(31);
            UUID secondBodyUuid = uuid(32);
            Ref<PhysicsStore> firstBodyRef = addIdentityRow(store, firstBodyUuid);
            Ref<PhysicsStore> secondBodyRef = addIdentityRow(store, secondBodyUuid);
            BackendBodyHandle firstHandle = new BackendBodyHandle(301L);
            BackendBodyHandle secondHandle = new BackendBodyHandle(302L);
            PhysicsRuntimeResource runtime =
                store.getResource(PhysicsRuntimeResource.getResourceType());
            bindBody(runtime, fixture, firstBodyUuid, firstBodyRef, firstHandle);
            bindBody(runtime, fixture, secondBodyUuid, secondBodyRef, secondHandle);

            boolean removed = PhysicsStoreRowCleanup.removeRuntimeBody(runtime,
                store.getResource(PhysicsIdentityIndexResource.getResourceType()),
                firstBodyUuid,
                secondBodyRef);

            assertFalse(removed);
            assertEquals(firstHandle, runtime.getBodyHandle(firstBodyRef));
            assertEquals(secondHandle, runtime.getBodyHandle(secondBodyRef));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void removeRuntimeJointRejectsStaleRefThatNoLongerMatchesUuid() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("row-cleanup-stale-joint-ref")),
            EmptyResourceStorage.get());
        try {
            RuntimeFixture fixture = addBoundSpace(store,
                uuid(40),
                new BackendId("test:row-cleanup-joint"));
            UUID firstJointUuid = uuid(41);
            UUID secondJointUuid = uuid(42);
            Ref<PhysicsStore> firstJointRef = addIdentityRow(store, firstJointUuid);
            Ref<PhysicsStore> secondJointRef = addIdentityRow(store, secondJointUuid);
            BackendJointHandle firstHandle = new BackendJointHandle(401L);
            BackendJointHandle secondHandle = new BackendJointHandle(402L);
            PhysicsRuntimeResource runtime =
                store.getResource(PhysicsRuntimeResource.getResourceType());
            bindJoint(runtime, fixture, firstJointUuid, firstJointRef, firstHandle);
            bindJoint(runtime, fixture, secondJointUuid, secondJointRef, secondHandle);

            boolean removed = PhysicsStoreRowCleanup.removeRuntimeJoint(runtime,
                store.getResource(PhysicsIdentityIndexResource.getResourceType()),
                firstJointUuid,
                secondJointRef);

            assertFalse(removed);
            assertEquals(firstHandle, runtime.getJointHandle(firstJointRef));
            assertEquals(secondHandle, runtime.getJointHandle(secondJointRef));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static Ref<PhysicsStore> addIdentityRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid) {
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.entityHolder(store, bodyUuid),
            AddReason.SPAWN);
        assertNotNull(ref);
        return ref;
    }

    @Nonnull
    private static RuntimeFixture addBoundSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull BackendId backendId) {
        Ref<PhysicsStore> spaceRef = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(backendId, new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        assertNotNull(spaceRef);
        FakePhysicsBackendRuntime backendRuntime = (FakePhysicsBackendRuntime)
            new FakePhysicsBackendRuntimeProvider(backendId, false, false).createRuntime();
        BackendSpaceHandle spaceHandle =
            new BackendSpaceHandle(backendRuntime.createSpace(new SpaceId(42)));
        PhysicsRuntimeResource runtime =
            store.getResource(PhysicsRuntimeResource.getResourceType());
        runtime.putRuntime(backendId, backendRuntime);
        runtime.putSpaceHandle(spaceRef, backendId, spaceHandle);
        runtime.putSpaceMetadata(backendId, spaceHandle, spaceUuid, spaceRef);
        return new RuntimeFixture(spaceUuid, backendId, spaceRef, spaceHandle);
    }

    private static void bindBody(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull RuntimeFixture fixture,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull BackendBodyHandle bodyHandle) {
        runtime.putBodyHandle(bodyRef, fixture.spaceRef(), fixture.spaceHandle(), bodyHandle);
        runtime.putBodySnapshotMetadata(fixture.backendId(),
            fixture.spaceHandle(),
            bodyHandle,
            bodyUuid,
            bodyRef,
            fixture.spaceUuid());
    }

    private static void bindJoint(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull RuntimeFixture fixture,
        @Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull BackendJointHandle jointHandle) {
        runtime.putJointHandle(jointRef, fixture.spaceRef(), fixture.spaceHandle(), jointHandle);
        runtime.putJointMetadata(fixture.backendId(),
            fixture.spaceHandle(),
            jointHandle,
            jointUuid,
            jointRef);
    }

    private static void publishCopiedState(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull UUID firstBodyUuid,
        @Nonnull Ref<PhysicsStore> firstBodyRef,
        @Nonnull UUID secondBodyUuid,
        @Nonnull Ref<PhysicsStore> secondBodyRef,
        @Nonnull UUID retainedBodyUuid,
        @Nonnull Ref<PhysicsStore> retainedBodyRef) {
        store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .putSpace(new SpaceId(42), spaceUuid);
        store.getResource(PhysicsSnapshotResource.getResourceType())
            .publish(new PhysicsSnapshotFrame(1L,
                0.05f,
                List.of(snapshot(firstBodyRef, firstBodyUuid, spaceUuid),
                    snapshot(secondBodyRef, secondBodyUuid, spaceUuid),
                    snapshot(retainedBodyRef, retainedBodyUuid, spaceUuid))));
    }

    @Nonnull
    private static PhysicsBodySnapshot snapshot(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid) {
        return PhysicsBodySnapshot.of(bodyRef,
            bodyUuid,
            spaceUuid,
            PhysicsBodyType.DYNAMIC,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            false);
    }

    @Nonnull
    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }

    private static void markCurrentThreadAsWorldThread(@Nonnull Store<PhysicsStore> store) {
        try {
            Method setThread = TickingThread.class.getDeclaredMethod("setThread", Thread.class);
            setThread.setAccessible(true);
            setThread.invoke(store.getExternalData().getWorld(), Thread.currentThread());
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not mark test world thread", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Could not mark test world thread",
                exception.getTargetException());
        }
    }

    private record RuntimeFixture(@Nonnull UUID spaceUuid,
                                  @Nonnull BackendId backendId,
                                  @Nonnull Ref<PhysicsStore> spaceRef,
                                  @Nonnull BackendSpaceHandle spaceHandle) {
    }
}
