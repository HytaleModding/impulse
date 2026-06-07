package dev.hytalemodding.impulse.core.internal.crucible;

import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshots;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SpaceBodyCountQuery;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

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
                CrucibleTestCase.asyncResult("world collision subplugin load/unload/reload",
                    ignored -> WorldCollisionSubPluginCrucibleSupport.loadUnloadReloadSmokeAsync(),
                    "World collision subplugin lifecycle smoke failed"),
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
                CrucibleTestCase.sync("fresh world has no spaces",
                    ImpulseApiCrucibleTests::freshWorldHasNoSpaces,
                    "Fresh PhysicsWorldResource unexpectedly has spaces"),
                CrucibleTestCase.sync("created explicit space lifecycle works",
                    ImpulseApiCrucibleTests::createdExplicitSpaceLifecycleWorks,
                    "Explicit space was not registered correctly"),
                CrucibleTestCase.sync("clear populated spaces",
                    ImpulseApiCrucibleTests::clearPopulatedSpaces,
                    "clearAllSpaces did not remove populated runtime spaces"),
                CrucibleTestCase.sync("detached unregister removes body",
                    ImpulseApiCrucibleTests::detachedUnregisterRemovesBackendBody,
                    "Detached unregister did not remove the backend body"),
                CrucibleTestCase.sync("settings round trip",
                    ImpulseApiCrucibleTests::settingsRoundTrip,
                    "PhysicsSpaceSettings did not retain runtime settings")));
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

    private static boolean freshWorldHasNoSpaces() {
        PhysicsWorldResource resource = new PhysicsWorldRuntimeResource();
        return resource.getSpaceIds().isEmpty();
    }

    private static boolean createdExplicitSpaceLifecycleWorks() {
        PhysicsWorldResource resource = new PhysicsWorldRuntimeResource();
        SpaceId spaceId = resource.createSpace(CrucibleBackends.requireBackendId(),
            "crucible",
            PhysicsSpaceSettings.streamingWorldCollision());
        try {
            return resource.hasSpace(spaceId)
                && resource.getSpaceSettings(spaceId).getWorldCollisionSettings().getWorldCollisionMode()
                == WorldCollisionMode.STREAMING;
        } finally {
            resource.clearAllSpaces("crucible");
        }
    }

    private static boolean clearPopulatedSpaces() {
        PhysicsWorldResource resource = new PhysicsWorldRuntimeResource();
        SpaceId spaceId = resource.createSpace(CrucibleBackends.requireBackendId(),
            "crucible",
            PhysicsSpaceSettings.defaults());
        submitCrucibleBox(resource, spaceId, RigidBodyKey.random());

        resource.clearAllSpaces("crucible");
        return resource.getSpaceIds().isEmpty();
    }

    private static boolean detachedUnregisterRemovesBackendBody() {
        PhysicsWorldResource resource = new PhysicsWorldRuntimeResource();
        SpaceId spaceId = resource.createSpace(CrucibleBackends.requireBackendId(),
            "crucible",
            PhysicsSpaceSettings.defaults());
        try {
            RigidBodyKey bodyKey = RigidBodyKey.random();
            submitCrucibleBox(resource, spaceId, bodyKey);

            resource.destroyBody(bodyKey);
            boolean spaceEmpty = resource.query(new SpaceBodyCountQuery(spaceId))
                .completion()
                .toCompletableFuture()
                .join() == 0;
            return spaceEmpty && resource.getBodyRegistrationViews().isEmpty();
        } finally {
            resource.clearAllSpaces("crucible");
        }
    }

    private static void submitCrucibleBox(@Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyKey) {
        resource.submitCommands(0L,
                1,
                commands -> commands.spawnBody(bodyKey, spawn -> spawn
                    .space(spaceId)
                    .shape(PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f))
                    .mass(1.0f)
                    .type(PhysicsBodyType.DYNAMIC)
                    .position(0f, 5f, 0f)
                    .settings(RigidBodySpawnSettings.defaults())
                    .kind(PhysicsBodyKind.BODY)
                    .persistence(PhysicsBodyPersistenceMode.RUNTIME_ONLY)))
            .firstRejected()
            .toCompletableFuture()
            .join()
            .ifPresent(result -> {
                throw new IllegalStateException("spawn crucible box command "
                    + result.commandSequence() + " rejected: " + result.message());
            });
    }

    private static boolean settingsRoundTrip() {
        PhysicsWorldResource resource = new PhysicsWorldRuntimeResource();
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.getWorldCollisionSettings().setWorldCollisionMode(WorldCollisionMode.STREAMING);
        settings.getWorldCollisionSettings().setWorldCollisionRadius(9);
        settings.getWorldCollisionSettings().setWorldCollisionBodyRadius(5);
        settings.getWorldCollisionSettings().setWorldCollisionTtlTicks(77);
        settings.getVisualSyncSettings().setVisualMaxSyncRadius(96);
        settings.getVisualSyncSettings().setVisualFullSyncRadius(48);
        settings.getVisualSyncSettings().setVisualFarSyncCutoffEnabled(false);
        settings.getVisualSyncSettings().setVisualMidSyncIntervalTicks(3);
        settings.getVisualSyncSettings().setVisualFarSyncIntervalTicks(17);
        settings.getVisualSyncSettings().setVisualOcclusionMode(VisualOcclusionMode.PRIORITY);
        settings.getVisualSyncSettings().setVisualOcclusionRaycastsPerTick(31);
        settings.getVisualSyncSettings().setVisualOcclusionCacheTicks(7);
        settings.getSolverSettings().setSolverIterations(5);
        settings.getSolverSettings().setStabilizationIterations(1);
        settings.getExtensionSettings().setInt(RAPIER_SOLVER_EXTENSION_ID,
            RAPIER_INTERNAL_PGS_ITERATIONS,
            2);
        settings.getExtensionSettings().setInt(RAPIER_SOLVER_EXTENSION_ID,
            RAPIER_MIN_ISLAND_SIZE,
            64);
        settings.getVisualSyncSettings().setEntityVisualSyncCullingEnabled(true);
        settings.getVisualSyncSettings().setVisualVisibilityCullingEnabled(true);
        settings.getVisualMaterializationSettings().setDetachedVisualMaterializationEnabled(true);
        settings.getVisualMaterializationSettings().setDetachedVisualDematerializationRadius(72);
        settings.getVisualMaterializationSettings().setDetachedVisualMaterializationRadius(48);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxSpawnsPerTick(33);
        settings.getVisualMaterializationSettings().setDetachedVisualMaxMaterialized(444);
        settings.getVisualMaterializationSettings().setDetachedVisualBlockType("Rock_Stone");

        SpaceId spaceId = resource.createSpace(CrucibleBackends.requireBackendId(),
            "crucible",
            settings);
        try {
            PhysicsSpaceSettings copy = resource.getSpaceSettings(spaceId);
            return copy.getWorldCollisionSettings().getWorldCollisionMode() == WorldCollisionMode.STREAMING
                && copy.getWorldCollisionSettings().getWorldCollisionRadius() == 9
                && copy.getWorldCollisionSettings().getWorldCollisionBodyRadius() == 5
                && copy.getWorldCollisionSettings().getWorldCollisionTtlTicks() == 77
                && copy.getVisualSyncSettings().getVisualFullSyncRadius() == 48
                && copy.getVisualSyncSettings().getVisualMaxSyncRadius() == 96
                && !copy.getVisualSyncSettings().isVisualFarSyncCutoffEnabled()
                && copy.getVisualSyncSettings().getVisualMidSyncIntervalTicks() == 3
                && copy.getVisualSyncSettings().getVisualFarSyncIntervalTicks() == 17
                && copy.getVisualSyncSettings().getVisualOcclusionMode() == VisualOcclusionMode.PRIORITY
                && copy.getVisualSyncSettings().getVisualOcclusionRaycastsPerTick() == 31
                && copy.getVisualSyncSettings().getVisualOcclusionCacheTicks() == 7
                && copy.getSolverSettings().getSolverIterations() == 5
                && copy.getSolverSettings().getStabilizationIterations() == 1
                && copy.getExtensionSettings()
                    .getInt(RAPIER_SOLVER_EXTENSION_ID,
                        RAPIER_INTERNAL_PGS_ITERATIONS)
                    .orElse(-1) == 2
                && copy.getExtensionSettings()
                    .getInt(RAPIER_SOLVER_EXTENSION_ID,
                        RAPIER_MIN_ISLAND_SIZE)
                    .orElse(-1) == 64
                && copy.getVisualSyncSettings().isEntityVisualSyncCullingEnabled()
                && copy.getVisualSyncSettings().isVisualVisibilityCullingEnabled()
                && copy.getVisualMaterializationSettings().isDetachedVisualMaterializationEnabled()
                && copy.getVisualMaterializationSettings().getDetachedVisualMaterializationRadius() == 48
                && copy.getVisualMaterializationSettings().getDetachedVisualDematerializationRadius() == 72
                && copy.getVisualMaterializationSettings().getDetachedVisualMaxSpawnsPerTick() == 33
                && copy.getVisualMaterializationSettings().getDetachedVisualMaxMaterialized() == 444
                && "Rock_Stone".equals(copy.getVisualMaterializationSettings().getDetachedVisualBlockType());
        } finally {
            resource.clearAllSpaces("crucible");
        }
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
        PhysicsBodySnapshot[] snapshot = new PhysicsBodySnapshot[1];
        runtime.bodySnapshot(spaceId,
            bodyId,
            (ignoredBodyId,
                shapeTypeCode,
                bodyTypeCode,
                positionX,
                positionY,
                positionZ,
                rotationX,
                rotationY,
                rotationZ,
                rotationW,
                linearVelocityX,
                linearVelocityY,
                linearVelocityZ,
                angularVelocityX,
                angularVelocityY,
                angularVelocityZ,
                sleeping,
                sensor,
                mass,
                friction,
                restitution,
                linearDamping,
                angularDamping,
                collisionGroup,
                collisionMask,
                continuousCollisionEnabled,
                centerOfMassOffsetY,
                hasBoxHalfExtents,
                halfExtentX,
                halfExtentY,
                halfExtentZ,
                radius,
                halfHeight,
                axisCode) -> snapshot[0] = PhysicsBodySnapshots.fromRuntimeFields(shapeTypeCode,
                bodyTypeCode,
                positionX,
                positionY,
                positionZ,
                rotationX,
                rotationY,
                rotationZ,
                rotationW,
                linearVelocityX,
                linearVelocityY,
                linearVelocityZ,
                angularVelocityX,
                angularVelocityY,
                angularVelocityZ,
                sleeping,
                sensor,
                mass,
                friction,
                restitution,
                linearDamping,
                angularDamping,
                collisionGroup,
                collisionMask,
                continuousCollisionEnabled,
                centerOfMassOffsetY,
                hasBoxHalfExtents,
                halfExtentX,
                halfExtentY,
                halfExtentZ,
                radius,
                halfHeight,
                axisCode));
        if (snapshot[0] == null) {
            throw new IllegalStateException("Missing backend snapshot for body " + bodyId);
        }
        return snapshot[0];
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
