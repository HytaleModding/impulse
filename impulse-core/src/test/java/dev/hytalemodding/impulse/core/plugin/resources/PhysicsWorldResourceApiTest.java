package dev.hytalemodding.impulse.core.plugin.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicsWorldResourceApiTest {

    @Test
    void publicFacadeDoesNotDirectlyExposeLiveBackendObjects() {
        List<String> exposedMethods = Arrays.stream(PhysicsWorldResource.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .filter(PhysicsWorldResourceApiTest::directlyExposesLiveBackendObject)
            .map(Method::toGenericString)
            .sorted()
            .toList();

        assertEquals(List.of(), exposedMethods);
    }

    private static boolean directlyExposesLiveBackendObject(Method method) {
        if (usesLiveBackendObject(method.getGenericReturnType())) {
            return true;
        }
        for (Type parameterType : method.getGenericParameterTypes()) {
            if (usesLiveBackendObject(parameterType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesLiveBackendObject(Type type) {
        if (type instanceof Class<?> clazz) {
            return PhysicsSpace.class.isAssignableFrom(clazz)
                || PhysicsBody.class.isAssignableFrom(clazz)
                || PhysicsJoint.class.isAssignableFrom(clazz);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            if (usesLiveBackendObject(parameterizedType.getRawType())) {
                return true;
            }
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (usesLiveBackendObject(argument)) {
                    return true;
                }
            }
        }
        return false;
    }
}
