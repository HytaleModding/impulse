package dev.hytalemodding.impulse.core;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

final class BackendDiscovery {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final String RUNTIME_PROVIDER_SERVICE_RESOURCE =
        "META-INF/services/" + PhysicsBackendRuntimeProvider.class.getName();

    private BackendDiscovery() {
    }

    @Nonnull
    static List<PhysicsBackendRuntimeProvider> discoverRuntimeProviders(
        @Nonnull Collection<Path> backendSearchRoots,
        @Nonnull ClassLoader parentClassLoader) {
        Map<BackendId, PhysicsBackendRuntimeProvider> discovered = new LinkedHashMap<>();
        loadRuntimeProvidersFrom(parentClassLoader, "plugin classpath", discovered);

        for (Path backendJar : findBackendProviderJars(backendSearchRoots)) {
            try {
                URL[] urls = {backendJar.toUri().toURL()};
                URLClassLoader backendLoader = new URLClassLoader(
                    "ImpulseBackendRuntimeProvider(" + backendJar.getFileName() + ")",
                    urls,
                    parentClassLoader);
                loadRuntimeProvidersFrom(backendLoader, backendJar.toString(), discovered);
            } catch (MalformedURLException e) {
                LOGGER.at(Level.WARNING)
                    .log("Skipping backend runtime provider jar %s: %s", backendJar, e.getMessage());
            }
        }

        return List.copyOf(discovered.values());
    }

    @Nonnull
    private static List<Path> findBackendProviderJars(
        @Nonnull Collection<Path> backendSearchRoots) {
        List<Path> jars = new ArrayList<>();
        for (Path searchRoot : backendSearchRoots) {
            if (!Files.isDirectory(searchRoot)) {
                continue;
            }

            try (Stream<Path> paths = Files.walk(searchRoot)) {
                paths.filter(Files::isRegularFile)
                    .filter(BackendDiscovery::isJar)
                    .sorted(Comparator.comparing(Path::toString))
                    .filter(BackendDiscovery::containsRuntimeProviderService)
                    .forEach(jars::add);
            } catch (IOException e) {
                LOGGER.at(Level.WARNING)
                    .log("Failed to scan backend provider directory %s: %s",
                        searchRoot,
                        e.getMessage());
            }
        }
        return jars;
    }

    private static boolean isJar(@Nonnull Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".jar");
    }

    private static boolean containsRuntimeProviderService(@Nonnull Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            return jar.getEntry(RUNTIME_PROVIDER_SERVICE_RESOURCE) != null;
        } catch (IOException e) {
            LOGGER.at(Level.WARNING)
                .log("Skipping unreadable backend provider jar %s: %s",
                    jarPath,
                    e.getMessage());
            return false;
        }
    }

    private static void loadRuntimeProvidersFrom(@Nonnull ClassLoader classLoader,
        @Nonnull String source,
        @Nonnull Map<BackendId, PhysicsBackendRuntimeProvider> discovered) {
        ServiceLoader<PhysicsBackendRuntimeProvider> loader =
            ServiceLoader.load(PhysicsBackendRuntimeProvider.class, classLoader);
        try {
            for (PhysicsBackendRuntimeProvider provider : loader) {
                discovered.put(provider.getId(), provider);
            }
        } catch (ServiceConfigurationError e) {
            LOGGER.at(Level.WARNING)
                .log("Failed to load physics backend runtime provider from %s: %s",
                    source,
                    e.getMessage());
        }
    }
}
