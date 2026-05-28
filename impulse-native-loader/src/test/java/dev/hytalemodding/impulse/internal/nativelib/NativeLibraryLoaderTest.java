package dev.hytalemodding.impulse.internal.nativelib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeLibraryLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsResourceUsingOwnerClassLoaderIntoBackendTempCache() throws IOException {
        NativeLibraryResource resource = new NativeLibraryResource(
            "native/linux/x86_64/libfake.so",
            "libfake.so");

        Path extracted = NativeLibraryLoader.extractResource(
            NativeLibraryLoaderTest.class,
            "fake",
            resource,
            tempDir);

        assertEquals("libfake.so", extracted.getFileName().toString());
        assertTrue(extracted.startsWith(tempDir.resolve("impulse").resolve("native").resolve("fake")));
        assertEquals("fake native payload\n", Files.readString(extracted));
    }

    @Test
    void missingResourceNamesSelectedResourcePath() {
        NativeLibraryResource resource = new NativeLibraryResource(
            "native/linux/x86_64/libmissing.so",
            "libmissing.so");

        IOException thrown = assertThrows(IOException.class,
            () -> NativeLibraryLoader.extractResource(
                NativeLibraryLoaderTest.class,
                "missing",
                resource,
                tempDir));

        assertTrue(thrown.getMessage().contains("native/linux/x86_64/libmissing.so"));
    }
}
