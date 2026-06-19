package dev.hytalemodding.impulse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
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

    private static final String RUNTIME_PROVIDER_SERVICE =
        "META-INF/services/dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider";
    private static final String SERVICE_PROVIDER_CLASS =
        "dev.hytalemodding.impulse.core.testbackend.JarOnlyServiceLoadedRuntimeProvider";
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
            List<PhysicsBackendRuntimeProvider> backends =
                BackendDiscovery.discoverRuntimeProviders(List.of(), loader);

            assertEquals(List.of(SERVICE_BACKEND_ID), backends.stream()
                .map(PhysicsBackendRuntimeProvider::getId)
                .toList());
        }
    }

    @Test
    void discoversServiceProvidersFromNestedModsJars() throws IOException {
        Path backendDirectory = tempDir.resolve("mods").resolve("backends");
        Files.createDirectories(backendDirectory);
        writeServiceJar(backendDirectory.resolve("provider.jar"));

        List<PhysicsBackendRuntimeProvider> backends = BackendDiscovery.discoverRuntimeProviders(
            List.of(tempDir.resolve("mods")),
            Thread.currentThread().getContextClassLoader());

        assertEquals(List.of(SERVICE_BACKEND_ID), backends.stream()
            .map(PhysicsBackendRuntimeProvider::getId)
            .toList());
    }

    private static void writeServiceJar(@Nonnull Path jarPath) throws IOException {
        Path classFile = compileServiceProviderClass(jarPath.getParent());
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry(RUNTIME_PROVIDER_SERVICE));
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
            import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
            import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
            import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
            import javax.annotation.Nonnull;

            public final class JarOnlyServiceLoadedRuntimeProvider
                implements PhysicsBackendRuntimeProvider {

                private final FakePhysicsBackendRuntimeProvider delegate =
                    new FakePhysicsBackendRuntimeProvider(new BackendId("test:service-loaded"),
                        false,
                        false);

                public JarOnlyServiceLoadedRuntimeProvider() {
                }

                @Nonnull
                @Override
                public BackendId getId() {
                    return delegate.getId();
                }

                @Nonnull
                @Override
                public PhysicsBackendRuntime createRuntime() {
                    return delegate.createRuntime();
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
