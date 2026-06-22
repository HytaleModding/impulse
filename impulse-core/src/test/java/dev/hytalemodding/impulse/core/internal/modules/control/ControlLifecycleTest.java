package dev.hytalemodding.impulse.core.internal.modules.control;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ControlLifecycleTest {

    @BeforeEach
    @AfterEach
    void disableLifecycle() {
        ControlLifecycle.disable();
        ControlTypeRegistry.clearComponentTypes();
    }

    @Test
    void lifecycleStartsDisabled() {
        assertFalse(ControlLifecycle.isEnabled());
    }

    @Test
    void disablingLifecycleWithoutRegisteredSessionComponentDoesNotThrow() {
        ControlTypeRegistry.clearComponentTypes();
        ControlLifecycle.enable();

        assertDoesNotThrow(ControlLifecycle::disable);

        assertFalse(ControlLifecycle.isEnabled());
    }

    @Test
    void disablingLifecycleClearsRegisteredControlledBodies() {
        ControlLifecycle.enable();
        World world = TestInstanceFactory.world("control-physics-world");
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(world),
            EmptyResourceStorage.get());
        Ref<PhysicsStore> bodyRef = new TestPhysicsRef(store, 7);

        runOnWorldThread(world, () -> {
            PhysicsControlRuntimeStates.markControlled(bodyRef);
            assertTrue(PhysicsControlRuntimeStates.isControlled(bodyRef));
        });

        ControlLifecycle.disable();

        runOnWorldThread(world, () -> assertFalse(PhysicsControlRuntimeStates.isControlled(bodyRef)));
        registry.shutdown();
    }

    @Test
    void publicFacadeReadsPhysicsStoreBodyControlState() {
        ControlLifecycle.enable();
        World world = TestInstanceFactory.world("control-body-state-world");
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(world),
            EmptyResourceStorage.get());
        Ref<PhysicsStore> bodyRef = new TestPhysicsRef(store, 7);
        try {
            runOnWorldThread(world, () -> {
                assertFalse(PhysicsControlSessions.isBodyControlled(bodyRef));

                PhysicsControlRuntimeStates.markControlled(bodyRef);

                assertTrue(PhysicsControlSessions.isBodyControlled(bodyRef));
            });
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void publicFacadeMatchesControllerSessionBody() {
        ControlLifecycle.enable();
        ComponentRegistry<EntityStore> entityRegistry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(entityRegistry);
        World world = TestInstanceFactory.world("control-session-body-world");
        ComponentRegistry<PhysicsStore> physicsRegistry = new ComponentRegistry<>();
        Store<PhysicsStore> physicsStore = physicsRegistry.addStore(
            new PhysicsStore(world),
            EmptyResourceStorage.get());
        Store<EntityStore> entityStore = entityRegistry.addStore(new EntityStore(world),
            EmptyResourceStorage.get());
        Ref<EntityStore> controllerRef = addEmptyEntity(entityStore);
        Ref<EntityStore> otherControllerRef = addEmptyEntity(entityStore);
        Ref<PhysicsStore> bodyRef = new TestPhysicsRef(physicsStore, 7);
        Ref<PhysicsStore> otherBodyRef = new TestPhysicsRef(physicsStore, 8);
        Ref<PhysicsStore> anchorRef = new TestPhysicsRef(physicsStore, 9);
        try {
            entityStore.putComponent(controllerRef,
                PhysicsControlSessionComponent.getComponentType(),
                session(bodyRef, anchorRef));

            assertTrue(PhysicsControlSessions.hasSessionForBody(entityStore,
                controllerRef,
                bodyRef));
            assertFalse(PhysicsControlSessions.hasSessionForBody(entityStore,
                controllerRef,
                otherBodyRef));
            assertFalse(PhysicsControlSessions.hasSessionForBody(entityStore,
                otherControllerRef,
                bodyRef));
        } finally {
            entityRegistry.shutdown();
            physicsRegistry.shutdown();
        }
    }

    @Test
    void startSessionRejectsBodyControlledByAnotherController() {
        ControlLifecycle.enable();
        ComponentRegistry<EntityStore> entityRegistry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(entityRegistry);
        World world = TestInstanceFactory.world("control-session-reject-world");
        ComponentRegistry<PhysicsStore> physicsRegistry = new ComponentRegistry<>();
        Store<PhysicsStore> physicsStore = physicsRegistry.addStore(
            new PhysicsStore(world),
            EmptyResourceStorage.get());
        Store<EntityStore> entityStore = entityRegistry.addStore(new EntityStore(world),
            EmptyResourceStorage.get());
        Ref<EntityStore> controllerRef = addEmptyEntity(entityStore);
        Ref<PhysicsStore> bodyRef = new TestPhysicsRef(physicsStore, 7);
        Ref<PhysicsStore> anchorRef = new TestPhysicsRef(physicsStore, 8);
        try {
            runOnWorldThread(world, () -> {
                PhysicsControlRuntimeStates.markControlled(bodyRef);

                assertThrows(IllegalStateException.class,
                    () -> PhysicsControlSessions.startSession(entityStore,
                        controllerRef,
                        bodyRef,
                        anchorRef,
                        null,
                        null,
                        PhysicsBodyType.DYNAMIC,
                        4.0f,
                        new Vector3f(),
                        new Vector3f()));
            });
        } finally {
            entityRegistry.shutdown();
            physicsRegistry.shutdown();
        }
    }

    @Test
    void controlSessionsAreAvailableOnlyWhenLifecycleAndComponentTypesAreRegistered() {
        assertFalse(PhysicsControlSessions.isAvailable());

        ControlLifecycle.enable();
        assertFalse(PhysicsControlSessions.isAvailable());

        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(registry);

        assertTrue(PhysicsControlSessions.isAvailable());

        ControlLifecycle.disable();

        assertFalse(PhysicsControlSessions.isAvailable());
    }

    @Test
    void disablingLifecycleSkipsStoresWhoseWorldThreadHasStopped() {
        ControlLifecycle.enable();
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ControlTypeRegistry.registerComponentTypes(registry);
        Store<EntityStore> store = registry.addStore(
            new EntityStore(TestInstanceFactory.world("stopped-control-world")),
            EmptyResourceStorage.get());
        ControlLifecycle.registerStore(store);

        assertDoesNotThrow(ControlLifecycle::disable);

        assertFalse(ControlLifecycle.isEnabled());
        registry.shutdown();
    }

    @Nonnull
    private static Ref<EntityStore> addEmptyEntity(@Nonnull Store<EntityStore> store) {
        Holder<EntityStore> holder = store.getRegistry().newHolder();
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null) {
            throw new AssertionError("Failed to add test entity");
        }
        return ref;
    }

    @Nonnull
    private static PhysicsControlSessionComponent session(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<PhysicsStore> anchorRef) {
        return new PhysicsControlSessionComponent(bodyRef,
            anchorRef,
            null,
            null,
            PhysicsBodyType.DYNAMIC,
            4.0f,
            new Vector3f(),
            new Vector3f());
    }

    private static final class TestPhysicsRef extends Ref<PhysicsStore> {

        private TestPhysicsRef(Store<PhysicsStore> store, int index) {
            super(store, index);
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }

    private static void runOnWorldThread(@Nonnull World world, @Nonnull Runnable task) {
        setThread(world, Thread.currentThread());
        try {
            task.run();
        } finally {
            setThread(world, null);
        }
    }

    private static void setThread(@Nonnull World world, @Nullable Thread thread) {
        try {
            Method setThread = TickingThread.class.getDeclaredMethod("setThread", Thread.class);
            setThread.setAccessible(true);
            setThread.invoke(world, thread);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to bind test world thread", exception);
        }
    }
}
