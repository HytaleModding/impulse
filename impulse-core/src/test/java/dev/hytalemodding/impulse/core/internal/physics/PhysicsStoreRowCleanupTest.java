package dev.hytalemodding.impulse.core.internal.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreRowCleanup.BodyEntityRemoval;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
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

    @Nonnull
    private static Ref<PhysicsStore> addIdentityRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid) {
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.entityHolder(store, bodyUuid),
            AddReason.SPAWN);
        assertNotNull(ref);
        return ref;
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
}
