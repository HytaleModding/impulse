package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class CleanCommandLifecycleGuardTest {

    @Test
    void radiusCleanUsesEcsOwnershipInsteadOfLegacyKind() throws Exception {
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
            UUID generatedUuid = UUID.randomUUID();
            UUID spaceUuid = UUID.randomUUID();
            Ref<PhysicsStore> bodyRef = addBodyIdentityRow(store, bodyUuid, false);
            Ref<PhysicsStore> generatedRef = addBodyIdentityRow(store, generatedUuid, true);
            publishSnapshots(store, bodyUuid, generatedUuid, spaceUuid);
            publishRegistrations(store, bodyUuid, bodyRef, generatedUuid, generatedRef);

            Set<?> selected = selectBodyUuidsNear(store, new Vector3d(), 10.0f);

            assertEquals(Set.of(bodyUuid), selected);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    private static void publishSnapshots(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID generatedUuid,
        @Nonnull UUID spaceUuid) {
        store.getResource(PhysicsSnapshotResource.getResourceType())
            .publish(new PhysicsSnapshotFrame(1L,
                0.05f,
                List.of(snapshot(bodyUuid, spaceUuid), snapshot(generatedUuid, spaceUuid))));
    }

    private static void publishRegistrations(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID generatedUuid,
        @Nonnull Ref<PhysicsStore> generatedRef) {
        store.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .publish(1L,
                List.of(publication(bodyRef, bodyUuid),
                    publication(generatedRef, generatedUuid)));
    }

    @Nonnull
    private static Ref<PhysicsStore> addBodyIdentityRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        boolean generatedChunkCollisionBody) {
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.entityHolder(store,
                bodyUuid),
            AddReason.SPAWN);
        store.getResource(PhysicsIdentityIndexResource.getResourceType()).putUuid(bodyUuid,
            ref);
        if (generatedChunkCollisionBody) {
            store.putComponent(ref,
                ChunkCollisionSourceComponent.getComponentType(),
                new ChunkCollisionSourceComponent("test-source",
                    0,
                    0,
                    0,
                    "test-payload",
                    PartKind.BOX,
                    0));
        }
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
    private static PhysicsBodyRegistrationResource.BodyRegistrationPublication publication(
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid) {
        return new PhysicsBodyRegistrationResource.BodyRegistrationPublication(
            bodyRef,
            bodyUuid,
            new SpaceId(1));
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

}
