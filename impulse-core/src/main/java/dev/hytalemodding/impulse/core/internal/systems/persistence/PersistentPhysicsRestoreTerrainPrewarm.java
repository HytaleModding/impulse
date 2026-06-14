package dev.hytalemodding.impulse.core.internal.systems.persistence;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionBuildOptions;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsBodyState;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

final class PersistentPhysicsRestoreTerrainPrewarm {

    private PersistentPhysicsRestoreTerrainPrewarm() {
    }

    static void prewarmRestoredDynamicBodyTerrain(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PhysicsWorldRuntimeResource runtime,
        @Nonnull PersistentPhysicsWorldResource persistent,
        long tick) {
        if (!WorldCollisionLifecycle.isEnabled()) {
            return;
        }
        for (Map.Entry<Integer, List<PersistentPhysicsBodyState>> entry : dynamicBodiesBySpace(
            persistent.getBodies()).entrySet()) {
            SpaceId spaceId = new SpaceId(entry.getKey());
            if (runtime.getSpaceBinding(spaceId) == null) {
                continue;
            }
            PhysicsWorldCollisionSettings settings =
                runtime.getLiveSpaceSettings(spaceId).getWorldCollisionSettings();
            if (settings.getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
                continue;
            }
            List<Vector3d> targets = dynamicPrewarmTargets(entry.getValue(),
                settings.getWorldCollisionBodyRadius());
            if (targets.isEmpty()) {
                continue;
            }
            UUID spaceUuid = physicsStore(world)
                .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
                .getSpaceUuid(spaceId);
            if (spaceUuid == null) {
                continue;
            }
            store.getResource(PhysicsStoreWorldCollisionStreamingResource.getResourceType())
                .ensureAround(world,
                    spaceUuid,
                    terrainMutationQueue(world),
                    targets,
                    settings.getWorldCollisionBodyRadius(),
                    tick,
                    null,
                    WorldCollisionBuildOptions.fromSettings(settings));
        }
    }

    @Nonnull
    private static Store<PhysicsStore> physicsStore(@Nonnull World world) {
        Store<PhysicsStore> store = ((PhysicsStoreWorld) world).getPhysicsStore().getStore();
        PhysicsStoreThreading.requireWorldThread(store,
            "read PhysicsStore restore terrain prewarm state");
        return store;
    }

    @Nonnull
    private static PhysicsTerrainMutationQueueResource terrainMutationQueue(@Nonnull World world) {
        return physicsStore(world).getResource(PhysicsTerrainMutationQueueResource.getResourceType());
    }

    @Nonnull
    static Map<Integer, List<Vector3d>> dynamicPrewarmTargetsBySpace(
        @Nonnull PersistentPhysicsBodyState[] bodies,
        int radius) {
        Map<Integer, List<Vector3d>> targetsBySpace = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<PersistentPhysicsBodyState>> entry : dynamicBodiesBySpace(bodies).entrySet()) {
            targetsBySpace.put(entry.getKey(), dynamicPrewarmTargets(entry.getValue(), radius));
        }
        return targetsBySpace;
    }

    @Nonnull
    private static Map<Integer, List<PersistentPhysicsBodyState>> dynamicBodiesBySpace(
        @Nonnull PersistentPhysicsBodyState[] bodies) {
        Map<Integer, List<PersistentPhysicsBodyState>> bodiesBySpace = new LinkedHashMap<>();
        for (PersistentPhysicsBodyState body : bodies) {
            if (body.restoreValidationFailureReason() != null
                || body.resolveSpaceId() <= 0
                || body.getBodyType() != PhysicsBodyType.DYNAMIC
                || body.isSensor()) {
                continue;
            }
            bodiesBySpace.computeIfAbsent(body.resolveSpaceId(), ignored -> new ArrayList<>()).add(body);
        }
        return bodiesBySpace;
    }

    @Nonnull
    private static List<Vector3d> dynamicPrewarmTargets(@Nonnull List<PersistentPhysicsBodyState> bodies,
        int radius) {
        List<Vector3d> targets = new ArrayList<>();
        for (PersistentPhysicsBodyState body : bodies) {
            addBodyTargets(targets,
                body,
                radius);
        }
        return targets;
    }

    private static void addBodyTargets(@Nonnull List<Vector3d> targets,
        @Nonnull PersistentPhysicsBodyState body,
        int radius) {
        Vector3f position = body.getPosition();
        Vector3f velocity = body.getLinearVelocity();
        if (!Float.isFinite(velocity.y) || velocity.y >= 0.0f) {
            targets.add(new Vector3d(position.x, position.y, position.z));
            return;
        }

        double minCenterY = Math.min(position.y, Math.max(0, radius));
        double step = Math.max(1.0, Math.max(0, radius) * 2.0);
        double lastY = Double.NaN;
        for (double y = position.y; y >= minCenterY; y -= step) {
            targets.add(new Vector3d(position.x, y, position.z));
            lastY = y;
        }
        if (Double.isNaN(lastY) || lastY > minCenterY) {
            targets.add(new Vector3d(position.x, minCenterY, position.z));
        }
    }
}
