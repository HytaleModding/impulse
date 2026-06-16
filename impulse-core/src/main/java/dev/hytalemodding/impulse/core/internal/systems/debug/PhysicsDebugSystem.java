package dev.hytalemodding.impulse.core.internal.systems.debug;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.simulation.view.PhysicsDebugContactView;
import dev.hytalemodding.impulse.core.internal.simulation.view.PhysicsDebugJointView;
import dev.hytalemodding.impulse.core.internal.simulation.view.PhysicsDebugWorldCollisionSectionView;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
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
public class PhysicsDebugSystem extends TickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, BodyAttachmentComponent> ATTACHMENT_TYPE =
        BodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    @Nonnull
    private final Map<Store<EntityStore>, DebugQueryCache> queryCachesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemDependency<>(Order.AFTER, PhysicsSyncSystem.class),
        new SystemDependency<>(Order.AFTER, UpdateLocationSystems.TickingSystem.class)
    );

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int index, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        PhysicsDebugResource debug = store.getResource(PhysicsDebugResource.getResourceType());

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

        Store<PhysicsStore> physicsStore =
            ((PhysicsStoreWorld) world).getPhysicsStore().getStore();
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
                    store,
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

            for (SpaceId spaceId : resource.getSpaceIds()) {
                if (overlayDue && debugShapes) {
                    renderSpaceOnlyShapes(target, resource, spaceId, overlayLifetime);
                }
                if (overlayDue && debugContacts) {
                    renderContacts(target,
                        physicsStore,
                        spaceId,
                        viewerUuid,
                        queryCache,
                        viewerPosition,
                        debug.getViewRadius(),
                        debug.getMaxContacts(),
                        overlayLifetime);
                }
                if (overlayDue && debugJoints) {
                    renderJoints(target,
                        physicsStore,
                        spaceId,
                        viewerUuid,
                        queryCache,
                        viewerPosition,
                        debug.getViewRadius(),
                        debug.getMaxJoints(),
                        overlayLifetime);
                }
                if (worldCollisionDue && debugWorldCollision) {
                    renderWorldCollision(target,
                        physicsStore,
                        spaceId,
                        viewerUuid,
                        queryCache,
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
    private DebugQueryCache queryCacheFor(@Nonnull Store<EntityStore> store) {
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
            Collection<Ref<EntityStore>> attachments = resource.getBodyAttachments(registration.bodyUuid(),
                null);
            if (attachments.isEmpty()) {
                continue;
            }

            for (Ref<EntityStore> attachmentRef : attachments) {
                if (!attachmentRef.isValid()) {
                    continue;
                }
                BodyAttachmentComponent attachment = store.getComponent(attachmentRef,
                    ATTACHMENT_TYPE);
                TransformComponent transform = store.getComponent(attachmentRef, TRANSFORM_TYPE);
                if (attachment == null
                    || attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY
                    || transform == null) {
                    continue;
                }

                PhysicsBodySnapshot snapshot = resource.getBodySnapshotIfRegistered(registration.bodyUuid(),
                    null);
                if (snapshot == null) {
                    continue;
                }
                PhysicsDebugRenderer.BodyDebugPose pose = PhysicsDebugRenderer.bodyPoseFromSyncedTransform(snapshot,
                    transform.getPosition(),
                    transform.getRotation().getQuaternion(new Quaterniond()),
                    attachment);
                Vector3d center = pose.center();
                if (viewerPosition.distanceSquared(center) > maxDistanceSquared) {
                    continue;
                }

                if (debugShapes) {
                    PhysicsDebugRenderer.renderBodyShape(viewers, snapshot, center, pose.rotation(), time);
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

        RenderedBodyCount rendered = new RenderedBodyCount();
        double maxDistanceSquared = viewRadius * viewRadius;
        for (SpaceId spaceId : resource.getSpaceIds()) {
            resource.forEachIndexedBodySnapshot(spaceId, (bodyUuid, snapshot, snapshotSpaceId, kind, persistenceMode) -> {
                if (rendered.hasReached(maxBodies)) {
                    return;
                }

                if (kind != PhysicsBodyKind.BODY
                    || resource.hasBodyAttachments(bodyUuid, null)) {
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
                rendered.increment();
            });
            if (rendered.hasReached(maxBodies)) {
                break;
            }
        }
        return rendered.value();
    }

    private static final class RenderedBodyCount {

        private int value;

        private void increment() {
            value++;
        }

        private boolean hasReached(int limit) {
            return value >= limit;
        }

        private int value() {
            return value;
        }
    }

    private static void renderSpaceOnlyShapes(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull SpaceId spaceId,
        float time) {
        resource.forEachIndexedBodySnapshot(spaceId, (bodyUuid, snapshot, snapshotSpaceId, kind, persistenceMode) -> {
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
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull SpaceId spaceId,
        @Nonnull UUID viewerUuid,
        @Nonnull DebugQueryCache queryCache,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxContacts,
        float time) {
        DebugQueryKey key = DebugQueryKey.contacts(spaceId, viewerUuid);
        try {
            queryCache.requestContactsIfIdle(key,
                () -> PhysicsStoreDebugQueries.contactsAsync(physicsStore,
                    spaceId,
                    viewerPosition,
                    viewRadius,
                    maxContacts));
        } catch (RuntimeException exception) {
            return;
        }
        for (PhysicsDebugContactView contact : queryCache.contactsOrEmpty(key)) {
            PhysicsDebugRenderer.renderContact(viewers, contact, time);
        }
    }

    private static void renderJoints(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull SpaceId spaceId,
        @Nonnull UUID viewerUuid,
        @Nonnull DebugQueryCache queryCache,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxJoints,
        float time) {
        DebugQueryKey key = DebugQueryKey.joints(spaceId, viewerUuid);
        try {
            queryCache.requestJointsIfIdle(key,
                () -> PhysicsStoreDebugQueries.jointsAsync(physicsStore,
                    spaceId,
                    viewerPosition,
                    viewRadius,
                    maxJoints));
        } catch (RuntimeException exception) {
            return;
        }
        for (PhysicsDebugJointView joint : queryCache.jointsOrEmpty(key)) {
            PhysicsDebugRenderer.renderJoint(viewers, joint, time);
        }
    }

    private static void renderWorldCollision(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull SpaceId spaceId,
        @Nonnull UUID viewerUuid,
        @Nonnull DebugQueryCache queryCache,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxSections,
        int maxBoxes,
        float time) {
        DebugQueryKey key = DebugQueryKey.worldCollision(spaceId, viewerUuid);
        try {
            queryCache.requestWorldCollisionIfIdle(key,
                () -> PhysicsStoreDebugQueries.worldCollisionSectionsAsync(physicsStore,
                    spaceId,
                    viewerPosition,
                    viewRadius));
        } catch (RuntimeException exception) {
            return;
        }

        double maxDistanceSquared = viewRadius * viewRadius;
        List<VisibleDebugSection> visibleSections = collectVisibleWorldCollisionSections(
            queryCache.worldCollisionSectionsOrEmpty(key),
            viewerPosition,
            maxDistanceSquared);
        visibleSections.sort(Comparator.comparingDouble(VisibleDebugSection::distanceSquared));

        int sectionLimit = Math.min(maxSections, visibleSections.size());
        for (int i = 0; i < sectionLimit; i++) {
            PhysicsDebugWorldCollisionSectionView section = visibleSections.get(i).section();
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
        @Nonnull Iterable<PhysicsDebugWorldCollisionSectionView> sections,
        @Nonnull Vector3d viewerPosition,
        double maxDistanceSquared) {
        List<VisibleDebugSection> visibleSections = new ArrayList<>();
        for (PhysicsDebugWorldCollisionSectionView section : sections) {
            double distanceSquared = distanceSquaredToSection(viewerPosition, section);
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }

            visibleSections.add(new VisibleDebugSection(section, distanceSquared));
        }
        return visibleSections;
    }

    @Nonnull
    private static List<VisibleDebugBox> collectVisibleWorldCollisionBoxes(
        @Nonnull List<VisibleDebugSection> visibleSections,
        @Nonnull Vector3d viewerPosition,
        double maxDistanceSquared) {
        List<VisibleDebugBox> visibleBoxes = new ArrayList<>();
        for (VisibleDebugSection visibleSection : visibleSections) {
            PhysicsDebugWorldCollisionSectionView section = visibleSection.section();
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
        @Nonnull PhysicsDebugWorldCollisionSectionView section) {
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
         * If a store tick query is still incomplete, the renderer uses the previous completed result
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
        @Nonnull
        private final Map<DebugQueryKey, CompletableFuture<List<PhysicsDebugWorldCollisionSectionView>>>
            pendingWorldCollisionSections = new Object2ObjectOpenHashMap<>();
        @Nonnull
        private final Map<DebugQueryKey, List<PhysicsDebugWorldCollisionSectionView>>
            completedWorldCollisionSections = new Object2ObjectOpenHashMap<>();

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

        synchronized boolean requestWorldCollisionIfIdle(@Nonnull DebugQueryKey key,
            @Nonnull Supplier<CompletionStage<List<PhysicsDebugWorldCollisionSectionView>>>
                completionSupplier) {
            pollWorldCollision(key);
            if (pendingWorldCollisionSections.containsKey(key)) {
                return false;
            }
            pendingWorldCollisionSections.put(key, completionSupplier.get().toCompletableFuture());
            return true;
        }

        @Nonnull
        synchronized List<PhysicsDebugWorldCollisionSectionView> worldCollisionSectionsOrEmpty(
            @Nonnull DebugQueryKey key) {
            pollWorldCollision(key);
            return completedWorldCollisionSections.getOrDefault(key, List.of());
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

        private void pollWorldCollision(@Nonnull DebugQueryKey key) {
            CompletableFuture<List<PhysicsDebugWorldCollisionSectionView>> pending =
                pendingWorldCollisionSections.get(key);
            if (pending == null || !pending.isDone()) {
                return;
            }
            pendingWorldCollisionSections.remove(key);
            completedWorldCollisionSections.put(key, completedList(pending));
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

        @Nonnull
        static DebugQueryKey worldCollision(@Nonnull SpaceId spaceId, @Nonnull UUID viewerUuid) {
            return new DebugQueryKey(QueryKind.WORLD_COLLISION, spaceId, viewerUuid);
        }
    }

    enum QueryKind {
        CONTACTS,
        JOINTS,
        WORLD_COLLISION
    }

    private record VisibleDebugSection(@Nonnull PhysicsDebugWorldCollisionSectionView section,
                                       double distanceSquared) {
    }

    private record VisibleDebugBox(@Nonnull BoxCollider box,
                                   @Nonnull Vector3f color,
                                   double distanceSquared) {
    }
}
