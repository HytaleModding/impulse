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
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStoreTypes;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.binding.BodyBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.ColliderBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.JointBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.SpaceBindingSystem;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointType;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import java.util.ArrayList;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class JointBindingSystemTest {

    @Test
    void jointBindingRejectsEndpointBodiesFromDifferentBackendWithSameSpaceHandle() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes(proxy);
        proxy.registerSystem(new PersistenceHydrationSystem());
        proxy.registerSystem(new IdentityIndexSystem());
        proxy.registerSystem(new SpaceBindingSystem());
        proxy.registerSystem(new SpaceSettingsApplicationSystem());
        proxy.registerSystem(new BodyBindingSystem());
        proxy.registerSystem(new ColliderBindingSystem());
        proxy.registerSystem(new JointBindingSystem());
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("joint-binding-cross-backend-test")),
            EmptyResourceStorage.get());
        try {
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            restore.markComplete();
            restore.markHydrated();
            BoundSpace jointSpace = addBoundSpace(store, uuid(1), new BackendId("test:joint-a"));
            BoundSpace bodySpace = addBoundSpace(store, uuid(2), new BackendId("test:joint-b"));
            UUID bodyAUuid = uuid(3);
            UUID bodyBUuid = uuid(4);
            Ref<PhysicsStore> bodyARef = addBoundBody(store, bodySpace, bodyAUuid, 0.0f);
            Ref<PhysicsStore> bodyBRef = addBoundBody(store, bodySpace, bodyBUuid, 1.0f);
            UUID jointUuid = uuid(5);
            Ref<PhysicsStore> jointRef = addJoint(store,
                jointSpace.uuid(),
                jointSpace.ref(),
                bodyAUuid,
                bodyARef,
                bodyBUuid,
                bodyBRef,
                jointUuid);

            store.tick(0.0f);

            PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
            assertFalse(restore.isFailed());
            assertNull(runtime.getJointHandle(jointRef));
            assertEquals(0, jointSpace.runtime().jointCount(jointSpace.handle().value()));
            assertEquals(1,
                restore.getSoftSkipsByReason()
                    .getInt("Joint endpoints are not in the joint space: " + jointUuid));
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
        store.getExternalData().putRefForUUID(spaceUuid, spaceRef);
        FakePhysicsBackendRuntime runtime = (FakePhysicsBackendRuntime)
            new FakePhysicsBackendRuntimeProvider(backendId, false, false).createRuntime();
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(runtime.createSpace(new SpaceId(42)));
        PhysicsRuntimeResource runtimeResource = store.getResource(
            PhysicsRuntimeResource.getResourceType());
        runtimeResource.putRuntime(backendId, runtime);
        runtimeResource.putSpaceHandle(spaceRef, backendId, spaceHandle);
        runtimeResource.putSpaceMetadata(backendId, spaceHandle, spaceUuid, spaceRef);
        return new BoundSpace(spaceUuid, backendId, spaceRef, spaceHandle, runtime);
    }

    @Nonnull
    private static Ref<PhysicsStore> addBoundBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull BoundSpace space,
        @Nonnull UUID bodyUuid,
        float positionX) {
        BodyComponent body = new BodyComponent(space.uuid());
        body.setSpaceRef(space.ref());
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
        store.getExternalData().putRefForUUID(bodyUuid, bodyRef);
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
        BackendBodyHandle bodyHandle = new BackendBodyHandle(bodyId);
        PhysicsRuntimeResource runtimeResource = store.getResource(
            PhysicsRuntimeResource.getResourceType());
        runtimeResource.putBodyHandle(bodyRef, space.ref(), space.handle(), bodyHandle);
        runtimeResource.putBodySnapshotMetadata(space.backendId(),
            space.handle(),
            bodyHandle,
            bodyUuid,
            bodyRef,
            space.uuid());
        runtimeResource.putBodyHitMetadata(space.backendId(),
            space.handle(),
            bodyHandle,
            bodyUuid,
            bodyRef,
            PhysicsBodyType.DYNAMIC,
            ShapeType.BOX);
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
        JointComponent joint = new JointComponent();
        joint.setSpaceUuid(spaceUuid);
        joint.setSpaceRef(spaceRef);
        joint.setBodyAUuid(bodyAUuid);
        joint.setBodyARef(bodyARef);
        joint.setBodyBUuid(bodyBUuid);
        joint.setBodyBRef(bodyBRef);
        joint.setType(JointType.FIXED);
        joint.setAnchorA(new Vector3f());
        joint.setAnchorB(new Vector3f());
        joint.setAxis(new Vector3f(0.0f, 1.0f, 0.0f));
        joint.setEnabled(true);
        Ref<PhysicsStore> jointRef = store.addEntity(PhysicsEntities.jointHolder(store,
                jointUuid,
                joint),
            AddReason.SPAWN);
        assertNotNull(jointRef);
        store.getExternalData().putRefForUUID(jointUuid, jointRef);
        return jointRef;
    }

    @Nonnull
    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }

    private record BoundSpace(@Nonnull UUID uuid,
                              @Nonnull BackendId backendId,
                              @Nonnull Ref<PhysicsStore> ref,
                              @Nonnull BackendSpaceHandle handle,
                              @Nonnull FakePhysicsBackendRuntime runtime) {
    }
}
