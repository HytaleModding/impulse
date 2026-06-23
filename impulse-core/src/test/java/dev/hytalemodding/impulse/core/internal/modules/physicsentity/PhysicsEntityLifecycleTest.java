package dev.hytalemodding.impulse.core.internal.modules.physicsentity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityAttachments;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsEntityLifecycleTest {

    @AfterEach
    void clearLifecycleAndTypes() {
        PhysicsEntityLifecycle.disable();
        PhysicsEntityTypeRegistry.clearEntityStoreTypes();
    }

    @Test
    void attachmentsAreUnavailableWithoutLifecycleAndRegisteredTypes() {
        assertFalse(PhysicsEntityLifecycle.isEnabled());
        assertFalse(PhysicsEntityTypes.areEntityStoreTypesRegistered());
        assertFalse(BodyAttachmentComponent.isComponentTypeRegistered());
        assertFalse(GeneratedVisualProxyComponent.isComponentTypeRegistered());
        assertFalse(PhysicsEntityAttachments.isAvailable());
    }

    @Test
    void attachmentsAreAvailableOnlyWhenLifecycleAndTypesAreRegistered() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<EntityStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        try {
            PhysicsEntityLifecycle.enable();
            assertFalse(PhysicsEntityAttachments.isAvailable());

            PhysicsEntityTypeRegistry.registerComponentTypes(proxy);
            PhysicsEntityTypeRegistry.registerResourceTypes(proxy);
            PhysicsEntityTypeRegistry.registerEventTypes(proxy);
            PhysicsEntityTypeRegistry.registerSystemGroups(proxy);

            assertTrue(PhysicsEntityTypes.areEntityStoreTypesRegistered());
            assertTrue(BodyAttachmentComponent.isComponentTypeRegistered());
            assertTrue(GeneratedVisualProxyComponent.isComponentTypeRegistered());
            assertTrue(PhysicsEntityAttachments.isAvailable());

            PhysicsEntityLifecycle.disable();

            assertFalse(PhysicsEntityAttachments.isAvailable());
        } finally {
            registry.shutdown();
        }
    }
}
