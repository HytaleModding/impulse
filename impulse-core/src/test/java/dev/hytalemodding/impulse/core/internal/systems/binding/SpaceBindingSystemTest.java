package dev.hytalemodding.impulse.core.internal.systems.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class SpaceBindingSystemTest {

    @Test
    void boundSpaceBackendIdMutationFailsRestoreInsteadOfSilentlyKeepingOldBinding() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("space-backend-mutation-test")),
            EmptyResourceStorage.get());
        try {
            UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000071");
            BackendId originalBackendId = new BackendId("test:space-backend-original");
            BackendId mutatedBackendId = new BackendId("test:space-backend-mutated");
            Ref<PhysicsStore> spaceRef = store.addEntity(PhysicsEntities.spaceHolder(store,
                    spaceUuid,
                    new SpaceComponent(originalBackendId, new Vector3f(0.0f, -9.81f, 0.0f))),
                AddReason.SPAWN);
            assertNotNull(spaceRef);
            store.getResource(PhysicsIdentityIndexResource.getResourceType())
                .putUuid(spaceUuid, spaceRef);
            store.getExternalData().putRefForUUID(spaceUuid, spaceRef);
            FakePhysicsBackendRuntime backendRuntime = (FakePhysicsBackendRuntime)
                new FakePhysicsBackendRuntimeProvider(originalBackendId, false, false)
                    .createRuntime();
            BackendSpaceHandle spaceHandle =
                new BackendSpaceHandle(backendRuntime.createSpace(new SpaceId(72)));
            PhysicsRuntimeResource runtime = store.getResource(
                PhysicsRuntimeResource.getResourceType());
            runtime.putRuntime(originalBackendId, backendRuntime);
            runtime.putSpaceHandle(spaceRef, originalBackendId, spaceHandle);
            runtime.putSpaceMetadata(originalBackendId, spaceHandle, spaceUuid, spaceRef);

            SpaceComponent component = store.getComponent(spaceRef, SpaceComponent.getComponentType());
            assertNotNull(component);
            component.setBackendId(mutatedBackendId);

            runSpaceBindingSystem(store);

            PhysicsRestoreStatusResource restore =
                store.getResource(PhysicsRestoreStatusResource.getResourceType());
            assertTrue(restore.isFailed());
            assertEquals(originalBackendId, runtime.getSpaceBackendId(spaceRef));
            assertEquals(spaceHandle, runtime.getSpaceHandle(spaceRef));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    private static void runSpaceBindingSystem(@Nonnull Store<PhysicsStore> store) {
        try {
            SpaceBindingSystem system = new SpaceBindingSystem();
            Method bindChunk = SpaceBindingSystem.class.getDeclaredMethod("bindChunk",
                PhysicsRuntimeResource.class,
                PhysicsSpaceCompatibilityIndexResource.class,
                PhysicsIdentityIndexResource.class,
                PhysicsRestoreStatusResource.class,
                PhysicsStepMode.class,
                ArchetypeChunk.class);
            bindChunk.setAccessible(true);
            PhysicsRuntimeResource runtime = store.getResource(
                PhysicsRuntimeResource.getResourceType());
            PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
                PhysicsSpaceCompatibilityIndexResource.getResourceType());
            PhysicsIdentityIndexResource identity = store.getResource(
                PhysicsIdentityIndexResource.getResourceType());
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            PhysicsStepMode stepMode =
                store.getResource(PhysicsWorldSettingsResource.getResourceType())
                    .getSettings()
                    .getStepMode();
            BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
                (chunk, _) -> invoke(bindChunk,
                    runtime,
                    compatibility,
                    identity,
                    restore,
                    stepMode,
                    chunk);
            store.forEachChunk(system.getQuery(), collector);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("Could not run SpaceBindingSystem", exception);
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
}
