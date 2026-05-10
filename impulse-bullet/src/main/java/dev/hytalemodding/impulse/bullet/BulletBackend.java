package dev.hytalemodding.impulse.bullet;

import com.jme3.bullet.util.NativeLibrary;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import electrostatic4j.snaploader.LibraryInfo;
import electrostatic4j.snaploader.LoadingCriterion;
import electrostatic4j.snaploader.NativeBinaryLoader;
import electrostatic4j.snaploader.filesystem.DirectoryPath;
import electrostatic4j.snaploader.platform.NativeDynamicLibrary;
import electrostatic4j.snaploader.platform.util.PlatformPredicate;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class BulletBackend implements PhysicsBackend {

    public static final BackendId ID = new BackendId("impulse:bullet");
    private static final Logger LOGGER = Logger.getLogger("Impulse");
    private static final AtomicInteger SPACES_CREATED = new AtomicInteger(0);

    private volatile String extractionDirectory;
    private volatile Level internalLoggingLevel = Level.WARNING;

    private volatile boolean initialized;

    @Override
    public void setDataDirectory(@Nullable Path dataDirectory) {
        if (dataDirectory == null) {
            return;
        }

        Path nativeLibDir = dataDirectory.resolve("native");
        nativeLibDir.toFile().mkdirs();
        extractionDirectory = nativeLibDir.toAbsolutePath().toString();
    }

    @Nonnull
    @Override
    public BackendId getId() {
        return ID;
    }

    @Override
    public void setInternalLoggingLevel(@Nonnull Level level) {
        this.internalLoggingLevel = level;
        if (initialized) {
            applyInternalLoggingLevel(level);
        }
    }

    private void applyInternalLoggingLevel(@Nonnull Level level) {
        // Set on both parent and the specific PhysicsRigidBody logger,
        // since child loggers can have their own level independent of parent.
        Logger.getLogger("com.jme3.bullet").setLevel(level);
        Logger.getLogger("com.jme3.bullet.objects.PhysicsRigidBody").setLevel(level);
    }

    @Override
    public void init() {
        if (initialized) {
            return;
        }

        String dir = extractionDirectory != null ? extractionDirectory
            : System.getProperty("user.dir");
        LibraryInfo info = new LibraryInfo(null, "bulletjme",
            new DirectoryPath(dir));
        NativeBinaryLoader loader = new NativeBinaryLoader(info);

        NativeDynamicLibrary[] libraries = {
            new NativeDynamicLibrary("native/linux/arm64", PlatformPredicate.LINUX_ARM_64),
            new NativeDynamicLibrary("native/linux/arm32", PlatformPredicate.LINUX_ARM_32),
            new NativeDynamicLibrary("native/linux/x86_64", PlatformPredicate.LINUX_X86_64),
            new NativeDynamicLibrary("native/osx/arm64", PlatformPredicate.MACOS_ARM_64),
            new NativeDynamicLibrary("native/osx/x86_64", PlatformPredicate.MACOS_X86_64),
            new NativeDynamicLibrary("native/windows/x86_64", PlatformPredicate.WIN_X86_64)
        };

        loader.registerNativeLibraries(libraries)
            .initPlatformLibrary()
            .setLoggingEnabled(true);
        loader.setRetryWithCleanExtraction(true);

        try {
            loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load the Libbulletjme native library", e);
        }

        // Disable Libbulletjme's native startup message, which may appear
        // multiple times if the library re-enters initialization paths.
        NativeLibrary.setStartupMessageEnabled(false);

        initialized = true;
        applyInternalLoggingLevel(internalLoggingLevel);
        LOGGER.log(Level.INFO, "Bullet backend initialized");
    }

    @Nonnull
    @Override
    public PhysicsSpace createSpace() {
        int count = SPACES_CREATED.incrementAndGet();
        LOGGER.log(Level.FINE,
            "Creating Bullet physics space #" + count + " on thread "
                + Thread.currentThread().getName());

        return new BulletSpace(this,
            new com.jme3.bullet.PhysicsSpace(com.jme3.bullet.PhysicsSpace.BroadphaseType.DBVT));
    }
}
