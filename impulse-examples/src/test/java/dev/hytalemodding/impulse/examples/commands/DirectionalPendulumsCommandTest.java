package dev.hytalemodding.impulse.examples.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.ImpulseBackendRegistry;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.IdentityIndexSystem;
import dev.hytalemodding.impulse.core.internal.systems.PersistenceHydrationSystem;
import dev.hytalemodding.impulse.core.internal.systems.SpaceSettingsApplicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.SpaceBindingSystem;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointType;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils.CreatedBlockBody;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

class DirectionalPendulumsCommandTest {

    private static final ObjenesisStd OBJENESIS = new ObjenesisStd();
    private static final BackendId JOLT_BACKEND_ID =
        new BackendId("test:directional-pendulums-jolt");
    private static final BackendId RAPIER_BACKEND_ID =
        new BackendId("test:directional-pendulums-rapier");
    private static final BackendId BINDING_BACKEND_ID =
        new BackendId("test:directional-pendulums-binding");
    private static final String CINEMATIC_JOLT_BLOCK_TYPE = "Rock_Aqua";
    private static final String CINEMATIC_RAPIER_BLOCK_TYPE = "Rock_Basalt";
    private static final float CINEMATIC_MIN_LINEAR_SPEED = 2.0f;
    private static final float CINEMATIC_MIN_ANGULAR_SPEED = 1.0f;

