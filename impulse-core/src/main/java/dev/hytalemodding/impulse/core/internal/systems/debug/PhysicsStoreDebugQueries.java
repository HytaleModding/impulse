package dev.hytalemodding.impulse.core.internal.systems.debug;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.simulation.view.PhysicsDebugContactView;
import dev.hytalemodding.impulse.core.internal.simulation.view.PhysicsDebugJointView;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
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
        return PhysicsStoreThreading.enqueueReadOnWorldThread(store,
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
        return PhysicsStoreThreading.enqueueReadOnWorldThread(store,
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
    private static List<PhysicsDebugContactView> contacts(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        double viewerX,
        double viewerY,
        double viewerZ,
        double viewRadius,
        int maxContacts) {
        PhysicsStoreThreading.requireWorldThread(store, "read PhysicsStore debug contacts");
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
        PhysicsStoreThreading.requireWorldThread(store, "read PhysicsStore debug joints");
        int limit = Math.max(0, maxJoints);
        if (limit == 0) {
            return List.of();
        }

        UUID spaceUuid = store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .getSpaceUuid(spaceId);
        if (spaceUuid == null) {
            return List.of();
        }

        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        double maxDistanceSquared = viewRadius * viewRadius;
        List<PhysicsDebugJointView> visible = new ArrayList<>(Math.min(limit, 64));
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectJointChunk(chunk,
                snapshots,
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

    private static void collectJointChunk(@Nonnull ArchetypeChunk<PhysicsStore> chunk,
        @Nonnull PhysicsSnapshotResource snapshots,
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
            if (joint == null || !spaceUuid.equals(joint.getSpaceUuid()) || !joint.isEnabled()) {
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
        PhysicsStoreBodySnapshot bodyA = snapshots.getBody(joint.getBodyAUuid());
        PhysicsStoreBodySnapshot bodyB = snapshots.getBody(joint.getBodyBUuid());
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

    @Nonnull
    private static Vector3f worldAnchor(@Nonnull PhysicsStoreBodySnapshot body,
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
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceUuid);
        BackendId backendId = runtime.getSpaceBackendId(spaceUuid);
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

    private record SpaceContext(@Nonnull BackendSpaceHandle spaceHandle,
                                @Nonnull PhysicsBackendRuntime backendRuntime) {
    }
}
