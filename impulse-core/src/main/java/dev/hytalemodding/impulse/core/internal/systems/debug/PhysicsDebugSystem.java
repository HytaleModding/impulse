package dev.hytalemodding.impulse.core.internal.systems.debug;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.internal.resources.debug.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource.BodyRegistration;
import dev.hytalemodding.impulse.core.internal.voxel.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache.DebugSection;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private static final Vector3f JOINT_BODY_A_POSITION_SCRATCH = new Vector3f();
    private static final Vector3f JOINT_BODY_B_POSITION_SCRATCH = new Vector3f();

    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
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
        if (!debugShapes && !debugMotion && !debugContacts && !debugJoints
            && !debugWorldCollision) {
            return;
        }

        float overlayLifetime = PhysicsDebugRenderer.lifetimeForRefresh(
            debug.getOverlayRefreshSeconds(), dt);
        float worldCollisionLifetime = PhysicsDebugRenderer.lifetimeForRefresh(
            debug.getWorldCollisionRefreshSeconds(), dt);

        for (PlayerRef viewer : viewers) {
            Vector3d viewerPosition = new Vector3d(viewer.getTransform().getPosition());
            List<PlayerRef> target = List.of(viewer);

            if (overlayDue && (debugShapes || debugMotion)) {
                int renderedBodies = renderEntityBodies(target,
                    entityStore,
                    resource,
                    viewerPosition,
                    debug.getViewRadius(),
                    debugShapes,
                    debugMotion,
                    debug.getMaxBodies(),
                    overlayLifetime);
                renderDetachedBodies(target,
                    resource,
                    viewerPosition,
                    debug.getViewRadius(),
                    debugShapes,
                    debugMotion,
                    Math.max(0, debug.getMaxBodies() - renderedBodies),
                    overlayLifetime);
            }

            boolean liveBackendReadable = resource.canAccessLiveBackendDirectly();
            for (PhysicsSpace space : resource.getSpaces()) {
                if (overlayDue && debugShapes) {
                    renderSpaceOnlyShapes(target, resource, space, overlayLifetime);
                }
                if (overlayDue && debugContacts && liveBackendReadable) {
                    renderContacts(target,
                        space,
                        viewerPosition,
                        debug.getViewRadius(),
                        debug.getMaxContacts(),
                        overlayLifetime);
                }
                if (overlayDue && debugJoints && liveBackendReadable) {
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
        Set<UUID> active = new ObjectOpenHashSet<>(debug.getSubscriberUuids());
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

    private static int renderEntityBodies(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        boolean debugShapes,
        boolean debugMotion,
        int maxBodies,
        float time) {
        int rendered = 0;
        if (maxBodies <= 0) {
            return 0;
        }
        double maxDistanceSquared = viewRadius * viewRadius;
        for (BodyRegistration registration : resource.getBodyRegistrations(PhysicsBodyKind.BODY)) {
            if (resource.getBodyAttachments(registration.id()).isEmpty()) {
                continue;
            }

            for (Ref<EntityStore> attachmentRef : resource.getBodyAttachments(registration.id())) {
                if (!attachmentRef.isValid()) {
                    continue;
                }
                PhysicsBodyAttachmentComponent attachment = store.getComponent(attachmentRef,
                    ATTACHMENT_TYPE);
                TransformComponent transform = store.getComponent(attachmentRef, TRANSFORM_TYPE);
                if (attachment == null
                    || attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY
                    || transform == null) {
                    continue;
                }

                PhysicsBody body = registration.body();
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
                    return rendered;
                }
                break;
            }
        }
        return rendered;
    }

    private static int renderDetachedBodies(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        boolean debugShapes,
        boolean debugMotion,
        int maxBodies,
        float time) {
        if (maxBodies <= 0) {
            return 0;
        }

        int[] rendered = {0};
        double maxDistanceSquared = viewRadius * viewRadius;
        for (PhysicsSpace space : resource.getSpaces()) {
            resource.forEachBodySnapshot(space.getId(), entry -> {
                if (rendered[0] >= maxBodies) {
                    return;
                }

                BodyRegistration registration = entry.registration();
                if (registration.kind() != PhysicsBodyKind.BODY
                    || !resource.getBodyAttachments(registration.id()).isEmpty()) {
                    return;
                }

                PhysicsBodySnapshot snapshot = entry.snapshot();
                Vector3f position = snapshot.position();
                Vector3d center = new Vector3d(position.x, position.y, position.z);
                if (viewerPosition.distanceSquared(center) > maxDistanceSquared) {
                    return;
                }

                PhysicsBody body = snapshot.body();
                Quaterniond rotation = new Quaterniond(snapshot.rotation().x,
                    snapshot.rotation().y,
                    snapshot.rotation().z,
                    snapshot.rotation().w);
                if (debugShapes) {
                    PhysicsDebugRenderer.renderBodyShape(viewers, body, center, rotation, time);
                }
                if (debugMotion) {
                    PhysicsDebugRenderer.renderBodyMotion(viewers, body, center, time);
                }
                rendered[0]++;
            });
            if (rendered[0] >= maxBodies) {
                break;
            }
        }
        return rendered[0];
    }

    private static void renderSpaceOnlyShapes(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        float time) {
        resource.forEachBodySnapshot(space.getId(), entry -> {
            PhysicsBodySnapshot snapshot = entry.snapshot();
            PhysicsBody body = snapshot.body();
            if (body.getShapeType() != ShapeType.PLANE) {
                return;
            }

            Vector3f position = snapshot.position();
            Quaterniond rotation = new Quaterniond(snapshot.rotation().x,
                snapshot.rotation().y,
                snapshot.rotation().z,
                snapshot.rotation().w);
            PhysicsDebugRenderer.renderBodyShape(viewers,
                body,
                new Vector3d(position.x, position.y, position.z),
                rotation,
                time);
        });
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
        int[] rendered = {0};
        double maxDistanceSquared = viewRadius * viewRadius;
        space.forEachJoint(joint -> {
            if (rendered[0] >= maxJoints) {
                return;
            }
            joint.getBodyA().getPosition(JOINT_BODY_A_POSITION_SCRATCH);
            joint.getBodyB().getPosition(JOINT_BODY_B_POSITION_SCRATCH);
            Vector3d midpoint = new Vector3d((JOINT_BODY_A_POSITION_SCRATCH.x
                + JOINT_BODY_B_POSITION_SCRATCH.x) * 0.5,
                (JOINT_BODY_A_POSITION_SCRATCH.y + JOINT_BODY_B_POSITION_SCRATCH.y) * 0.5,
                (JOINT_BODY_A_POSITION_SCRATCH.z + JOINT_BODY_B_POSITION_SCRATCH.z) * 0.5);
            if (viewerPosition.distanceSquared(midpoint) > maxDistanceSquared) {
                return;
            }

            PhysicsDebugRenderer.renderJoint(viewers, joint, time);
            rendered[0]++;
        });
    }

    private static void renderWorldCollision(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxSections,
        int maxBoxes,
        float time) {
        double maxDistanceSquared = viewRadius * viewRadius;
        List<VisibleDebugSection> visibleSections = collectVisibleWorldCollisionSections(
            resource, space, viewerPosition, maxDistanceSquared);
        visibleSections.sort(Comparator.comparingDouble(VisibleDebugSection::distanceSquared));

        int sectionLimit = Math.min(maxSections, visibleSections.size());
        for (int i = 0; i < sectionLimit; i++) {
            DebugSection section = visibleSections.get(i).section();
            PhysicsDebugRenderer.renderWorldCollisionSection(viewers,
                section.chunkX(),
                section.sectionY(),
                section.chunkZ(),
                section.voxelTerrain(),
                time);
        }

        List<VisibleDebugBox> visibleBoxes = collectVisibleWorldCollisionBoxes(
            visibleSections, viewerPosition, maxDistanceSquared);
        visibleBoxes.sort(Comparator.comparingDouble(VisibleDebugBox::distanceSquared));

        int boxLimit = Math.min(maxBoxes, visibleBoxes.size());
        for (int i = 0; i < boxLimit; i++) {
            VisibleDebugBox visibleBox = visibleBoxes.get(i);
            PhysicsDebugRenderer.renderWorldCollisionBox(viewers,
                visibleBox.box(),
                visibleBox.color(),
                time);
        }
    }

    @Nonnull
    private static List<VisibleDebugSection> collectVisibleWorldCollisionSections(
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d viewerPosition,
        double maxDistanceSquared) {
        List<VisibleDebugSection> visibleSections = new ArrayList<>();
        resource.getWorldVoxelCollisionCache().forEachDebugSection(space.getId(), section -> {
            double distanceSquared = distanceSquaredToSection(viewerPosition, section);
            if (distanceSquared > maxDistanceSquared) {
                return;
            }

            visibleSections.add(new VisibleDebugSection(section, distanceSquared));
        });
        return visibleSections;
    }

    @Nonnull
    private static List<VisibleDebugBox> collectVisibleWorldCollisionBoxes(
        @Nonnull List<VisibleDebugSection> visibleSections,
        @Nonnull Vector3d viewerPosition,
        double maxDistanceSquared) {
        List<VisibleDebugBox> visibleBoxes = new ArrayList<>();
        for (VisibleDebugSection visibleSection : visibleSections) {
            DebugSection section = visibleSection.section();
            collectVisibleWorldCollisionBoxes(viewerPosition,
                maxDistanceSquared,
                section.fullCubeBoxes(),
                DebugUtils.COLOR_CYAN,
                visibleBoxes);
            collectVisibleWorldCollisionBoxes(viewerPosition,
                maxDistanceSquared,
                section.detailBoxes(),
                DebugUtils.COLOR_MAGENTA,
                visibleBoxes);
        }
        return visibleBoxes;
    }

    private static void collectVisibleWorldCollisionBoxes(@Nonnull Vector3d viewerPosition,
        double maxDistanceSquared,
        @Nonnull Iterable<BoxCollider> boxes,
        @Nonnull Vector3f color,
        @Nonnull List<VisibleDebugBox> visibleBoxes) {
        for (BoxCollider box : boxes) {
            double distanceSquared = distanceSquaredToBox(viewerPosition, box);
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }

            visibleBoxes.add(new VisibleDebugBox(box, color, distanceSquared));
        }
    }

    private static double distanceSquared(@Nonnull Vector3d point,
        @Nonnull Vector3f target) {
        double dx = point.x - target.x;
        double dy = point.y - target.y;
        double dz = point.z - target.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSquaredToSection(@Nonnull Vector3d viewerPosition,
        @Nonnull DebugSection section) {
        double minX = section.chunkX() << ChunkUtil.BITS;
        double minY = section.sectionY() << ChunkUtil.BITS;
        double minZ = section.chunkZ() << ChunkUtil.BITS;
        return distanceSquaredToBounds(viewerPosition,
            minX,
            minY,
            minZ,
            minX + ChunkUtil.SIZE,
            minY + ChunkUtil.SIZE,
            minZ + ChunkUtil.SIZE);
    }

    private static double distanceSquaredToBox(@Nonnull Vector3d viewerPosition,
        @Nonnull BoxCollider box) {
        return distanceSquaredToBounds(viewerPosition,
            box.centerX() - box.halfX(),
            box.centerY() - box.halfY(),
            box.centerZ() - box.halfZ(),
            box.centerX() + box.halfX(),
            box.centerY() + box.halfY(),
            box.centerZ() + box.halfZ());
    }

    private static double distanceSquaredToBounds(@Nonnull Vector3d viewerPosition,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ) {
        double dx = axisDistance(viewerPosition.x, minX, maxX);
        double dy = axisDistance(viewerPosition.y, minY, maxY);
        double dz = axisDistance(viewerPosition.z, minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0;
    }

    private record VisibleDebugSection(@Nonnull DebugSection section, double distanceSquared) {
    }

    private record VisibleDebugBox(@Nonnull BoxCollider box,
                                   @Nonnull Vector3f color,
                                   double distanceSquared) {
    }
}
