package dev.hytalemodding.impulse.core.internal.crucible;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
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
                CrucibleTestCase.sync("test backend selectable", () ->
                    {
                        CrucibleBackends.requireBackendId();
                        return true;
                    },
                    "No backend id is available for Crucible tests"),
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
        BackendId backendId = CrucibleBackends.requireBackendId();
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
        resource.runOnPhysicsOwner("populate crucible physics space", access -> {
            PhysicsSpace space = access.requireSpace(spaceId);
            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            body.setPosition(0f, 5f, 0f);
            space.addBody(body);
        });

        resource.clearAllSpaces("crucible");
        return resource.getSpaceIds().isEmpty();
    }

    private static boolean detachedUnregisterRemovesBackendBody() {
        PhysicsWorldResource resource = new PhysicsWorldRuntimeResource();
        SpaceId spaceId = resource.createSpace(CrucibleBackends.requireBackendId(),
            "crucible",
            PhysicsSpaceSettings.defaults());
        try {
            PhysicsBodyId bodyId = resource.callOnPhysicsOwner("register crucible detached body", access -> {
                PhysicsSpace space = access.requireSpace(spaceId);
                PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                body.setPosition(0f, 5f, 0f);
                return access.addBody(spaceId,
                    body,
                    PhysicsBodyKind.BODY,
                    PhysicsBodyPersistenceMode.RUNTIME_ONLY);
            });

            resource.destroyBody(bodyId);
            boolean spaceEmpty = resource.callOnPhysicsOwner("count crucible detached space",
                access -> access.requireSpace(spaceId).bodyCount() == 0);
            return spaceEmpty && resource.getBodyRegistrationViews().isEmpty();
        } finally {
            resource.clearAllSpaces("crucible");
        }
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
        settings.getSolverSettings().setInternalPgsIterations(2);
        settings.getSolverSettings().setStabilizationIterations(1);
        settings.getSolverSettings().setMinIslandSize(64);
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
                && copy.getSolverSettings().getInternalPgsIterations() == 2
                && copy.getSolverSettings().getStabilizationIterations() == 1
                && copy.getSolverSettings().getMinIslandSize() == 64
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
        BackendId backendId = CrucibleBackends.requireBackendId();
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
        BackendId backendId = CrucibleBackends.requireBackendId();
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
        BackendId backendId = CrucibleBackends.requireBackendId();
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
        BackendId backendId = CrucibleBackends.requireBackendId();
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
        BackendId backendId = CrucibleBackends.requireBackendId();
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
        BackendId backendId = CrucibleBackends.requireBackendId();
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
