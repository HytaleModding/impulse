package dev.hytalemodding.impulse.core.internal.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.voxel.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WorldVoxelCollisionCacheTest {

    @Test
    void streamingApplyGateAllowsOnlyOnePendingMutation() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();

        assertFalse(cache.isStreamingApplyPending());
        assertTrue(cache.tryBeginStreamingApply());
        assertTrue(cache.isStreamingApplyPending());
        assertFalse(cache.tryBeginStreamingApply());

        cache.finishStreamingApply();

        assertFalse(cache.isStreamingApplyPending());
        assertTrue(cache.tryBeginStreamingApply());
    }

    @Test
    void copyFromDoesNotInheritPendingStreamingApply() {
        WorldVoxelCollisionCache source = new WorldVoxelCollisionCache();
        WorldVoxelCollisionCache target = new WorldVoxelCollisionCache();

        assertTrue(source.tryBeginStreamingApply());

        target.copyFrom(source);

        assertFalse(target.isStreamingApplyPending());
    }

    @Test
    void bodyTargetCacheRefreshesActiveBodiesEveryFourTicks() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();
        SpaceId spaceId = new SpaceId(1001);
        RigidBodyKey bodyId = bodyId(1);
        WorldCollisionStreamingBounds bounds = boundsAt(10.0f, 65.0f, 10.0f);

        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 1L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 2L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 3L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 4L, 100, snapshot)
            .refresh());
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 5L, 100, snapshot)
            .refresh());

        assertEquals(1, snapshot.getBodyTargetFirstSeen());
        assertEquals(4, snapshot.getBodyTargetCacheHits());
        assertEquals(3, snapshot.getBodyTargetActiveStableSkips());
        assertEquals(1, snapshot.getBodyTargetActiveRefreshes());
    }

    @Test
    void bodyTargetCacheRefreshesSleepingBodiesOnTtlBoundedInterval() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();
        SpaceId spaceId = new SpaceId(1002);
        RigidBodyKey bodyId = bodyId(2);
        WorldCollisionStreamingBounds bounds = boundsAt(10.0f, 65.0f, 10.0f);

        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, true, 1L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, true, 2L, 100, snapshot)
            .refresh());
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, true, 21L, 100, snapshot)
            .refresh());

        assertEquals(1, snapshot.getBodyTargetFirstSeen());
        assertEquals(2, snapshot.getBodyTargetCacheHits());
        assertEquals(1, snapshot.getBodyTargetSleepingStableSkips());
        assertEquals(1, snapshot.getBodyTargetSleepingRefreshes());
    }

    @Test
    void bodyTargetCacheRefreshesImmediatelyWhenBoundsChange() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();
        SpaceId spaceId = new SpaceId(1003);
        RigidBodyKey bodyId = bodyId(3);

        assertTrue(cache.shouldRefreshBodyTarget(spaceId,
            bodyId,
            boundsAt(10.0f, 65.0f, 10.0f),
            false,
            1L,
            100,
            snapshot).refresh());
        assertTrue(cache.shouldRefreshBodyTarget(spaceId,
            bodyId,
            boundsAt(40.0f, 65.0f, 10.0f),
            false,
            2L,
            100,
            snapshot).refresh());

        assertEquals(1, snapshot.getBodyTargetFirstSeen());
        assertEquals(1, snapshot.getBodyTargetCacheHits());
        assertEquals(1, snapshot.getBodyTargetBoundsChanged());
    }

    @Test
    void bodyTargetCachePrunesBodiesThatDisappearPastDoubleTtl() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();
        SpaceId spaceId = new SpaceId(1004);
        RigidBodyKey bodyId = bodyId(4);
        WorldCollisionStreamingBounds bounds = boundsAt(10.0f, 65.0f, 10.0f);

        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 1L, 100, snapshot)
            .refresh());
        assertEquals(1, cache.pruneBodyStreamingTargets(spaceId, 202L, 100, snapshot));
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 203L, 100, snapshot)
            .refresh());

        assertEquals(2, snapshot.getBodyTargetFirstSeen());
        assertEquals(1, snapshot.getBodyTargetsPruned());
    }

    @Test
    void absentVoxelCapabilityFallsBackToMergedFullCubeBoxes() throws Exception {
        RecordingSpace recording = new RecordingSpace();
        SectionCollisionGeometry geometry = new SectionCollisionGeometry(new int[] {0, 0, 0, 1, 0, 0},
            List.of(new BoxCollider(1.0, 0.5, 0.5, 1.0, 0.5, 0.5)),
            List.of(new BoxCollider(4.25, 2.25, 4.25, 0.25, 0.25, 0.25)),
            4096,
            2,
            0,
            1);

        assertDoesNotThrow(() -> addGeometryBodies(recording.space(),
            newCachedSection(),
            geometry,
            0,
            0,
            0));

        assertEquals(2, recording.space().bodyCount());
        assertEquals(2, recording.createdBoxes());
        assertEquals(0, recording.createdVoxelTerrains());
    }

    private static RigidBodyKey bodyId(long leastSignificantBits) {
        return RigidBodyKey.of(new UUID(0L, leastSignificantBits));
    }

    private static WorldCollisionStreamingBounds boundsAt(float x, float y, float z) {
        return WorldCollisionStreamingBounds.from(new Vector3f(x, y, z), 4);
    }

    private static Object newCachedSection() throws Exception {
        Class<?> sectionType = nestedClass("CachedSection");
        Constructor<?> constructor = sectionType.getDeclaredConstructor(int.class,
            int.class,
            int.class,
            long.class,
            long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0, 0, 0, 0L, 1L);
    }

    private static void addGeometryBodies(@Nonnull PhysicsSpace space,
        @Nonnull Object target,
        @Nonnull SectionCollisionGeometry geometry,
        int chunkX,
        int sectionY,
        int chunkZ) throws Throwable {
        Method method = Arrays.stream(WorldVoxelCollisionCache.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals("addGeometryBodies"))
            .findFirst()
            .orElseThrow();
        method.setAccessible(true);
        try {
            method.invoke(null, addGeometryBodiesArguments(method,
                space,
                target,
                geometry,
                chunkX,
                sectionY,
                chunkZ));
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    @Nonnull
    private static Object[] addGeometryBodiesArguments(@Nonnull Method method,
        @Nonnull PhysicsSpace space,
        @Nonnull Object target,
        @Nonnull SectionCollisionGeometry geometry,
        int chunkX,
        int sectionY,
        int chunkZ) {
        Object[] arguments = new Object[method.getParameterCount()];
        int integerIndex = 0;
        for (int index = 0; index < arguments.length; index++) {
            Class<?> type = method.getParameterTypes()[index];
            if (type == PhysicsSpace.class) {
                arguments[index] = space;
            } else if (type == SectionCollisionGeometry.class) {
                arguments[index] = geometry;
            } else if (type.getSimpleName().equals("CachedSection")) {
                arguments[index] = target;
            } else if (type == int.class) {
                arguments[index] = switch (integerIndex++) {
                    case 0 -> chunkX;
                    case 1 -> sectionY;
                    case 2 -> chunkZ;
                    default -> throw new IllegalStateException("Unexpected integer parameter");
                };
            } else {
                arguments[index] = null;
            }
        }
        return arguments;
    }

    @Nonnull
    private static Class<?> nestedClass(@Nonnull String simpleName) {
        return Arrays.stream(WorldVoxelCollisionCache.class.getDeclaredClasses())
            .filter(candidate -> candidate.getSimpleName().equals(simpleName))
            .findFirst()
            .orElseThrow();
    }

    private static final class RecordingSpace implements InvocationHandler {

        private final PhysicsSpace delegate = new FakePhysicsBackend("test:voxel-capability-fallback")
            .createSpace(SpaceId.next());
        private final PhysicsSpace space = (PhysicsSpace) Proxy.newProxyInstance(
            PhysicsSpace.class.getClassLoader(),
            new Class<?>[] {PhysicsSpace.class},
            this);
        private int createdBoxes;
        private int createdVoxelTerrains;

        @Nonnull
        private PhysicsSpace space() {
            return space;
        }

        private int createdBoxes() {
            return createdBoxes;
        }

        private int createdVoxelTerrains() {
            return createdVoxelTerrains;
        }

        @Override
        public Object invoke(@Nonnull Object proxy,
            @Nonnull Method method,
            @Nullable Object[] arguments) throws Throwable {
            return switch (method.getName()) {
                case "supportsVoxelTerrain" -> true;
                case "getCapability" -> capabilityAbsent(method);
                case "createVoxelTerrain" -> {
                    createdVoxelTerrains++;
                    throw new AssertionError("Voxel terrain should not be created without a typed capability");
                }
                case "createBox" -> {
                    createdBoxes++;
                    yield invokeDelegate(method, arguments);
                }
                default -> invokeDelegate(method, arguments);
            };
        }

        @Nullable
        private static Object capabilityAbsent(@Nonnull Method method) {
            return method.getReturnType() == Optional.class ? Optional.empty() : null;
        }

        @Nullable
        private Object invokeDelegate(@Nonnull Method method,
            @Nullable Object[] arguments) throws Throwable {
            try {
                return method.invoke(delegate, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }
}
