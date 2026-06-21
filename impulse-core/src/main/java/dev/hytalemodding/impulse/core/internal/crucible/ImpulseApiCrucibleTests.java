package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsSpaceMutations;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshots;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsDiagnostics;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.physics.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsExtensionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Crucible suites that exercise Impulse API behavior inside a live Hytale server.
 */
final class ImpulseApiCrucibleTests {

    private static final PhysicsBackendExtensionId RAPIER_SOLVER_EXTENSION_ID =
        new PhysicsBackendExtensionId("impulse:rapier_solver");
    private static final String RAPIER_INTERNAL_PGS_ITERATIONS = "internalPgsIterations";
    private static final String RAPIER_MIN_ISLAND_SIZE = "minIslandSize";

    private ImpulseApiCrucibleTests() {
    }

    static void register(CrucibleBridge bridge, ClassLoader loader)
        throws ReflectiveOperationException {

        bridge.registerSuite(loader, smokeSuite());
        bridge.registerSuite(loader, runtimeStabilitySuite());
        bridge.registerSuite(loader, transformSyncSuite());
        bridge.registerSuite(loader, terrainCollisionSuite());
    }

    private static CrucibleSuite smokeSuite() {
        return new CrucibleSuite(
            "impulse:smoke",
            "Impulse Smoke",
            "Verifies plugin load, backend registration, and basic API operations",
            Set.of("smoke"),
            List.of(
                CrucibleTestCase.sync("plugin loaded", () -> ImpulsePlugin.get() != null,
                    "ImpulsePlugin singleton is null"),
                CrucibleTestCase.sync("backends registered", () -> {
                    Collection<PhysicsBackendRuntimeProvider> providers = Impulse.getRuntimeProviders();
                    return !providers.isEmpty();
                }, "No physics backend runtimes registered"),
                CrucibleTestCase.sync("test backend selectable", () ->
                    {
                        CrucibleBackends.requireBackendId();
                        return true;
                    },
                    "No backend id is available for Crucible tests"),
                CrucibleTestCase.asyncResult("PhysicsChunk subplugin load/unload/reload",
                    ignored -> PhysicsChunkSubPluginCrucibleSupport.loadUnloadReloadSmokeAsync(),
                    "PhysicsChunk subplugin lifecycle smoke failed"),
                CrucibleTestCase.asyncResult("physics entity subplugin load/unload/reload",
                    ignored -> PhysicsEntitySubPluginCrucibleSupport.loadUnloadReloadSmokeAsync(),
                    "PhysicsEntity subplugin lifecycle smoke failed"),
                CrucibleTestCase.asyncResult("control subplugin load/unload/reload",
                    ControlSubPluginCrucibleSupport::loadUnloadReloadSmokeAsync,
                    "Control subplugin lifecycle smoke failed"),
                CrucibleTestCase.sync("create space and body",
                    ImpulseApiCrucibleTests::createSpaceAndBody,
                    "Expected a body to be added to the physics space"),
                CrucibleTestCase.sync("step",
                    ImpulseApiCrucibleTests::stepSpaceDoesNotThrow,
                    "Physics space step failed")));
    }

    private static CrucibleSuite runtimeStabilitySuite() {
        return new CrucibleSuite(
            "impulse:runtime_stability",
            "Impulse Runtime Stability",
            "Verifies explicit space lifecycle, detached cleanup, and settings retention",
            Set.of("smoke", "stability"),
            List.of(
                CrucibleTestCase.async("space count round trip",
                    ImpulseApiCrucibleTests::spaceCountRoundTrip,
                    "PhysicsStore space count did not return to its previous value"),
                CrucibleTestCase.async("created explicit space lifecycle works",
                    ImpulseApiCrucibleTests::createdExplicitSpaceLifecycleWorks,
                    "Explicit space was not registered correctly"),
                CrucibleTestCase.async("clear populated spaces",
                    ImpulseApiCrucibleTests::clearPopulatedSpaces,
                    "PhysicsStore entity cleanup did not remove populated runtime spaces"),
                CrucibleTestCase.async("detached unregister removes body",
                    ImpulseApiCrucibleTests::detachedUnregisterRemovesBackendBody,
                    "Detached unregister did not remove the backend body"),
                CrucibleTestCase.async("settings round trip",
                    ImpulseApiCrucibleTests::settingsRoundTrip,
                    "Composable space settings did not retain runtime settings")));
    }