    @Test
    void spawnDemoCreatesFourNonStreamingSpacesWithDirectionalDoublePendulums() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(world("directional-pendulums-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);

            DirectionalPendulumsCommand.SpawnResult result =
                DirectionalPendulumsCommand.spawnDemo(store,
                    JOLT_BACKEND_ID,
                    RAPIER_BACKEND_ID,
                    new Vector3d(10.0, 20.0, 30.0));

            assertEquals(4, result.pendulums().size());
            assertEquals(12, result.createdBodies().size());

            Vector3f[] expectedGravities = {
                new Vector3f(0.0f, -9.81f, 0.0f),
                new Vector3f(0.0f, 9.81f, 0.0f),
                new Vector3f(9.81f, 0.0f, 0.0f),
                new Vector3f(-9.81f, 0.0f, 0.0f)
            };
            Vector3f[] expectedAnchors = {
                new Vector3f(2.0f, 20.0f, 22.0f),
                new Vector3f(18.0f, 20.0f, 22.0f),
                new Vector3f(2.0f, 20.0f, 38.0f),
                new Vector3f(18.0f, 20.0f, 38.0f)
            };
            BackendId[] expectedBackends = {
                JOLT_BACKEND_ID,
                JOLT_BACKEND_ID,
                RAPIER_BACKEND_ID,
                RAPIER_BACKEND_ID
            };
            for (int index = 0; index < expectedGravities.length; index++) {
                DirectionalPendulumsCommand.CreatedPendulum pendulum =
                    result.pendulums().get(index);
                assertEquals(expectedBackends[index], pendulum.backendId());
                assertVectorEquals(expectedGravities[index], pendulum.gravity());
                assertBodyPosition(expectedAnchors[index], pendulum.anchor());
                assertPendulumVisual(pendulum.anchor());
                assertPendulumVisual(pendulum.upper());
                assertPendulumVisual(pendulum.lower());
                assertEquals(PhysicsChunkCollisionMode.NONE,
                    PhysicsSpaces.chunkCollisionSettings(store, pendulum.spaceRef()).getMode());
                assertNoSleepSolverSettings(store, pendulum.spaceRef());
                assertVectorEquals(expectedGravities[index],
                    store.getComponent(pendulum.spaceRef(), SpaceComponent.getComponentType())
                        .getGravity());
                assertBodyMass(store, pendulum.anchor().bodyRef(), 0.0f);
                assertBodyMass(store, pendulum.upper().bodyRef(), 1.0f);
                assertBodyMass(store, pendulum.lower().bodyRef(), 1.0f);
                assertPendulumCollisionFilter(store, pendulum.anchor().bodyRef());
                assertPendulumCollisionFilter(store, pendulum.upper().bodyRef());
                assertPendulumCollisionFilter(store, pendulum.lower().bodyRef());
                assertTangentialVelocity(store,
                    pendulum.upper().bodyRef(),
                    expectedGravities[index]);
                assertTangentialVelocity(store,
                    pendulum.lower().bodyRef(),
                    expectedGravities[index]);
                assertPointJoint(store,
                    pendulum.topJointRef(),
                    pendulum.anchor().bodyRef(),
                    pendulum.upper().bodyRef(),
                    new Vector3f(),
                    normalized(expectedGravities[index], -1.0f));
                assertEndpointVelocityMatch(store,
                    pendulum.anchor().bodyRef(),
                    new Vector3f(),
                    pendulum.upper().bodyRef(),
                    normalized(expectedGravities[index], -1.0f));
                assertPointJoint(store,
                    pendulum.middleJointRef(),
                    pendulum.upper().bodyRef(),
                    pendulum.lower().bodyRef(),
                    normalized(expectedGravities[index], 1.0f),
                    normalized(expectedGravities[index], -1.0f));
                assertEndpointVelocityMatch(store,
                    pendulum.upper().bodyRef(),
                    normalized(expectedGravities[index], 1.0f),
                    pendulum.lower().bodyRef(),
                    normalized(expectedGravities[index], -1.0f));
            }
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void spawnCinematicPresetKeepsFourNonStreamingBackendSplitSpaces() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(world("directional-pendulums-cinematic-spaces-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);

            DirectionalPendulumsCommand.SpawnResult result =
                spawnCinematicDemo(store,
                    JOLT_BACKEND_ID,
                    RAPIER_BACKEND_ID,
                    new Vector3d(10.0, 20.0, 30.0));

            assertEquals(4, result.pendulums().size());
            assertEquals(12, result.createdBodies().size());
            BackendId[] expectedBackends = {
                JOLT_BACKEND_ID,
                JOLT_BACKEND_ID,
                RAPIER_BACKEND_ID,
                RAPIER_BACKEND_ID
            };
            Vector3f[] expectedGravities = directionalGravities();
            for (int index = 0; index < expectedBackends.length; index++) {
                DirectionalPendulumsCommand.CreatedPendulum pendulum =
                    result.pendulums().get(index);
                assertEquals(expectedBackends[index], pendulum.backendId());
                assertVectorEquals(expectedGravities[index], pendulum.gravity());
                assertEquals(PhysicsChunkCollisionMode.NONE,
                    PhysicsSpaces.chunkCollisionSettings(store, pendulum.spaceRef()).getMode());
                assertNoSleepSolverSettings(store, pendulum.spaceRef());
                assertVectorEquals(expectedGravities[index],
                    store.getComponent(pendulum.spaceRef(), SpaceComponent.getComponentType())
                        .getGravity());
            }
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void spawnCinematicPresetUsesTighterBackendDistinctVisualLayout() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(world("directional-pendulums-cinematic-layout-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);

            DirectionalPendulumsCommand.SpawnResult result =
                spawnCinematicDemo(store,
                    JOLT_BACKEND_ID,
                    RAPIER_BACKEND_ID,
                    new Vector3d(10.0, 20.0, 30.0));

            Vector3f[] expectedAnchors = {
                new Vector3f(6.0f, 20.0f, 26.0f),
                new Vector3f(14.0f, 20.0f, 26.0f),
                new Vector3f(6.0f, 20.0f, 34.0f),
                new Vector3f(14.0f, 20.0f, 34.0f)
            };
            String[] expectedBlockTypes = {
                CINEMATIC_JOLT_BLOCK_TYPE,
                CINEMATIC_JOLT_BLOCK_TYPE,
                CINEMATIC_RAPIER_BLOCK_TYPE,
                CINEMATIC_RAPIER_BLOCK_TYPE
            };
            for (int index = 0; index < expectedAnchors.length; index++) {
                DirectionalPendulumsCommand.CreatedPendulum pendulum =
                    result.pendulums().get(index);
                assertBodyPosition(expectedAnchors[index], pendulum.anchor());
                assertCinematicVisual(pendulum.anchor(), expectedBlockTypes[index]);
                assertCinematicVisual(pendulum.upper(), expectedBlockTypes[index]);
                assertCinematicVisual(pendulum.lower(), expectedBlockTypes[index]);
            }
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void spawnCinematicPresetUsesStrongerLateralInitialMotion() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(world("directional-pendulums-cinematic-motion-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);

            DirectionalPendulumsCommand.SpawnResult result =
                spawnCinematicDemo(store,
                    JOLT_BACKEND_ID,
                    RAPIER_BACKEND_ID,
                    new Vector3d(10.0, 20.0, 30.0));

            Vector3f[] expectedGravities = directionalGravities();
            for (int index = 0; index < expectedGravities.length; index++) {
                DirectionalPendulumsCommand.CreatedPendulum pendulum =
                    result.pendulums().get(index);
                assertStrongLateralVelocity(store,
                    pendulum.upper().bodyRef(),
                    expectedGravities[index]);
                assertStrongLateralVelocity(store,
                    pendulum.lower().bodyRef(),
                    expectedGravities[index]);
                assertEndpointVelocityMatch(store,
                    pendulum.anchor().bodyRef(),
                    new Vector3f(),
                    pendulum.upper().bodyRef(),
                    normalized(expectedGravities[index], -1.0f));
                assertEndpointVelocityMatch(store,
                    pendulum.upper().bodyRef(),
                    normalized(expectedGravities[index], 1.0f),
                    pendulum.lower().bodyRef(),
                    normalized(expectedGravities[index], -1.0f));
            }
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void storeTickAppliesDirectionalSpaceGravitiesWithoutChangingThem() {
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(BINDING_BACKEND_ID, false, false);
        ImpulseBackendRegistry.registerRuntimeProvider(provider);
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        proxy.registerSystem(new PersistenceHydrationSystem());
        proxy.registerSystem(new IdentityIndexSystem());
        proxy.registerSystem(new SpaceBindingSystem());
        proxy.registerSystem(new SpaceSettingsApplicationSystem());
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(world("directional-pendulums-binding-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            restore.markComplete();
            restore.markHydrated();

            DirectionalPendulumsCommand.SpawnResult result =
                DirectionalPendulumsCommand.spawnDemo(store,
                    BINDING_BACKEND_ID,
                    new Vector3d(10.0, 20.0, 30.0));

            store.tick(0.0f);
            assertFalse(restore.isFailed(), restore.getFailureMessage());
            assertEquals(1, provider.createdRuntimes().size());
            FakePhysicsBackendRuntime backendRuntime = provider.createdRuntimes().get(0);
            assertBackendGravities(store, backendRuntime, result);

            store.tick(0.0f);
            assertFalse(restore.isFailed(), restore.getFailureMessage());
            assertBackendGravities(store, backendRuntime, result);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    private static void assertBackendGravities(@Nonnull Store<PhysicsStore> store,
        @Nonnull FakePhysicsBackendRuntime backendRuntime,
        @Nonnull DirectionalPendulumsCommand.SpawnResult result) {
        PhysicsRuntimeResource runtime = store.getResource(
            PhysicsRuntimeResource.getResourceType());
        for (DirectionalPendulumsCommand.CreatedPendulum pendulum : result.pendulums()) {
            BackendSpaceHandle handle = runtime.getSpaceHandle(pendulum.spaceRef());
            assertNotNull(handle);
            assertVectorEquals(pendulum.gravity(),
                backendGravity(backendRuntime, handle.value()));
        }
    }

    @Nonnull
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DirectionalPendulumsCommand.SpawnResult spawnCinematicDemo(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull BackendId joltBackendId,
        @Nonnull BackendId rapierBackendId,
        @Nonnull Vector3d center) {
        try {
            Class<?> presetType = Class.forName(
                DirectionalPendulumsCommand.class.getName() + "$Preset");
            Object cinematicPreset = Enum.valueOf((Class<Enum>) presetType.asSubclass(Enum.class),
                "CINEMATIC");
            Method spawnDemo = DirectionalPendulumsCommand.class.getDeclaredMethod("spawnDemo",
                Store.class,
                BackendId.class,
                BackendId.class,
                Vector3d.class,
                presetType);
            spawnDemo.setAccessible(true);
            return (DirectionalPendulumsCommand.SpawnResult) spawnDemo.invoke(null,
                store,
                joltBackendId,
                rapierBackendId,
                center,
                cinematicPreset);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Expected DirectionalPendulumsCommand.Preset.CINEMATIC",
                exception);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("Expected cinematic spawnDemo overload", exception);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Could not invoke cinematic spawnDemo overload", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getTargetException();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError("Cinematic spawnDemo overload failed", cause);
        }
    }

    @Nonnull
    private static Vector3f backendGravity(@Nonnull FakePhysicsBackendRuntime runtime,
        int spaceId) {
        Vector3f gravity = new Vector3f();
        runtime.getGravity(spaceId, gravity::set);
        return gravity;
    }

    private static void assertBodyMass(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        float expectedMass) {
        DynamicsComponent dynamics = store.getComponent(bodyRef,
            DynamicsComponent.getComponentType());
        assertNotNull(dynamics);
        assertEquals(expectedMass, dynamics.getMass());
    }

    private static void assertPendulumCollisionFilter(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        CollisionFilterComponent filter = store.getComponent(bodyRef,
            CollisionFilterComponent.getComponentType());
        assertNotNull(filter);
        assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY, filter.getCollisionGroup());
        assertEquals(PhysicsCollisionFilters.TERRAIN, filter.getCollisionMask());
    }

    private static void assertPointJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull Ref<PhysicsStore> expectedBodyARef,
        @Nonnull Ref<PhysicsStore> expectedBodyBRef,
        @Nonnull Vector3f expectedAnchorA,
        @Nonnull Vector3f expectedAnchorB) {
        JointComponent joint = store.getComponent(jointRef,
            JointComponent.getComponentType());
        assertNotNull(joint);
        assertEquals(JointType.POINT, joint.getType());
        assertSame(expectedBodyARef, joint.getBodyARef());
        assertSame(expectedBodyBRef, joint.getBodyBRef());
        assertVectorEquals(expectedAnchorA, joint.getAnchorA());
        assertVectorEquals(expectedAnchorB, joint.getAnchorB());
    }

    private static void assertBodyPosition(@Nonnull Vector3f expected,
        @Nonnull CreatedBlockBody body) {
        assertEquals(expected.x, body.positionX(), 0.0001f);
        assertEquals(expected.y, body.positionY(), 0.0001f);
        assertEquals(expected.z, body.positionZ(), 0.0001f);
    }

    private static void assertPendulumVisual(@Nonnull CreatedBlockBody body) {
        assertEquals(DirectionalPendulumsCommand.PENDULUM_BLOCK_TYPE, body.blockType());
        assertEquals(DirectionalPendulumsCommand.PENDULUM_VISUAL_ORIGIN_OFFSET_Y,
            body.visualOriginOffsetY(),
            0.0001f);
    }

    private static void assertCinematicVisual(@Nonnull CreatedBlockBody body,
        @Nonnull String expectedBlockType) {
        assertEquals(expectedBlockType, body.blockType());
        assertEquals(DirectionalPendulumsCommand.PENDULUM_VISUAL_ORIGIN_OFFSET_Y,
            body.visualOriginOffsetY(),
            0.0001f);
    }

    private static void assertNoSleepSolverSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        PhysicsSolverSettings settings = PhysicsSpaces.solverSettings(store, spaceRef);
        assertNotNull(settings);
        assertEquals(DirectionalPendulumsCommand.PENDULUM_SLEEP_LINEAR_THRESHOLD,
            settings.getDynamicSleepLinearThreshold(),
            0.0001f);
        assertEquals(DirectionalPendulumsCommand.PENDULUM_SLEEP_ANGULAR_THRESHOLD,
            settings.getDynamicSleepAngularThreshold(),
            0.0001f);
        assertEquals(DirectionalPendulumsCommand.PENDULUM_TIME_UNTIL_SLEEP,
            settings.getDynamicSleepTimeUntilSleep(),
            0.0001f);
    }

    private static void assertTangentialVelocity(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Vector3f gravity) {
        TargetComponent target = store.getComponent(bodyRef,
            TargetComponent.getComponentType());
        assertNotNull(target);
        Vector3f velocity = target.getLinearVelocity();
        assertEquals(0.0f, velocity.dot(gravity), 0.0001f);
        if (velocity.lengthSquared() <= 0.1f) {
            throw new AssertionError("Expected tangential initial velocity but got " + velocity);
        }
        Vector3f angularVelocity = target.getAngularVelocity();
        assertEquals(0.0f, angularVelocity.dot(gravity), 0.0001f);
        if (angularVelocity.lengthSquared() <= 0.1f) {
            throw new AssertionError("Expected angular initial velocity but got "
                + angularVelocity);
        }
    }

    private static void assertStrongLateralVelocity(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Vector3f gravity) {
        TargetComponent target = store.getComponent(bodyRef,
            TargetComponent.getComponentType());
        assertNotNull(target);
        Vector3f velocity = target.getLinearVelocity();
        assertEquals(0.0f, velocity.dot(gravity), 0.0001f);
        assertTrue(velocity.length() >= CINEMATIC_MIN_LINEAR_SPEED,
            () -> "Expected cinematic linear speed >= " + CINEMATIC_MIN_LINEAR_SPEED
                + " but got " + velocity);
        Vector3f angularVelocity = target.getAngularVelocity();
        assertEquals(0.0f, angularVelocity.dot(gravity), 0.0001f);
        assertTrue(angularVelocity.length() >= CINEMATIC_MIN_ANGULAR_SPEED,
            () -> "Expected cinematic angular speed >= " + CINEMATIC_MIN_ANGULAR_SPEED
                + " but got " + angularVelocity);
    }

    private static void assertEndpointVelocityMatch(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyARef,
        @Nonnull Vector3f anchorA,
        @Nonnull Ref<PhysicsStore> bodyBRef,
        @Nonnull Vector3f anchorB) {
        assertVectorEquals(endpointVelocity(store, bodyARef, anchorA),
            endpointVelocity(store, bodyBRef, anchorB));
    }

    @Nonnull
    private static Vector3f endpointVelocity(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Vector3f localAnchor) {
        TargetComponent target = store.getComponent(bodyRef,
            TargetComponent.getComponentType());
        if (target == null) {
            return new Vector3f();
        }
        Vector3f angular = target.getAngularVelocity();
        Vector3f anchorVelocity = new Vector3f();
        angular.cross(localAnchor, anchorVelocity);
        return target.getLinearVelocity().add(anchorVelocity);
    }

    private static void assertVectorEquals(@Nonnull Vector3f expected,
        @Nonnull Vector3f actual) {
        assertEquals(expected.x, actual.x, 0.0001f);
        assertEquals(expected.y, actual.y, 0.0001f);
        assertEquals(expected.z, actual.z, 0.0001f);
    }

    @Nonnull
    private static Vector3f[] directionalGravities() {
        return new Vector3f[] {
            new Vector3f(0.0f, -9.81f, 0.0f),
            new Vector3f(0.0f, 9.81f, 0.0f),
            new Vector3f(9.81f, 0.0f, 0.0f),
            new Vector3f(-9.81f, 0.0f, 0.0f)
        };
    }

    @Nonnull
    private static Vector3f normalized(@Nonnull Vector3f vector, float scale) {
        return new Vector3f(vector).normalize().mul(scale);
    }

    @Nonnull
    private static World world(@Nonnull String worldName) {
        World world = OBJENESIS.newInstance(World.class);
        try {
            Field name = World.class.getDeclaredField("name");
            name.setAccessible(true);
            name.set(world, worldName);
            return world;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not create test world", exception);
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
