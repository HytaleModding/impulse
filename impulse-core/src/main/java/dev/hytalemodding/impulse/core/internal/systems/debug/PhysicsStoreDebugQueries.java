package dev.hytalemodding.impulse.core.internal.systems.debug;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.terrain.TerrainColliderPayload;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Queued PhysicsStore reads for debug overlay contact and joint views.
 */
final class PhysicsStoreDebugQueries {

    private static final float CONTACT_NORMAL_SCALE = 0.75f;
    private static final float JOINT_AXIS_SCALE = 0.9f;

    private PhysicsStoreDebugQueries() {
    }

    @Nonnull
    static CompletionStage<List<PhysicsDebugContactView>> contactsAsync(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxContacts) {
        double viewerX = viewerPosition.x;
        double viewerY = viewerPosition.y;
        double viewerZ = viewerPosition.z;
        return PhysicsThreading.enqueueReadOnWorldThread(store,
            "queue PhysicsStore debug contact read",
            physics -> contacts(physics,
                spaceId,
                viewerX,
                viewerY,
                viewerZ,
                viewRadius,
                maxContacts));
    }

    @Nonnull
    static CompletionStage<List<PhysicsDebugJointView>> jointsAsync(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d viewerPosition,
        double viewRadius,
        int maxJoints) {
        double viewerX = viewerPosition.x;
        double viewerY = viewerPosition.y;
        double viewerZ = viewerPosition.z;
        return PhysicsThreading.enqueueReadOnWorldThread(store,
            "queue PhysicsStore debug joint read",
            physics -> joints(physics,
                spaceId,
                viewerX,
                viewerY,
                viewerZ,
                viewRadius,
                maxJoints));
    }

    @Nonnull
    static CompletionStage<List<PhysicsChunkDebugSectionView>> physicsChunkSectionsAsync(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d viewerPosition,
        double viewRadius) {
        double viewerX = viewerPosition.x;
        double viewerY = viewerPosition.y;
        double viewerZ = viewerPosition.z;
        return PhysicsThreading.enqueueReadOnWorldThread(store,
            "queue PhysicsStore PhysicsChunk terrain debug read",
            physics -> physicsChunkSections(physics,
                spaceId,
                viewerX,
                viewerY,
                viewerZ,
                viewRadius));
    }

    @Nonnull
    private static List<PhysicsDebugContactView> contacts(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        double viewerX,
        double viewerY,
        double viewerZ,
        double viewRadius,
        int maxContacts) {
        PhysicsThreading.requireWorldThread(store, "read PhysicsStore debug contacts");
        int limit = Math.max(0, maxContacts);
        if (limit == 0) {
            return List.of();
        }

        SpaceContext space = space(store, spaceId);
        if (space == null) {
            return List.of();
        }

        double maxDistanceSquared = viewRadius * viewRadius;
        List<PhysicsDebugContactView> visible = new ArrayList<>(Math.min(limit, 64));
        space.backendRuntime().contacts(space.spaceHandle().value(), (bodyAId,
            bodyBId,
            pointAX,
            pointAY,
            pointAZ,
            pointBX,
            pointBY,
            pointBZ,
            normalBX,
            normalBY,
            normalBZ,
            distance,
            impulse) -> {
            if (visible.size() >= limit) {
                return;
            }
            if (distanceSquared(pointBX, pointBY, pointBZ, viewerX, viewerY, viewerZ)
                > maxDistanceSquared) {
                return;
            }
            visible.add(toDebugContactView(pointBX,
                pointBY,
                pointBZ,
                normalBX,
                normalBY,
                normalBZ,
                impulse));
        });
        return List.copyOf(visible);
    }

