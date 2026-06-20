package dev.hytalemodding.impulse.core.plugin.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsBodiesTest {

    @Test
    void registrationViewsAreDerivedFromSnapshotsWithCompatibleSpaces() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("physics-bodies-snapshot-index")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            UUID firstSpaceUuid = uuid(1);
            UUID secondSpaceUuid = uuid(2);
            UUID unmappedSpaceUuid = uuid(3);
            SpaceId firstSpaceId = new SpaceId(41);
            SpaceId secondSpaceId = new SpaceId(42);
            Ref<PhysicsStore> firstBodyRef = new TestRef(store, 11);
            Ref<PhysicsStore> secondBodyRef = new TestRef(store, 12);
            Ref<PhysicsStore> unmappedBodyRef = new TestRef(store, 13);
            UUID firstBodyUuid = uuid(101);
            UUID secondBodyUuid = uuid(102);
            UUID unmappedBodyUuid = uuid(103);

            store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
                .putSpace(firstSpaceId, firstSpaceUuid);
            store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
                .putSpace(secondSpaceId, secondSpaceUuid);
            store.getResource(PhysicsSnapshotResource.getResourceType())
                .publish(new PhysicsSnapshotFrame(1L,
                    0.05f,
                    List.of(snapshot(firstBodyRef, firstBodyUuid, firstSpaceUuid),
                        snapshot(secondBodyRef, secondBodyUuid, secondSpaceUuid),
                        snapshot(unmappedBodyRef, unmappedBodyUuid, unmappedSpaceUuid))));

            assertEquals(firstSpaceId, PhysicsBodies.spaceId(store, firstBodyUuid));
            assertEquals(secondSpaceId, PhysicsBodies.spaceId(store, secondBodyRef));
            assertNull(PhysicsBodies.spaceId(store, unmappedBodyUuid));
            assertTrue(PhysicsBodies.isRegistered(store, firstBodyUuid));
            assertTrue(PhysicsBodies.isRegistered(store, secondBodyRef));
            assertFalse(PhysicsBodies.isRegistered(store, unmappedBodyUuid));
            assertEquals(List.of(firstBodyUuid, secondBodyUuid),
                List.copyOf(PhysicsBodies.bodyUuids(store)));
            assertEquals(2, PhysicsBodies.registrationCount(store));
            assertEquals(1, PhysicsBodies.registrationCount(store, firstSpaceId));
            assertEquals(1, PhysicsBodies.registrationCount(store, secondSpaceId));
            assertEquals(0, PhysicsBodies.registrationCount(store, new SpaceId(404)));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void bodyRegistrationResourceTypeIsNotExposed() {
        assertThrows(NoSuchMethodException.class,
            () -> PhysicsResourceTypes.class.getDeclaredMethod("bodyRegistrationResourceType"));
    }

    @Nonnull
    private static UUID uuid(long lowBits) {
        return new UUID(0L, lowBits);
    }

    @Nonnull
    private static PhysicsBodySnapshot snapshot(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid) {
        return new PhysicsBodySnapshot(bodyRef,
            bodyUuid,
            spaceUuid,
            PhysicsBodyType.DYNAMIC,
            new Vector3f(),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            0.0f,
            false);
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

    private static final class TestRef extends Ref<PhysicsStore> {

        private TestRef(@Nonnull Store<PhysicsStore> store, int index) {
            super(store, index);
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }
}
