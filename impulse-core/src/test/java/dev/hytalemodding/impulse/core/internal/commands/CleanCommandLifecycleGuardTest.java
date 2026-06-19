package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class CleanCommandLifecycleGuardTest {

    @Test
    void cleanCommandDoesNotRemoveEveryBodyAttachmentEntity() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/commands/CleanCommand.java"));

        assertTrue(source.contains("cleanAttachedEntity("));
        assertTrue(source.contains("shouldRemoveEntityWhenBodyMissing()"));
        assertFalse(source.contains("removedEntities.incrementAndGet(REMOVED_BODY_ENTITIES);\n"
            + "                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), "
            + "RemoveReason.REMOVE);"));
    }

    @Test
    void radiusCleanSelectsOnlyNormalBodySnapshots() throws Exception {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("clean-radius-kind-filter")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            UUID bodyUuid = UUID.randomUUID();
            UUID terrainUuid = UUID.randomUUID();
            UUID spaceUuid = UUID.randomUUID();
            publishSnapshots(store, bodyUuid, terrainUuid, spaceUuid);
            publishRegistrations(store, bodyUuid, terrainUuid);

            Set<?> selected = selectBodyUuidsNear(store, new Vector3d(), 10.0f);

            assertEquals(Set.of(bodyUuid), selected);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    private static void publishSnapshots(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID terrainUuid,
        @Nonnull UUID spaceUuid) {
        store.getResource(PhysicsSnapshotResource.getResourceType())
            .publish(new PhysicsSnapshotFrame(1L,
                0.05f,
                List.of(snapshot(bodyUuid, spaceUuid), snapshot(terrainUuid, spaceUuid))));
    }

    private static void publishRegistrations(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID terrainUuid) {
        store.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .publish(1L,
                List.of(publication(1, bodyUuid, PhysicsBodyKind.BODY),
                    publication(2, terrainUuid, PhysicsBodyKind.TERRAIN)));
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
    private static PhysicsBodyRegistrationResource.BodyRegistrationPublication publication(
        int rowIndex,
        @Nonnull UUID bodyUuid,
        @Nonnull PhysicsBodyKind kind) {
        return new PhysicsBodyRegistrationResource.BodyRegistrationPublication(
            new TestPhysicsRef(rowIndex),
            new PhysicsBodyRegistrationView(bodyUuid,
                new SpaceId(1),
                kind,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY));
    }

    @Nonnull
    private static Set<?> selectBodyUuidsNear(@Nonnull Store<PhysicsStore> store,
        @Nonnull Vector3d center,
        float radius) throws Exception {
        Method selectBodiesNear = CleanCommand.class.getDeclaredMethod("selectBodiesNear",
            Store.class,
            Vector3d.class,
            float.class);
        selectBodiesNear.setAccessible(true);
        Object selected = selectBodiesNear.invoke(null, store, center, radius);
        Method bodyUuids = selected.getClass().getDeclaredMethod("bodyUuids");
        bodyUuids.setAccessible(true);
        return (Set<?>) bodyUuids.invoke(selected);
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

    private static final class TestPhysicsRef extends Ref<PhysicsStore> {

        private TestPhysicsRef(int index) {
            super(null, index);
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }
}
