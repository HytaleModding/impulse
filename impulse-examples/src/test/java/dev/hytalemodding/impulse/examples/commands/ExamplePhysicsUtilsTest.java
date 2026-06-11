package dev.hytalemodding.impulse.examples.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyDynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyIdentityComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyKinematicTargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyMaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyShapeComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import java.lang.reflect.Field;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExamplePhysicsUtilsTest {

    private ComponentRegistry<EntityStore> registry;
    private Object previousEntityModule;
    private Object previousImpulsePlugin;

    @BeforeEach
    void registerComponentTypes() throws Exception {
        previousEntityModule = staticField(EntityModule.class, "instance").get(null);
        previousImpulsePlugin = staticField(ImpulsePlugin.class, "instance").get(null);
        registry = new ComponentRegistry<>();
        registerEntityModuleTypes();
        registerImpulsePluginTypes();
        ControlLifecycle.enable();
        ImpulseControllableComponent.setComponentType(registry.registerComponent(
            ImpulseControllableComponent.class,
            "ImpulseControllable",
            ImpulseControllableComponent.CODEC));
        PhysicsControlSessionComponent.setComponentType(registry.registerComponent(
            PhysicsControlSessionComponent.class,
            PhysicsControlSessionComponent::new));
    }

    @AfterEach
    void clearComponentTypes() throws Exception {
        ControlLifecycle.disable();
        ImpulseControllableComponent.clearComponentType();
        PhysicsControlSessionComponent.clearComponentType();
        staticField(EntityModule.class, "instance").set(null, previousEntityModule);
        staticField(ImpulsePlugin.class, "instance").set(null, previousImpulsePlugin);
        registry.shutdown();
    }

    private void registerEntityModuleTypes() throws Exception {
        EntityModule entityModule = allocate(EntityModule.class);
        setField(entityModule,
            "transformComponentType",
            registry.registerComponent(TransformComponent.class,
                "Transform",
                TransformComponent.CODEC));
        setField(entityModule,
            "headRotationComponentType",
            registry.registerComponent(HeadRotation.class,
                "HeadRotation",
                HeadRotation.CODEC));
        setField(entityModule,
            "modelComponentType",
            registry.registerComponent(ModelComponent.class, () -> new ModelComponent(null)));
        staticField(EntityModule.class, "instance").set(null, entityModule);
    }

    private void registerImpulsePluginTypes() throws Exception {
        ImpulsePlugin plugin = allocate(ImpulsePlugin.class);
        setField(plugin,
            "physicsBodyAttachmentComponentType",
            registry.registerComponent(PhysicsBodyAttachmentComponent.class,
                "PhysicsBodyAttachment",
                PhysicsBodyAttachmentComponent.CODEC));
        setField(plugin,
            "physicsBodyIdentityComponentType",
            registry.registerComponent(PhysicsBodyIdentityComponent.class,
                "PhysicsBodyIdentity",
                PhysicsBodyIdentityComponent.CODEC));
        setField(plugin,
            "physicsBodyShapeComponentType",
            registry.registerComponent(PhysicsBodyShapeComponent.class,
                "PhysicsBodyShape",
                PhysicsBodyShapeComponent.CODEC));
        setField(plugin,
            "physicsBodyDynamicsComponentType",
            registry.registerComponent(PhysicsBodyDynamicsComponent.class,
                "PhysicsBodyDynamics",
                PhysicsBodyDynamicsComponent.CODEC));
        setField(plugin,
            "physicsBodyMaterialComponentType",
            registry.registerComponent(PhysicsBodyMaterialComponent.class,
                "PhysicsBodyMaterial",
                PhysicsBodyMaterialComponent.CODEC));
        setField(plugin,
            "physicsBodyCollisionComponentType",
            registry.registerComponent(PhysicsBodyCollisionComponent.class,
                "PhysicsBodyCollision",
                PhysicsBodyCollisionComponent.CODEC));
        setField(plugin,
            "physicsBodyKinematicTargetComponentType",
            registry.registerComponent(PhysicsBodyKinematicTargetComponent.class,
                "PhysicsBodyKinematicTarget",
                PhysicsBodyKinematicTargetComponent.CODEC));
        staticField(ImpulsePlugin.class, "instance").set(null, plugin);
    }

    @Test
    void physicsBodyCenterConvertsBackToVisualBasePosition() {
        Vector3d visualPosition = ExamplePhysicsOriginMath.visualPositionFromBodyCenter(new Vector3d(1.0, 2.5, 3.0),
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f));

        assertEquals(1.0, visualPosition.x, 0.0001);
        assertEquals(2.0, visualPosition.y, 0.0001);
        assertEquals(3.0, visualPosition.z, 0.0001);
    }

    @Test
    void ecsAuthoredDynamicBodyHolderAddsControllableMarkerWhenControlIsAvailable() {
        Holder<EntityStore> holder = registry.newHolder();

        ExamplePhysicsUtils.addControllableMarkerIfAvailable(holder, PhysicsBodyType.DYNAMIC);

        assertTrue(holder.getArchetype().contains(ImpulseControllableComponent.getComponentType()));
    }

    @Nonnull
    private static <T> T allocate(@Nonnull Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeType.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class)
            .invoke(unsafe, type));
    }

    private static void setField(@Nonnull Object target,
        @Nonnull String name,
        @Nonnull Object value) throws Exception {
        Field field = staticField(target.getClass(), name);
        field.set(target, value);
    }

    @Nonnull
    private static Field staticField(@Nonnull Class<?> owner, @Nonnull String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
