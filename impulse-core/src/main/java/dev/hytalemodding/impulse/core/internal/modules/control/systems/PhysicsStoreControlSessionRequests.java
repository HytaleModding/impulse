package dev.hytalemodding.impulse.core.internal.modules.control.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRequestQueueResource;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyRemoveRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTargetRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTypeRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.JointRemoveRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Copied PhysicsStore request batches for kinematic control lifecycle cleanup.
 */
public final class PhysicsStoreControlSessionRequests {

    private static final Quaternionf IDENTITY_ROTATION = new Quaternionf();
    private static final Vector3f ZERO = new Vector3f();

    private PhysicsStoreControlSessionRequests() {
    }

    public static void enqueueRelease(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsControlSessionComponent session) {
        List<PhysicsStoreRequest> requests = releaseRequests(session);
        if (!requests.isEmpty()) {
            Store<PhysicsStore> physicsStore =
                ((PhysicsStoreWorld) store.getExternalData().getWorld()).getPhysicsStore()
                    .getStore();
            physicsStore.getResource(PhysicsRequestQueueResource.getResourceType())
                .enqueueAll(requests);
        }
    }

    @Nonnull
    static List<PhysicsStoreRequest> releaseRequests(
        @Nonnull PhysicsControlSessionComponent session) {
        ArrayList<PhysicsStoreRequest> requests = new ArrayList<>(4);
        JointKey controlJointKey = session.getControlJointKey();
        if (controlJointKey != null) {
            requests.add(JointRemoveRequest.of(controlJointKey.value()));
        }

        RigidBodyKey bodyKey = session.getBodyKey();
        if (bodyKey != null) {
            UUID bodyUuid = bodyKey.value();
            PhysicsBodyType originalBodyType = session.getOriginalBodyType();
            requests.add(BodyTypeRequest.of(bodyUuid, originalBodyType, true));
            requests.add(BodyTargetRequest.of(bodyUuid,
                ZERO,
                IDENTITY_ROTATION,
                releaseVelocity(session),
                ZERO,
                false,
                true,
                true));
        }

        RigidBodyKey anchorBodyKey = session.getAnchorBodyKey();
        if (anchorBodyKey != null) {
            requests.add(BodyRemoveRequest.of(anchorBodyKey.value()));
        }
        return requests;
    }

    @Nonnull
    private static Vector3f releaseVelocity(@Nonnull PhysicsControlSessionComponent session) {
        if (session.getOriginalBodyType() == PhysicsBodyType.DYNAMIC) {
            return session.getReleaseVelocity();
        }
        return new Vector3f();
    }
}
