package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.voxel.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.voxel.WorldVoxelCollisionCache.DebugSection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Renders Impulse debug overlays on a throttled cadence for subscribed viewers.
 *
 * <p>This avoids the default broadcast behavior used by Hytale's DebugUtils,
 * which sends every primitive to every player and can cause packet spikes and
 * visible flicker when overlays are redrawn every tick.</p>
 */
public class PhysicsDebugSystem extends TickingSystem<ChunkStore> {

    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entityStore =
            world.getEntityStore().getStore();
        PhysicsWorldResource resource = entityStore.getResource(
            PhysicsWorldResource.getResourceType());
        PhysicsDebugResource debug = entityStore.getResource(PhysicsDebugResource.getResourceType());

        if (!debug.hasSubscribers()) {
            return;
        }

        List<PlayerRef> viewers = resolveSubscribers(world, debug);
        if (viewers.isEmpty()) {
            return;
        }

        boolean overlayDue = debug.tickOverlayBudget(dt);
        boolean worldCollisionDue = debug.tickWorldCollisionBudget(dt);
        if (!overlayDue && !worldCollisionDue) {
            return;
        }

        boolean debugShapes = debug.isDebugShapesEnabled();
        boolean debugMotion = debug.isDebugMotionEnabled();
        boolean debugContacts = debug.isDebugContactsEnabled();
        boolean debugJoints = debug.isDebugJointsEnabled();
        boolean debugWorldCollision = debug.isDebugWorldCollisionEnabled();
        if (!debugShapes && !debugContacts && !debugJoints && !debugWorldCollision) {
            return;
        }

        float overlayLifetime = PhysicsDebugRenderer.lifetimeForRefresh(debug.getOverlayRefreshSeconds());
        float worldCollisionLifetime = PhysicsDebugRenderer.lifetimeForRefresh(
            debug.getWorldCollisionRefreshSeconds());

