package dev.hytalemodding.impulse.core.internal.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.BsonUtil;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.ImpulseBackendRegistry;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStoreTypes;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionRestoreDependencyComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.PhysicsStoreRegistration;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.systems.PersistenceHydrationSystem;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.early.PhysicsStoreHooks;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhysicsStoreHolderPersistenceTest {

    private static final UUID SPACE_UUID = uuid(1);
    private static final UUID BODY_A_UUID = uuid(2);
    private static final UUID BODY_B_UUID = uuid(3);
    private static final UUID GENERATED_BODY_UUID = uuid(4);
    private static final UUID JOINT_UUID = uuid(5);
    private static final BackendId HOLDER_BACKEND_ID = new BackendId("test:holder-persistence");

    @TempDir
    Path tempDir;

    @Test
    void holderBlobsPersistUuidBodyRowsWithSnapshotTargetsOnly() {
        StoreFixture fixture = store("holder-capture", tempDir.resolve("capture"));
        try {
            Ref<PhysicsStore> spaceRef = addSpace(fixture.store(), SPACE_UUID);
            Ref<PhysicsStore> bodyARef = addBody(fixture.store(),
                BODY_A_UUID,
                spaceRef,
                null);
            Ref<PhysicsStore> bodyBRef = addBody(fixture.store(),
                BODY_B_UUID,
                spaceRef,
                null);
            fixture.store().putComponent(bodyARef,
                BodyCommandComponent.getComponentType(),
                BodyCommandComponent.wake());
            addBody(fixture.store(),
                GENERATED_BODY_UUID,
                spaceRef,
                new ChunkCollisionSourceComponent("0:0:0",
                    0,
                    0,
                    0,
                    "chunk-collision/0/0/0",
                    PartKind.BOX,
                    0));
            addJoint(fixture.store(), JOINT_UUID, BODY_A_UUID, BODY_B_UUID);
            publishSnapshot(fixture.store(), bodyARef);

            List<Holder<PhysicsStore>> decoded = PhysicsStoreHolderPersistence
                .capturePersistentHolderBlobs(fixture.store())
                .stream()
                .map(blob -> PhysicsStoreHolderPersistence.decodeHolder(
                    fixture.store().getRegistry(),
                    blob))
                .toList();

            assertNotNull(holder(decoded, SPACE_UUID));
            Holder<PhysicsStore> bodyA = holder(decoded, BODY_A_UUID);
            assertNotNull(bodyA);
            assertNotNull(holder(decoded, BODY_B_UUID));
            assertNotNull(holder(decoded, JOINT_UUID));
            assertNull(holder(decoded, GENERATED_BODY_UUID));

            assertNull(bodyA.getComponent(BodyCommandComponent.getComponentType()));
            BodyComponent body = bodyA.getComponent(BodyComponent.getComponentType());
            assertNotNull(body);
            assertNull(body.getSpaceRef());
            TargetComponent target = bodyA.getComponent(TargetComponent.getComponentType());
            assertNotNull(target);
            assertFalse(target.isActive());
            assertFalse(target.isActivate());
            assertEquals(new Vector3f(9.0f, 8.0f, 7.0f), target.getPosition());
            assertEquals(new Vector3f(0.1f, 0.2f, 0.3f), target.getLinearVelocity());
            assertEquals(new Vector3f(0.4f, 0.5f, 0.6f), target.getAngularVelocity());
        } finally {
            fixture.close();
        }
    }

    @Test
    void holderCaptureAddsChunkCollisionRestoreDependencyToDynamicBodiesNearGeneratedRows() {
        StoreFixture fixture = store("holder-capture-restore-context",
            tempDir.resolve("capture-restore-context"));
        try {
            Ref<PhysicsStore> spaceRef = addSpace(fixture.store(), SPACE_UUID);
            fixture.store().putComponent(spaceRef,
                ChunkCollisionSettingsComponent.getComponentType(),
                new ChunkCollisionSettingsComponent(PhysicsChunkCollisionMode.STREAMING,
                    false,
                    8,
                    4,
                    100));
            addBody(fixture.store(), BODY_A_UUID, spaceRef, null);
            addBody(fixture.store(),
                GENERATED_BODY_UUID,
                spaceRef,
                new ChunkCollisionSourceComponent("chunk:0:0:0",
                    0,
                    0,
                    0,
                    "chunk-collision/0/0/0",
                    PartKind.BOX,
                    0));

            List<Holder<PhysicsStore>> decoded = PhysicsStoreHolderPersistence
                .capturePersistentHolderBlobs(fixture.store())
                .stream()
                .map(blob -> PhysicsStoreHolderPersistence.decodeHolder(
                    fixture.store().getRegistry(),
                    blob))
                .toList();

            assertNull(holder(decoded, GENERATED_BODY_UUID));
            ChunkCollisionRestoreDependencyComponent dependency = holder(decoded, BODY_A_UUID)
                .getComponent(ChunkCollisionRestoreDependencyComponent.getComponentType());
            assertNotNull(dependency);
            assertEquals(SPACE_UUID, dependency.getSpaceUuid());
            assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), dependency.getCenter());
            assertEquals(4, dependency.getRadius());
            assertEquals(PhysicsChunkCollisionMode.STREAMING, dependency.getModeAtSave());
        } finally {
            fixture.close();
        }
    }

    @Test
    void holderStorageUsesWorldOwnedPhysicsStoreDirectory() {
        Path savePath = tempDir.resolve("storage-layout");
        StoreFixture fixture = store("storage-layout", savePath);
        try {
            Path file = PhysicsStoreHolderStorage.file(fixture.store().getExternalData());

            assertEquals(savePath.resolve("physicsstore").resolve("holders.bson"),
                file);
        } finally {
            fixture.close();
        }
    }

    @Test
    void holderSaveCreatesWorldOwnedPhysicsStoreDirectory() {
        Path savePath = tempDir.resolve("chunk-shaped-layout");
        StoreFixture fixture = store("chunk-shaped-layout", savePath);
        try {
            Ref<PhysicsStore> spaceRef = addSpace(fixture.store(), SPACE_UUID);
            addBody(fixture.store(),
                BODY_A_UUID,
                spaceRef,
                null);

            PhysicsStoreHolderStorage.save(fixture.store()).join();

            assertTrue(Files.exists(savePath.resolve("physicsstore")
                .resolve("holders.bson")));
            assertTrue(Files.exists(savePath.resolve("physicsstore")));
            assertFalse(Files.exists(savePath.resolve("resources")
                .resolve("PhysicsStoreHolders.bson")));
        } finally {
            fixture.close();
        }
    }

    @Test
    void holderHydrationRejectsDuplicateUuidWithoutAddingPartialRows() {
        StoreFixture fixture = store("holder-duplicate-uuid",
            tempDir.resolve("duplicate-uuid"));
        try {
            Holder<PhysicsStore> first = PhysicsEntities.spaceHolder(fixture.store(),
                SPACE_UUID,
                new SpaceComponent(new BackendId("test:holder-persistence"),
                    new Vector3f(0.0f, -9.81f, 0.0f)));
            Holder<PhysicsStore> second = fixture.store().getRegistry().newHolder();
            second.addComponent(UuidComponent.getComponentType(), new UuidComponent(SPACE_UUID));
            second.addComponent(BodyComponent.getComponentType(),
                new BodyComponent(SPACE_UUID));
            writeHolderStorage(fixture.store(), List.of(first, second));

            new PersistenceHydrationSystem().tick(0.0f, 0, fixture.store());

            PhysicsRestoreStatusResource restore = fixture.store().getResource(
                PhysicsRestoreStatusResource.getResourceType());
            assertTrue(restore.isFailed());
            assertFalse(restore.isHydrated());
            assertTrue(rowUuids(fixture.store()).isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void holderHydrationRejectsBodyWithoutSavedSpaceWithoutAddingPartialRows() {
        StoreFixture fixture = store("holder-missing-space",
            tempDir.resolve("missing-space"));
        try {
            Holder<PhysicsStore> body = fixture.store().getRegistry().newHolder();
            body.addComponent(UuidComponent.getComponentType(), new UuidComponent(BODY_A_UUID));
            body.addComponent(BodyComponent.getComponentType(),
                new BodyComponent(SPACE_UUID));
            writeHolderStorage(fixture.store(), List.of(body));

            new PersistenceHydrationSystem().tick(0.0f, 0, fixture.store());

            PhysicsRestoreStatusResource restore = fixture.store().getResource(
                PhysicsRestoreStatusResource.getResourceType());
            assertTrue(restore.isFailed());
            assertFalse(restore.isHydrated());
            assertTrue(rowUuids(fixture.store()).isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void registeredStoreReloadBindsSavedBodiesOnce() {
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(HOLDER_BACKEND_ID, false, false);
        ImpulseBackendRegistry.registerRuntimeProvider(provider);
        Path savePath = tempDir.resolve("registered-reload");

        StoreFixture source = registeredStore("registered-reload-source", savePath);
        try {
            Ref<PhysicsStore> spaceRef = addSpace(source.store(), SPACE_UUID);
            addBody(source.store(), BODY_A_UUID, spaceRef, null);
            addBody(source.store(), BODY_B_UUID, spaceRef, null);

            source.store().tick(0.0f);

            BackendSpaceHandle sourceHandle = source.store()
                .getResource(PhysicsRuntimeResource.getResourceType())
                .getSpaceHandle(spaceRef);
            assertNotNull(sourceHandle);
            assertEquals(1, provider.createdRuntimes().size());
            assertEquals(2, provider.createdRuntimes().get(0).bodyCount(sourceHandle.value()));

            PhysicsStoreHooks.shutdown(source.store().getExternalData());
        } finally {
            source.close();
        }

        StoreFixture target = registeredStore("registered-reload-target", savePath);
        try {
            target.store().tick(0.0f);

            PhysicsRestoreStatusResource restore = target.store().getResource(
                PhysicsRestoreStatusResource.getResourceType());
            assertTrue(restore.isHydrated());
            assertFalse(restore.isFailed());
            assertTrue(restore.getSoftSkipsByReason().isEmpty());
            List<UUID> rowUuids = rowUuids(target.store());
            assertEquals(3, rowUuids.size());
            assertTrue(rowUuids.contains(SPACE_UUID));
            assertTrue(rowUuids.contains(BODY_A_UUID));
            assertTrue(rowUuids.contains(BODY_B_UUID));

            Ref<PhysicsStore> spaceRef = target.store().getExternalData().getRefFromUUID(
                SPACE_UUID);
            assertNotNull(spaceRef);
            PhysicsRuntimeResource runtime = target.store().getResource(
                PhysicsRuntimeResource.getResourceType());
            BackendSpaceHandle handle = runtime.getSpaceHandle(spaceRef);
            assertNotNull(handle);
            assertEquals(2, provider.createdRuntimes().size());
            FakePhysicsBackendRuntime reloadedRuntime = provider.createdRuntimes().get(1);
            assertTrue(reloadedRuntime.hasSpace(handle.value()));
            assertEquals(2, reloadedRuntime.bodyCount(handle.value()));

            target.store().tick(0.05f);
            target.store()
                .getResource(PhysicsStepSchedulerResource.getResourceType())
                .whenIdle()
                .toCompletableFuture()
                .join();
            target.store().tick(0.0f);

            assertEquals(2, target.store()
                .getResource(PhysicsSnapshotResource.getResourceType())
                .getLatestFrame()
                .bodies()
                .size());
        } finally {
            target.close();
        }
    }

    @Test
    void holderHydrationClosesStaleUntrackedBackendRuntimeBeforeRebinding() {
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(HOLDER_BACKEND_ID, false, false);
        ImpulseBackendRegistry.registerRuntimeProvider(provider);
        Path savePath = tempDir.resolve("stale-backend-runtime");
        SpaceId compatibilitySpaceId = new SpaceId(2);

        StoreFixture source = store("stale-runtime-source", savePath);
        try {
            Ref<PhysicsStore> spaceRef = addSpace(source.store(), SPACE_UUID);
            addBody(source.store(), BODY_A_UUID, spaceRef, null);
            PhysicsStoreHolderStorage.save(source.store()).join();
        } finally {
            source.close();
        }

        StoreFixture target = registeredStore("stale-runtime-target", savePath);
        try {
            FakePhysicsBackendRuntime staleRuntime = (FakePhysicsBackendRuntime)
                provider.createRuntime();
            staleRuntime.createSpace(compatibilitySpaceId);
            target.store()
                .getResource(PhysicsRuntimeResource.getResourceType())
                .putRuntime(HOLDER_BACKEND_ID, staleRuntime);
            target.store()
                .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
                .putSpace(compatibilitySpaceId, SPACE_UUID);

            target.store().tick(0.0f);

            PhysicsRestoreStatusResource restore = target.store().getResource(
                PhysicsRestoreStatusResource.getResourceType());
            assertTrue(restore.isHydrated());
            assertFalse(restore.isFailed(), restore.getFailureMessage());
            assertFalse(staleRuntime.hasSpace(compatibilitySpaceId.value()));
            assertEquals(2, provider.createdRuntimes().size());
            FakePhysicsBackendRuntime reboundRuntime = provider.createdRuntimes().get(1);
            assertTrue(reboundRuntime.hasSpace(compatibilitySpaceId.value()));
            assertEquals(1, reboundRuntime.bodyCount(compatibilitySpaceId.value()));
        } finally {
            target.close();
        }
    }

    @Nonnull
    private static StoreFixture store(@Nonnull String worldName, @Nonnull Path savePath) {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        PhysicsStore physicsStore = new PhysicsStore(world(worldName, savePath));
        Store<PhysicsStore> store = registry.addStore(physicsStore, EmptyResourceStorage.get());
        setField(physicsStore, PhysicsStore.class, "store", store);
        return new StoreFixture(registry, store);
    }

    @Nonnull
    private static StoreFixture registeredStore(@Nonnull String worldName, @Nonnull Path savePath) {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes(proxy);
        PhysicsStoreRegistration.register(proxy);
        PhysicsStore physicsStore = new PhysicsStore(world(worldName, savePath));
        Store<PhysicsStore> store = registry.addStore(physicsStore, EmptyResourceStorage.get());
        setField(physicsStore, PhysicsStore.class, "store", store);
        return new StoreFixture(registry, store);
    }

    @Nonnull
    private static World world(@Nonnull String worldName, @Nonnull Path savePath) {
        World world = TestInstanceFactory.world(worldName);
        setField(world, World.class, "savePath", savePath);
        return world;
    }

    @Nonnull
    private static Ref<PhysicsStore> addSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(HOLDER_BACKEND_ID, new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        assertNotNull(ref);
        return ref;
    }

    @Nonnull
    private static Ref<PhysicsStore> addBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> spaceRef,
        ChunkCollisionSourceComponent source) {
        Holder<PhysicsStore> holder = PhysicsEntities.bodyHolder(store,
            bodyUuid,
            body(SPACE_UUID, spaceRef),
            new DynamicsComponent(PhysicsBodyType.DYNAMIC, 1.0f, 0.0f, 0.0f, false),
            target(),
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
            new MaterialComponent(0.6f, 0.1f),
            new CollisionFilterComponent(PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.ALL));
        if (source != null) {
            holder.addComponent(ChunkCollisionSourceComponent.getComponentType(), source);
        }
        Ref<PhysicsStore> ref = store.addEntity(holder, AddReason.SPAWN);
        assertNotNull(ref);
        return ref;
    }

    @Nonnull
    private static BodyComponent body(@Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        BodyComponent body = new BodyComponent(spaceUuid);
        body.setSpaceRef(spaceRef);
        return body;
    }

    @Nonnull
    private static TargetComponent target() {
        TargetComponent target = new TargetComponent();
        target.setActive(true);
        target.setPosition(new Vector3f(1.0f, 2.0f, 3.0f));
        return target;
    }

    private static void addJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID jointUuid,
        @Nonnull UUID bodyAUuid,
        @Nonnull UUID bodyBUuid) {
        Holder<PhysicsStore> holder = store.getRegistry().newHolder();
        holder.addComponent(UuidComponent.getComponentType(), new UuidComponent(jointUuid));
        JointComponent joint = new JointComponent();
        joint.setSpaceUuid(SPACE_UUID);
        joint.setBodyAUuid(bodyAUuid);
        joint.setBodyBUuid(bodyBUuid);
        holder.addComponent(JointComponent.getComponentType(), joint);
        assertNotNull(store.addEntity(holder, AddReason.SPAWN));
    }

    private static void publishSnapshot(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        store.getResource(PhysicsSnapshotResource.getResourceType()).publish(
            new PhysicsSnapshotFrame(1L,
                0.05f,
                List.of(new PhysicsBodySnapshot(bodyRef,
                    BODY_A_UUID,
                    SPACE_UUID,
                    PhysicsBodyType.DYNAMIC,
                    new Vector3f(9.0f, 8.0f, 7.0f),
                    new Quaternionf(),
                    new Vector3f(0.1f, 0.2f, 0.3f),
                    new Vector3f(0.4f, 0.5f, 0.6f),
                    0.0f,
                    true))));
    }

    private static void writeHolderStorage(@Nonnull Store<PhysicsStore> store,
        @Nonnull List<Holder<PhysicsStore>> holders) {
        writeHolderStorageFile(PhysicsStoreHolderStorage.file(store.getExternalData()),
            store,
            holders);
    }

    private static void writeHolderStorageFile(@Nonnull Path file,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull List<Holder<PhysicsStore>> holders) {
        BsonArray holderBlobs = new BsonArray();
        for (Holder<PhysicsStore> holder : holders) {
            holderBlobs.add(new BsonBinary(PhysicsStoreHolderPersistence.encodeHolder(store,
                holder)));
        }
        BsonDocument document = new BsonDocument()
            .append("SchemaVersion", new BsonInt32(1))
            .append("Holders", holderBlobs);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, BsonUtil.writeToBytes(document));
        } catch (IOException exception) {
            throw new AssertionError("Failed to write test holder storage", exception);
        }
    }

    private static Holder<PhysicsStore> holder(@Nonnull List<Holder<PhysicsStore>> holders,
        @Nonnull UUID uuid) {
        for (Holder<PhysicsStore> holder : holders) {
            UuidComponent component = holder.getComponent(UuidComponent.getComponentType());
            if (component != null && uuid.equals(component.getUuid())) {
                return holder;
            }
        }
        return null;
    }

    @Nonnull
    private static List<UUID> rowUuids(@Nonnull Store<PhysicsStore> store) {
        List<UUID> uuids = new ArrayList<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> {
            for (int index = 0; index < chunk.size(); index++) {
                UuidComponent uuid = chunk.getComponent(index, UuidComponent.getComponentType());
                if (uuid != null) {
                    uuids.add(uuid.getUuid());
                }
            }
        };
        store.forEachChunk(UuidComponent.getComponentType(), collector);
        return uuids;
    }

    private static void setField(@Nonnull Object target,
        @Nonnull Class<?> owner,
        @Nonnull String name,
        @Nonnull Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to set test field " + owner.getName() + "." + name,
                exception);
        }
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
        }
    }
}
