package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.CombineCall;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload.Neighbor;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsEntities;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ChunkCollisionVoxelStitchingSystemTest {

    @Test
    void voxelRowsAreStitchedThroughBodyRuntimeHandlesOncePerPayload() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("chunk-collision-stitch-row-test")),
            EmptyResourceStorage.get());
        try {
            BackendId backendId = new BackendId("test:chunk-collision-stitch");
            UUID spaceUuid = uuid(1);
            RuntimeFixture runtime = addBoundSpace(store, spaceUuid, backendId);
            String firstSourceKey = "0:0:0";
            String secondSourceKey = "1:0:0";
            String firstPayloadKey = "chunk-collision/0/0/0";
            String secondPayloadKey = "chunk-collision/1/0/0";
            Ref<PhysicsStore> firstRef = addVoxelRow(store,
                runtime,
                spaceUuid,
                firstSourceKey,
                firstPayloadKey,
                0,
                0,
                0);
            Ref<PhysicsStore> secondRef = addVoxelRow(store,
                runtime,
                spaceUuid,
                secondSourceKey,
                secondPayloadKey,
                1,
                0,
                0);
            long firstBodyId = store.getResource(PhysicsRuntimeResource.getResourceType())
                .getBodyHandle(firstRef)
                .value();
            long secondBodyId = store.getResource(PhysicsRuntimeResource.getResourceType())
                .getBodyHandle(secondRef)
                .value();
            store.getResource(PhysicsChunkCollisionPayloadResource.getResourceType())
                .put(firstPayloadKey,
                    payloadWithNeighbors(List.of(new Neighbor(secondSourceKey, 16, 0, 0))));
            store.getResource(PhysicsChunkCollisionPayloadResource.getResourceType())
                .put(secondPayloadKey,
                    payloadWithNeighbors(List.of(new Neighbor(firstSourceKey, -16, 0, 0))));

            runStitchingSystem(store);
            runStitchingSystem(store);

            List<CombineCall> calls = runtime.backendRuntime().combineCalls(runtime.spaceHandle()
                .value());
            assertEquals(1, calls.size());
            CombineCall call = calls.getFirst();
            assertEquals(firstBodyId, call.bodyAId());
            assertEquals(secondBodyId, call.bodyBId());
            assertEquals(16, call.shiftX());
            assertEquals(0, call.shiftY());
            assertEquals(0, call.shiftZ());
            assertSoftSkipsEmpty(store);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static RuntimeFixture addBoundSpace(@Nonnull Store<PhysicsStore> store,
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

        FakePhysicsBackendRuntime runtime = (FakePhysicsBackendRuntime)
            new FakePhysicsBackendRuntimeProvider(backendId, false, true).createRuntime();
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(runtime.createSpace(new SpaceId(42)));
        PhysicsRuntimeResource runtimeResource = store.getResource(
            PhysicsRuntimeResource.getResourceType());
        runtimeResource.putRuntime(backendId, runtime);
        runtimeResource.putSpaceBinding(spaceUuid, spaceRef, backendId, spaceHandle);
        identity.putSpaceHandle(spaceHandle, spaceRef);
        return new RuntimeFixture(spaceRef, spaceHandle, runtime);
    }

    @Nonnull
    private static Ref<PhysicsStore> addVoxelRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull RuntimeFixture runtime,
        @Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        @Nonnull String payloadKey,
        int chunkX,
        int sectionY,
        int chunkZ) {
        UUID bodyUuid = ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
            sourceKey,
            PartKind.VOXEL_TERRAIN,
            0);
        BodyComponent body = new BodyComponent(spaceUuid,
            PhysicsBodyKind.TERRAIN,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        body.setSpaceRef(runtime.spaceRef());
        Holder<PhysicsStore> holder = PhysicsEntities.bodyHolder(store,
            bodyUuid,
            body,
            new DynamicsComponent(PhysicsBodyType.STATIC, 0.0f, 0.0f, 0.0f, false),
            target(chunkX, sectionY, chunkZ),
            new ColliderComponent(new Vector3f(), new Quaternionf(), false),
            new ShapeComponent(ShapeType.VOXELS,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                PhysicsAxis.Y,
                0.0f,
                payloadKey),
            new MaterialComponent(0.7f, 0.05f),
            new CollisionFilterComponent(PhysicsCollisionFilters.TERRAIN,
                PhysicsCollisionFilters.ALL));
        holder.addComponent(ChunkCollisionSourceComponent.getComponentType(),
            new ChunkCollisionSourceComponent(sourceKey,
                chunkX,
                sectionY,
                chunkZ,
                payloadKey,
                PartKind.VOXEL_TERRAIN,
                0));
        Ref<PhysicsStore> bodyRef = store.addEntity(holder, AddReason.SPAWN);
        assertNotNull(bodyRef);
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        identity.putUuid(bodyUuid, bodyRef);
        store.getExternalData().putRefForUUID(bodyUuid, bodyRef);

        long bodyHandle = runtime.backendRuntime()
            .createVoxelTerrain(runtime.spaceHandle().value(),
                1.0f,
                1.0f,
                1.0f,
                new int[] {0, 0, 0},
                chunkX * 16.0f,
                sectionY * 16.0f,
                chunkZ * 16.0f,
                0.7f,
                0.05f,
                PhysicsCollisionFilters.TERRAIN,
                PhysicsCollisionFilters.ALL);
        BackendBodyHandle backendBodyHandle = new BackendBodyHandle(bodyHandle);
        store.getResource(PhysicsRuntimeResource.getResourceType())
            .putBodyHandle(bodyUuid,
                bodyRef,
                spaceUuid,
                runtime.spaceHandle(),
                backendBodyHandle);
        identity.putBodyHandle(backendBodyHandle, bodyRef);
        return bodyRef;
    }

    @Nonnull
    private static TargetComponent target(int chunkX, int sectionY, int chunkZ) {
        TargetComponent target = new TargetComponent();
        target.setPosition(new Vector3f(chunkX * 16.0f, sectionY * 16.0f, chunkZ * 16.0f));
        return target;
    }

    @Nonnull
    private static ChunkCollisionPayload payloadWithNeighbors(
        @Nonnull List<Neighbor> neighbors) {
        return new ChunkCollisionPayload(1.0f,
            1.0f,
            1.0f,
            new int[] {0, 0, 0},
            List.of(),
            List.of(),
            true,
            0.7f,
            0.05f,
            PhysicsCollisionFilters.TERRAIN,
            PhysicsCollisionFilters.ALL,
            neighbors);
    }

    private static void runStitchingSystem(@Nonnull Store<PhysicsStore> store) {
        try {
            ChunkCollisionVoxelStitchingSystem system = new ChunkCollisionVoxelStitchingSystem();
            Method stitchChunk = ChunkCollisionVoxelStitchingSystem.class.getDeclaredMethod(
                "stitchChunk",
                PhysicsRuntimeResource.class,
                PhysicsIdentityIndexResource.class,
                PhysicsChunkCollisionPayloadResource.class,
                PhysicsRestoreStatusResource.class,
                Set.class,
                ArchetypeChunk.class);
            stitchChunk.setAccessible(true);
            Set<Object> stitchedPairs = new HashSet<>();
            PhysicsRuntimeResource runtime = store.getResource(
                PhysicsRuntimeResource.getResourceType());
            PhysicsIdentityIndexResource identity = store.getResource(
                PhysicsIdentityIndexResource.getResourceType());
            PhysicsChunkCollisionPayloadResource payloads = store.getResource(
                PhysicsChunkCollisionPayloadResource.getResourceType());
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
                (chunk, _) -> invoke(stitchChunk,
                    runtime,
                    identity,
                    payloads,
                    restore,
                    stitchedPairs,
                    chunk);
            store.forEachChunk(system.getQuery(), collector);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("Could not run ChunkCollisionVoxelStitchingSystem",
                exception);
        }
    }

    private static void invoke(@Nonnull Method method, @Nonnull Object... arguments) {
        try {
            method.invoke(null, arguments);
        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        }
    }

    private static void assertSoftSkipsEmpty(@Nonnull Store<PhysicsStore> store) {
        assertEquals(0,
            store.getResource(PhysicsRestoreStatusResource.getResourceType())
                .getSoftSkipsByReason()
                .size());
    }

    @Nonnull
    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }

    private record RuntimeFixture(@Nonnull Ref<PhysicsStore> spaceRef,
                                  @Nonnull BackendSpaceHandle spaceHandle,
                                  @Nonnull FakePhysicsBackendRuntime backendRuntime) {
    }
}