        for (PlayerRef viewer : viewers) {
            Vector3d viewerPosition = new Vector3d(viewer.getTransform().getPosition());
            List<PlayerRef> target = List.of(viewer);

            if (overlayDue && (debugShapes || debugMotion)) {
                renderEntityBodies(target,
                    entityStore,
                    resource,
                    viewerPosition,
                    debug.getViewRadius(),
                    debugShapes,
                    debugMotion,
                    debug.getMaxBodies(),
                    overlayLifetime);
            }

            for (PhysicsSpace space : resource.iterateSpaces()) {
                if (overlayDue && debugShapes) {
                    renderSpaceOnlyShapes(target, space, overlayLifetime);
                }
                if (overlayDue && debugContacts) {
                    renderContacts(target,
                        space,
                        viewerPosition,
                        debug.getViewRadius(),
                        debug.getMaxContacts(),
                        overlayLifetime);
                }
                if (overlayDue && debugJoints) {
                    renderJoints(target,
                        space,
                        viewerPosition,
                        debug.getViewRadius(),
                        debug.getMaxJoints(),
                        overlayLifetime);
                }
                if (worldCollisionDue && debugWorldCollision) {
                    renderWorldCollision(target,
                        resource,
                        space,
                        viewerPosition,
                        debug.getViewRadius(),
                        debug.getMaxWorldCollisionSections(),
                        debug.getMaxWorldCollisionBoxes(),
                        worldCollisionLifetime);
                }
            }
        }
    }

    @Nonnull
    private static List<PlayerRef> resolveSubscribers(@Nonnull World world,
        @Nonnull PhysicsDebugResource debug) {
        Set<UUID> active = new HashSet<>(debug.getSubscriberUuids());
        List<PlayerRef> viewers = new ArrayList<>();
        for (PlayerRef player : world.getPlayerRefs()) {
            if (active.remove(player.getUuid())) {
                viewers.add(player);
            }
        }

        for (UUID stale : active) {
            debug.removeSubscriber(stale);
        }
        return viewers;
    }

    private static void renderEntityBodies(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        boolean debugShapes,
        boolean debugMotion,
        int maxBodies,
        float time) {
        int rendered = 0;
        double maxDistanceSquared = viewRadius * viewRadius;
        for (Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> owner
            : resource.getBodyOwners()) {
            if (!owner.isValid()) {
                continue;
            }

            TransformComponent transform = store.getComponent(owner, TransformComponent.getComponentType());
            PhysicsBodyComponent component = store.getComponent(owner, PhysicsBodyComponent.getComponentType());
            if (transform == null || component == null) {
                continue;
            }

            PhysicsBody body = component.getBody();
            Vector3d center = PhysicsDebugRenderer.centerFromSyncedTransform(body,
                transform.getPosition());
            if (viewerPosition.distanceSquared(center) > maxDistanceSquared) {
                continue;
            }

            Quaterniond rotation = transform.getRotation().getQuaternion(new Quaterniond());
            if (debugShapes) {
                PhysicsDebugRenderer.renderBodyShape(viewers, body, center, rotation, time);
            }
            if (debugMotion) {
                PhysicsDebugRenderer.renderBodyMotion(viewers, body, center, time);
            }

            rendered++;
            if (rendered >= maxBodies) {
                return;
            }
        }
    }

    private static void renderSpaceOnlyShapes(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsSpace space,
        float time) {
        for (PhysicsBody body : space.getBodies()) {
            if (body.getShapeType() != ShapeType.PLANE) {
                continue;
            }

            Vector3f pos = body.getPosition();
            PhysicsDebugRenderer.renderBodyShape(viewers,
                body,
                new Vector3d(pos.x, pos.y, pos.z),
                new Quaterniond(), time);
        }
    }

    private static void renderContacts(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxContacts,
        float time) {
        int rendered = 0;
        double maxDistanceSquared = viewRadius * viewRadius;
        for (PhysicsContact contact : space.getContacts()) {
            Vector3f point = contact.pointOnB();
            if (distanceSquared(viewerPosition, point) > maxDistanceSquared) {
                continue;
            }

            PhysicsDebugRenderer.renderContact(viewers, contact, time);
            rendered++;
            if (rendered >= maxContacts) {
                return;
            }
        }
    }

    private static void renderJoints(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxJoints,
        float time) {
        int rendered = 0;
        double maxDistanceSquared = viewRadius * viewRadius;
        for (PhysicsJoint joint : space.getJoints()) {
            Vector3f a = joint.getBodyA().getPosition();
            Vector3f b = joint.getBodyB().getPosition();
            Vector3d midpoint = new Vector3d((a.x + b.x) * 0.5,
                (a.y + b.y) * 0.5,
                (a.z + b.z) * 0.5);
            if (viewerPosition.distanceSquared(midpoint) > maxDistanceSquared) {
                continue;
            }

            PhysicsDebugRenderer.renderJoint(viewers, joint, time);
            rendered++;
            if (rendered >= maxJoints) {
                return;
            }
        }
    }

    private static void renderWorldCollision(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxSections,
        int maxBoxes,
        float time) {
        int[] sectionCount = {0};
        int[] boxCount = {0};
        double maxDistanceSquared = viewRadius * viewRadius;
        resource.getWorldVoxelCollisionCache().forEachDebugSection(space.getId(), section -> {
            Vector3d center = sectionCenter(section);
            if (viewerPosition.distanceSquared(center) > maxDistanceSquared) {
                return;
            }

            if (sectionCount[0] < maxSections) {
                PhysicsDebugRenderer.renderWorldCollisionSection(viewers,
                    section.chunkX(),
                    section.sectionY(),
                    section.chunkZ(),
                    section.voxelTerrain(),
                    time);
            }
            sectionCount[0]++;
            boxCount[0] = renderWorldCollisionBoxes(viewers,
                viewerPosition,
                maxDistanceSquared,
                maxBoxes,
                section,
                boxCount[0],
                time);
        });
    }

    private static int renderWorldCollisionBoxes(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d viewerPosition,
        double maxDistanceSquared,
        int maxBoxes,
        @Nonnull DebugSection section,
        int boxCount,
        float time) {
        boxCount = renderWorldCollisionBoxes(viewers,
            viewerPosition,
            maxDistanceSquared,
            maxBoxes,
            section.fullCubeBoxes(),
            DebugUtils.COLOR_CYAN,
            boxCount,
            time);
        return renderWorldCollisionBoxes(viewers,
            viewerPosition,
            maxDistanceSquared,
            maxBoxes,
            section.detailBoxes(),
            DebugUtils.COLOR_MAGENTA,
            boxCount,
            time);
    }

    private static int renderWorldCollisionBoxes(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d viewerPosition,
        double maxDistanceSquared,
        int maxBoxes,
        @Nonnull Iterable<BoxCollider> boxes,
        @Nonnull Vector3f color,
        int boxCount,
        float time) {
        for (BoxCollider box : boxes) {
            if (boxCount >= maxBoxes) {
                return boxCount;
            }
            Vector3d boxCenter = new Vector3d(box.centerX(), box.centerY(), box.centerZ());
            if (viewerPosition.distanceSquared(boxCenter) > maxDistanceSquared) {
                continue;
            }

            PhysicsDebugRenderer.renderWorldCollisionBox(viewers, box, color, time);
            boxCount++;
        }
        return boxCount;
    }

    private static double distanceSquared(@Nonnull Vector3d point,
        @Nonnull Vector3f target) {
        double dx = point.x - target.x;
        double dy = point.y - target.y;
        double dz = point.z - target.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Nonnull
    private static Vector3d sectionCenter(@Nonnull DebugSection section) {
        double half = 8.0;
        return new Vector3d((section.chunkX() << 4) + half,
            (section.sectionY() << 4) + half,
            (section.chunkZ() << 4) + half);
    }
}
