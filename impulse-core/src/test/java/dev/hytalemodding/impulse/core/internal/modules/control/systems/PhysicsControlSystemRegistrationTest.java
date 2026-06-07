package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class PhysicsControlSystemRegistrationTest {

    @AfterEach
    void clearRegistrations() {
        ControlLifecycle.disable();
        ImpulseControllableComponent.clearComponentType();
        PhysicsControlSessionComponent.clearComponentType();
    }

    @Test
    void sessionCleanupSystemCapturesCurrentSessionComponentType() {
        ComponentType<EntityStore, PhysicsControlSessionComponent> first = registerSessionType();
        PhysicsControlSessionComponent.setComponentType(first);
        PhysicsControlSessionCleanupSystem firstSystem = new PhysicsControlSessionCleanupSystem();

        ComponentType<EntityStore, PhysicsControlSessionComponent> second = registerSessionType();
        PhysicsControlSessionComponent.setComponentType(second);
        PhysicsControlSessionCleanupSystem secondSystem = new PhysicsControlSessionCleanupSystem();

        assertSame(first, firstSystem.componentType());
        assertSame(second, secondSystem.componentType());
    }

    @Test
    void controllableLifecycleSystemCapturesCurrentControllableComponentType() {
        ComponentType<EntityStore, ImpulseControllableComponent> first =
            registerControllableType();
        ImpulseControllableComponent.setComponentType(first);
        PhysicsControllableLifecycleSystem firstSystem = new PhysicsControllableLifecycleSystem();

        ComponentType<EntityStore, ImpulseControllableComponent> second =
            registerControllableType();
        ImpulseControllableComponent.setComponentType(second);
        PhysicsControllableLifecycleSystem secondSystem = new PhysicsControllableLifecycleSystem();

        assertSame(first, firstSystem.componentType());
        assertSame(second, secondSystem.componentType());
    }

    @Test
    void holderSystemCapturesCurrentControlComponentTypes() throws ReflectiveOperationException {
        ComponentType<EntityStore, ImpulseControllableComponent> firstControllable =
            registerControllableType();
        ComponentType<EntityStore, PhysicsControlSessionComponent> firstSession =
            registerSessionType();
        ImpulseControllableComponent.setComponentType(firstControllable);
        PhysicsControlSessionComponent.setComponentType(firstSession);
        PhysicsControlRuntimeHolderSystem firstSystem = new PhysicsControlRuntimeHolderSystem();

        ComponentType<EntityStore, ImpulseControllableComponent> secondControllable =
            registerControllableType();
        ComponentType<EntityStore, PhysicsControlSessionComponent> secondSession =
            registerSessionType();
        ImpulseControllableComponent.setComponentType(secondControllable);
        PhysicsControlSessionComponent.setComponentType(secondSession);
        PhysicsControlRuntimeHolderSystem secondSystem = new PhysicsControlRuntimeHolderSystem();

        assertSame(firstControllable, field(firstSystem, "controllableType"));
        assertSame(firstSession, field(firstSystem, "sessionType"));
        assertSame(secondControllable, field(secondSystem, "controllableType"));
        assertSame(secondSession, field(secondSystem, "sessionType"));
    }

    @Test
    void holderLoadKeepsControllableMarker() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ComponentType<EntityStore, ImpulseControllableComponent> controllableType =
            registry.registerComponent(ImpulseControllableComponent.class,
                "ImpulseControllable",
                ImpulseControllableComponent.CODEC);
        ComponentType<EntityStore, PhysicsControlSessionComponent> sessionType =
            registry.registerComponent(PhysicsControlSessionComponent.class,
                PhysicsControlSessionComponent::new);
        PhysicsControlRuntimeHolderSystem system = new PhysicsControlRuntimeHolderSystem(
            controllableType,
            sessionType);
        Store<EntityStore> store = registry.addStore(testEntityStore("control-marker-load-test"),
            EmptyResourceStorage.get());
        Holder<EntityStore> holder = registry.newHolder();
        holder.addComponent(controllableType, new ImpulseControllableComponent());

        system.onEntityAdd(holder, AddReason.LOAD, store);

        assertNotNull(holder.getComponent(controllableType));
        registry.shutdown();
    }

    private static ComponentType<EntityStore, ImpulseControllableComponent> registerControllableType() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        return registry.registerComponent(ImpulseControllableComponent.class,
            "ImpulseControllable",
            ImpulseControllableComponent.CODEC);
    }

    private static ComponentType<EntityStore, PhysicsControlSessionComponent> registerSessionType() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        return registry.registerComponent(PhysicsControlSessionComponent.class,
            PhysicsControlSessionComponent::new);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Nonnull
    private static EntityStore testEntityStore(@Nonnull String worldName) {
        return new EntityStore(testWorld(worldName));
    }

    @Nonnull
    private static World testWorld(@Nonnull String worldName) {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);
            World world = (World) unsafe.allocateInstance(World.class);
            Field nameField = World.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(world, worldName);
            return world;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to create test world", exception);
        }
    }
}
