package dev.hytalemodding.impulse.core.internal.commands.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStoreTypes;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
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

class SpaceCommandDeleteTest {

    private static final BackendId BACKEND_ID = new BackendId("test:space-delete");

    @Test
    void deleteCoreReportsInvalidMissingAndUnboundSpaces() {
        StoreFixture fixture = store("space-delete-invalid");
        try {
            Store<PhysicsStore> store = fixture.store();
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(92);
            store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
                .putSpace(new SpaceId(92), spaceUuid);

            assertEquals(SpaceDeleteSupport.DeleteOutcome.INVALID,
                deleteOnWorldThread(store, 0).outcome());
            assertEquals(SpaceDeleteSupport.DeleteOutcome.MISSING,
                deleteOnWorldThread(store, 93).outcome());
            assertEquals(SpaceDeleteSupport.DeleteOutcome.UNBOUND,
                deleteOnWorldThread(store, 92).outcome());
        } finally {
            fixture.close();
        }
    }

    @Test
    void deleteCoreRemovesEmptySpaceOnWorldThread() {
        StoreFixture fixture = store("space-delete-core");
        try {
            Store<PhysicsStore> store = fixture.store();
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(91);
            Ref<PhysicsStore> spaceRef = addSpace(store, spaceUuid);
            bindSpaceId(store, new SpaceId(91), spaceUuid, spaceRef);

            SpaceDeleteSupport.DeleteResult result = deleteOnWorldThread(store, 91);

            assertEquals(SpaceDeleteSupport.DeleteOutcome.DELETED, result.outcome());
            assertEquals(91, result.rawSpaceId());
            assertEquals(0, result.backendBodies());
            assertEquals(0, result.joints());
            assertFalse(spaceRef.isValid());
        } finally {
            fixture.close();
        }
    }

    @Test
    void deleteCoreRejectsRegisteredBodies() {
        StoreFixture fixture = store("space-delete-not-empty");
        try {
            Store<PhysicsStore> store = fixture.store();
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(94);
            Ref<PhysicsStore> spaceRef = addSpace(store, spaceUuid);
            bindSpaceId(store, new SpaceId(94), spaceUuid, spaceRef);
            store.getResource(PhysicsSnapshotResource.getResourceType())
                .publish(new PhysicsSnapshotFrame(1L,
                    0.05f,
                    List.of(PhysicsBodySnapshot.of(uuid(95),
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
                        false))));

            SpaceDeleteSupport.DeleteResult result = deleteOnWorldThread(store, 94);

            assertEquals(SpaceDeleteSupport.DeleteOutcome.NOT_EMPTY, result.outcome());
            assertEquals(1, result.registeredBodies());
            assertEquals(0, result.backendBodies());
            assertEquals(0, result.joints());
            assertTrue(spaceRef.isValid());
        } finally {
            fixture.close();
        }
    }

    @Test
    void deleteCoreRemovesStreamingChunkCollisionBodies() {
        StoreFixture fixture = store("space-delete-streaming-chunks");
        try {
            Store<PhysicsStore> store = fixture.store();
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(96);
            Ref<PhysicsStore> spaceRef = addSpace(store, spaceUuid);
            bindSpaceId(store, new SpaceId(96), spaceUuid, spaceRef);
            UUID chunkBodyUuid = uuid(97);
            Ref<PhysicsStore> chunkBodyRef = addChunkCollisionBody(store,
                spaceUuid,
                spaceRef,
                chunkBodyUuid);
            store.getResource(PhysicsSnapshotResource.getResourceType())
                .publish(new PhysicsSnapshotFrame(1L,
                    0.05f,
                    List.of(snapshot(chunkBodyUuid, spaceUuid))));
            assertEquals(1, PhysicsBodies.registrationCount(store, new SpaceId(96)));

            SpaceDeleteSupport.DeleteResult result = deleteOnWorldThread(store, 96);

            assertEquals(SpaceDeleteSupport.DeleteOutcome.DELETED, result.outcome());
            assertEquals(96, result.rawSpaceId());
            assertFalse(spaceRef.isValid());
            assertFalse(chunkBodyRef.isValid());
            assertEquals(0, PhysicsBodies.registrationCount(store, new SpaceId(96)));
        } finally {
            fixture.close();
        }
    }

    private static void bindSpaceId(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .putSpace(spaceId, spaceUuid);
        store.getExternalData().putRefForUUID(spaceUuid, spaceRef);
    }

    @Nonnull
    private static StoreFixture store(@Nonnull String worldName) {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world(worldName)),
            EmptyResourceStorage.get());
        return new StoreFixture(registry, store);
    }

    @Nonnull
    private static SpaceDeleteSupport.DeleteResult deleteOnWorldThread(
        @Nonnull Store<PhysicsStore> store,
        int rawSpaceId) {
        return SpaceDeleteSupport.deleteOnWorldThread(store.getExternalData().getWorld(),
            store,
            rawSpaceId);
    }

    @Nonnull
    private static Ref<PhysicsStore> addSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(BACKEND_ID, new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        assertNotNull(ref);
        return ref;
    }

    @Nonnull
    private static Ref<PhysicsStore> addChunkCollisionBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid) {
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.entityHolder(store,
                bodyUuid),
            AddReason.SPAWN);
        assertNotNull(ref);
        BodyComponent body = new BodyComponent(spaceUuid);
        body.setSpaceRef(spaceRef);
        store.putComponent(ref, BodyComponent.getComponentType(), body);
        store.putComponent(ref,
            ChunkCollisionSourceComponent.getComponentType(),
            new ChunkCollisionSourceComponent("test-source",
                0,
                0,
                0,
                "test-payload",
                PartKind.BOX,
                0));
        store.getExternalData().putRefForUUID(bodyUuid, ref);
        return ref;
    }

    @Nonnull
    private static PhysicsBodySnapshot snapshot(@Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid) {
        return PhysicsBodySnapshot.of(bodyUuid,
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

    private record StoreFixture(@Nonnull ComponentRegistry<PhysicsStore> registry,
                                @Nonnull Store<PhysicsStore> store) {

        private void close() {
            if (!store.isShutdown()) {
                registry.removeStore(store);
            }
            registry.shutdown();
            PhysicsChunkStoreTypes.clearPhysicsStoreResourceTypes();
        }
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
