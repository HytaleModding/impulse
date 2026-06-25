package dev.hytalemodding.impulse.jolt;

import dev.hytalemodding.impulse.internal.nativelib.NativeLibraryLoader;
import dev.hytalemodding.impulse.internal.nativelib.NativeLibraryResource;
import java.lang.foreign.SymbolLookup;

final class JoltNative {

    private static final String LIBRARY_NAME = "impulse_jolt";
    private static JoltNativeLibrary library;

    private JoltNative() {
    }

    static synchronized JoltNativeLibrary open() {
        if (library != null) {
            return library;
        }

        try {
            NativeLibraryLoader.load(JoltNative.class,
                "jolt",
                NativeLibraryResource.forCurrentPlatform(LIBRARY_NAME));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IllegalStateException("Failed to load the Jolt native library", exception);
        }

        library = PanamaJoltNativeLibrary.open(SymbolLookup.loaderLookup());
        return library;
    }
}
