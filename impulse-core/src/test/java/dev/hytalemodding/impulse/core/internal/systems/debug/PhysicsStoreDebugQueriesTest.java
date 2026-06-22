package dev.hytalemodding.impulse.core.internal.systems.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendContactSink;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsStoreDebugQueriesTest {

    @Test
    void contactDebugQueryUsesBoundedRuntimeContactRead() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("debug-contact-query-bounded-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            RecordingContactRuntime runtime = new RecordingContactRuntime();
            bindSpace(store, runtime);

            List<PhysicsDebugContactView> contacts = PhysicsStoreDebugQueries.contacts(store,
                new SpaceId(77),
                0.0,
                0.0,
                0.0,
                32.0,
                3);

            assertEquals(3, contacts.size());
            assertEquals(1, runtime.boundedCalls());
            assertEquals(0, runtime.unboundedCalls());
            assertEquals(3, runtime.lastMaxContacts());
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    private static void bindSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull RecordingContactRuntime recordingRuntime) {
        UUID spaceUuid = new UUID(0L, 77L);
        BackendId backendId = new BackendId("test:debug-contact-query");
        Ref<PhysicsStore> spaceRef = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(backendId, new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        store.getExternalData().putRefForUUID(spaceUuid, spaceRef);
        store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .putSpace(new SpaceId(77), spaceUuid);
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(7700);
        runtime.putRuntime(backendId, recordingRuntime.proxy());
        runtime.putSpaceHandle(spaceRef, backendId, spaceHandle);
        runtime.putSpaceMetadata(backendId, spaceHandle, spaceUuid, spaceRef);
    }

    private static void markCurrentThreadAsWorldThread(@Nonnull Store<PhysicsStore> store) {
        try {
            Method setThread = TickingThread.class.getDeclaredMethod("setThread", Thread.class);
            setThread.setAccessible(true);
            setThread.invoke(store.getExternalData().getWorld(), Thread.currentThread());
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not mark test world thread", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Could not mark test world thread", exception.getCause());
        }
    }

    private static final class RecordingContactRuntime implements InvocationHandler {

        private final PhysicsBackendRuntime proxy =
            (PhysicsBackendRuntime) Proxy.newProxyInstance(
                PhysicsBackendRuntime.class.getClassLoader(),
                new Class<?>[] { PhysicsBackendRuntime.class },
                this);
        private int boundedCalls;
        private int unboundedCalls;
        private int lastMaxContacts;

        @Nonnull
        private PhysicsBackendRuntime proxy() {
            return proxy;
        }

        private int boundedCalls() {
            return boundedCalls;
        }

        private int unboundedCalls() {
            return unboundedCalls;
        }

        private int lastMaxContacts() {
            return lastMaxContacts;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("contacts".equals(method.getName()) && args != null && args.length == 3) {
                boundedCalls++;
                lastMaxContacts = (Integer) args[1];
                return emitContacts(lastMaxContacts, (BackendContactSink) args[2]);
            }
            if ("contacts".equals(method.getName()) && args != null && args.length == 2) {
                unboundedCalls++;
                return emitContacts(8, (BackendContactSink) args[1]);
            }
            return defaultValue(method.getReturnType());
        }

        private static int emitContacts(int maxContacts, @Nonnull BackendContactSink sink) {
            int emitted = 0;
            for (int i = 0; i < Math.max(0, maxContacts); i++) {
                sink.accept(1L,
                    2L,
                    i,
                    0.0f,
                    0.0f,
                    i,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f,
                    0.0f,
                    -0.1f,
                    1.0f);
                emitted++;
            }
            return emitted;
        }

        private static Object defaultValue(@Nonnull Class<?> returnType) {
            if (returnType == Boolean.TYPE) {
                return false;
            }
            if (returnType == Integer.TYPE) {
                return 0;
            }
            if (returnType == Long.TYPE) {
                return 0L;
            }
            if (returnType == Float.TYPE) {
                return 0.0f;
            }
            return null;
        }
    }
}
