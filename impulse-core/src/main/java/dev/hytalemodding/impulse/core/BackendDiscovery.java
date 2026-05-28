package dev.hytalemodding.impulse.core;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
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
    private static final String BACKEND_SERVICE_RESOURCE =
        "META-INF/services/" + PhysicsBackend.class.getName();

    private BackendDiscovery() {
    }

    @Nonnull
    static List<PhysicsBackend> discover(@Nonnull Collection<Path> backendSearchRoots,
        @Nonnull ClassLoader parentClassLoader) {
        Map<BackendId, PhysicsBackend> discovered = new LinkedHashMap<>();
        loadFrom(parentClassLoader, "plugin classpath", discovered);

        for (Path backendJar : findBackendProviderJars(backendSearchRoots)) {
            try {
                URL[] urls = {backendJar.toUri().toURL()};
                URLClassLoader backendLoader = new URLClassLoader(
                    "ImpulseBackendProvider(" + backendJar.getFileName() + ")",
                    urls,
                    parentClassLoader);
                loadFrom(backendLoader, backendJar.toString(), discovered);
            } catch (MalformedURLException e) {
                LOGGER.at(Level.WARNING)
                    .log("Skipping backend provider jar %s: %s", backendJar, e.getMessage());
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
                    .filter(BackendDiscovery::containsBackendService)
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

    private static boolean containsBackendService(@Nonnull Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            return jar.getEntry(BACKEND_SERVICE_RESOURCE) != null;
        } catch (IOException e) {
            LOGGER.at(Level.WARNING)
                .log("Skipping unreadable backend provider jar %s: %s",
                    jarPath,
                    e.getMessage());
            return false;
        }
    }

    private static void loadFrom(@Nonnull ClassLoader classLoader,
        @Nonnull String source,
        @Nonnull Map<BackendId, PhysicsBackend> discovered) {
        ServiceLoader<PhysicsBackend> loader = ServiceLoader.load(PhysicsBackend.class,
            classLoader);
        try {
            for (PhysicsBackend backend : loader) {
                discovered.put(backend.getId(), backend);
            }
        } catch (ServiceConfigurationError e) {
            LOGGER.at(Level.WARNING)
                .log("Failed to load physics backend provider from %s: %s",
                    source,
                    e.getMessage());
        }
    }
}
