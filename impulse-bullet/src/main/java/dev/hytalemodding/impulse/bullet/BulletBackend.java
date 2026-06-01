package dev.hytalemodding.impulse.bullet;

import com.jme3.bullet.util.NativeLibrary;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.internal.nativelib.NativeLibraryLoader;
import dev.hytalemodding.impulse.internal.nativelib.NativeLibraryResource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * TODO: right now the BulletBackend has an additional bulletjme layer on top of Bullet natives
 */
public final class BulletBackend implements PhysicsBackend {

    public static final BackendId ID = new BackendId("impulse:bullet");
    private static final Logger LOGGER = Logger.getLogger("Impulse");
    private static final AtomicInteger SPACES_CREATED = new AtomicInteger(0);
    private static final Object NATIVE_LOAD_LOCK = new Object();
    private static volatile boolean nativeLibraryLoaded;

    private volatile Level internalLoggingLevel = Level.WARNING;

    private volatile boolean initialized;

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
    public synchronized void init() {
        if (initialized) {
            return;
        }

        loadNativeLibrary();
        NativeLibrary.setStartupMessageEnabled(false);

        initialized = true;
        applyInternalLoggingLevel(internalLoggingLevel);
        LOGGER.log(Level.INFO, "Bullet backend initialized");
    }

    private static void loadNativeLibrary() {
        if (nativeLibraryLoaded) {
            return;
        }

        synchronized (NATIVE_LOAD_LOCK) {
            if (nativeLibraryLoaded) {
                return;
            }

            try {
                NativeLibraryLoader.load(BulletBackend.class,
                    "bullet",
                    NativeLibraryResource.forCurrentPlatform("bulletjme"));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw new IllegalStateException("Failed to load the Libbulletjme native library",
                    exception);
            }

            nativeLibraryLoaded = true;
        }
    }

    @Nonnull
    @Override
    public PhysicsSpace createSpace() {
        return createSpace(SpaceId.next());
    }

    @Nonnull
    @Override
    public PhysicsSpace createSpace(@Nonnull SpaceId spaceId) {
        int count = SPACES_CREATED.incrementAndGet();
        LOGGER.log(Level.FINE,
            "Creating Bullet physics space #" + count + " on thread "
                + Thread.currentThread().getName());

        return new BulletSpace(spaceId, this,
            new BulletNativeSpace(com.jme3.bullet.PhysicsSpace.BroadphaseType.DBVT));
    }
}
