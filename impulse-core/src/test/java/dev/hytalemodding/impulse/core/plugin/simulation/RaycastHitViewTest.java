package dev.hytalemodding.impulse.core.plugin.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import java.util.ArrayList;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class RaycastHitViewTest {

    @Test
    void infersBodyAndShapeTypesFromBodyRefComponents() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("raycast-hit-view-type-inference-test")),
            EmptyResourceStorage.get());
        try {
            Holder<PhysicsStore> holder = registry.newHolder();
            holder.putComponent(DynamicsComponent.getComponentType(),
                new DynamicsComponent(PhysicsBodyType.KINEMATIC,
                    0.0f,
                    0.0f,
                    0.0f,
                    false));
            holder.putComponent(ShapeComponent.getComponentType(),
                new ShapeComponent(ShapeType.CAPSULE,
                    0.5f,
                    0.5f,
                    0.5f,
                    0.25f,
                    1.0f,
                    PhysicsAxis.Y,
                    0.0f,
                    ""));
            Ref<PhysicsStore> bodyRef = store.addEntity(holder, AddReason.SPAWN);
            RaycastHitView view = new RaycastHitView(bodyRef,
                new Vector3f(1.0f, 2.0f, 3.0f),
                new Vector3f(0.0f, 1.0f, 0.0f),
                0.25f,
                4.5f);

            assertEquals(PhysicsBodyType.KINEMATIC, view.bodyType());
            assertEquals(ShapeType.CAPSULE, view.shapeType());
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }
}