    @Nonnull
    private static List<PhysicsDebugJointView> joints(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        double viewerX,
        double viewerY,
        double viewerZ,
        double viewRadius,
        int maxJoints) {
        PhysicsThreading.requireWorldThread(store, "read PhysicsStore debug joints");
        int limit = Math.max(0, maxJoints);
        if (limit == 0) {
            return List.of();
        }

        UUID spaceUuid = store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(spaceId);
        if (spaceUuid == null) {
            return List.of();
        }
        Ref<PhysicsStore> spaceRef = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);

        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        double maxDistanceSquared = viewRadius * viewRadius;
        List<PhysicsDebugJointView> visible = new ArrayList<>(Math.min(limit, 64));
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectJointChunk(chunk,
                snapshots,
                spaceRef,
                spaceUuid,
                viewerX,
                viewerY,
                viewerZ,
                maxDistanceSquared,
                limit,
                visible);
        store.forEachChunk(JointComponent.getComponentType(), collector);
        return List.copyOf(visible);
    }

    @Nonnull
    private static List<PhysicsChunkDebugSectionView> physicsChunkSections(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        double viewerX,
        double viewerY,
        double viewerZ,
        double viewRadius) {
        PhysicsThreading.requireWorldThread(store,
            "read PhysicsStore PhysicsChunk terrain debug sections");
        SpaceContext spaceContext = space(store, spaceId);
        if (spaceContext == null) {
            return List.of();
        }

        UUID spaceUuid = store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(spaceId);
        if (spaceUuid == null) {
            return List.of();
        }
        Ref<PhysicsStore> spaceRef = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);

        PhysicsTerrainPayloadResource payloads = store.getResource(
            PhysicsTerrainPayloadResource.getResourceType());
        double maxDistanceSquared = viewRadius * viewRadius;
        List<PhysicsChunkDebugSectionView> visible = new ArrayList<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectPhysicsChunkTerrainChunk(chunk,
                payloads,
                spaceContext,
                spaceRef,
                spaceUuid,
                viewerX,
                viewerY,
                viewerZ,
                maxDistanceSquared,
                visible);
        store.forEachChunk(TerrainColliderComponent.getComponentType(), collector);
        return List.copyOf(visible);
    }

    private static void collectPhysicsChunkTerrainChunk(@Nonnull ArchetypeChunk<PhysicsStore> chunk,
        @Nonnull PhysicsTerrainPayloadResource payloads,
        @Nonnull SpaceContext spaceContext,
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nonnull UUID spaceUuid,
        double viewerX,
        double viewerY,
        double viewerZ,
        double maxDistanceSquared,
        @Nonnull List<PhysicsChunkDebugSectionView> visible) {
        for (int index = 0; index < chunk.size(); index++) {
            TerrainColliderComponent terrain = chunk.getComponent(index,
                TerrainColliderComponent.getComponentType());
            if (terrain == null || !terrain.isRetained()
                || !matchesSpace(terrain, spaceRef, spaceUuid)) {
                continue;
            }
            if (distanceSquaredToSection(terrain, viewerX, viewerY, viewerZ)
                > maxDistanceSquared) {
                continue;
            }
            TerrainColliderPayload payload = payloads.get(terrain.getPayloadResourceKey());
            if (payload == null || payload.isEmpty()) {
                continue;
            }
            visible.add(toPhysicsChunkSectionView(terrain, payload, spaceContext));
        }
    }

    @Nonnull
    private static PhysicsChunkDebugSectionView toPhysicsChunkSectionView(
        @Nonnull TerrainColliderComponent terrain,
        @Nonnull TerrainColliderPayload payload,
        @Nonnull SpaceContext spaceContext) {
        boolean voxelTerrain = payload.nativeVoxelTerrainEnabled()
            && payload.hasFullCubeVoxels()
            && spaceContext.backendRuntime()
                .supportsVoxelTerrain(spaceContext.spaceHandle().value());
        return new PhysicsChunkDebugSectionView(terrain.getChunkX(),
            terrain.getSectionY(),
            terrain.getChunkZ(),
            voxelTerrain,
            boxes(payload.mergedFullCubeBoxes()),
            boxes(payload.detailBoxes()));
    }

    @Nonnull
    private static List<BoxCollider> boxes(
        @Nonnull List<TerrainColliderPayload.BoxPayload> payloadBoxes) {
        if (payloadBoxes.isEmpty()) {
            return List.of();
        }
        List<BoxCollider> boxes = new ArrayList<>(payloadBoxes.size());
        for (TerrainColliderPayload.BoxPayload box : payloadBoxes) {
            boxes.add(new BoxCollider(box.centerX(),
                box.centerY(),
                box.centerZ(),
                box.halfX(),
                box.halfY(),
                box.halfZ()));
        }
        return boxes;
    }

    private static void collectJointChunk(@Nonnull ArchetypeChunk<PhysicsStore> chunk,
        @Nonnull PhysicsSnapshotResource snapshots,
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nonnull UUID spaceUuid,
        double viewerX,
        double viewerY,
        double viewerZ,
        double maxDistanceSquared,
        int limit,
        @Nonnull List<PhysicsDebugJointView> visible) {
        for (int index = 0; index < chunk.size(); index++) {
            if (visible.size() >= limit) {
                return;
            }
            JointComponent joint = chunk.getComponent(index, JointComponent.getComponentType());
            if (joint == null || !matchesSpace(joint, spaceRef, spaceUuid) || !joint.isEnabled()) {
                continue;
            }
            PhysicsDebugJointView view = toDebugJointView(joint, snapshots);
            if (view == null) {
                continue;
            }
            double midpointX = (view.anchorAX() + view.anchorBX()) * 0.5;
            double midpointY = (view.anchorAY() + view.anchorBY()) * 0.5;
            double midpointZ = (view.anchorAZ() + view.anchorBZ()) * 0.5;
            if (distanceSquared(midpointX, midpointY, midpointZ, viewerX, viewerY, viewerZ)
                > maxDistanceSquared) {
                continue;
            }
            visible.add(view);
        }
    }

    @Nullable
    private static PhysicsDebugJointView toDebugJointView(@Nonnull JointComponent joint,
        @Nonnull PhysicsSnapshotResource snapshots) {
        PhysicsBodySnapshot bodyA =
            bodySnapshot(snapshots, joint.getBodyARef(), joint.getBodyAUuid());
        PhysicsBodySnapshot bodyB =
            bodySnapshot(snapshots, joint.getBodyBRef(), joint.getBodyBUuid());
        if (bodyA == null || bodyB == null) {
            return null;
        }

        Vector3f anchorA = worldAnchor(bodyA, joint.getAnchorA());
        Vector3f anchorB = worldAnchor(bodyB, joint.getAnchorB());
        Vector3f axis = joint.getAxis();
        if (axis.lengthSquared() <= 0.0f) {
            return new PhysicsDebugJointView(anchorA.x,
                anchorA.y,
                anchorA.z,
                anchorB.x,
                anchorB.y,
                anchorB.z,
                false,
                0.0f,
                0.0f,
                0.0f);
        }

        Vector3f worldAxis = new Vector3f(axis).normalize().mul(JOINT_AXIS_SCALE);
        bodyA.rotation().transform(worldAxis);
        return new PhysicsDebugJointView(anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            true,
            worldAxis.x,
            worldAxis.y,
            worldAxis.z);
    }

    private static boolean matchesSpace(@Nonnull JointComponent joint,
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nonnull UUID spaceUuid) {
        Ref<PhysicsStore> jointSpaceRef = joint.getSpaceRef();
        if (jointSpaceRef != null && spaceRef != null) {
            return sameRef(jointSpaceRef, spaceRef);
        }
        return spaceUuid.equals(joint.getSpaceUuid());
    }

    private static boolean matchesSpace(@Nonnull TerrainColliderComponent terrain,
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nonnull UUID spaceUuid) {
        Ref<PhysicsStore> terrainSpaceRef = terrain.getSpaceRef();
        if (terrainSpaceRef != null && spaceRef != null) {
            return sameRef(terrainSpaceRef, spaceRef);
        }
        return spaceUuid.equals(terrain.getSpaceUuid());
    }

    @Nullable
    private static PhysicsBodySnapshot bodySnapshot(
        @Nonnull PhysicsSnapshotResource snapshots,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid) {
        return bodyRef != null ? snapshots.getBody(bodyRef) : snapshots.getBody(bodyUuid);
    }

    private static boolean sameRef(@Nonnull Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first.getStore() == second.getStore()
            && first.getIndex() == second.getIndex();
    }

    @Nonnull
    private static Vector3f worldAnchor(@Nonnull PhysicsBodySnapshot body,
        @Nonnull Vector3f localAnchor) {
        Vector3f anchor = new Vector3f(localAnchor);
        Quaternionf rotation = body.rotation();
        rotation.transform(anchor);
        return anchor.add(body.position());
    }

    @Nonnull
    private static PhysicsDebugContactView toDebugContactView(float pointX,
        float pointY,
        float pointZ,
        float normalX,
        float normalY,
        float normalZ,
        float impulse) {
        Vector3f normal = new Vector3f(normalX, normalY, normalZ);
        if (normal.lengthSquared() <= 0.0f) {
            return new PhysicsDebugContactView(pointX,
                pointY,
                pointZ,
                false,
                0.0f,
                0.0f,
                0.0f);
        }

        float magnitude = Math.max(CONTACT_NORMAL_SCALE, Math.abs(impulse) * 0.05f);
        normal.normalize().mul(magnitude);
        return new PhysicsDebugContactView(pointX,
            pointY,
            pointZ,
            true,
            normal.x,
            normal.y,
            normal.z);
    }

    @Nullable
    private static SpaceContext space(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        UUID spaceUuid = compatibility.getSpaceUuid(spaceId);
        if (spaceUuid == null) {
            return null;
        }
        Ref<PhysicsStore> spaceRef = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);
        if (spaceRef == null || !spaceRef.isValid()) {
            return null;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceRef);
        BackendId backendId = runtime.getSpaceBackendId(spaceRef);
        PhysicsBackendRuntime backendRuntime =
            backendId != null ? runtime.getRuntime(backendId) : null;
        if (spaceHandle == null || backendRuntime == null) {
            return null;
        }
        return new SpaceContext(spaceHandle, backendRuntime);
    }

    private static double distanceSquared(double x,
        double y,
        double z,
        double viewerX,
        double viewerY,
        double viewerZ) {
        double dx = x - viewerX;
        double dy = y - viewerY;
        double dz = z - viewerZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSquaredToSection(@Nonnull TerrainColliderComponent terrain,
        double viewerX,
        double viewerY,
        double viewerZ) {
        double minX = terrain.getChunkX() << ChunkUtil.BITS;
        double minY = terrain.getSectionY() << ChunkUtil.BITS;
        double minZ = terrain.getChunkZ() << ChunkUtil.BITS;
        return distanceSquaredToBounds(viewerX,
            viewerY,
            viewerZ,
            minX,
            minY,
            minZ,
            minX + ChunkUtil.SIZE,
            minY + ChunkUtil.SIZE,
            minZ + ChunkUtil.SIZE);
    }

    private static double distanceSquaredToBounds(double viewerX,
        double viewerY,
        double viewerZ,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ) {
        double dx = axisDistance(viewerX, minX, maxX);
        double dy = axisDistance(viewerY, minY, maxY);
        double dz = axisDistance(viewerZ, minZ, maxZ);
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

    private record SpaceContext(@Nonnull BackendSpaceHandle spaceHandle,
                                @Nonnull PhysicsBackendRuntime backendRuntime) {
    }
}
