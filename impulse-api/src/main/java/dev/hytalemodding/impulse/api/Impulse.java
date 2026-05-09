package dev.hytalemodding.impulse.api;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.PlaneCollisionShape;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Plane;
import electrostatic4j.snaploader.LibraryInfo;
import electrostatic4j.snaploader.LoadingCriterion;
import electrostatic4j.snaploader.NativeBinaryLoader;
import electrostatic4j.snaploader.filesystem.DirectoryPath;
import electrostatic4j.snaploader.platform.NativeDynamicLibrary;
import electrostatic4j.snaploader.platform.util.PlatformPredicate;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Entry point for the Impulse physics library.
 * <p>
 * Call {@link #init()} once before using any other API.
 */
public final class Impulse
{
    private static boolean initialized;

    private Impulse()
    {
    }

    public static void init()
    {
        if (initialized)
        {
            return;
        }

        LibraryInfo info = new LibraryInfo(null, "bulletjme", DirectoryPath.USER_DIR);
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

        try
        {
            loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to load the Libbulletjme native library", e);
        }

        initialized = true;
    }

    /**
     * Create a new physics space with {@link com.jme3.bullet.PhysicsSpace.BroadphaseType} DBVT.
     */
    public static ImpulseSpace createSpace()
    {
        ensureInitialized();
        return new ImpulseSpace(new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT));
    }

    /**
     * Create a static ground plane at the given Y level.
     */
    public static ImpulseBody createStaticPlane(float groundY)
    {
        ensureInitialized();
        Plane plane = new Plane(com.jme3.math.Vector3f.UNIT_Y, groundY);
        CollisionShape shape = new PlaneCollisionShape(plane);
        PhysicsRigidBody body = new PhysicsRigidBody(shape, PhysicsBody.massForStatic);
        return new ImpulseBody(body);
    }

    /**
     * Create a dynamic box body with the given half-extents and mass.
     */
    public static ImpulseBody createBox(float halfX, float halfY, float halfZ, float mass)
    {
        ensureInitialized();
        CollisionShape shape = new BoxCollisionShape(new com.jme3.math.Vector3f(halfX, halfY, halfZ));
        PhysicsRigidBody body = new PhysicsRigidBody(shape, mass);
        return new ImpulseBody(body);
    }

    /**
     * Create a dynamic box body with the given half-extents and mass.
     */
    public static ImpulseBody createBox(@Nonnull Vector3f halfExtents, float mass)
    {
        ensureInitialized();
        CollisionShape shape = new BoxCollisionShape(new com.jme3.math.Vector3f(halfExtents.x, halfExtents.y, halfExtents.z));
        PhysicsRigidBody body = new PhysicsRigidBody(shape, mass);
        return new ImpulseBody(body);
    }

    private static void ensureInitialized()
    {
        if (!initialized)
        {
            throw new IllegalStateException("ImpulseLib not initialized — call ImpulseLib.init() first");
        }
    }
}