    private static CrucibleSuite transformSyncSuite() {
        return new CrucibleSuite(
            "impulse:transform_sync",
            "Impulse Transform Sync",
            "Verifies physics bodies move correctly under gravity",
            Set.of("integration"),
            List.of(
                CrucibleTestCase.sync("body fell", ImpulseApiCrucibleTests::dynamicBodyFalls,
                    "Dynamic body did not fall under gravity"),
                CrucibleTestCase.sync("static body stays",
                    ImpulseApiCrucibleTests::staticBodyDoesNotMove,
                    "Static body moved while stepping the physics space")));
    }

    private static CrucibleSuite terrainCollisionSuite() {
        return new CrucibleSuite(
            "impulse:terrain_collision",
            "Impulse Terrain Collision",
            "Verifies dynamic bodies land on ground plane collision",
            Set.of("collision"),
            List.of(
                CrucibleTestCase.sync("body landed",
                    ImpulseApiCrucibleTests::bodyLandsOnGroundPlane,
                    "Body did not land on the ground plane"),
                CrucibleTestCase.sync("body settled",
                    ImpulseApiCrucibleTests::bodySettlesWithinTolerance,
                    "Body did not settle within the expected speed tolerance"),
                CrucibleTestCase.sync("ray hit ground",
                    ImpulseApiCrucibleTests::raycastHitsGroundPlane,
                    "Raycast from above did not hit the ground plane")));
    }

