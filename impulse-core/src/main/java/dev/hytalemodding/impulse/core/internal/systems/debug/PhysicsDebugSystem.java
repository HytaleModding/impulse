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
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.internal.voxel.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugContactView;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugContactsQuery;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugJointView;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugJointsQuery;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache.DebugSection;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
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

    @Nonnull
    private final Map<Store<ChunkStore>, DebugQueryCache> queryCachesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(entityStore);
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
        DebugQueryCache queryCache = queryCacheFor(store);

        for (PlayerRef viewer : viewers) {
            Vector3d viewerPosition = new Vector3d(viewer.getTransform().getPosition());
            List<PlayerRef> target = List.of(viewer);
            UUID viewerUuid = viewer.getUuid();

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

            for (PhysicsSpaceBinding space : resource.getSpaceBindings()) {
                if (overlayDue && debugShapes) {
                    renderSpaceOnlyShapes(target, resource, space, overlayLifetime);
                }
                if (overlayDue && debugContacts) {
                    renderContacts(target,
                        resource,
                        space,
                        viewerUuid,
                        queryCache,
                        viewerPosition,
                        debug.getViewRadius(),
                        debug.getMaxContacts(),
                        overlayLifetime);
                }
                if (overlayDue && debugJoints) {
                    renderJoints(target,
                        resource,
                        space,
                        viewerUuid,
                        queryCache,
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
    private DebugQueryCache queryCacheFor(@Nonnull Store<ChunkStore> store) {
        synchronized (queryCachesByStore) {
            return queryCachesByStore.computeIfAbsent(store, ignored -> new DebugQueryCache());
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
        @Nonnull PhysicsWorldRuntimeResource resource,
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
        for (PhysicsBodyRegistrationView registration : resource.getBodyRegistrationViews(PhysicsBodyKind.BODY)) {
            if (!resource.hasBodyAttachments(registration.id())) {
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

                PhysicsBodySnapshot snapshot = resource.getBodySnapshot(registration.id());
                Vector3d center = PhysicsDebugRenderer.centerFromSyncedTransform(snapshot,
                    transform.getPosition());
                if (viewerPosition.distanceSquared(center) > maxDistanceSquared) {
                    continue;
                }

                Quaterniond rotation = transform.getRotation().getQuaternion(new Quaterniond());
                if (debugShapes) {
                    PhysicsDebugRenderer.renderBodyShape(viewers, snapshot, center, rotation, time);
                }
                if (debugMotion) {
                    PhysicsDebugRenderer.renderBodyMotion(viewers, center, snapshot, time);
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
        @Nonnull PhysicsWorldRuntimeResource resource,
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
        for (PhysicsSpaceBinding space : resource.getSpaceBindings()) {
            resource.forEachIndexedBodySnapshot(space.spaceId(), (bodyKey, snapshot, spaceId, kind, persistenceMode) -> {
                if (rendered[0] >= maxBodies) {
                    return;
                }

                if (kind != PhysicsBodyKind.BODY
                    || resource.hasBodyAttachments(bodyKey)) {
                    return;
                }

                Vector3d center = new Vector3d(snapshot.positionX(),
                    snapshot.positionY(),
                    snapshot.positionZ());
                if (viewerPosition.distanceSquared(center) > maxDistanceSquared) {
                    return;
                }

                Quaterniond rotation = new Quaterniond(snapshot.rotationX(),
                    snapshot.rotationY(),
                    snapshot.rotationZ(),
                    snapshot.rotationW());
                if (debugShapes) {
                    PhysicsDebugRenderer.renderBodyShape(viewers, snapshot, center, rotation, time);
                }
                if (debugMotion) {
                    PhysicsDebugRenderer.renderBodyMotion(viewers, center, snapshot, time);
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
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpaceBinding space,
        float time) {
        resource.forEachIndexedBodySnapshot(space.spaceId(), (bodyKey, snapshot, spaceId, kind, persistenceMode) -> {
            if (snapshot.shapeType() != ShapeType.PLANE) {
                return;
            }

            Quaterniond rotation = new Quaterniond(snapshot.rotationX(),
                snapshot.rotationY(),
                snapshot.rotationZ(),
                snapshot.rotationW());
            PhysicsDebugRenderer.renderBodyShape(viewers,
                snapshot,
                new Vector3d(snapshot.positionX(), snapshot.positionY(), snapshot.positionZ()),
                rotation,
                time);
        });
    }

    private static void renderContacts(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull UUID viewerUuid,
        @Nonnull DebugQueryCache queryCache,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxContacts,
        float time) {
        DebugQueryKey key = DebugQueryKey.contacts(space.spaceId(), viewerUuid);
        try {
            queryCache.requestContactsIfIdle(key,
                () -> resource.queryInternal(new PhysicsDebugContactsQuery(space.spaceId(),
                    viewerPosition.x,
                    viewerPosition.y,
                    viewerPosition.z,
                    viewRadius,
                    maxContacts)));
        } catch (RuntimeException exception) {
            return;
        }
        for (PhysicsDebugContactView contact : queryCache.contactsOrEmpty(key)) {
            PhysicsDebugRenderer.renderContact(viewers, contact, time);
        }
    }

    private static void renderJoints(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull UUID viewerUuid,
        @Nonnull DebugQueryCache queryCache,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxJoints,
        float time) {
        DebugQueryKey key = DebugQueryKey.joints(space.spaceId(), viewerUuid);
        try {
            queryCache.requestJointsIfIdle(key,
                () -> resource.queryInternal(new PhysicsDebugJointsQuery(space.spaceId(),
                    viewerPosition.x,
                    viewerPosition.y,
                    viewerPosition.z,
                    viewRadius,
                    maxJoints)));
        } catch (RuntimeException exception) {
            return;
        }
        for (PhysicsDebugJointView joint : queryCache.jointsOrEmpty(key)) {
            PhysicsDebugRenderer.renderJoint(viewers, joint, time);
        }
    }

    private static void renderWorldCollision(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpaceBinding space,
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
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d viewerPosition,
        double maxDistanceSquared) {
        List<VisibleDebugSection> visibleSections = new ArrayList<>();
        resource.worldCollisionCache().forEachDebugSection(space.spaceId(), section -> {
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

    static final class DebugQueryCache {

        /*
         * Debug overlays run in the tick path, so contact/joint queries are cached and polled.
         * If an owner query is still incomplete, the renderer uses the previous completed result
         * or skips that overlay for the frame instead of joining the world thread.
         */
        @Nonnull
        private final Map<DebugQueryKey, CompletableFuture<List<PhysicsDebugContactView>>> pendingContacts =
            new Object2ObjectOpenHashMap<>();
        @Nonnull
        private final Map<DebugQueryKey, List<PhysicsDebugContactView>> completedContacts =
            new Object2ObjectOpenHashMap<>();
        @Nonnull
        private final Map<DebugQueryKey, CompletableFuture<List<PhysicsDebugJointView>>> pendingJoints =
            new Object2ObjectOpenHashMap<>();
        @Nonnull
        private final Map<DebugQueryKey, List<PhysicsDebugJointView>> completedJoints =
            new Object2ObjectOpenHashMap<>();

        synchronized boolean requestContactsIfIdle(@Nonnull DebugQueryKey key,
            @Nonnull Supplier<CompletionStage<List<PhysicsDebugContactView>>> completionSupplier) {
            pollContacts(key);
            if (pendingContacts.containsKey(key)) {
                return false;
            }
            pendingContacts.put(key, completionSupplier.get().toCompletableFuture());
            return true;
        }

        @Nonnull
        synchronized List<PhysicsDebugContactView> contactsOrEmpty(@Nonnull DebugQueryKey key) {
            pollContacts(key);
            return completedContacts.getOrDefault(key, List.of());
        }

        synchronized boolean requestJointsIfIdle(@Nonnull DebugQueryKey key,
            @Nonnull Supplier<CompletionStage<List<PhysicsDebugJointView>>> completionSupplier) {
            pollJoints(key);
            if (pendingJoints.containsKey(key)) {
                return false;
            }
            pendingJoints.put(key, completionSupplier.get().toCompletableFuture());
            return true;
        }

        @Nonnull
        synchronized List<PhysicsDebugJointView> jointsOrEmpty(@Nonnull DebugQueryKey key) {
            pollJoints(key);
            return completedJoints.getOrDefault(key, List.of());
        }

        private void pollContacts(@Nonnull DebugQueryKey key) {
            CompletableFuture<List<PhysicsDebugContactView>> pending = pendingContacts.get(key);
            if (pending == null || !pending.isDone()) {
                return;
            }
            pendingContacts.remove(key);
            completedContacts.put(key, completedList(pending));
        }

        private void pollJoints(@Nonnull DebugQueryKey key) {
            CompletableFuture<List<PhysicsDebugJointView>> pending = pendingJoints.get(key);
            if (pending == null || !pending.isDone()) {
                return;
            }
            pendingJoints.remove(key);
            completedJoints.put(key, completedList(pending));
        }

        @Nonnull
        private static <T> List<T> completedList(@Nonnull CompletableFuture<List<T>> future) {
            try {
                return List.copyOf(future.getNow(List.of()));
            } catch (RuntimeException exception) {
                return List.of();
            }
        }
    }

    record DebugQueryKey(@Nonnull QueryKind kind,
                         @Nonnull SpaceId spaceId,
                         @Nonnull UUID viewerUuid) {

        DebugQueryKey {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(spaceId, "spaceId");
            Objects.requireNonNull(viewerUuid, "viewerUuid");
        }

        @Nonnull
        static DebugQueryKey contacts(@Nonnull SpaceId spaceId, @Nonnull UUID viewerUuid) {
            return new DebugQueryKey(QueryKind.CONTACTS, spaceId, viewerUuid);
        }

        @Nonnull
        static DebugQueryKey joints(@Nonnull SpaceId spaceId, @Nonnull UUID viewerUuid) {
            return new DebugQueryKey(QueryKind.JOINTS, spaceId, viewerUuid);
        }
    }

    enum QueryKind {
        CONTACTS,
        JOINTS
    }

    private record VisibleDebugSection(@Nonnull DebugSection section, double distanceSquared) {
    }

    private record VisibleDebugBox(@Nonnull BoxCollider box,
                                   @Nonnull Vector3f color,
                                   double distanceSquared) {
    }
}
