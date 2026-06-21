package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointType;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsJointEntities;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

class PhysicsStoreControlSessionMutationsTest {

    private static final ObjenesisStd OBJENESIS = new ObjenesisStd();
    private static final BackendId BACKEND_ID = new BackendId("test:control-release");
    private static final SpaceId SPACE_ID = new SpaceId(42);

    @Test
    void releaseClearsTemporaryAnchorBodyCopiedState() {
        StoreFixture fixture = store("control-release-anchor-cleanup");
        Store<PhysicsStore> physicsStore = fixture.physicsStore();
        Store<EntityStore> entityStore = fixture.entityStore();
        try {
            markCurrentThreadAsWorldThread(physicsStore);
            UUID spaceUuid = uuid(1);
            UUID controlledBodyUuid = uuid(2);
            UUID anchorBodyUuid = uuid(3);
            UUID controlJointUuid = uuid(4);
            Ref<PhysicsStore> spaceRef = addSpace(physicsStore, spaceUuid);
            Ref<PhysicsStore> controlledBodyRef = addBody(physicsStore,
                spaceUuid,
                spaceRef,
                controlledBodyUuid,
                0.0f);
            Ref<PhysicsStore> anchorBodyRef = addBody(physicsStore,
                spaceUuid,
                spaceRef,
                anchorBodyUuid,
                1.0f);
            Ref<PhysicsStore> controlJointRef = addJoint(physicsStore,
                spaceRef,
                controlledBodyRef,
                anchorBodyRef,
                controlJointUuid);
            publishCopiedState(physicsStore,
                spaceUuid,
                controlledBodyUuid,
                controlledBodyRef,
                anchorBodyUuid,
                anchorBodyRef);

            PhysicsControlSessionComponent session = new PhysicsControlSessionComponent(
                controlledBodyRef,
                anchorBodyRef,
                controlJointRef,
                null,
                PhysicsBodyType.DYNAMIC,
                4.0f,
                new Vector3f(),
                new Vector3f());

            PhysicsStoreControlSessionMutations.applyRelease(entityStore, session);

            assertFalse(anchorBodyRef.isValid());
            assertNull(physicsStore.getResource(PhysicsIdentityIndexResource.getResourceType())
                .getByUuid(anchorBodyUuid));
            assertNull(physicsStore.getResource(PhysicsSnapshotResource.getResourceType())
                .getBody(anchorBodyUuid));
            assertFalse(PhysicsBodies.isRegistered(physicsStore, anchorBodyUuid));
            assertNotNull(PhysicsBodies.snapshot(physicsStore, controlledBodyUuid));
        } finally {
            fixture.close();
        }
    }

    @Nonnull
    private static StoreFixture store(@Nonnull String worldName) {
        TestWorld world = OBJENESIS.newInstance(TestWorld.class);
        setField(world, World.class, "name", worldName);
        ComponentRegistry<PhysicsStore> physicsRegistry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> physicsProxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), physicsRegistry);
        PhysicsComponentTypeRegistry.registerComponentTypes(physicsProxy);
        PhysicsResourceTypes.registerResourceTypes(physicsProxy);
        PhysicsStore physicsStoreExternal = new PhysicsStore(world);
        Store<PhysicsStore> physicsStore =
            physicsRegistry.addStore(physicsStoreExternal, EmptyResourceStorage.get());
        setField(physicsStoreExternal, PhysicsStore.class, "store", physicsStore);
        world.physicsStore = physicsStoreExternal;

        ComponentRegistry<EntityStore> entityRegistry = new ComponentRegistry<>();
        Store<EntityStore> entityStore = entityRegistry.addStore(new EntityStore(world),
            EmptyResourceStorage.get());
        return new StoreFixture(physicsRegistry, entityRegistry, physicsStore, entityStore);
    }

    @Nonnull
    private static Ref<PhysicsStore> addSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(BACKEND_ID, new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        assertNotNull(ref);
        store.getResource(PhysicsIdentityIndexResource.getResourceType()).putUuid(spaceUuid, ref);
        store.getExternalData().putRefForUUID(spaceUuid, ref);
        store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .putSpace(SPACE_ID, spaceUuid);
        return ref;
    }

    @Nonnull
    private static Ref<PhysicsStore> addBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        float positionX) {
        BodyComponent body = new BodyComponent(spaceUuid);
        body.setSpaceRef(spaceRef);
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.bodyHolder(store,
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
        assertNotNull(ref);
        store.getResource(PhysicsIdentityIndexResource.getResourceType()).putUuid(bodyUuid, ref);
        store.getExternalData().putRefForUUID(bodyUuid, ref);
        return ref;
    }

    @Nonnull
    private static Ref<PhysicsStore> addJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Ref<PhysicsStore> controlledBodyRef,
        @Nonnull Ref<PhysicsStore> anchorBodyRef,
        @Nonnull UUID jointUuid) {
        JointComponent joint = PhysicsJointEntities.joint(spaceRef,
            anchorBodyRef,
            controlledBodyRef,
            JointType.POINT,
            new Vector3f(),
            new Vector3f(),
            new Vector3f());
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.jointHolder(store,
                jointUuid,
                joint),
            AddReason.SPAWN);
        assertNotNull(ref);
        store.getResource(PhysicsIdentityIndexResource.getResourceType()).putUuid(jointUuid, ref);
        store.getExternalData().putRefForUUID(jointUuid, ref);
        return ref;
    }

    private static void publishCopiedState(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull UUID controlledBodyUuid,
        @Nonnull Ref<PhysicsStore> controlledBodyRef,
        @Nonnull UUID anchorBodyUuid,
        @Nonnull Ref<PhysicsStore> anchorBodyRef) {
        store.getResource(PhysicsSnapshotResource.getResourceType())
            .publish(new PhysicsSnapshotFrame(1L,
                0.05f,
                List.of(snapshot(controlledBodyRef, controlledBodyUuid, spaceUuid, 0.0f),
                    snapshot(anchorBodyRef, anchorBodyUuid, spaceUuid, 1.0f))));
    }

    @Nonnull
    private static PhysicsBodySnapshot snapshot(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid,
        float positionX) {
        return PhysicsBodySnapshot.of(bodyRef,
            bodyUuid,
            spaceUuid,
            PhysicsBodyType.DYNAMIC,
            positionX,
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

    private static void setField(@Nonnull Object target,
        @Nonnull Class<?> owner,
        @Nonnull String name,
        Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to set test field " + owner.getName() + "." + name,
                exception);
        }
    }

    private static final class TestWorld extends World implements PhysicsStoreWorld {

        private PhysicsStore physicsStore;

        private TestWorld() throws IOException {
            super("unused", Path.of("build/tmp/unused-control-release-world"), (WorldConfig) null);
        }

        @Nonnull
        @Override
        public PhysicsStore getPhysicsStore() {
            return physicsStore;
        }
    }

    private record StoreFixture(
        @Nonnull ComponentRegistry<PhysicsStore> physicsRegistry,
        @Nonnull ComponentRegistry<EntityStore> entityRegistry,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull Store<EntityStore> entityStore) implements AutoCloseable {

        @Override
        public void close() {
            physicsRegistry.removeStore(physicsStore);
            entityRegistry.removeStore(entityStore);
            physicsRegistry.shutdown();
            entityRegistry.shutdown();
        }
    }
}
