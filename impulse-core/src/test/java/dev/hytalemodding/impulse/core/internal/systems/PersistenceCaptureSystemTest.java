package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.hytalemodding.impulse.core.internal.persistence.PersistentColliderDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentMaterialDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentShapeDto;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PersistenceCaptureSystemTest {

    @Test
    void generatedChunkCollisionRowsAreExcludedFromPersistentDtoTables() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("physics-capture-runtime-only-terrain-test")),
            EmptyResourceStorage.get());
        try {
            UUID spaceUuid = uuid(1);
            UUID persistentBodyUuid = uuid(2);
            UUID generatedBodyUuid = uuid(3);
            Ref<PhysicsStore> spaceRef = addSpace(store, spaceUuid);
            store.putComponent(spaceRef,
                CollisionFilterComponent.getComponentType(),
                new CollisionFilterComponent(0x40, 0x03));
            addBody(store,
                persistentBodyUuid,
                body(spaceUuid, PhysicsBodyKind.BODY, PhysicsBodyPersistenceMode.PERSISTENT, spaceRef),
                null);
            Ref<PhysicsStore> generatedRef = addBody(store,
                generatedBodyUuid,
                body(spaceUuid, PhysicsBodyKind.TERRAIN, PhysicsBodyPersistenceMode.RUNTIME_ONLY, spaceRef),
                new ChunkCollisionSourceComponent("0:0:0",
                    0,
                    0,
                    0,
                    "chunk-collision/0/0/0",
                    PartKind.BOX,
                    0));

            BodyComponent generatedBody = store.getComponent(generatedRef,
                BodyComponent.getComponentType());
            assertNotNull(generatedBody);
            assertEquals(PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                generatedBody.getPersistenceMode());
            assertNotNull(store.getComponent(generatedRef,
                ChunkCollisionSourceComponent.getComponentType()));

            capturePersistence(store);

            PersistentPhysicsStoreResource persistent = store.getResource(
                PersistentPhysicsStoreResource.getResourceType());
            assertEquals(1, persistent.getSpaces().length);
            assertEquals(1, persistent.getBodies().length);
            assertEquals(1, persistent.getColliders().length);
            assertEquals(1, persistent.getShapes().length);
            assertEquals(1, persistent.getMaterials().length);
            assertEquals(0x40, persistent.getSpaces()[0].getChunkCollisionGroup());
            assertEquals(0x03, persistent.getSpaces()[0].getChunkCollisionMask());
            assertTrue(containsBody(persistent, persistentBodyUuid));
            assertFalse(containsBody(persistent, generatedBodyUuid));
            assertFalse(containsCollider(persistent, generatedBodyUuid));
            assertFalse(containsShape(persistent, generatedBodyUuid));
            assertFalse(containsMaterial(persistent, generatedBodyUuid));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static Ref<PhysicsStore> addSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        Ref<PhysicsStore> ref = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(new BackendId("test:persistence-capture"),
                    new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        assertNotNull(ref);
        return ref;
    }

    private static void capturePersistence(@Nonnull Store<PhysicsStore> store) {
        try {
            // Full system registration pulls in unrelated backend-binding dependencies.
            Class<?> captureType = Arrays.stream(PersistenceCaptureSystem.class.getDeclaredClasses())
                .filter(candidate -> candidate.getSimpleName().equals("Capture"))
                .findFirst()
                .orElseThrow();
            Constructor<?> constructor = captureType.getDeclaredConstructor(Map.class);
            constructor.setAccessible(true);
            Object capture = constructor.newInstance(Map.of());
            Method collectChunk = captureType.getDeclaredMethod("collectChunk",
                ArchetypeChunk.class);
            collectChunk.setAccessible(true);
            BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
                (chunk, _) -> invoke(collectChunk, capture, chunk);
            store.forEachChunk(new PersistenceCaptureSystem().getQuery(), collector);
            Method writeTo = captureType.getDeclaredMethod("writeTo",
                PersistentPhysicsStoreResource.class);
            writeTo.setAccessible(true);
            invoke(writeTo,
                capture,
                store.getResource(PersistentPhysicsStoreResource.getResourceType()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not run PersistenceCaptureSystem capture", exception);
        }
    }

    private static void invoke(@Nonnull Method method,
        @Nonnull Object target,
        @Nonnull Object argument) {
        try {
            method.invoke(target, argument);
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

    @Nonnull
    private static Ref<PhysicsStore> addBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyComponent body,
        ChunkCollisionSourceComponent source) {
        Holder<PhysicsStore> holder = PhysicsEntities.bodyHolder(store,
            bodyUuid,
            body,
            new DynamicsComponent(PhysicsBodyType.STATIC, 0.0f, 0.0f, 0.0f, false),
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
            new CollisionFilterComponent(PhysicsCollisionFilters.TERRAIN,
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
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        BodyComponent body = new BodyComponent(spaceUuid, kind, persistenceMode);
        body.setSpaceRef(spaceRef);
        return body;
    }

    @Nonnull
    private static TargetComponent target() {
        TargetComponent target = new TargetComponent();
        target.setPosition(new Vector3f(1.0f, 2.0f, 3.0f));
        return target;
    }

    private static boolean containsBody(@Nonnull PersistentPhysicsStoreResource persistent,
        @Nonnull UUID bodyUuid) {
        return Arrays.stream(persistent.getBodies())
            .anyMatch(body -> bodyUuid.equals(body.getBodyUuid()));
    }

    private static boolean containsCollider(@Nonnull PersistentPhysicsStoreResource persistent,
        @Nonnull UUID colliderUuid) {
        return Arrays.stream(persistent.getColliders())
            .map(PersistentColliderDto::getColliderUuid)
            .anyMatch(colliderUuid::equals);
    }

    private static boolean containsShape(@Nonnull PersistentPhysicsStoreResource persistent,
        @Nonnull UUID shapeUuid) {
        return Arrays.stream(persistent.getShapes())
            .map(PersistentShapeDto::getShapeUuid)
            .anyMatch(shapeUuid::equals);
    }

    private static boolean containsMaterial(@Nonnull PersistentPhysicsStoreResource persistent,
        @Nonnull UUID materialUuid) {
        return Arrays.stream(persistent.getMaterials())
            .map(PersistentMaterialDto::getMaterialUuid)
            .anyMatch(materialUuid::equals);
    }

    @Nonnull
    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }
}
