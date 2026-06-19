package dev.hytalemodding.impulse.core.internal.systems.visual;

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
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.PhysicsEntityTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import java.util.ArrayList;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsProjectionCleanupSystemTest {

    @AfterEach
    void clearTypes() {
        PhysicsEntityTypeRegistry.clearEntityStoreTypes();
    }

    @Test
    void externalAttachmentWithDestroyedBodyRefIsDetachedButEntityRemains() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        Store<EntityStore> store = store(registry, "projection-cleanup-external");
        try {
            UUID bodyUuid = UUID.randomUUID();
            Ref<PhysicsStore> bodyRef = new TestPhysicsRef(11, false);
            Ref<EntityStore> entityRef = addAttachment(store,
                bodyUuid,
                bodyRef,
                AttachmentLifecycle.EXTERNAL_ENTITY,
                false);
            PhysicsProjectionIndexResource projection = projection(store);
            projection.registerAttachment(bodyUuid, bodyRef, entityRef);

            new PhysicsProjectionCleanupSystem().tick(0.0f, 0, store);

            assertTrue(entityRef.isValid());
            assertNull(store.getComponent(entityRef, BodyAttachmentComponent.getComponentType()));
            assertFalse(projection.hasAttachments(bodyUuid));
            assertFalse(projection.hasAttachments(bodyRef));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void generatedProxyWithDestroyedBodyRefIsRemovedAndUnindexed() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        Store<EntityStore> store = store(registry, "projection-cleanup-generated");
        try {
            UUID bodyUuid = UUID.randomUUID();
            Ref<PhysicsStore> bodyRef = new TestPhysicsRef(12, false);
            Ref<EntityStore> proxyRef = addAttachment(store,
                bodyUuid,
                bodyRef,
                AttachmentLifecycle.GENERATED_PROXY,
                true);
            PhysicsProjectionIndexResource projection = projection(store);
            projection.registerAttachment(bodyUuid, bodyRef, proxyRef);
            projection.setGeneratedVisualProxy(bodyUuid, bodyRef, proxyRef);

            new PhysicsProjectionCleanupSystem().tick(0.0f, 0, store);

            assertFalse(proxyRef.isValid());
            assertFalse(projection.hasAttachments(bodyUuid));
            assertFalse(projection.hasAttachments(bodyRef));
            assertNull(projection.getGeneratedVisualProxy(bodyUuid));
            assertNull(projection.getGeneratedVisualProxy(bodyRef));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void generatedProxyWithMissingBodyRefIsRemovedAndUnindexed() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        Store<EntityStore> store = store(registry, "projection-cleanup-generated-missing-ref");
        try {
            UUID bodyUuid = UUID.randomUUID();
            Ref<EntityStore> proxyRef = addAttachment(store,
                bodyUuid,
                null,
                AttachmentLifecycle.GENERATED_PROXY,
                true);
            PhysicsProjectionIndexResource projection = projection(store);
            projection.registerAttachment(bodyUuid, proxyRef);
            projection.setGeneratedVisualProxy(bodyUuid, proxyRef);

            new PhysicsProjectionCleanupSystem().tick(0.0f, 0, store);

            assertFalse(proxyRef.isValid());
            assertFalse(projection.hasAttachments(bodyUuid));
            assertNull(projection.getGeneratedVisualProxy(bodyUuid));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void externalAttachmentWithMissingBodyRefIsPreservedForUuidResolution() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        Store<EntityStore> store = store(registry, "projection-cleanup-external-missing-ref");
        try {
            UUID bodyUuid = UUID.randomUUID();
            Ref<EntityStore> entityRef = addAttachment(store,
                bodyUuid,
                null,
                AttachmentLifecycle.EXTERNAL_ENTITY,
                false);
            PhysicsProjectionIndexResource projection = projection(store);
            projection.registerAttachment(bodyUuid, entityRef);

            new PhysicsProjectionCleanupSystem().tick(0.0f, 0, store);

            assertTrue(entityRef.isValid());
            assertNotNull(store.getComponent(entityRef, BodyAttachmentComponent.getComponentType()));
            assertTrue(projection.hasAttachments(bodyUuid));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void validBodyRefIsPreservedWhenSnapshotPublicationLags() {
        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        Store<EntityStore> store = store(registry, "projection-cleanup-valid-ref");
        try {
            UUID bodyUuid = UUID.randomUUID();
            Ref<PhysicsStore> bodyRef = new TestPhysicsRef(13, true);
            Ref<EntityStore> entityRef = addAttachment(store,
                bodyUuid,
                bodyRef,
                AttachmentLifecycle.EXTERNAL_ENTITY,
                false);
            PhysicsProjectionIndexResource projection = projection(store);
            projection.registerAttachment(bodyUuid, bodyRef, entityRef);

            new PhysicsProjectionCleanupSystem().tick(0.0f, 0, store);

            assertTrue(entityRef.isValid());
            assertNotNull(store.getComponent(entityRef, BodyAttachmentComponent.getComponentType()));
            assertTrue(projection.hasAttachments(bodyUuid));
            assertTrue(projection.hasAttachments(bodyRef));
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
        PhysicsEntityTypeRegistry.registerSystemGroups(proxy);
        Store<EntityStore> store = registry.addStore(
            new EntityStore(TestInstanceFactory.world(worldName)),
            EmptyResourceStorage.get());
        PhysicsWorldRuntimeResource.require(store);
        return store;
    }

    @Nonnull
    private static Ref<EntityStore> addAttachment(@Nonnull Store<EntityStore> store,
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull AttachmentLifecycle lifecycle,
        boolean generatedProxy) {
        Holder<EntityStore> holder = store.getRegistry().newHolder();
        BodyAttachmentComponent attachment = new BodyAttachmentComponent(bodyUuid,
            BodyAttachmentComponent.TransformAuthority.BODY,
            lifecycle);
        attachment.setBodyRef(bodyRef);
        holder.addComponent(BodyAttachmentComponent.getComponentType(), attachment);
        if (generatedProxy) {
            holder.addComponent(GeneratedVisualProxyComponent.getComponentType(),
                new GeneratedVisualProxyComponent());
        }
        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        assertNotNull(ref);
        return ref;
    }

    @Nonnull
    private static PhysicsProjectionIndexResource projection(@Nonnull Store<EntityStore> store) {
        return store.getResource(PhysicsProjectionIndexResource.getResourceType());
    }

    private static final class TestPhysicsRef extends Ref<PhysicsStore> {

        private final boolean valid;

        private TestPhysicsRef(int index, boolean valid) {
            super(null, index);
            this.valid = valid;
        }

        @Override
        public boolean isValid() {
            return valid;
        }
    }
}
