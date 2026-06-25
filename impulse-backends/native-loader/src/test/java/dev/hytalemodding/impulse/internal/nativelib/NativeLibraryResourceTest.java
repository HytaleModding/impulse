package dev.hytalemodding.impulse.internal.nativelib;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NativeLibraryResourceTest {

    @Test
    void selectsLinuxX8664Resource() {
        NativeLibraryResource resource = NativeLibraryResource.forPlatform(
            "Linux",
            "amd64",
            "impulse_rapier");

        assertEquals("libimpulse_rapier.so", resource.fileName());
        assertEquals("native/linux/x86_64/libimpulse_rapier.so", resource.path());
    }

    @Test
    void selectsMacArm64Resource() {
        NativeLibraryResource resource = NativeLibraryResource.forPlatform(
            "Mac OS X",
            "aarch64",
            "impulse_rapier");

        assertEquals("libimpulse_rapier.dylib", resource.fileName());
        assertEquals("native/osx/arm64/libimpulse_rapier.dylib", resource.path());
    }

    @Test
    void selectsWindowsX8664Resource() {
        NativeLibraryResource resource = NativeLibraryResource.forPlatform(
            "Windows 11",
            "amd64",
            "impulse_rapier");

        assertEquals("impulse_rapier.dll", resource.fileName());
        assertEquals("native/windows/x86_64/impulse_rapier.dll", resource.path());
    }
}