    private static boolean createSpaceAndBody() {
        PhysicsBackendRuntime runtime = Impulse.createRuntime(CrucibleBackends.requireBackendId());
        int spaceId = runtime.createSpace(SpaceId.next());
        try {
            long bodyId = runtime.createBody(spaceId,
                BackendRuntimeCodes.SHAPE_BOX,
                0.5f,
                0.5f,
                0.5f,
                0.0f,
                0.0f,
                BackendRuntimeCodes.AXIS_Y,
                0.0f,
                1.0f,
                BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.DYNAMIC),
                0f,
                10f,
                0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f);
            return runtime.bodyCount(spaceId) == 1 && runtime.containsBody(spaceId, bodyId);
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static CompletionStage<Boolean> spaceCountRoundTrip(@Nonnull CrucibleContext context) {
        return callWhenPhysicsStoreIdle(context, "run Crucible space count round trip", world -> {
            Store<PhysicsStore> store = physicsStore(world);
            int previousCount = PhysicsSpaces.count(store);
            SpaceId spaceId = PhysicsSpaces.create(store, CrucibleBackends.requireBackendId());
            PhysicsSpaceMutations.removeEmptySpace(store, spaceId);
            return PhysicsSpaces.count(store) == previousCount
                && !PhysicsSpaces.hasSpace(store, spaceId);
        });
    }

    private static CompletionStage<Boolean> createdExplicitSpaceLifecycleWorks(
        @Nonnull CrucibleContext context) {
        return callWhenPhysicsStoreIdle(context, "run Crucible explicit space lifecycle", world -> {
            Store<PhysicsStore> store = physicsStore(world);
            SpaceId spaceId = PhysicsSpaces.create(store, CrucibleBackends.requireBackendId());
            PhysicsChunkCollisionSettings chunkCollisionSettings =
                new PhysicsChunkCollisionSettings();
            chunkCollisionSettings.setMode(PhysicsChunkCollisionMode.STREAMING);
            PhysicsSpaces.putChunkCollisionSettings(store, spaceId, chunkCollisionSettings);
            PhysicsChunkCollisionSettings spaceSettings =
                PhysicsSpaces.chunkCollisionSettings(store, spaceId);
            boolean registered = PhysicsSpaces.hasSpace(store, spaceId)
                && spaceSettings != null
                && spaceSettings.getMode() == PhysicsChunkCollisionMode.STREAMING;
            PhysicsSpaceMutations.removeEmptySpace(store, spaceId);
            return registered && !PhysicsSpaces.hasSpace(store, spaceId);
        });
    }

    private static CompletionStage<Boolean> clearPopulatedSpaces(
        @Nonnull CrucibleContext context) {
        return populatedBodyCleanup(context, true);
    }

    private static CompletionStage<Boolean> detachedUnregisterRemovesBackendBody(
        @Nonnull CrucibleContext context) {
        return populatedBodyCleanup(context, false);
    }

    private static CompletionStage<Boolean> populatedBodyCleanup(
        @Nonnull CrucibleContext context,
        boolean checkSpaceRemoval) {
        return createPopulatedBodyCleanupState(context)
            .thenCompose(state -> waitApproxTicksOnWorld(context, 4)
                .thenCompose(_ -> removeBodyEntityAndWait(context, state.store(), state.bodyRef()))
                .thenCompose(_ -> PhysicsDiagnostics.bodyCountAsync(state.store(), state.spaceRef()))
                .thenCompose(bodyCount -> PhysicsThreading.callWhenBackendIdleOnWorldThread(
                    state.world(),
                    "check Crucible body cleanup",
                    _ -> {
                        boolean spaceEmpty = bodyCount == 0;
                        boolean noRegistrations =
                            PhysicsBodies.bodyUuids(state.store()).isEmpty();
                        boolean removedSpace = true;
                        if (checkSpaceRemoval || spaceEmpty) {
                            PhysicsSpaceMutations.removeEmptySpace(
                                state.store(),
                                state.spaceId());
                            removedSpace = !PhysicsSpaces.hasSpace(state.store(), state.spaceId());
                        }
                        return spaceEmpty && noRegistrations && removedSpace;
                    })));
    }

    private static CompletionStage<PopulatedBodyCleanupState> createPopulatedBodyCleanupState(
        @Nonnull CrucibleContext context) {
        return callWhenPhysicsStoreIdle(context, "create Crucible body cleanup state", world -> {
            Store<PhysicsStore> store = physicsStore(world);
            SpaceId spaceId = SpaceId.next();
            Ref<PhysicsStore> spaceRef = PhysicsSpaces.create(store,
                UUID.randomUUID(),
                spaceId,
                CrucibleBackends.requireBackendId());
            Ref<PhysicsStore> bodyRef = addCrucibleBox(store, spaceRef, UUID.randomUUID());
            return new PopulatedBodyCleanupState(world, store, spaceId, spaceRef, bodyRef);
        });
    }

    @Nonnull
    private static <T> CompletionStage<T> callWhenPhysicsStoreIdle(
        @Nonnull CrucibleContext context,
        @Nonnull String operation,
        @Nonnull Function<World, T> action) {
        try {
            World world = context.world();
            return PhysicsThreading.callWhenBackendIdleOnWorldThread(world,
                operation,
                _ -> action.apply(world));
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static CompletionStage<Void> removeBodyEntityAndWait(@Nonnull CrucibleContext context,
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return PhysicsThreading.executeOnWorldThread(PhysicsThreading.world(store),
            "remove Crucible body entity",
            _ -> {
                if (bodyRef.isValid()) {
                    store.removeEntity(bodyRef, store.getRegistry().newHolder(), RemoveReason.REMOVE);
                }
            })
            .thenCompose(_ -> waitApproxTicksOnWorld(context, 4));
    }

    private static CompletionStage<Void> waitApproxTicksOnWorld(@Nonnull CrucibleContext context,
        int ticks) {
        try {
            return context.waitApproxTicksOnWorld(ticks);
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    private static Ref<PhysicsStore> addCrucibleBox(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid) {
        return store.addEntity(PhysicsBodyEntities.dynamicBodyHolder(spaceRef,
            bodyUuid,
            new Vector3f(0.0f, 5.0f, 0.0f),
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
            1.0f,
            RigidBodySpawnSettings.defaults(),
            null), AddReason.SPAWN);
    }

    private static CompletionStage<Boolean> settingsRoundTrip(@Nonnull CrucibleContext context) {
        PopulatedSettings settings = populatedSettings();

        return callWhenPhysicsStoreIdle(context, "run Crucible settings round trip", world -> {
            Store<PhysicsStore> store = physicsStore(world);
            SpaceId spaceId = PhysicsSpaces.create(store, CrucibleBackends.requireBackendId());
            try {
                applyPopulatedSettings(store, spaceId, settings);
                PhysicsChunkCollisionSettings chunkCollision =
                    PhysicsSpaces.chunkCollisionSettings(store, spaceId);
                PhysicsVisualSyncSettings visualSync =
                    PhysicsSpaces.visualSyncSettings(store, spaceId);
                PhysicsSolverSettings solver = PhysicsSpaces.solverSettings(store, spaceId);
                PhysicsExtensionSettings extension = PhysicsSpaces.extensionSettings(store,
                    spaceId);
                PhysicsVisualMaterializationSettings visualMaterialization =
                    PhysicsSpaces.visualMaterializationSettings(store, spaceId);
                if (chunkCollision == null
                    || visualSync == null
                    || solver == null
                    || extension == null
                    || visualMaterialization == null) {
                    return false;
                }
                return chunkCollision.getMode() == PhysicsChunkCollisionMode.STREAMING
                && chunkCollision.getRadius() == 9
                && chunkCollision.getBodyRadius() == 5
                && chunkCollision.getTtlTicks() == 77
                && visualSync.getVisualFullSyncRadius() == 48
                && visualSync.getVisualMaxSyncRadius() == 96
                && !visualSync.isVisualFarSyncCutoffEnabled()
                && visualSync.getVisualMidSyncIntervalTicks() == 3
                && visualSync.getVisualFarSyncIntervalTicks() == 17
                && visualSync.getVisualOcclusionMode() == VisualOcclusionMode.PRIORITY
                && visualSync.getVisualOcclusionRaycastsPerTick() == 31
                && visualSync.getVisualOcclusionCacheTicks() == 7
                && solver.getSolverIterations() == 5
                && solver.getStabilizationIterations() == 1
                && extension
                    .getInt(RAPIER_SOLVER_EXTENSION_ID,
                        RAPIER_INTERNAL_PGS_ITERATIONS)
                    .orElse(-1) == 2
                && extension
                    .getInt(RAPIER_SOLVER_EXTENSION_ID,
                        RAPIER_MIN_ISLAND_SIZE)
                    .orElse(-1) == 64
                && visualSync.isEntityVisualSyncCullingEnabled()
                && visualSync.isVisualVisibilityCullingEnabled()
                && visualMaterialization.isDetachedVisualMaterializationEnabled()
                && visualMaterialization.getDetachedVisualMaterializationRadius() == 48
                && visualMaterialization.getDetachedVisualDematerializationRadius() == 72
                && visualMaterialization.getDetachedVisualMaxSpawnsPerTick() == 33
                && visualMaterialization.getDetachedVisualMaxMaterialized() == 444
                && "Rock_Stone".equals(visualMaterialization.getDetachedVisualBlockType());
            } finally {
                PhysicsSpaceMutations.removeEmptySpace(store, spaceId);
            }
        });
    }

    @Nonnull
    private static PopulatedSettings populatedSettings() {
        PhysicsChunkCollisionSettings chunkCollision = new PhysicsChunkCollisionSettings();
        chunkCollision.setMode(PhysicsChunkCollisionMode.STREAMING);
        chunkCollision.setRadius(9);
        chunkCollision.setBodyRadius(5);
        chunkCollision.setTtlTicks(77);

        PhysicsVisualSyncSettings visualSync = new PhysicsVisualSyncSettings();
        visualSync.setVisualMaxSyncRadius(96);
        visualSync.setVisualFullSyncRadius(48);
        visualSync.setVisualFarSyncCutoffEnabled(false);
        visualSync.setVisualMidSyncIntervalTicks(3);
        visualSync.setVisualFarSyncIntervalTicks(17);
        visualSync.setVisualOcclusionMode(VisualOcclusionMode.PRIORITY);
        visualSync.setVisualOcclusionRaycastsPerTick(31);
        visualSync.setVisualOcclusionCacheTicks(7);
        visualSync.setEntityVisualSyncCullingEnabled(true);
        visualSync.setVisualVisibilityCullingEnabled(true);

        PhysicsSolverSettings solver = new PhysicsSolverSettings();
        solver.setSolverIterations(5);
        solver.setStabilizationIterations(1);

        PhysicsExtensionSettings extension = new PhysicsExtensionSettings();
        extension.setInt(RAPIER_SOLVER_EXTENSION_ID,
            RAPIER_INTERNAL_PGS_ITERATIONS,
            2);
        extension.setInt(RAPIER_SOLVER_EXTENSION_ID,
            RAPIER_MIN_ISLAND_SIZE,
            64);

        PhysicsVisualMaterializationSettings visualMaterialization =
            new PhysicsVisualMaterializationSettings();
        visualMaterialization.setDetachedVisualMaterializationEnabled(true);
        visualMaterialization.setDetachedVisualDematerializationRadius(72);
        visualMaterialization.setDetachedVisualMaterializationRadius(48);
        visualMaterialization.setDetachedVisualMaxSpawnsPerTick(33);
        visualMaterialization.setDetachedVisualMaxMaterialized(444);
        visualMaterialization.setDetachedVisualBlockType("Rock_Stone");
        return new PopulatedSettings(chunkCollision,
            visualSync,
            solver,
            extension,
            visualMaterialization);
    }

    private static void applyPopulatedSettings(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull PopulatedSettings settings) {
        PhysicsSpaces.putChunkCollisionSettings(store,
            spaceId,
            settings.chunkCollisionSettings());
        PhysicsSpaces.putVisualSyncSettings(store, spaceId, settings.visualSyncSettings());
        PhysicsSpaces.putSolverSettings(store, spaceId, settings.solverSettings());
        PhysicsSpaces.putExtensionSettings(store, spaceId, settings.extensionSettings());
        PhysicsSpaces.putVisualMaterializationSettings(store,
            spaceId,
            settings.visualMaterializationSettings());
    }

    private static Store<PhysicsStore> physicsStore(@Nonnull World world) {
        return PhysicsStoreCrucibleSupport.physicsStore(world);
    }

    private record PopulatedSettings(
        @Nonnull PhysicsChunkCollisionSettings chunkCollisionSettings,
        @Nonnull PhysicsVisualSyncSettings visualSyncSettings,
        @Nonnull PhysicsSolverSettings solverSettings,
        @Nonnull PhysicsExtensionSettings extensionSettings,
        @Nonnull PhysicsVisualMaterializationSettings visualMaterializationSettings) {
    }

    private record PopulatedBodyCleanupState(@Nonnull World world,
                                             @Nonnull Store<PhysicsStore> store,
                                             @Nonnull SpaceId spaceId,
                                             @Nonnull Ref<PhysicsStore> spaceRef,
                                             @Nonnull Ref<PhysicsStore> bodyRef) {
    }

    private static boolean stepSpaceDoesNotThrow() {
        PhysicsBackendRuntime runtime = Impulse.createRuntime(CrucibleBackends.requireBackendId());
        int spaceId = runtime.createSpace(SpaceId.next());
        try {
            runtime.setGravity(spaceId, 0f, -9.81f, 0f);
            createBox(runtime, spaceId, 1.0f, PhysicsBodyType.DYNAMIC, 0f, 10f, 0f);
            runtime.step(spaceId, 1f / 60f);
            return true;
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static boolean dynamicBodyFalls() {
        PhysicsBackendRuntime runtime = Impulse.createRuntime(CrucibleBackends.requireBackendId());
        int spaceId = runtime.createSpace(SpaceId.next());
        try {
            runtime.setGravity(spaceId, 0f, -9.81f, 0f);
            long bodyId = createBox(runtime, spaceId, 1.0f, PhysicsBodyType.DYNAMIC, 0f, 20f, 0f);

            float dt = 1f / 60f;
            for (int i = 0; i < 60; i++) {
                runtime.step(spaceId, dt);
            }

            PhysicsBodySnapshot snapshot = requireSnapshot(runtime, spaceId, bodyId);
            float yAfter = snapshot.positionY();
            float vy = snapshot.linearVelocityY();
            return yAfter < 20f && vy < 0f;
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static boolean staticBodyDoesNotMove() {
        PhysicsBackendRuntime runtime = Impulse.createRuntime(CrucibleBackends.requireBackendId());
        int spaceId = runtime.createSpace(SpaceId.next());
        try {
            runtime.setGravity(spaceId, 0f, -9.81f, 0f);
            long bodyId = createBox(runtime, spaceId, 0.0f, PhysicsBodyType.STATIC, 0f, 10f, 0f);

            float dt = 1f / 60f;
            for (int i = 0; i < 60; i++) {
                runtime.step(spaceId, dt);
            }

            float yAfter = requireSnapshot(runtime, spaceId, bodyId).positionY();
            return Math.abs(yAfter - 10f) < 0.01f;
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static boolean bodyLandsOnGroundPlane() {
        PhysicsBackendRuntime runtime = Impulse.createRuntime(CrucibleBackends.requireBackendId());
        int spaceId = runtime.createSpace(SpaceId.next());
        try {
            runtime.setGravity(spaceId, 0f, -9.81f, 0f);
            createPlane(runtime, spaceId, 0f);

            long bodyId = createBox(runtime, spaceId, 1.0f, PhysicsBodyType.DYNAMIC, 0f, 5f, 0f);

            float dt = 1f / 60f;
            for (int i = 0; i < 300; i++) {
                runtime.step(spaceId, dt);
            }

            float yAfter = requireSnapshot(runtime, spaceId, bodyId).positionY();
            return yAfter < 3f && yAfter > -0.5f;
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static boolean bodySettlesWithinTolerance() {
        PhysicsBackendRuntime runtime = Impulse.createRuntime(CrucibleBackends.requireBackendId());
        int spaceId = runtime.createSpace(SpaceId.next());
        try {
            runtime.setGravity(spaceId, 0f, -9.81f, 0f);
            createPlane(runtime, spaceId, 0f);

            long bodyId = createBox(runtime, spaceId, 1.0f, PhysicsBodyType.DYNAMIC, 0f, 2f, 0f);

            float dt = 1f / 60f;
            for (int i = 0; i < 300; i++) {
                runtime.step(spaceId, dt);
            }

            float speed = requireSnapshot(runtime, spaceId, bodyId).linearVelocity().length();
            return speed < 0.5f;
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    private static boolean raycastHitsGroundPlane() {
        PhysicsBackendRuntime runtime = Impulse.createRuntime(CrucibleBackends.requireBackendId());
        int spaceId = runtime.createSpace(SpaceId.next());
        try {
            createPlane(runtime, spaceId, 0f);

            return runtime.raycastAll(spaceId, 0f, 10f, 0f, 0f, -10f, 0f, (_, _, _, _, _, _, _, _, _) -> {
            }) > 0;
        } finally {
            runtime.destroySpace(spaceId);
        }
    }

    @Nonnull
    private static PhysicsBodySnapshot requireSnapshot(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        long bodyId) {
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshots.read(runtime, spaceId, bodyId);
        if (snapshot == null) {
            throw new IllegalStateException("Missing backend snapshot for body " + bodyId);
        }
        return snapshot;
    }

    private static long createBox(@Nonnull PhysicsBackendRuntime runtime,
        int spaceId,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ) {
        return runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            mass,
            BackendRuntimeCodes.bodyTypeCode(bodyType),
            positionX,
            positionY,
            positionZ,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    private static long createPlane(@Nonnull PhysicsBackendRuntime runtime, int spaceId, float groundY) {
        return runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_PLANE,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            groundY,
            0.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.STATIC),
            0.0f,
            groundY,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }
}
