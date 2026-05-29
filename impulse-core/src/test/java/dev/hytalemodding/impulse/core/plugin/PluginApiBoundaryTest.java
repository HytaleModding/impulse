package dev.hytalemodding.impulse.core.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.core.plugin.control.PhysicsControlSessions;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PluginApiBoundaryTest {
    private static final Pattern INTERNAL_REFERENCE =
            Pattern.compile("\\bdev\\.hytalemodding\\.impulse\\.core\\.internal\\.");
    private static final Pattern BACKEND_CAPABILITY_API_REFERENCE =
        Pattern.compile("\\bdev\\.hytalemodding\\.impulse\\.api\\.capability\\.");

    @Test
    void pluginApiPublicSurfaceDoesNotExposeInternalImplementationPackages() throws IOException {
        Path pluginSourceRoot = pluginSourceRoot();
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(pluginSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> collectPublicSurfaceViolations(pluginSourceRoot,
                    path,
                    violations));
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Plugin API public surface must not expose core.internal packages:\n"
                    + String.join("\n", violations));
    }

    @Test
    void pluginBodyPackageDoesNotExposeLiveRegistrationRecords() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("body/PhysicsBodyRegistration.java")),
            "Live backend body registrations belong behind owner-scoped/internal APIs");
    }

    @Test
    void pluginComponentsDoNotExposeControlSessionRuntimeState() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("components/PhysicsControlSessionComponent.java")),
            "Kinematic control session state is runtime implementation state, not plugin ABI");
    }

    @Test
    void pluginPersistenceDoesNotExposeCodecDtoHelpers() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("persistence/PersistentQuaternion.java")),
            "Codec DTO helpers should not become plugin persistence ABI");
        assertFalse(Files.exists(pluginSourceRoot().resolve("components/PersistentQuaternion.java")),
            "Component codec DTO helpers should not be duplicated as plugin ABI");
    }

    @Test
    void pluginApiDoesNotExposeBackendCapabilityTypes() throws IOException {
        Path pluginSourceRoot = pluginSourceRoot();
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(pluginSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String source = Files.readString(path);
                        if (BACKEND_CAPABILITY_API_REFERENCE.matcher(source).find()) {
                            violations.add(pluginSourceRoot.relativize(path).toString());
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to inspect " + path, exception);
                    }
                });
        }

        assertTrue(violations.isEmpty(),
            () -> "Plugin API must not expose backend capability API imports:\n"
                + String.join("\n", violations));
    }

    @Test
    void pluginControlDoesNotExposeServiceLayer() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("control/PhysicsControlSessionService.java")),
            "Control sessions should use the public helper directly");
    }

    @Test
    void pluginControlDoesNotExposeDedicatedResource() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("control/PhysicsControlResource.java")),
            "Control sessions do not need a Hytale resource when the helper has no resource state");
    }

    @Test
    void pluginControlExposesStatelessHelper() {
        assertTrue(Files.exists(pluginSourceRoot().resolve("control/PhysicsControlSessions.java")),
            "Control sessions should be managed by a stateless public helper");
    }

    @Test
    void pluginControlSessionsDoNotExposeRawJointHandles() {
        for (Method method : PhysicsControlSessions.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertFalse(parameterType == PhysicsJoint.class,
                    "Control-session API should use PhysicsJointId, not raw PhysicsJoint handles");
            }
        }
    }

    @Test
    void pluginControlDoesNotExposeStaticConvenienceFacade() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("control/PhysicsControls.java")),
            "Use the scoped PhysicsControlSessions helper instead of a vague control facade");
    }

    @Test
    void physicsWorldResourceDoesNotOwnControlSessionLifecycle() throws IOException {
        String worldResource =
            Files.readString(pluginSourceRoot().resolve("resources/PhysicsWorldResource.java"));
        assertFalse(worldResource.contains("ControlSession"),
            "Control-session lifecycle belongs to the control helper, not PhysicsWorldResource");
    }

    @Test
    void physicsWorldResourceDoesNotExposeControlledBodyMutation() throws IOException {
        String worldResource =
            Files.readString(pluginSourceRoot().resolve("resources/PhysicsWorldResource.java"));
        assertFalse(worldResource.contains("markBodyControlled"),
            "Controlled-body flags are internal control runtime state, not plugin ABI");
        assertFalse(worldResource.contains("clearControlledBody"),
            "Controlled-body flags are internal control runtime state, not plugin ABI");
    }

    private static Path pluginSourceRoot() {
        Path moduleRelative = Path.of("src/main/java/dev/hytalemodding/impulse/core/plugin");
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("impulse-core/src/main/java/dev/hytalemodding/impulse/core/plugin");
    }

    private static void collectPublicSurfaceViolations(Path root,
        Path source,
        List<String> violations) {
        String fileName = source.getFileName().toString();
        if ("package-info.java".equals(fileName) || "module-info.java".equals(fileName)) {
            return;
        }

        Class<?> type = loadPluginClass(root, source);
        for (Method method : type.getDeclaredMethods()) {
            if (isExposed(method)) {
                collectTypeViolation(type, method, "return", method.getGenericReturnType(), violations);
                for (Type parameterType : method.getGenericParameterTypes()) {
                    collectTypeViolation(type, method, "parameter", parameterType, violations);
                }
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isExposed(constructor)) {
                for (Type parameterType : constructor.getGenericParameterTypes()) {
                    collectTypeViolation(type, constructor, "constructor parameter", parameterType, violations);
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (isExposed(field)) {
                collectTypeViolation(type, field, "field", field.getGenericType(), violations);
            }
        }
    }

    private static Class<?> loadPluginClass(Path root, Path source) {
        String relativeName = root.relativize(source).toString()
            .replace('/', '.')
            .replace('\\', '.')
            .replaceAll("\\.java$", "");
        String className = "dev.hytalemodding.impulse.core.plugin." + relativeName;
        try {
            return Class.forName(className, false, PluginApiBoundaryTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to load " + className, exception);
        }
    }

    private static boolean isExposed(Member member) {
        int modifiers = member.getModifiers();
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static void collectTypeViolation(Class<?> owner,
        Member member,
        String role,
        Type type,
        List<String> violations) {
        String typeName = type.getTypeName();
        if (INTERNAL_REFERENCE.matcher(typeName).find()) {
            violations.add(owner.getName() + "#" + member.getName() + " " + role + ": " + typeName);
        }
    }
}
