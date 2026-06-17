package dev.hytalemodding.impulse.core.internal.registration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsComponentTypes;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrain;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PhysicsTypeRegistrationApiTest {

    @Test
    void internalRegistriesOwnRegistrationWithoutPublicTypeSetters() throws NoSuchMethodException {
        assertNotNull(PhysicsComponentTypeRegistry.class.getDeclaredMethod("registerComponentTypes",
            ComponentRegistryProxy.class));
        assertNotNull(PhysicsResourceTypes.class.getDeclaredMethod("registerResourceTypes",
            ComponentRegistryProxy.class));

        assertFalse(hasPublicSetter(PhysicsComponentTypes.class));
        assertFalse(hasPublicSetter(PhysicsResourceTypes.class));
        assertFalse(hasPublicRegistrationMethod(PhysicsComponentTypes.class));
        assertFalse(hasPublicRegistrationMethod(PhysicsEntityTypes.class));
        assertFalse(hasPublicMethodNamed(PhysicsChunkTerrain.class, "enableModule"));
        assertFalse(hasPublicMethodNamed(PhysicsChunkTerrain.class, "disableModule"));
    }

    private static boolean hasPublicSetter(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::getName)
            .anyMatch(name -> name.startsWith("set"));
    }

    private static boolean hasPublicRegistrationMethod(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::getName)
            .anyMatch(name -> name.startsWith("register")
                || name.startsWith("unregister")
                || name.startsWith("clear"));
    }

    private static boolean hasPublicMethodNamed(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::getName)
            .anyMatch(name::equals);
    }
}
