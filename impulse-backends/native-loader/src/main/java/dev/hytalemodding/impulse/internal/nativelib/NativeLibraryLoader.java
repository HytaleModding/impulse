package dev.hytalemodding.impulse.internal.nativelib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NativeLibraryLoader {

    private static final Logger LOGGER = Logger.getLogger("Impulse");

    private NativeLibraryLoader() {
    }

    public static Path load(
        Class<?> resourceOwner,
        String backendName,
        NativeLibraryResource resource) {

        Path tempRoot = defaultTempRoot();
        Path extracted;
        try {
            extracted = extractResource(resourceOwner, backendName, resource, tempRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to extract native library resource "
                + resource.path() + " for backend " + backendName + " into "
                + tempRoot.toAbsolutePath(), exception);
        }

        try {
            System.load(extracted.toAbsolutePath().toString());
        } catch (LinkageError error) {
            throw new IllegalStateException("Failed to load native library resource "
                + resource.path() + " from " + extracted.toAbsolutePath(), error);
        }

        LOGGER.log(Level.INFO, "Loaded native library " + resource.path()
            + " from " + extracted.toAbsolutePath());
        return extracted;
    }

    static Path extractResource(
        Class<?> resourceOwner,
        String backendName,
        NativeLibraryResource resource,
        Path tempRoot) throws IOException {

        Objects.requireNonNull(resourceOwner, "resourceOwner");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(tempRoot, "tempRoot");
        String normalizedBackendName = normalizeBackendName(backendName);

        Path backendRoot = tempRoot
            .resolve("impulse")
            .resolve("native")
            .resolve(normalizedBackendName)
            .resolve(classLoaderId(resourceOwner));
        Files.createDirectories(backendRoot);

        Path temporaryFile = Files.createTempFile(backendRoot, resource.fileName(), ".tmp");
        try {
            MessageDigest digest = sha256();
            long bytesCopied;
            try (InputStream rawInput = openResource(resourceOwner, resource);
                 DigestInputStream input = new DigestInputStream(rawInput, digest);
                 OutputStream output = Files.newOutputStream(temporaryFile)) {
                bytesCopied = input.transferTo(output);
            }

            String resourceHash = HexFormat.of().formatHex(digest.digest()).substring(0, 12);
            Path targetDirectory = backendRoot.resolve(resourceHash);
            Path target = targetDirectory.resolve(resource.fileName());
            Files.createDirectories(targetDirectory);
            installExtractedFile(temporaryFile, target, bytesCopied);
            markLoadable(target);
            return target;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporaryFile);
            throw exception;
        }
    }

    private static Path defaultTempRoot() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir == null || tmpDir.isBlank()) {
            throw new IllegalStateException("java.io.tmpdir is not configured");
        }
        return Path.of(tmpDir);
    }

    private static InputStream openResource(
        Class<?> resourceOwner,
        NativeLibraryResource resource) throws IOException {

        ClassLoader classLoader = resourceOwner.getClassLoader();
        InputStream input = classLoader == null
            ? ClassLoader.getSystemResourceAsStream(resource.path())
            : classLoader.getResourceAsStream(resource.path());
        if (input == null) {
            throw new IOException("Native library resource not found: " + resource.path()
                + " using " + resourceOwner.getName() + " classloader");
        }
        return input;
    }

    private static void installExtractedFile(
        Path temporaryFile,
        Path target,
        long bytesCopied) throws IOException {

        if (Files.exists(target) && Files.size(target) == bytesCopied) {
            Files.deleteIfExists(temporaryFile);
            return;
        }

        try {
            Files.move(temporaryFile, target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void markLoadable(Path target) {
        var file = target.toFile();
        file.setReadable(true, true);
        file.setExecutable(true, true);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static String normalizeBackendName(String backendName) {
        String normalized = Objects.requireNonNull(backendName, "backendName");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Native backend cache name cannot be blank");
        }
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new IllegalArgumentException("Native backend cache name is not a path segment: "
                + backendName);
        }
        return normalized;
    }

    private static String classLoaderId(Class<?> resourceOwner) {
        ClassLoader classLoader = resourceOwner.getClassLoader();
        return classLoader == null
            ? "bootstrap"
            : Integer.toHexString(System.identityHashCode(classLoader));
    }
}
