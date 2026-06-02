package dev.hytalemodding.impulse.core.internal.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.math.util.ChunkUtil;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.CombineCall;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.VoxelTerrainCall;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
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
        cache.recordBodyTargetRefresh(spaceId, bodyId, bounds, false, 1L);
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 2L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 3L, 100, snapshot)
            .refresh());
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 4L, 100, snapshot)
            .refresh());
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 5L, 100, snapshot)
            .refresh());
        cache.recordBodyTargetRefresh(spaceId, bodyId, bounds, false, 5L);

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
        cache.recordBodyTargetRefresh(spaceId, bodyId, bounds, true, 1L);
        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, true, 2L, 100, snapshot)
            .refresh());
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, true, 21L, 100, snapshot)
            .refresh());
        cache.recordBodyTargetRefresh(spaceId, bodyId, bounds, true, 21L);

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
        cache.recordBodyTargetRefresh(spaceId,
            bodyId,
            boundsAt(10.0f, 65.0f, 10.0f),
            false,
            1L);
        assertTrue(cache.shouldRefreshBodyTarget(spaceId,
            bodyId,
            boundsAt(40.0f, 65.0f, 10.0f),
            false,
            2L,
            100,
            snapshot).refresh());
        cache.recordBodyTargetRefresh(spaceId,
            bodyId,
            boundsAt(40.0f, 65.0f, 10.0f),
            false,
            2L);

        assertEquals(1, snapshot.getBodyTargetFirstSeen());
        assertEquals(1, snapshot.getBodyTargetCacheHits());
        assertEquals(1, snapshot.getBodyTargetBoundsChanged());
    }

    @Test
    void bodyTargetRefreshIsNotConsumedUntilTerrainApplyRecordsIt() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        SpaceId spaceId = new SpaceId(1005);
        RigidBodyKey bodyId = bodyId(5);
        WorldCollisionStreamingBounds bounds = boundsAt(10.0f, 65.0f, 10.0f);

        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 1L, 100, null)
            .refresh());
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 2L, 100, null)
            .refresh());

        cache.recordBodyTargetRefresh(spaceId, bodyId, bounds, false, 2L);

        assertFalse(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 3L, 100, null)
            .refresh());
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
        cache.recordBodyTargetRefresh(spaceId, bodyId, bounds, false, 1L);
        assertEquals(1, cache.pruneBodyStreamingTargets(spaceId, 202L, 100, snapshot));
        assertTrue(cache.shouldRefreshBodyTarget(spaceId, bodyId, bounds, false, 203L, 100, snapshot)
            .refresh());
        cache.recordBodyTargetRefresh(spaceId, bodyId, bounds, false, 203L);

        assertEquals(2, snapshot.getBodyTargetFirstSeen());
        assertEquals(1, snapshot.getBodyTargetsPruned());
    }

    @Test
    void absentVoxelCapabilityFallsBackToMergedFullCubeBoxes() throws Exception {
        RuntimeFixture fixture = runtimeFixture("test:voxel-runtime-fallback", false);
        SectionCollisionGeometry geometry = new SectionCollisionGeometry(new int[] {0, 0, 0, 1, 0, 0},
            List.of(new BoxCollider(1.0, 0.5, 0.5, 1.0, 0.5, 0.5)),
            List.of(new BoxCollider(4.25, 2.25, 4.25, 0.25, 0.25, 0.25)),
            4096,
            2,
            0,
            1);
        Object cachedSection = newCachedSection();

        assertDoesNotThrow(() -> addGeometryBodies(fixture.binding(),
            cachedSection,
            geometry,
            0,
            0,
            0));

        WorldVoxelCollisionCache.DebugSection debugSection = debugSection(cachedSection);
        assertEquals(2, fixture.runtime().bodyCount(fixture.backendSpaceId()));
        assertEquals(0, fixture.runtime().voxelTerrainCalls(fixture.backendSpaceId()).size());
        assertFalse(debugSection.voxelTerrain());
        assertEquals(1, debugSection.fullCubeBoxes().size());
        assertEquals(1, debugSection.detailBoxes().size());
    }

    @Test
    void supportedRuntimeCreatesVoxelTerrainAndKeepsDebugBoxes() throws Throwable {
        RuntimeFixture fixture = runtimeFixture("test:voxel-runtime-native", true);
        SectionCollisionGeometry geometry = new SectionCollisionGeometry(new int[] {0, 0, 0, 1, 0, 0},
            List.of(new BoxCollider(40.0, 48.5, 64.0, 1.0, 0.5, 0.5)),
            List.of(new BoxCollider(36.25, 50.25, 68.25, 0.25, 0.25, 0.25)),
            4096,
            2,
            0,
            1);
        Object cachedSection = newCachedSection(2, 3, 4);

        addGeometryBodies(fixture.binding(), cachedSection, geometry, 2, 3, 4);

        List<VoxelTerrainCall> calls = fixture.runtime().voxelTerrainCalls(fixture.backendSpaceId());
        WorldVoxelCollisionCache.DebugSection debugSection = debugSection(cachedSection);
        assertEquals(1, calls.size());
        assertArrayEquals(new int[] {0, 0, 0, 1, 0, 0}, calls.getFirst().voxelCoordinates());
        assertEquals((float) (2 << ChunkUtil.BITS), calls.getFirst().positionX());
        assertEquals((float) (3 << ChunkUtil.BITS), calls.getFirst().positionY());
        assertEquals((float) (4 << ChunkUtil.BITS), calls.getFirst().positionZ());
        assertEquals(0.75f, calls.getFirst().friction());
        assertEquals(0.0f, calls.getFirst().restitution());
        assertEquals(PhysicsCollisionFilters.TERRAIN, calls.getFirst().collisionGroup());
        assertEquals(PhysicsCollisionFilters.ALL, calls.getFirst().collisionMask());
        assertEquals(2, fixture.runtime().bodyCount(fixture.backendSpaceId()));
        assertTrue(debugSection.voxelTerrain());
        assertEquals(1, debugSection.fullCubeBoxes().size());
        assertEquals(1, debugSection.detailBoxes().size());
    }

    @Test
    void disabledNativeVoxelTerrainFallsBackToMergedFullCubeBoxes() throws Throwable {
        RuntimeFixture fixture = runtimeFixture("test:voxel-runtime-disabled", true);
        SectionCollisionGeometry geometry = new SectionCollisionGeometry(new int[] {0, 0, 0, 1, 0, 0},
            List.of(new BoxCollider(1.0, 0.5, 0.5, 1.0, 0.5, 0.5)),
            List.of(),
            4096,
            2,
            0,
            0);
        Object cachedSection = newCachedSection();

        addGeometryBodies(fixture.binding(), cachedSection, geometry, 0, 0, 0, false);

        WorldVoxelCollisionCache.DebugSection debugSection = debugSection(cachedSection);
        assertEquals(1, fixture.runtime().bodyCount(fixture.backendSpaceId()));
        assertEquals(0, fixture.runtime().voxelTerrainCalls(fixture.backendSpaceId()).size());
        assertFalse(debugSection.voxelTerrain());
        assertEquals(1, debugSection.fullCubeBoxes().size());
    }

    @Test
    void stitchesVoxelTerrainToSixAdjacentSections() throws Throwable {
        RuntimeFixture fixture = runtimeFixture("test:voxel-runtime-stitch", true);
        Object cache = newSpaceCollisionCache();
        Object center = newCachedSection(10, 5, 20);
        long centerBody = createVoxelTerrain(fixture);
        markVoxelTerrain(center, centerBody);
        putCachedSection(cache, center);

        Object west = cachedVoxelNeighbor(fixture, cache, 9, 5, 20);
        Object east = cachedVoxelNeighbor(fixture, cache, 11, 5, 20);
        Object down = cachedVoxelNeighbor(fixture, cache, 10, 4, 20);
        Object up = cachedVoxelNeighbor(fixture, cache, 10, 6, 20);
        Object north = cachedVoxelNeighbor(fixture, cache, 10, 5, 19);
        Object south = cachedVoxelNeighbor(fixture, cache, 10, 5, 21);

        stitchAdjacentVoxelTerrains(fixture.binding(), cache, center);

        assertEquals(List.of(new CombineCall(centerBody, voxelTerrainBodyId(west), -16, 0, 0),
                new CombineCall(centerBody, voxelTerrainBodyId(east), 16, 0, 0),
                new CombineCall(centerBody, voxelTerrainBodyId(down), 0, -16, 0),
                new CombineCall(centerBody, voxelTerrainBodyId(up), 0, 16, 0),
                new CombineCall(centerBody, voxelTerrainBodyId(north), 0, 0, -16),
                new CombineCall(centerBody, voxelTerrainBodyId(south), 0, 0, 16)),
            fixture.runtime().combineCalls(fixture.backendSpaceId()));
    }

    private static RigidBodyKey bodyId(long leastSignificantBits) {
        return RigidBodyKey.of(new UUID(0L, leastSignificantBits));
    }

    private static WorldCollisionStreamingBounds boundsAt(float x, float y, float z) {
        return WorldCollisionStreamingBounds.from(new Vector3f(x, y, z), 4);
    }

    private static Object newCachedSection() throws Exception {
        return newCachedSection(0, 0, 0);
    }

    private static Object newCachedSection(int chunkX, int sectionY, int chunkZ) throws Exception {
        Class<?> sectionType = nestedClass("CachedSection");
        Constructor<?> constructor = sectionType.getDeclaredConstructor(int.class,
            int.class,
            int.class,
            long.class,
            long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(chunkX, sectionY, chunkZ, 0L, 1L);
    }

    private static void addGeometryBodies(@Nonnull PhysicsSpaceBinding space,
        @Nonnull Object target,
        @Nonnull SectionCollisionGeometry geometry,
        int chunkX,
        int sectionY,
        int chunkZ) throws Throwable {
        addGeometryBodies(space, target, geometry, chunkX, sectionY, chunkZ, true);
    }

    private static void addGeometryBodies(@Nonnull PhysicsSpaceBinding space,
        @Nonnull Object target,
        @Nonnull SectionCollisionGeometry geometry,
        int chunkX,
        int sectionY,
        int chunkZ,
        boolean nativeVoxelTerrainEnabled) throws Throwable {
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
                chunkZ,
                nativeVoxelTerrainEnabled));
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    @Nonnull
    private static Object[] addGeometryBodiesArguments(@Nonnull Method method,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Object target,
        @Nonnull SectionCollisionGeometry geometry,
        int chunkX,
        int sectionY,
        int chunkZ,
        boolean nativeVoxelTerrainEnabled) {
        Object[] arguments = new Object[method.getParameterCount()];
        int integerIndex = 0;
        for (int index = 0; index < arguments.length; index++) {
            Class<?> type = method.getParameterTypes()[index];
            if (type == PhysicsSpaceBinding.class) {
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
            } else if (type == boolean.class) {
                arguments[index] = nativeVoxelTerrainEnabled;
            } else {
                arguments[index] = null;
            }
        }
        return arguments;
    }

    private static void stitchAdjacentVoxelTerrains(@Nonnull PhysicsSpaceBinding space,
        @Nonnull Object cache,
        @Nonnull Object built) throws Throwable {
        Method method = Arrays.stream(WorldVoxelCollisionCache.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals("stitchAdjacentVoxelTerrains"))
            .findFirst()
            .orElseThrow();
        method.setAccessible(true);
        try {
            method.invoke(null, space, cache, built);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static Object newSpaceCollisionCache() throws Exception {
        Class<?> cacheType = nestedClass("SpaceCollisionCache");
        Constructor<?> constructor = cacheType.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @SuppressWarnings("unchecked")
    private static void putCachedSection(@Nonnull Object cache, @Nonnull Object section) throws Exception {
        Method keyMethod = WorldVoxelCollisionCache.class.getDeclaredMethod("packSectionKey",
            int.class,
            int.class,
            int.class);
        keyMethod.setAccessible(true);
        long key = (long) keyMethod.invoke(null,
            intField(section, "chunkX"),
            intField(section, "sectionY"),
            intField(section, "chunkZ"));
        java.lang.reflect.Field sectionsField = cache.getClass().getDeclaredField("sections");
        sectionsField.setAccessible(true);
        ((java.util.Map<Long, Object>) sectionsField.get(cache)).put(key, section);
    }

    private static Object cachedVoxelNeighbor(@Nonnull RuntimeFixture fixture,
        @Nonnull Object cache,
        int chunkX,
        int sectionY,
        int chunkZ) throws Exception {
        Object section = newCachedSection(chunkX, sectionY, chunkZ);
        markVoxelTerrain(section, createVoxelTerrain(fixture));
        putCachedSection(cache, section);
        return section;
    }

    private static long createVoxelTerrain(@Nonnull RuntimeFixture fixture) {
        return fixture.runtime().createVoxelTerrain(fixture.backendSpaceId(),
            1.0f,
            1.0f,
            1.0f,
            new int[] {0, 0, 0},
            0.0f,
            0.0f,
            0.0f,
            0.75f,
            0.0f,
            PhysicsCollisionFilters.TERRAIN,
            PhysicsCollisionFilters.ALL);
    }

    @SuppressWarnings("unchecked")
    private static void markVoxelTerrain(@Nonnull Object section, long backendBodyId) throws Exception {
        java.lang.reflect.Field backendBodyIds = section.getClass().getDeclaredField("backendBodyIds");
        backendBodyIds.setAccessible(true);
        ((List<Long>) backendBodyIds.get(section)).add(backendBodyId);
        setBooleanField(section, "voxelTerrain", true);
        setLongField(section, "voxelTerrainBodyId", backendBodyId);
    }

    private static long voxelTerrainBodyId(@Nonnull Object section) throws Exception {
        java.lang.reflect.Field field = section.getClass().getDeclaredField("voxelTerrainBodyId");
        field.setAccessible(true);
        return field.getLong(section);
    }

    private static WorldVoxelCollisionCache.DebugSection debugSection(@Nonnull Object section) throws Exception {
        Method method = section.getClass().getDeclaredMethod("debugSection");
        method.setAccessible(true);
        return (WorldVoxelCollisionCache.DebugSection) method.invoke(section);
    }

    private static int intField(@Nonnull Object target, @Nonnull String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setBooleanField(@Nonnull Object target,
        @Nonnull String name,
        boolean value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void setLongField(@Nonnull Object target,
        @Nonnull String name,
        long value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    @Nonnull
    private static Class<?> nestedClass(@Nonnull String simpleName) {
        return Arrays.stream(WorldVoxelCollisionCache.class.getDeclaredClasses())
            .filter(candidate -> candidate.getSimpleName().equals(simpleName))
            .findFirst()
            .orElseThrow();
    }

    private static RuntimeFixture runtimeFixture(@Nonnull String backendId, boolean voxelTerrain) {
        BackendId id = new BackendId(backendId);
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(id, false, voxelTerrain);
        FakePhysicsBackendRuntime runtime = (FakePhysicsBackendRuntime) provider.createRuntime();
        SpaceId spaceId = new SpaceId(42);
        int backendSpaceId = runtime.createSpace(spaceId);
        return new RuntimeFixture(new PhysicsSpaceBinding(id,
            spaceId,
            new BackendSpaceHandle(backendSpaceId),
            runtime), runtime, backendSpaceId);
    }

    private record RuntimeFixture(@Nonnull PhysicsSpaceBinding binding,
                                  @Nonnull FakePhysicsBackendRuntime runtime,
                                  int backendSpaceId) {
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
