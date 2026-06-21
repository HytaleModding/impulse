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
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityAttachments;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollision;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.systems.sync.PhysicsSyncSystem;
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
import javax.annotation.Nullable;
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

    @Nonnull
    private final ComponentType<EntityStore, BodyAttachmentComponent> attachmentType;
    @Nonnull
    private final ComponentType<EntityStore, TransformComponent> transformType;
    @Nonnull
    private final Map<Store<EntityStore>, DebugQueryCache> queryCachesByStore =
        Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemDependency<>(Order.AFTER, PhysicsSyncSystem.class),
        new SystemDependency<>(Order.AFTER, UpdateLocationSystems.TickingSystem.class)
    );

    public PhysicsDebugSystem() {
        this(BodyAttachmentComponent.getComponentType(), TransformComponent.getComponentType());
    }

    PhysicsDebugSystem(@Nonnull ComponentType<EntityStore, BodyAttachmentComponent> attachmentType,
        @Nonnull ComponentType<EntityStore, TransformComponent> transformType) {
        this.attachmentType = Objects.requireNonNull(attachmentType, "attachmentType");
        this.transformType = Objects.requireNonNull(transformType, "transformType");
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int index, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        assert PhysicsDebugResource.getResourceType() != null;
        PhysicsDebugResource debug = store.getResource(PhysicsDebugResource.getResourceType());

        if (!debug.hasSubscribers()) {
            return;
        }

        List<PlayerRef> viewers = resolveSubscribers(world, debug);
        if (viewers.isEmpty()) {
            return;
        }

        boolean overlayDue = debug.tickOverlayBudget(dt);
        boolean terrainDue = debug.tickPhysicsChunkBudget(dt);
        if (!overlayDue && !terrainDue) {
            return;
        }

        boolean debugShapes = debug.isDebugShapesEnabled();
        boolean debugMotion = debug.isDebugMotionEnabled();
        boolean debugContacts = debug.isDebugContactsEnabled();
        boolean debugJoints = debug.isDebugJointsEnabled();
        boolean debugCollision = debug.isDebugPhysicsChunkCollisionEnabled();
        if (!debugShapes && !debugMotion && !debugContacts && !debugJoints
            && !debugCollision) {
            return;
        }

        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        float overlayLifetime = PhysicsDebugRenderer.lifetimeForRefresh(
            debug.getOverlayRefreshSeconds(), dt);
        float terrainLifetime = PhysicsDebugRenderer.lifetimeForRefresh(
            debug.getPhysicsChunkRefreshSeconds(), dt);
        DebugQueryCache queryCache = queryCacheFor(store);

        for (PlayerRef viewer : viewers) {
            Vector3d viewerPosition = new Vector3d(viewer.getTransform().getPosition());
            List<PlayerRef> target = List.of(viewer);
            UUID viewerUuid = viewer.getUuid();

            if (overlayDue && (debugShapes || debugMotion)) {
                int renderedBodies = renderEntityBodies(target,
                    store,
                    physicsStore,
                    viewerPosition,
                    debug.getViewRadius(),
                    debugShapes,
                    debugMotion,
                    debug.getMaxBodies(),
                    overlayLifetime);
                renderDetachedBodies(target,
                    store,
                    physicsStore,
                    viewerPosition,
                    debug.getViewRadius(),
                    debugShapes,
                    debugMotion,
                    Math.max(0, debug.getMaxBodies() - renderedBodies),
                    overlayLifetime);
            }

            for (SpaceId spaceId : PhysicsSpaces.spaceIds(physicsStore)) {
                if (overlayDue && debugShapes) {
                    renderSpaceOnlyShapes(target, physicsStore, spaceId, overlayLifetime);
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
                if (terrainDue && debugCollision) {
                    renderPhysicsChunkCollision(target,
                        physicsStore,
                        spaceId,
                        viewerUuid,
                        queryCache,
                        viewerPosition,
                        debug.getViewRadius(),
                        debug.getMaxPhysicsChunkSections(),
                        debug.getMaxPhysicsChunkBoxes(),
                        terrainLifetime);
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

    private int renderEntityBodies(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Store<EntityStore> store,
        @Nonnull Store<PhysicsStore> physicsStore,
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
        for (UUID bodyUuid : PhysicsBodies.bodyUuids(physicsStore)) {
            if (PhysicsChunkCollision.isChunkCollisionBody(physicsStore, bodyUuid)) {
                continue;
            }
            Collection<Ref<EntityStore>> attachments = PhysicsEntityAttachments.attachments(store,
                bodyUuid,
                null);
            if (attachments.isEmpty()) {
                continue;
            }

            for (Ref<EntityStore> attachmentRef : attachments) {
                if (!attachmentRef.isValid()) {
                    continue;
                }
                BodyAttachmentComponent attachment = store.getComponent(attachmentRef,
                    attachmentType);
                TransformComponent transform = store.getComponent(attachmentRef, transformType);
                if (attachment == null
                    || attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY
                    || transform == null) {
                    continue;
                }

                PhysicsBodySnapshot snapshot = apiSnapshot(physicsStore,
                    PhysicsBodies.snapshot(physicsStore, bodyUuid));
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
        @Nonnull Store<EntityStore> store,
        @Nonnull Store<PhysicsStore> physicsStore,
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
        for (SpaceId spaceId : PhysicsSpaces.spaceIds(physicsStore)) {
            forEachBodySnapshot(physicsStore, spaceId, (bodyUuid, snapshot) -> {
                if (rendered.hasReached(maxBodies)) {
                    return;
                }

                if (PhysicsChunkCollision.isChunkCollisionBody(physicsStore, bodyUuid)
                    || PhysicsEntityAttachments.hasAttachments(store, bodyUuid, null)) {
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
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull SpaceId spaceId,
        float time) {
        forEachBodySnapshot(physicsStore, spaceId, (_, snapshot) -> {
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

    private static void forEachBodySnapshot(@Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull SpaceId spaceId,
        @Nonnull BodySnapshotConsumer consumer) {
        UUID spaceUuid = physicsStore
            .getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(spaceId);
        if (spaceUuid == null) {
            return;
        }
        for (dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot snapshot
            : PhysicsBodies.snapshotFrame(physicsStore).bodies()) {
            if (!spaceUuid.equals(snapshot.spaceUuid())) {
                continue;
            }
            PhysicsBodySnapshot apiSnapshot = apiSnapshot(physicsStore, snapshot);
            if (apiSnapshot != null) {
                consumer.accept(snapshot.bodyUuid(), apiSnapshot);
            }
        }
    }

    @Nullable
    private static PhysicsBodySnapshot apiSnapshot(@Nonnull Store<PhysicsStore> store,
        @Nullable dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Ref<PhysicsStore> ref = snapshot.bodyRef();
        if (ref == null || ref.getStore() != store || !ref.isValid()) {
            ref = PhysicsEntities.resolveRef(store, snapshot.bodyUuid());
        }
        boolean validRef = ref != null && ref.isValid();
        DynamicsComponent dynamics = validRef
            ? store.getComponent(ref, DynamicsComponent.getComponentType())
            : null;
        ColliderComponent collider = validRef
            ? store.getComponent(ref, ColliderComponent.getComponentType())
            : null;
        MaterialComponent material = validRef
            ? store.getComponent(ref, MaterialComponent.getComponentType())
            : null;
        CollisionFilterComponent filter = validRef
            ? store.getComponent(ref, CollisionFilterComponent.getComponentType())
            : null;
        ShapeComponent shape = validRef
            ? store.getComponent(ref, ShapeComponent.getComponentType())
            : null;

        Vector3f position = snapshot.position();
        Quaterniond rotationD = new Quaterniond(snapshot.rotationX(),
            snapshot.rotationY(),
            snapshot.rotationZ(),
            snapshot.rotationW());
        org.joml.Quaternionf rotation = new org.joml.Quaternionf((float) rotationD.x,
            (float) rotationD.y,
            (float) rotationD.z,
            (float) rotationD.w);
        Vector3f linearVelocity = snapshot.linearVelocity();
        Vector3f angularVelocity = snapshot.angularVelocity();
        PhysicsBodyType bodyType = snapshot.bodyType();
        ShapeType shapeType = shape != null ? shape.getShapeType() : ShapeType.UNKNOWN;
        boolean hasBoxHalfExtents = shapeType == ShapeType.BOX && shape != null;

        return PhysicsBodySnapshot.of(position.x,
            position.y,
            position.z,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w,
            linearVelocity.x,
            linearVelocity.y,
            linearVelocity.z,
            angularVelocity.x,
            angularVelocity.y,
            angularVelocity.z,
            bodyType,
            snapshot.sleeping(),
            collider != null && collider.isSensor(),
            bodyType == PhysicsBodyType.DYNAMIC ? authoredMass(dynamics) : 0.0f,
            material != null ? material.getFriction() : 0.5f,
            material != null ? material.getRestitution() : 0.0f,
            dynamics != null ? dynamics.getLinearDamping() : 0.0f,
            dynamics != null ? dynamics.getAngularDamping() : 0.0f,
            filter != null ? filter.getCollisionGroup() : PhysicsCollisionFilters.DYNAMIC_BODY,
            filter != null ? filter.getCollisionMask() : PhysicsCollisionFilters.ALL,
            dynamics != null && dynamics.isContinuousCollisionEnabled(),
            snapshot.centerOfMassOffsetY(),
            shapeType,
            hasBoxHalfExtents,
            hasBoxHalfExtents ? shape.getHalfExtentX() : 0.0f,
            hasBoxHalfExtents ? shape.getHalfExtentY() : 0.0f,
            hasBoxHalfExtents ? shape.getHalfExtentZ() : 0.0f,
            shape != null ? shape.getRadius() : 0.0f,
            shape != null ? shape.getHalfHeight() : 0.0f,
            shape != null ? shape.getAxis() : PhysicsAxis.Y);
    }

    private static float authoredMass(@Nullable DynamicsComponent dynamics) {
        return dynamics != null ? dynamics.getMass() : 1.0f;
    }

    @FunctionalInterface
    private interface BodySnapshotConsumer {

        void accept(@Nonnull UUID bodyUuid, @Nonnull PhysicsBodySnapshot snapshot);
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

    private static void renderPhysicsChunkCollision(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull SpaceId spaceId,
        @Nonnull UUID viewerUuid,
        @Nonnull DebugQueryCache queryCache,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxSections,
        int maxBoxes,
        float time) {
        DebugQueryKey key = DebugQueryKey.physicsChunk(spaceId, viewerUuid);
        try {
            queryCache.requestPhysicsChunkIfIdle(key,
                () -> PhysicsStoreDebugQueries.physicsChunkSectionsAsync(physicsStore,
                    spaceId,
                    viewerPosition,
                    viewRadius));
        } catch (RuntimeException exception) {
            return;
        }

        double maxDistanceSquared = viewRadius * viewRadius;
        List<VisibleDebugSection> visibleSections = collectVisiblePhysicsChunkSections(
            queryCache.physicsChunkSectionsOrEmpty(key),
            viewerPosition,
            maxDistanceSquared);
        visibleSections.sort(Comparator.comparingDouble(VisibleDebugSection::distanceSquared));

        int sectionLimit = Math.min(maxSections, visibleSections.size());
        for (int i = 0; i < sectionLimit; i++) {
            PhysicsChunkDebugSectionView section = visibleSections.get(i).section();
            PhysicsDebugRenderer.renderPhysicsChunkCollisionSection(viewers,
                section.chunkX(),
                section.sectionY(),
                section.chunkZ(),
                section.voxelTerrain(),
                time);
        }

        List<VisibleDebugBox> visibleBoxes = collectVisiblePhysicsChunkBoxes(
            visibleSections, viewerPosition, maxDistanceSquared);
        visibleBoxes.sort(Comparator.comparingDouble(VisibleDebugBox::distanceSquared));

        int boxLimit = Math.min(maxBoxes, visibleBoxes.size());
        for (int i = 0; i < boxLimit; i++) {
            VisibleDebugBox visibleBox = visibleBoxes.get(i);
            PhysicsDebugRenderer.renderPhysicsChunkCollisionBox(viewers,
                visibleBox.box(),
                visibleBox.color(),
                time);
        }
    }

    @Nonnull
    private static List<VisibleDebugSection> collectVisiblePhysicsChunkSections(
        @Nonnull Iterable<PhysicsChunkDebugSectionView> sections,
        @Nonnull Vector3d viewerPosition,
        double maxDistanceSquared) {
        List<VisibleDebugSection> visibleSections = new ArrayList<>();
        for (PhysicsChunkDebugSectionView section : sections) {
            double distanceSquared = distanceSquaredToSection(viewerPosition, section);
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }

            visibleSections.add(new VisibleDebugSection(section, distanceSquared));
        }
        return visibleSections;
    }

    @Nonnull
    private static List<VisibleDebugBox> collectVisiblePhysicsChunkBoxes(
        @Nonnull List<VisibleDebugSection> visibleSections,
        @Nonnull Vector3d viewerPosition,
        double maxDistanceSquared) {
        List<VisibleDebugBox> visibleBoxes = new ArrayList<>();
        for (VisibleDebugSection visibleSection : visibleSections) {
            PhysicsChunkDebugSectionView section = visibleSection.section();
            collectVisiblePhysicsChunkBoxes(viewerPosition,
                maxDistanceSquared,
                section.fullCubeBoxes(),
                DebugUtils.COLOR_CYAN,
                visibleBoxes);
            collectVisiblePhysicsChunkBoxes(viewerPosition,
                maxDistanceSquared,
                section.detailBoxes(),
                DebugUtils.COLOR_MAGENTA,
                visibleBoxes);
        }
        return visibleBoxes;
    }

    private static void collectVisiblePhysicsChunkBoxes(@Nonnull Vector3d viewerPosition,
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
        @Nonnull PhysicsChunkDebugSectionView section) {
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
        private final Map<DebugQueryKey, CompletableFuture<List<PhysicsChunkDebugSectionView>>>
            pendingPhysicsChunkSections = new Object2ObjectOpenHashMap<>();
        @Nonnull
        private final Map<DebugQueryKey, List<PhysicsChunkDebugSectionView>>
            completedPhysicsChunkSections = new Object2ObjectOpenHashMap<>();

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

        synchronized boolean requestPhysicsChunkIfIdle(@Nonnull DebugQueryKey key,
            @Nonnull Supplier<CompletionStage<List<PhysicsChunkDebugSectionView>>>
                completionSupplier) {
            pollPhysicsChunk(key);
            if (pendingPhysicsChunkSections.containsKey(key)) {
                return false;
            }
            pendingPhysicsChunkSections.put(key, completionSupplier.get().toCompletableFuture());
            return true;
        }

        @Nonnull
        synchronized List<PhysicsChunkDebugSectionView> physicsChunkSectionsOrEmpty(
            @Nonnull DebugQueryKey key) {
            pollPhysicsChunk(key);
            return completedPhysicsChunkSections.getOrDefault(key, List.of());
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

        private void pollPhysicsChunk(@Nonnull DebugQueryKey key) {
            CompletableFuture<List<PhysicsChunkDebugSectionView>> pending =
                pendingPhysicsChunkSections.get(key);
            if (pending == null || !pending.isDone()) {
                return;
            }
            pendingPhysicsChunkSections.remove(key);
            completedPhysicsChunkSections.put(key, completedList(pending));
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
        static DebugQueryKey physicsChunk(@Nonnull SpaceId spaceId, @Nonnull UUID viewerUuid) {
            return new DebugQueryKey(QueryKind.PHYSICS_CHUNK, spaceId, viewerUuid);
        }
    }

    enum QueryKind {
        CONTACTS,
        JOINTS,
        PHYSICS_CHUNK
    }

    private record VisibleDebugSection(@Nonnull PhysicsChunkDebugSectionView section,
                                       double distanceSquared) {
    }

    private record VisibleDebugBox(@Nonnull BoxCollider box,
                                   @Nonnull Vector3f color,
                                   double distanceSquared) {
    }
}
