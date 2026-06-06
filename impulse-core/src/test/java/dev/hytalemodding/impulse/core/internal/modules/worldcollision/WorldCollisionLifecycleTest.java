package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("DataFlowIssue")
class WorldCollisionLifecycleTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @BeforeEach
    @AfterEach
    void disableLifecycle() {
        WorldCollisionLifecycle.disable();
    }

    @Test
    void lifecycleStartsDisabledAndRejectsManualBuilds() {
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        SpaceId spaceId = createSpace(resource, PhysicsSpaceSettings.streamingWorldCollision());

        assertFalse(WorldCollisionLifecycle.isEnabled());
        assertThrows(IllegalStateException.class,
            () -> resource.rebuildWorldCollisionAround(null, spaceId, new Vector3d(), 1));
        assertThrows(IllegalStateException.class,
            () -> resource.ensureWorldCollisionAround(null, spaceId, List.of(new Vector3d()), 1, 0L));
        assertDoesNotThrow(() -> resource.clearWorldCollision(spaceId));
        assertDoesNotThrow(resource::getWorldCollisionStats);
    }

    @Test
    void enabledLifecycleStillRequiresSpaceOptIn() {
        WorldCollisionLifecycle.enable();
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        SpaceId spaceId = createSpace(resource, PhysicsSpaceSettings.defaults());

        assertThrows(IllegalStateException.class,
            () -> resource.rebuildWorldCollisionAround(null, spaceId, new Vector3d(), 1));
        assertThrows(IllegalStateException.class,
            () -> resource.ensureWorldCollisionAround(null, spaceId, List.of(new Vector3d()), 1, 0L));
        assertDoesNotThrow(() -> resource.clearWorldCollision(spaceId));
    }

    @Test
    void lifecycleGenerationChangesWhenLifecycleIsDisabled() {
        WorldCollisionLifecycle.enable();
        long enabledGeneration = WorldCollisionLifecycle.generation();

        WorldCollisionLifecycle.disable();

        assertTrue(WorldCollisionLifecycle.generation() > enabledGeneration);
    }

    @Test
    void disablingLifecycleRestoresChunkBoundaryPausedBodies() {
        WorldCollisionLifecycle.enable();
        RuntimeFixture fixture = createRuntimeFixture(PhysicsSpaceSettings.streamingWorldCollision());
        RigidBodyKey bodyKey = spawnDynamicBody(fixture.resource(), fixture.spaceId());
        BodyHandle body = bodyHandle(fixture.resource(), bodyKey);
        fixture.runtime().setBodyType(body.backendSpaceId(),
            body.backendBodyId(),
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.KINEMATIC));
        fixture.resource().pauseChunkBoundaryBody(bodyKey,
            42L,
            PhysicsBodyType.DYNAMIC,
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Vector3f(4.0f, 5.0f, 6.0f));

        WorldCollisionLifecycle.disable();

        assertEquals(BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
            fixture.runtime().bodyTypeCode(body.backendSpaceId(), body.backendBodyId()));
        assertNull(fixture.resource().getChunkBoundaryPauseState(bodyKey));
    }

    @Test
    void disablingLifecycleRestoresCollisionLodFilters() {
        WorldCollisionLifecycle.enable();
        RuntimeFixture fixture = createRuntimeFixture(PhysicsSpaceSettings.streamingWorldCollision());
        RigidBodyKey bodyKey = spawnDynamicBody(fixture.resource(), fixture.spaceId());
        BodyHandle body = bodyHandle(fixture.resource(), bodyKey);
        fixture.runtime().setBodyCollisionFilter(body.backendSpaceId(),
            body.backendBodyId(),
            PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsCollisionFilters.TERRAIN);

        WorldCollisionLifecycle.disable();

        assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY,
            fixture.runtime().bodyCollisionGroup(body.backendSpaceId(), body.backendBodyId()));
        assertEquals(PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY,
            fixture.runtime().bodyCollisionMask(body.backendSpaceId(), body.backendBodyId()));
    }

    @Test
    void disablingLifecycleDestroysRetainedTerrainBodiesBeforeClearingCache() throws Exception {
        WorldCollisionLifecycle.enable();
        RuntimeFixture fixture = createRuntimeFixture(PhysicsSpaceSettings.streamingWorldCollision());
        PhysicsSpaceBinding space = requireSpaceBinding(fixture);
        long backendBodyId = cacheTerrainBody(fixture.resource(), space);

        assertTrue(fixture.runtime().containsBody(space.backendSpaceHandle().value(), backendBodyId));
        assertEquals(1, fixture.resource().getWorldCollisionStats().bodies());

        WorldCollisionLifecycle.disable();

        assertFalse(fixture.runtime().containsBody(space.backendSpaceHandle().value(), backendBodyId));
        assertEquals(0, fixture.runtime().bodyCount(space.backendSpaceHandle().value()));
        assertEquals(0, fixture.resource().getWorldCollisionStats().bodies());
        assertFalse(fixture.resource().worldCollisionCache().isStreamingApplyPending());
        assertTrue(fixture.resource().worldCollisionCache().tryBeginStreamingApply());

        WorldCollisionLifecycle.enable();
        assertTrue(WorldCollisionLifecycle.isEnabled());
    }

    private static SpaceId createSpace(PhysicsWorldRuntimeResource resource,
        PhysicsSpaceSettings settings) {
        BackendId backendId = new BackendId("test:world-collision-lifecycle-"
            + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerRuntimeProvider(new FakePhysicsBackendRuntimeProvider(backendId,
            false,
            false));
        return resource.createSpace(backendId, "test-world", settings);
    }

    private static RuntimeFixture createRuntimeFixture(PhysicsSpaceSettings settings) {
        BackendId backendId = new BackendId("test:world-collision-cleanup-"
            + BACKEND_COUNTER.incrementAndGet());
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(backendId, false, false);
        Impulse.registerRuntimeProvider(provider);
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        SpaceId spaceId = resource.createSpace(backendId, "test-world", settings);
        return new RuntimeFixture(resource, spaceId, provider.createdRuntimes().getFirst());
    }

    private static long cacheTerrainBody(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpaceBinding space) throws Exception {
        long backendBodyId = space.runtime().createBody(space.backendSpaceHandle().value(),
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            0.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.STATIC),
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        Object spaceCache = newSpaceCollisionCache();
        Object section = newCachedSection(0, 0, 0);
        markBackendBody(section, backendBodyId);
        putCachedSection(spaceCache, section);
        putSpaceCache(resource.worldCollisionCache(), space.spaceId(), spaceCache);
        return backendBodyId;
    }

    private static Object newSpaceCollisionCache() throws Exception {
        Class<?> cacheType = nestedCollisionCacheClass("SpaceCollisionCache");
        Constructor<?> constructor = cacheType.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object newCachedSection(int chunkX, int sectionY, int chunkZ) throws Exception {
        Class<?> sectionType = nestedCollisionCacheClass("CachedSection");
        Constructor<?> constructor = sectionType.getDeclaredConstructor(int.class,
            int.class,
            int.class,
            long.class,
            long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(chunkX, sectionY, chunkZ, 0L, 1L);
    }

    @SuppressWarnings("unchecked")
    private static void markBackendBody(@Nonnull Object section, long backendBodyId)
        throws Exception {
        java.lang.reflect.Field backendBodyIds =
            section.getClass().getDeclaredField("backendBodyIds");
        backendBodyIds.setAccessible(true);
        ((List<Long>) backendBodyIds.get(section)).add(backendBodyId);
    }

    @SuppressWarnings("unchecked")
    private static void putCachedSection(@Nonnull Object cache, @Nonnull Object section)
        throws Exception {
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
        ((Long2ObjectMap<Object>) sectionsField.get(cache)).put(key, section);
    }

    @SuppressWarnings("unchecked")
    private static void putSpaceCache(@Nonnull WorldVoxelCollisionCache cache,
        @Nonnull SpaceId spaceId,
        @Nonnull Object spaceCache) throws Exception {
        java.lang.reflect.Field spaces = WorldVoxelCollisionCache.class.getDeclaredField("spaces");
        spaces.setAccessible(true);
        ((Int2ObjectMap<Object>) spaces.get(cache)).put(spaceId.value(), spaceCache);
    }

    private static int intField(@Nonnull Object target, @Nonnull String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    @Nonnull
    private static Class<?> nestedCollisionCacheClass(@Nonnull String simpleName) {
        return Arrays.stream(WorldVoxelCollisionCache.class.getDeclaredClasses())
            .filter(candidate -> candidate.getSimpleName().equals(simpleName))
            .findFirst()
            .orElseThrow();
    }

    private static RigidBodyKey spawnDynamicBody(PhysicsWorldRuntimeResource resource,
        SpaceId spaceId) {
        RigidBodyKey bodyKey = RigidBodyKey.random();
        assertTrue(resource.submitCommands(1L, commands -> commands
                .spawnBody(bodyKey, spawn -> spawn
                    .space(spaceId)
                    .box(0.5f, 0.5f, 0.5f)
                    .mass(1.0f)
                    .dynamic()
                    .kind(PhysicsBodyKind.BODY)
                    .runtimeOnly()))
            .allApplied()
            .toCompletableFuture()
            .join());
        return bodyKey;
    }

    @Nonnull
    private static PhysicsSpaceBinding requireSpaceBinding(@Nonnull RuntimeFixture fixture) {
        PhysicsSpaceBinding space = fixture.resource().getSpaceBinding(fixture.spaceId());
        assertNotNull(space);
        return space;
    }

    private static BodyHandle bodyHandle(PhysicsWorldRuntimeResource resource,
        RigidBodyKey bodyKey) {
        PhysicsBodyRegistration registration = resource.getRegistration(bodyKey);
        assertNotNull(registration);
        PhysicsSpaceBinding space = resource.getSpaceBinding(registration.spaceId());
        assertNotNull(space);
        return new BodyHandle(space.backendSpaceHandle().value(),
            registration.backendBodyHandle().value());
    }

    private record RuntimeFixture(PhysicsWorldRuntimeResource resource,
                                  SpaceId spaceId,
                                  FakePhysicsBackendRuntime runtime) {
    }

    private record BodyHandle(int backendSpaceId, long backendBodyId) {
    }
}
