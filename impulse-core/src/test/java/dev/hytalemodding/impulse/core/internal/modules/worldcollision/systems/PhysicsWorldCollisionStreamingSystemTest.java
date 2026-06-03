package dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsWorldCollisionStreamingSystem;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerBridge;
import dev.hytalemodding.impulse.core.internal.resources.owner.TestPhysicsOwnerLane;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.testsupport.LegacyLiveHandleTestResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class PhysicsWorldCollisionStreamingSystemTest {

    private static final AtomicInteger BACKEND_COUNTER = new AtomicInteger();

    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void disableWorldCollisionLifecycle() {
        WorldCollisionLifecycle.disable();
    }

    @Test
    void dynamicCommandSpawnStreamsBeforeSnapshotPublication() throws Exception {
        WorldCollisionLifecycle.enable();
        BackendId backendId =
            new BackendId("test:stream-command-spawn-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerBackend(new FakePhysicsBackend(backendId));
        LegacyLiveHandleTestResource resource = new LegacyLiveHandleTestResource();

        try (TestPhysicsOwnerLane owner = new TestPhysicsOwnerLane()) {
            owner.start("stream-command-spawn-test");
            resource.attachOwnerExecutor(owner);
            PhysicsSpace space = resource.createLiveSpace(backendId,
                "test-world",
                PhysicsSpaceSettings.streamingWorldCollision());
            RigidBodyKey bodyKey = RigidBodyKey.random();

            var handle = resource.submitCommands(100L, commands -> commands
                .spawnBody(bodyKey, spawn -> spawn
                    .space(space.id())
                    .box(0.5f, 0.5f, 0.5f)
                    .mass(1.0f)
                    .dynamic()
                    .position(10.0f, 20.0f, 30.0f)
                    .settings(RigidBodySpawnSettings.material(0.5f, 0.0f))
                    .kind(PhysicsBodyKind.BODY)
                    .runtimeOnly()));

            assertTrue(handle.allApplied().toCompletableFuture().get(2L, TimeUnit.SECONDS));

            PhysicsWorldCollisionStreamingSystem system = new PhysicsWorldCollisionStreamingSystem();
            List<?> firstTargets = PhysicsOwnerBridge.call(owner,
                "collect command-spawned body streaming targets",
                () -> collectDynamicBodyTargets(system, resource, space.id(), 1L));
            List<?> secondTargets = PhysicsOwnerBridge.call(owner,
                "collect command-spawned body streaming targets after missed apply",
                () -> collectDynamicBodyTargets(system, resource, space.id(), 2L));

            assertEquals(1, firstTargets.size());
            assertEquals(1, secondTargets.size());
        }
    }

    @Test
    void collectedStreamingPlanRevisionBecomesStaleAfterSettingsChange() throws Exception {
        WorldCollisionLifecycle.enable();
        BackendId backendId =
            new BackendId("test:stream-plan-revision-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerRuntimeProvider(new FakePhysicsBackendRuntimeProvider(backendId, false, false));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        SpaceId spaceId = resource.createSpace(backendId,
            "test-world",
            PhysicsSpaceSettings.streamingWorldCollision());
        PhysicsWorldCollisionStreamingSystem system = new PhysicsWorldCollisionStreamingSystem();

        List<?> plans = collectStreamingPlans(system, resource, 1L);
        Object plan = plans.getFirst();
        long planRevision = streamingPlanRevision(plan);

        assertEquals(resource.worldCollisionStreamingRevision(spaceId), planRevision);

        PhysicsSpaceSettings settings = resource.getSpaceSettings(spaceId);
        settings.getWorldCollisionSettings().setWorldCollisionRadius(
            settings.getWorldCollisionSettings().getWorldCollisionRadius() + 1);
        resource.setSpaceSettings(spaceId, settings);

        assertTrue(resource.worldCollisionStreamingRevision(spaceId) > planRevision);
    }

    @Test
    void disabledLifecycleCollectsNoStreamingPlans() throws Exception {
        BackendId backendId =
            new BackendId("test:stream-lifecycle-disabled-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerRuntimeProvider(new FakePhysicsBackendRuntimeProvider(backendId, false, false));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        resource.createSpace(backendId,
            "test-world",
            PhysicsSpaceSettings.streamingWorldCollision());
        PhysicsWorldCollisionStreamingSystem system = new PhysicsWorldCollisionStreamingSystem();

        assertTrue(collectStreamingPlans(system, resource, 1L).isEmpty());
    }

    @Test
    void collectedStreamingPlanCapturesLifecycleGeneration() throws Exception {
        WorldCollisionLifecycle.enable();
        BackendId backendId =
            new BackendId("test:stream-lifecycle-generation-" + BACKEND_COUNTER.incrementAndGet());
        Impulse.registerRuntimeProvider(new FakePhysicsBackendRuntimeProvider(backendId, false, false));
        PhysicsWorldRuntimeResource resource = new PhysicsWorldRuntimeResource();
        resource.createSpace(backendId,
            "test-world",
            PhysicsSpaceSettings.streamingWorldCollision());
        PhysicsWorldCollisionStreamingSystem system = new PhysicsWorldCollisionStreamingSystem();

        Object plan = collectStreamingPlans(system, resource, 1L).getFirst();

        assertEquals(WorldCollisionLifecycle.generation(), streamingPlanLifecycleGeneration(plan));
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static List<?> collectDynamicBodyTargets(@Nonnull PhysicsWorldCollisionStreamingSystem system,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull SpaceId spaceId,
        long currentTick) throws ReflectiveOperationException {
        Method method = PhysicsWorldCollisionStreamingSystem.class.getDeclaredMethod(
            "collectDynamicBodyTargets",
            PhysicsWorldRuntimeResource.class,
            WorldVoxelCollisionCache.class,
            SpaceId.class,
            int.class,
            long.class,
            int.class,
            Snapshot.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(system,
            resource,
            resource.worldCollisionCache(),
            spaceId,
            PhysicsWorldCollisionStreamingSystem.DEFAULT_BODY_STREAMING_RADIUS,
            currentTick,
            PhysicsSpaceSettings.streamingWorldCollision()
                .getWorldCollisionSettings()
                .getWorldCollisionTtlTicks(),
            null);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static List<?> collectStreamingPlans(@Nonnull PhysicsWorldCollisionStreamingSystem system,
        @Nonnull PhysicsWorldRuntimeResource resource,
        long currentTick) throws ReflectiveOperationException {
        Method method = PhysicsWorldCollisionStreamingSystem.class.getDeclaredMethod(
            "collectStreamingPlans",
            PhysicsWorldRuntimeResource.class,
            WorldVoxelCollisionCache.class,
            List.class,
            long.class,
            Snapshot.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(system,
            resource,
            resource.worldCollisionCache(),
            List.of(),
            currentTick,
            null);
    }

    private static long streamingPlanRevision(@Nonnull Object plan) throws ReflectiveOperationException {
        Method method = plan.getClass().getDeclaredMethod("settingsRevision");
        method.setAccessible(true);
        return (long) method.invoke(plan);
    }

    private static long streamingPlanLifecycleGeneration(@Nonnull Object plan)
        throws ReflectiveOperationException {
        Method method = plan.getClass().getDeclaredMethod("lifecycleGeneration");
        method.setAccessible(true);
        return (long) method.invoke(plan);
    }
}
