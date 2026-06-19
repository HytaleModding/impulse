package dev.hytalemodding.impulse.core.internal.physicsstore;

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
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsJointEntities;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
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

class PhysicsStoreTopologyMutationsTest {

    @Test
    void destroyBodyRemovesDependentJointRowAndRuntimeHandles() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("topology-destroy-body-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(1);
            UUID bodyAUuid = uuid(2);
            UUID bodyBUuid = uuid(3);
            UUID jointUuid = uuid(4);
            BackendId backendId = new BackendId("test:topology-cleanup");
            BoundSpace space = addBoundSpace(store, spaceUuid, backendId);
            Ref<PhysicsStore> bodyARef = addBody(store, spaceUuid, space.ref(), bodyAUuid);
            Ref<PhysicsStore> bodyBRef = addBody(store, spaceUuid, space.ref(), bodyBUuid);
            Ref<PhysicsStore> jointRef = addJoint(store,
                spaceUuid,
                space.ref(),
                bodyAUuid,
                bodyARef,
                bodyBUuid,
                bodyBRef,
                jointUuid);
            BackendBodyHandle bodyAHandle = bindBody(store,
                space,
                bodyAUuid,
                bodyARef,
                0.0f);
            bindBody(store, space, bodyBUuid, bodyBRef, 2.0f);
            bindJoint(store, space, jointUuid, jointRef, bodyAHandle, bodyBRef);
            publishCopiedState(store, spaceUuid, bodyAUuid, bodyARef, bodyBUuid, bodyBRef);

            PhysicsStoreTopologyMutations.destroyBody(store, bodyAUuid);

            PhysicsIdentityIndexResource identity = store.getResource(
                PhysicsIdentityIndexResource.getResourceType());
            PhysicsRuntimeResource runtime = store.getResource(
                PhysicsRuntimeResource.getResourceType());
            PhysicsSnapshotResource snapshots =
                store.getResource(PhysicsSnapshotResource.getResourceType());
            PhysicsBodyRegistrationResource registrations = store.getResource(
                PhysicsBodyRegistrationResource.getResourceType());
            assertNull(identity.getByUuid(bodyAUuid));
            assertNull(identity.getByUuid(jointUuid));
            Ref<PhysicsStore> remainingBodyRef = identity.getByUuid(bodyBUuid);
            assertNotNull(remainingBodyRef);
            assertNull(runtime.getBodyHandle(bodyARef));
            assertNull(runtime.getJointHandle(jointRef));
            assertNotNull(runtime.getBodyHandle(remainingBodyRef));
            assertEquals(1, space.runtime().bodyCount(space.handle().value()));
            assertEquals(0, space.runtime().jointCount(space.handle().value()));
            assertNull(snapshots.getBody(bodyAUuid));
            assertNotNull(snapshots.getBody(bodyBUuid));
            assertNull(registrations.getBodyRegistrationView(bodyAUuid));
            assertNotNull(registrations.getBodyRegistrationView(bodyBUuid));
            assertFalse(bodyARef.isValid());
            assertFalse(jointRef.isValid());
            assertNotNull(store.getComponent(remainingBodyRef, BodyComponent.getComponentType()));
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
        PhysicsBackendRuntime backendRuntime =
            new FakePhysicsBackendRuntimeProvider(backendId, false, false).createRuntime();
        BackendSpaceHandle spaceHandle =
            new BackendSpaceHandle(backendRuntime.createSpace(new SpaceId(42)));
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        runtime.putRuntime(backendId, backendRuntime);
        runtime.putSpaceBinding(spaceUuid, spaceRef, backendId, spaceHandle);
        return new BoundSpace(spaceRef, backendRuntime, spaceHandle, backendId);
    }

    @Nonnull
    private static Ref<PhysicsStore> addBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid) {
        BodyComponent body = new BodyComponent(spaceUuid,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
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

    @Nonnull
    private static Ref<PhysicsStore> addJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyAUuid,
        @Nonnull Ref<PhysicsStore> bodyARef,
        @Nonnull UUID bodyBUuid,
        @Nonnull Ref<PhysicsStore> bodyBRef,
        @Nonnull UUID jointUuid) {
        JointComponent joint = PhysicsJointEntities.joint(spaceRef,
            bodyARef,
            bodyBRef,
            JointType.FIXED,
            new Vector3f(),
            new Vector3f(),
            new Vector3f(0.0f, 1.0f, 0.0f));
        joint.setSpaceUuid(spaceUuid);
        joint.setBodyAUuid(bodyAUuid);
        joint.setBodyBUuid(bodyBUuid);
        Ref<PhysicsStore> jointRef = store.addEntity(PhysicsEntities.jointHolder(store,
                jointUuid,
                joint),
            AddReason.SPAWN);
        assertNotNull(jointRef);
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        identity.putUuid(jointUuid, jointRef);
        store.getExternalData().putRefForUUID(jointUuid, jointRef);
        return jointRef;
    }

    @Nonnull
    private static BackendBodyHandle bindBody(@Nonnull Store<PhysicsStore> store,
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
        runtime.putBodyHandle(bodyUuid, bodyRef, uuid(1), space.handle(), handle);
        store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .putBodyHandle(handle, bodyRef);
        return handle;
    }

    private static void bindJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull BoundSpace space,
        @Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull BackendBodyHandle bodyAHandle,
        @Nonnull Ref<PhysicsStore> bodyBRef) {
        BackendBodyHandle bodyBHandle = store.getResource(PhysicsRuntimeResource.getResourceType())
            .getBodyHandle(bodyBRef);
        assertNotNull(bodyBHandle);
        long jointId = space.runtime().createJoint(space.handle().value(),
            BackendRuntimeCodes.jointTypeCode(BackendJointType.FIXED),
            bodyAHandle.value(),
            bodyBHandle.value(),
            0.0f,
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
            false,
            0.0f,
            0.0f);
        BackendJointHandle handle = new BackendJointHandle(jointId);
        store.getResource(PhysicsRuntimeResource.getResourceType())
            .putJointHandle(jointRef, jointUuid, space.backendId(), space.handle(), handle);
        store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .putJointHandle(handle, jointRef);
    }

    private static void publishCopiedState(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull UUID bodyAUuid,
        @Nonnull Ref<PhysicsStore> bodyARef,
        @Nonnull UUID bodyBUuid,
        @Nonnull Ref<PhysicsStore> bodyBRef) {
        store.getResource(PhysicsSnapshotResource.getResourceType())
            .publish(new PhysicsSnapshotFrame(1L,
                0.05f,
                List.of(snapshot(bodyARef, bodyAUuid, spaceUuid),
                    snapshot(bodyBRef, bodyBUuid, spaceUuid))));
        store.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .publish(1L,
                List.of(publication(bodyARef, bodyAUuid),
                    publication(bodyBRef, bodyBUuid)));
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
    private static PhysicsBodyRegistrationResource.BodyRegistrationPublication publication(
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid) {
        return new PhysicsBodyRegistrationResource.BodyRegistrationPublication(bodyRef,
            new PhysicsBodyRegistrationView(bodyUuid,
                new SpaceId(42),
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY));
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

    @Nonnull
    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }

    private record BoundSpace(@Nonnull Ref<PhysicsStore> ref,
                              @Nonnull PhysicsBackendRuntime runtime,
                              @Nonnull BackendSpaceHandle handle,
                              @Nonnull BackendId backendId) {
    }
}
