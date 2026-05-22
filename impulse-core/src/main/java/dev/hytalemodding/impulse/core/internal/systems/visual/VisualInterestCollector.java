package dev.hytalemodding.impulse.core.internal.systems.visual;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncPolicy;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Shared player-interest collection for visual sync and detached visual materialization.
 */
public final class VisualInterestCollector {

    private static final ComponentType<EntityStore, HeadRotation> HEAD_ROTATION_TYPE =
        HeadRotation.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private VisualInterestCollector() {
    }

    @Nonnull
    public static List<PhysicsSyncPolicy.PlayerInterest> collectSyncInterests(
        @Nonnull Store<EntityStore> store) {
        List<PhysicsSyncPolicy.PlayerInterest> interests = new ArrayList<>();
        for (PlayerRef playerRef : store.getExternalData().getWorld().getPlayerRefs()) {
            Ref<EntityStore> playerEntity = playerRef.getReference();
            if (playerEntity == null || !playerEntity.isValid()) {
                continue;
            }

            TransformComponent transform = store.getComponent(playerEntity, TRANSFORM_TYPE);
            if (transform == null) {
                continue;
            }

            Vector3d position = transform.getPosition();
            interests.add(new PhysicsSyncPolicy.PlayerInterest(
                new Vector3f((float) position.x, (float) position.y, (float) position.z),
                playerLookDirection(store, playerEntity, transform)));
        }
        return interests.isEmpty() ? List.of() : interests;
    }

    @Nonnull
    public static List<PhysicsWorldResource.VisualInterest> collectMaterializationInterests(
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource) {
        List<PhysicsWorldResource.VisualInterest> interests = new ArrayList<>();
        for (PlayerRef playerRef : store.getExternalData().getWorld().getPlayerRefs()) {
            Ref<EntityStore> playerEntity = playerRef.getReference();
            if (playerEntity == null || !playerEntity.isValid()) {
                continue;
            }

            TransformComponent transform = store.getComponent(playerEntity, TRANSFORM_TYPE);
            if (transform == null) {
                continue;
            }

            Vector3d position = transform.getPosition();
            interests.add(new PhysicsWorldResource.VisualInterest(
                new Vector3f((float) position.x, (float) position.y, (float) position.z),
                playerLookDirection(store, playerEntity, transform)));
        }
        interests.addAll(resource.getSyntheticVisualInterests());
        return interests;
    }

    @Nonnull
    private static Vector3f playerLookDirection(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerEntity,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = store.getComponent(playerEntity, HEAD_ROTATION_TYPE);
        Rotation3f rotation = headRotation != null ? headRotation.getRotation() : transform.getRotation();
        Vector3d direction = new Vector3d(Vector3dUtil.FORWARD);
        rotation.getQuaternion(new Quaterniond()).transform(direction);
        if (direction.lengthSquared() == 0.0) {
            direction.set(Vector3dUtil.FORWARD);
        } else {
            direction.normalize();
        }
        return new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
    }
}
