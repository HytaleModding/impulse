package dev.hytalemodding.impulse.core.internal.modules.physicsentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsEntityProjectionCleanupTest {

    @AfterEach
    void clearTypes() {
        PhysicsEntityLifecycle.disable();
        PhysicsEntityTypeRegistry.clearEntityStoreTypes();
    }

    @Test
    void cleanAllOwnsAttachmentAndProxyCleanupResult() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        Store<EntityStore> store = store(registry, "physicsentity-clean-all");
        try {
            UUID ownedBodyUuid = UUID.randomUUID();
            UUID externalBodyUuid = UUID.randomUUID();
            Ref<EntityStore> ownedVisual = addAttachment(store,
                ownedBodyUuid,
                BodyAttachmentComponent.impulseOwnedVisual(ownedBodyUuid,
                    new Vector3f(),
                    new org.joml.Quaternionf(),
                    Float.NaN));
            Ref<EntityStore> external = addAttachment(store,
                externalBodyUuid,
                BodyAttachmentComponent.externalEntity(externalBodyUuid));
            Ref<EntityStore> orphanProxy = addOrphanProxy(store);

            PhysicsEntityProjectionCleanup.Result result =
                PhysicsEntityProjectionCleanup.cleanAll(store, List.of());

            assertFalse(result.skipped());
            assertEquals(1, result.removedAttachmentEntities());
            assertEquals(1, result.detachedExternalAttachments());
            assertEquals(1, result.removedOrphanVisualEntities());
            assertFalse(ownedVisual.isValid());
            assertTrue(external.isValid());
            assertNull(store.getComponent(external, BodyAttachmentComponent.getComponentType()));
            assertFalse(orphanProxy.isValid());
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void cleanSelectedFiltersAttachmentsByBody() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        Store<EntityStore> store = store(registry, "physicsentity-clean-selected");
        try {
            UUID selectedBodyUuid = UUID.randomUUID();
            UUID ignoredBodyUuid = UUID.randomUUID();
            Ref<EntityStore> selected = addAttachment(store,
                selectedBodyUuid,
                BodyAttachmentComponent.externalEntity(selectedBodyUuid));
            Ref<EntityStore> ignored = addAttachment(store,
                ignoredBodyUuid,
                BodyAttachmentComponent.externalEntity(ignoredBodyUuid));
            PhysicsEntityProjectionCleanup.Result result =
                PhysicsEntityProjectionCleanup.cleanSelected(store,
                    Set.of(selectedBodyUuid),
                    new org.joml.Vector3d(),
                    1.0,
                    List.of());

            assertEquals(1, result.detachedExternalAttachments());
            assertEquals(0, result.removedOrphanVisualEntities());
            assertTrue(selected.isValid());
            assertNull(store.getComponent(selected, BodyAttachmentComponent.getComponentType()));
            assertNotNull(store.getComponent(ignored, BodyAttachmentComponent.getComponentType()));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static Store<EntityStore> store(@Nonnull ComponentRegistry<EntityStore> registry,
        @Nonnull String worldName) {
        ComponentRegistryProxy<EntityStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsEntityTypeRegistry.registerComponentTypes(proxy);
        PhysicsEntityTypeRegistry.registerResourceTypes(proxy);
        PhysicsEntityTypeRegistry.registerEventTypes(proxy);
        PhysicsEntityTypeRegistry.registerSystemGroups(proxy);
        PhysicsEntityLifecycle.enable();
        return registry.addStore(new EntityStore(TestInstanceFactory.world(worldName)),
            EmptyResourceStorage.get());
    }

    @Nonnull
    private static Ref<EntityStore> addAttachment(@Nonnull Store<EntityStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyAttachmentComponent attachment) {
        Holder<EntityStore> holder = store.getRegistry().newHolder();
        holder.addComponent(BodyAttachmentComponent.getComponentType(), attachment);
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        store.getResource(PhysicsProjectionIndexResource.getResourceType())
            .registerAttachment(bodyUuid, ref);
        assertNotNull(ref);
        return ref;
    }

    @Nonnull
    private static Ref<EntityStore> addOrphanProxy(@Nonnull Store<EntityStore> store) {
        Holder<EntityStore> holder = store.getRegistry().newHolder();
        holder.addComponent(GeneratedVisualProxyComponent.getComponentType(),
            new GeneratedVisualProxyComponent());
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        assertNotNull(ref);
        return ref;
    }
}
