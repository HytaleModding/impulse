package dev.hytalemodding.impulse.core.internal.systems;

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
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class StaleBodyRemovalSystemTest {

    @Test
    void tickRemovesMultipleStaleBodiesAndTheirCopiedStateTogether() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("stale-body-removal-batch")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(1);
            UUID firstStaleUuid = uuid(2);
            UUID secondStaleUuid = uuid(3);
            UUID retainedUuid = uuid(4);
            BoundSpace space = addBoundSpace(store,
                spaceUuid,
                new BackendId("test:stale-body-removal"));
            Ref<PhysicsStore> firstStaleRef = addBody(store, spaceUuid, space.ref(), firstStaleUuid);
            Ref<PhysicsStore> secondStaleRef = addBody(store,
                spaceUuid,
                space.ref(),
                secondStaleUuid);
            Ref<PhysicsStore> retainedRef = addBody(store, spaceUuid, space.ref(), retainedUuid);
            bindBody(store, space, firstStaleUuid, firstStaleRef, 0.0f);
            bindBody(store, space, secondStaleUuid, secondStaleRef, 2.0f);
            bindBody(store, space, retainedUuid, retainedRef, 4.0f);
            publishCopiedState(store,
                spaceUuid,
                firstStaleUuid,
                firstStaleRef,
                secondStaleUuid,
                secondStaleRef,
                retainedUuid,
                retainedRef);
            store.removeComponent(firstStaleRef, BodyComponent.getComponentType());
            store.removeComponent(secondStaleRef, BodyComponent.getComponentType());

            new StaleBodyRemovalSystem().tick(0.0f, 0, store);

            PhysicsIdentityIndexResource identity = store.getResource(
                PhysicsIdentityIndexResource.getResourceType());
            PhysicsRuntimeResource runtime = store.getResource(
                PhysicsRuntimeResource.getResourceType());
            PhysicsSnapshotResource snapshots =
                store.getResource(PhysicsSnapshotResource.getResourceType());
            assertFalse(store.getResource(PhysicsRestoreStatusResource.getResourceType())
                .isFailed());
            assertNull(identity.getByUuid(firstStaleUuid));
            assertNull(identity.getByUuid(secondStaleUuid));
            assertNotNull(identity.getByUuid(retainedUuid));
            assertNull(runtime.getBodyHandle(firstStaleRef));
            assertNull(runtime.getBodyHandle(secondStaleRef));
            assertNotNull(runtime.getBodyHandle(retainedRef));
            assertEquals(1, space.runtime().bodyCount(space.handle().value()));
            assertNull(snapshots.getBody(firstStaleUuid));
            assertNull(snapshots.getBody(secondStaleUuid));
            assertNotNull(snapshots.getBody(retainedUuid));
            assertNull(PhysicsBodies.spaceId(store, firstStaleUuid));
            assertNull(PhysicsBodies.spaceId(store, secondStaleUuid));
            assertEquals(new SpaceId(42), PhysicsBodies.spaceId(store, retainedUuid));
            assertFalse(firstStaleRef.isValid());
            assertFalse(secondStaleRef.isValid());
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static BoundSpace addBoundSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull BackendId backendId) {
        Ref<PhysicsStore> spaceRef = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(backendId, new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        assertNotNull(spaceRef);
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        identity.putUuid(spaceUuid, spaceRef);
        store.getExternalData().putRefForUUID(spaceUuid, spaceRef);
        PhysicsBackendRuntime runtime =
            new FakePhysicsBackendRuntimeProvider(backendId, false, false).createRuntime();
        BackendSpaceHandle spaceHandle =
            new BackendSpaceHandle(runtime.createSpace(new SpaceId(42)));
        PhysicsRuntimeResource runtimeResource = store.getResource(
            PhysicsRuntimeResource.getResourceType());
        runtimeResource.putRuntime(backendId, runtime);
        runtimeResource.putSpaceHandle(spaceRef, backendId, spaceHandle);
        runtimeResource.putSpaceMetadata(backendId, spaceHandle, spaceUuid, spaceRef);
        store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .putSpace(new SpaceId(42), spaceUuid);
        return new BoundSpace(spaceUuid, spaceRef, runtime, spaceHandle, backendId);
    }

    @Nonnull
    private static Ref<PhysicsStore> addBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid) {
        BodyComponent body = new BodyComponent(spaceUuid);
        body.setSpaceRef(spaceRef);
        Ref<PhysicsStore> bodyRef = store.addEntity(PhysicsEntities.bodyHolder(store,
                bodyUuid,
                body,
                new DynamicsComponent(PhysicsBodyType.DYNAMIC, 1.0f, 0.0f, 0.0f, false),
                null,
                new ColliderComponent(new Vector3f(), new Quaternionf(), false),
                new ShapeComponent(ShapeType.BOX,
                    0.5f,
                    0.5f,
                    0.5f,
                    0.0f,
                    0.0f,
                    PhysicsAxis.Y,
                    0.0f,
                    ""),
                new MaterialComponent(0.5f, 0.1f),
                new CollisionFilterComponent(0x01, 0x02)),
            AddReason.SPAWN);
        assertNotNull(bodyRef);
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        identity.putUuid(bodyUuid, bodyRef);
        store.getExternalData().putRefForUUID(bodyUuid, bodyRef);
        return bodyRef;
    }

    private static void bindBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull BoundSpace space,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        float positionX) {
        long bodyId = space.runtime().createBody(space.handle().value(),
            BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.axisCode(PhysicsAxis.Y),
            0.0f,
            1.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            positionX,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        BackendBodyHandle handle = new BackendBodyHandle(bodyId);
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        runtime.putBodyHandle(bodyRef, space.ref(), space.handle(), handle);
        runtime.putBodySnapshotMetadata(space.backendId(),
            space.handle(),
            handle,
            bodyUuid,
            bodyRef,
            space.uuid());
        runtime.putBodyHitMetadata(space.backendId(),
            space.handle(),
            handle,
            bodyUuid,
            bodyRef,
            PhysicsBodyType.DYNAMIC,
            ShapeType.BOX);
    }

    private static void publishCopiedState(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull UUID firstBodyUuid,
        @Nonnull Ref<PhysicsStore> firstBodyRef,
        @Nonnull UUID secondBodyUuid,
        @Nonnull Ref<PhysicsStore> secondBodyRef,
        @Nonnull UUID retainedBodyUuid,
        @Nonnull Ref<PhysicsStore> retainedBodyRef) {
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

    private record BoundSpace(@Nonnull UUID uuid,
                              @Nonnull Ref<PhysicsStore> ref,
                              @Nonnull PhysicsBackendRuntime runtime,
                              @Nonnull BackendSpaceHandle handle,
                              @Nonnull BackendId backendId) {
    }
}
