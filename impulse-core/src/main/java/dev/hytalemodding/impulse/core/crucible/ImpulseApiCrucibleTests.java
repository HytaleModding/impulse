package dev.hytalemodding.impulse.core.crucible;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.voxel.WorldCollisionMode;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.joml.Vector3f;

/**
 * Crucible suites that exercise Impulse API behavior inside a live Hytale server.
 */
final class ImpulseApiCrucibleTests {

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
                    Collection<PhysicsBackend> backends = Impulse.getBackends();
                    return !backends.isEmpty();
                }, "No physics backends registered"),
                CrucibleTestCase.sync("default backend", () ->
                    ImpulsePlugin.get().getDefaultBackendId() != null,
                    "Default backend id is null"),
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
                CrucibleTestCase.sync("default space is explicit",
                    ImpulseApiCrucibleTests::defaultSpaceIsExplicit,
                    "Fresh PhysicsWorldResource unexpectedly has a default space"),
                CrucibleTestCase.sync("create default space",
                    ImpulseApiCrucibleTests::createDefaultSpaceLifecycle,
                    "Explicit default space was not registered correctly"),
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
        BackendId backendId = ImpulsePlugin.get().getDefaultBackendId();
        PhysicsSpace space = Impulse.createSpace(backendId);
        try {
            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            body.setPosition(0f, 10f, 0f);
            space.addBody(body);
            return space.bodyCount() == 1;
        } finally {
            space.close();
        }
    }

    private static boolean defaultSpaceIsExplicit() {
        PhysicsWorldResource resource = new PhysicsWorldResource();
        return resource.getDefaultSpaceId() == null && resource.getDefaultSpace() == null;
    }

    private static boolean createDefaultSpaceLifecycle() {
        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsSpace space = resource.createSpace(ImpulsePlugin.get().getDefaultBackendId(),
            "crucible",
            PhysicsSpaceSettings.streamingWorldCollision(),
            true);
        try {
            SpaceId spaceId = space.getId();
            return resource.getDefaultSpaceId().equals(spaceId)
                && resource.getDefaultSpace() == space
                && resource.getSpace(spaceId) == space
                && resource.getSpaceSettings(spaceId).getWorldCollisionMode()
                == WorldCollisionMode.STREAMING;
        } finally {
            resource.clearAllSpaces("crucible");
        }
    }

    private static boolean clearPopulatedSpaces() {
        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsSpace space = resource.createSpace(ImpulsePlugin.get().getDefaultBackendId(),
            "crucible",
            PhysicsSpaceSettings.defaults(),
            true);
        PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        body.setPosition(0f, 5f, 0f);
        space.addBody(body);

        resource.clearAllSpaces("crucible");
        return resource.getSpaces().isEmpty()
            && resource.getDefaultSpaceId() == null
            && resource.getDefaultSpace() == null;
    }

    private static boolean detachedUnregisterRemovesBackendBody() {
        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsSpace space = resource.createSpace(ImpulsePlugin.get().getDefaultBackendId(),
            "crucible",
            PhysicsSpaceSettings.defaults(),
            true);
        try {
            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            body.setPosition(0f, 5f, 0f);
            space.addBody(body);
            resource.registerDetachedBody(body, space.getId());

            resource.unregisterBody(body, true);
            return space.bodyCount() == 0 && resource.getDetachedBodies().isEmpty();
        } finally {
            resource.clearAllSpaces("crucible");
        }
    }

    private static boolean settingsRoundTrip() {
        PhysicsWorldResource resource = new PhysicsWorldResource();
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.setWorldCollisionMode(WorldCollisionMode.STREAMING);
        settings.setWorldCollisionRadius(9);
        settings.setWorldCollisionBodyRadius(5);
        settings.setWorldCollisionTtlTicks(77);
        settings.setVisualMaxSyncRadius(96);
        settings.setVisualFullSyncRadius(48);
        settings.setVisualFarSyncCutoffEnabled(false);
        settings.setVisualMidSyncIntervalTicks(3);
        settings.setVisualFarSyncIntervalTicks(17);
        settings.setVisualOcclusionMode(VisualOcclusionMode.PRIORITY);
        settings.setVisualOcclusionRaycastsPerTick(31);
        settings.setVisualOcclusionCacheTicks(7);
        settings.setSolverIterations(5);
        settings.setInternalPgsIterations(2);
        settings.setStabilizationIterations(1);
        settings.setMinIslandSize(64);
        settings.setEntityVisualSyncCullingEnabled(true);
        settings.setVisualVisibilityCullingEnabled(true);
        settings.setDetachedVisualMaterializationEnabled(true);
        settings.setDetachedVisualDematerializationRadius(72);
        settings.setDetachedVisualMaterializationRadius(48);
        settings.setDetachedVisualMaxSpawnsPerTick(33);
        settings.setDetachedVisualMaxMaterialized(444);
        settings.setDetachedVisualBlockType("Rock_Stone");

        PhysicsSpace space = resource.createSpace(ImpulsePlugin.get().getDefaultBackendId(),
            "crucible",
            settings,
            true);
        try {
            PhysicsSpaceSettings copy = resource.getSpaceSettings(space.getId());
            return copy.getWorldCollisionMode() == WorldCollisionMode.STREAMING
                && copy.getWorldCollisionRadius() == 9
                && copy.getWorldCollisionBodyRadius() == 5
                && copy.getWorldCollisionTtlTicks() == 77
                && copy.getVisualFullSyncRadius() == 48
                && copy.getVisualMaxSyncRadius() == 96
                && !copy.isVisualFarSyncCutoffEnabled()
                && copy.getVisualMidSyncIntervalTicks() == 3
                && copy.getVisualFarSyncIntervalTicks() == 17
                && copy.getVisualOcclusionMode() == VisualOcclusionMode.PRIORITY
                && copy.getVisualOcclusionRaycastsPerTick() == 31
                && copy.getVisualOcclusionCacheTicks() == 7
                && copy.getSolverIterations() == 5
                && copy.getInternalPgsIterations() == 2
                && copy.getStabilizationIterations() == 1
                && copy.getMinIslandSize() == 64
                && copy.isEntityVisualSyncCullingEnabled()
                && copy.isVisualVisibilityCullingEnabled()
                && copy.isDetachedVisualMaterializationEnabled()
                && copy.getDetachedVisualMaterializationRadius() == 48
                && copy.getDetachedVisualDematerializationRadius() == 72
                && copy.getDetachedVisualMaxSpawnsPerTick() == 33
                && copy.getDetachedVisualMaxMaterialized() == 444
                && "Rock_Stone".equals(copy.getDetachedVisualBlockType());
        } finally {
            resource.clearAllSpaces("crucible");
        }
    }

    private static boolean stepSpaceDoesNotThrow() {
        BackendId backendId = ImpulsePlugin.get().getDefaultBackendId();
        PhysicsSpace space = Impulse.createSpace(backendId);
        try {
            space.setGravity(0f, -9.81f, 0f);
            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            body.setPosition(0f, 10f, 0f);
            space.addBody(body);
            space.step(1f / 60f);
            return true;
        } finally {
            space.close();
        }
    }

    private static boolean dynamicBodyFalls() {
        BackendId backendId = ImpulsePlugin.get().getDefaultBackendId();
        PhysicsSpace space = Impulse.createSpace(backendId);
        try {
            space.setGravity(0f, -9.81f, 0f);
            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            body.setPosition(0f, 20f, 0f);
            space.addBody(body);

            float dt = 1f / 60f;
            for (int i = 0; i < 60; i++) {
                space.step(dt);
            }

            float yAfter = body.getPosition().y;
            float vy = body.getLinearVelocity().y;
            return yAfter < 20f && vy < 0f;
        } finally {
            space.close();
        }
    }

    private static boolean staticBodyDoesNotMove() {
        BackendId backendId = ImpulsePlugin.get().getDefaultBackendId();
        PhysicsSpace space = Impulse.createSpace(backendId);
        try {
            space.setGravity(0f, -9.81f, 0f);
            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 0f);
            body.setPosition(0f, 10f, 0f);
            space.addBody(body);

            float dt = 1f / 60f;
            for (int i = 0; i < 60; i++) {
                space.step(dt);
            }

            float yAfter = body.getPosition().y;
            return Math.abs(yAfter - 10f) < 0.01f;
        } finally {
            space.close();
        }
    }

    private static boolean bodyLandsOnGroundPlane() {
        BackendId backendId = ImpulsePlugin.get().getDefaultBackendId();
        PhysicsSpace space = Impulse.createSpace(backendId);
        try {
            space.setGravity(0f, -9.81f, 0f);
            PhysicsBody ground = space.createStaticPlane(0f);
            space.addBody(ground);

            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            body.setPosition(0f, 5f, 0f);
            space.addBody(body);

            float dt = 1f / 60f;
            for (int i = 0; i < 300; i++) {
                space.step(dt);
            }

            float yAfter = body.getPosition().y;
            return yAfter < 3f && yAfter > -0.5f;
        } finally {
            space.close();
        }
    }

    private static boolean bodySettlesWithinTolerance() {
        BackendId backendId = ImpulsePlugin.get().getDefaultBackendId();
        PhysicsSpace space = Impulse.createSpace(backendId);
        try {
            space.setGravity(0f, -9.81f, 0f);
            PhysicsBody ground = space.createStaticPlane(0f);
            space.addBody(ground);

            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            body.setPosition(0f, 2f, 0f);
            space.addBody(body);

            float dt = 1f / 60f;
            for (int i = 0; i < 300; i++) {
                space.step(dt);
            }

            float speed = body.getLinearVelocity().length();
            return speed < 0.5f;
        } finally {
            space.close();
        }
    }

    private static boolean raycastHitsGroundPlane() {
        BackendId backendId = ImpulsePlugin.get().getDefaultBackendId();
        PhysicsSpace space = Impulse.createSpace(backendId);
        try {
            PhysicsBody ground = space.createStaticPlane(0f);
            space.addBody(ground);

            var hits = space.raycastAll(
                new Vector3f(0f, 10f, 0f),
                new Vector3f(0f, -10f, 0f));

            return !hits.isEmpty();
        } finally {
            space.close();
        }
    }
}
