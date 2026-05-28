package dev.hytalemodding.impulse.internal.nativelib;

import java.util.Locale;
import java.util.Objects;

public record NativeLibraryResource(String path, String fileName) {

    public NativeLibraryResource {
        path = normalizePath(path);
        fileName = normalizeFileName(fileName);
    }

    public static NativeLibraryResource forCurrentPlatform(String baseName) {
        return forPlatform(System.getProperty("os.name"), System.getProperty("os.arch"), baseName);
    }

    static NativeLibraryResource forPlatform(String osName, String osArch, String baseName) {
        String normalizedOs = normalizeOs(osName);
        String normalizedArch = normalizeArch(osArch);
        String fileName = nativeFileName(normalizedOs, baseName);
        return new NativeLibraryResource(
            "native/" + normalizedOs + "/" + normalizedArch + "/" + fileName,
            fileName);
    }

    private static String normalizePath(String path) {
        String normalized = Objects.requireNonNull(path, "path")
            .replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Native library resource path cannot be blank");
        }
        return normalized;
    }

    private static String normalizeFileName(String fileName) {
        String normalized = Objects.requireNonNull(fileName, "fileName");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Native library file name cannot be blank");
        }
        if (normalized.contains("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException("Native library file name must not contain path separators");
        }
        return normalized;
    }

    private static String normalizeOs(String osName) {
        String normalized = Objects.requireNonNull(osName, "osName").toLowerCase(Locale.ROOT);
        if (normalized.contains("linux")) {
            return "linux";
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return "osx";
        }
        if (normalized.contains("windows")) {
            return "windows";
        }
        throw new IllegalArgumentException("Unsupported native library OS: " + osName);
    }

    private static String normalizeArch(String osArch) {
        return switch (Objects.requireNonNull(osArch, "osArch").toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "arm64";
            case "arm", "arm32" -> "arm32";
            default -> throw new IllegalArgumentException(
                "Unsupported native library architecture: " + osArch);
        };
    }

    private static String nativeFileName(String os, String baseName) {
        String normalizedBaseName = Objects.requireNonNull(baseName, "baseName");
        if (normalizedBaseName.isBlank()) {
            throw new IllegalArgumentException("Native library base name cannot be blank");
        }
        return switch (os) {
            case "windows" -> normalizedBaseName + ".dll";
            case "osx" -> "lib" + normalizedBaseName + ".dylib";
            case "linux" -> "lib" + normalizedBaseName + ".so";
            default -> throw new IllegalArgumentException("Unsupported native library OS: " + os);
        };
    }
}
