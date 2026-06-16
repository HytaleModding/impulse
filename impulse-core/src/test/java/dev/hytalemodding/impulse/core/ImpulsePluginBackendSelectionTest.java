package dev.hytalemodding.impulse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.annotation.Nonnull;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImpulsePluginBackendSelectionTest {

    private static final String BACKEND_SERVICE =
        "META-INF/services/dev.hytalemodding.impulse.api.PhysicsBackend";
    private static final String SERVICE_PROVIDER_CLASS =
        "dev.hytalemodding.impulse.core.testbackend.JarOnlyServiceLoadedBackend";
    private static final BackendId SERVICE_BACKEND_ID = new BackendId("test:service-loaded");

    @TempDir
    private Path tempDir;

    @Test
    void singleRuntimeProviderIsDefaultBackend() {
        BackendId backendId = new BackendId("impulse:rapier");

        assertEquals(backendId, ImpulsePlugin.selectDefaultRuntimeProviderId(List.of(
            new FakePhysicsBackendRuntimeProvider(backendId, false, false))));
    }

    @Test
    void discoversClasspathVisibleServiceProviders() throws IOException {
        Path providerJar = tempDir.resolve("provider.jar");
        writeServiceJar(providerJar);

        try (URLClassLoader loader = new URLClassLoader(
            new URL[]{providerJar.toUri().toURL()},
            Thread.currentThread().getContextClassLoader())) {
            List<PhysicsBackend> backends = BackendDiscovery.discover(List.of(), loader);

            assertEquals(List.of(SERVICE_BACKEND_ID), backends.stream()
                .map(PhysicsBackend::getId)
                .toList());
        }
    }

    @Test
    void discoversServiceProvidersFromNestedModsJars() throws IOException {
        Path backendDirectory = tempDir.resolve("mods").resolve("backends");
        Files.createDirectories(backendDirectory);
        writeServiceJar(backendDirectory.resolve("provider.jar"));

        List<PhysicsBackend> backends = BackendDiscovery.discover(
            List.of(tempDir.resolve("mods")),
            Thread.currentThread().getContextClassLoader());

        assertEquals(List.of(SERVICE_BACKEND_ID), backends.stream()
            .map(PhysicsBackend::getId)
            .toList());
    }

    private static void writeServiceJar(@Nonnull Path jarPath) throws IOException {
        Path classFile = compileServiceProviderClass(jarPath.getParent());
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry(BACKEND_SERVICE));
            jar.write((SERVICE_PROVIDER_CLASS + "\n")
                .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();

            jar.putNextEntry(new JarEntry(SERVICE_PROVIDER_CLASS.replace('.', '/') + ".class"));
            Files.copy(classFile, jar);
            jar.closeEntry();
        }
    }

    @Nonnull
    private static Path compileServiceProviderClass(@Nonnull Path outputRoot) throws IOException {
        Path sourceRoot = outputRoot.resolve("provider-source");
        Path classesRoot = outputRoot.resolve("provider-classes");
        Path sourceFile = sourceRoot.resolve(SERVICE_PROVIDER_CLASS.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(sourceFile, """
            package dev.hytalemodding.impulse.core.testbackend;

            import dev.hytalemodding.impulse.api.BackendId;
            import dev.hytalemodding.impulse.api.PhysicsBackend;
            import dev.hytalemodding.impulse.api.PhysicsSpace;
            import javax.annotation.Nonnull;

            public final class JarOnlyServiceLoadedBackend implements PhysicsBackend {

                public JarOnlyServiceLoadedBackend() {
                }

                @Nonnull
                @Override
                public BackendId getId() {
                    return new BackendId("test:service-loaded");
                }

                @Override
                public void init() {
                }

                @Nonnull
                @Override
                public PhysicsSpace createSpace() {
                    throw new UnsupportedOperationException("not used");
                }
            }
            """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required for backend provider fixture");
        int result = compiler.run(null,
            null,
            null,
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            classesRoot.toString(),
            sourceFile.toString());
        assertEquals(0, result);
        Path classFile = classesRoot.resolve(SERVICE_PROVIDER_CLASS.replace('.', '/') + ".class");
        assertTrue(Files.isRegularFile(classFile));
        return classFile;
    }

}
