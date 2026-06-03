package dev.hytalemodding.impulse.core.internal.modules.control.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Vector3f;

@Getter
public class PhysicsControlSessionComponent implements Component<EntityStore> {

    @Nullable
    private static ComponentType<EntityStore, PhysicsControlSessionComponent> componentType;

    @Nullable
    private RigidBodyKey bodyKey;
    @Nullable
    private RigidBodyKey anchorBodyKey;
    @Nullable
    private JointKey controlJointKey;
    @Nullable
    private Ref<EntityStore> targetRef;
    @Nullable
    private SpaceId spaceId;
    @Nonnull
    private PhysicsBodyType originalBodyType = PhysicsBodyType.DYNAMIC;
    @Getter
    private float grabDistance;
    @Nonnull
    private final Vector3f viewOffset = new Vector3f();
    @Nonnull
    private final Vector3f previousTarget = new Vector3f();
    @Nonnull
    private final Vector3f releaseVelocity = new Vector3f();
    @Getter
    private boolean active;

    public PhysicsControlSessionComponent() {
    }

    public PhysicsControlSessionComponent(@Nonnull RigidBodyKey bodyKey,
        @Nonnull RigidBodyKey anchorBodyKey,
        @Nullable JointKey controlJointKey,
        @Nullable Ref<EntityStore> targetRef,
        @Nullable SpaceId spaceId,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        this.bodyKey = bodyKey;
        this.anchorBodyKey = anchorBodyKey;
        this.controlJointKey = controlJointKey;
        this.targetRef = targetRef;
        this.spaceId = spaceId;
        this.originalBodyType = originalBodyType;
        this.grabDistance = grabDistance;
        this.viewOffset.set(viewOffset);
        this.previousTarget.set(previousTarget);
        this.active = true;
    }

    public static void setComponentType(
        @Nonnull ComponentType<EntityStore, PhysicsControlSessionComponent> type) {
        componentType = Objects.requireNonNull(type, "type");
    }

    public static boolean isComponentTypeRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, PhysicsControlSessionComponent> getComponentType() {
        if (componentType == null) {
            throw new IllegalStateException("Physics control session component is not registered");
        }
        return componentType;
    }

    public void deactivate() {
        active = false;
    }

    @Nonnull
    @Override
    public PhysicsControlSessionComponent clone() {
        PhysicsControlSessionComponent copy = new PhysicsControlSessionComponent();
        copy.bodyKey = bodyKey;
        copy.anchorBodyKey = anchorBodyKey;
        copy.controlJointKey = controlJointKey;
        copy.targetRef = targetRef;
        copy.spaceId = spaceId;
        copy.originalBodyType = originalBodyType;
        copy.grabDistance = grabDistance;
        copy.viewOffset.set(viewOffset);
        copy.previousTarget.set(previousTarget);
        copy.releaseVelocity.set(releaseVelocity);
        copy.active = active;
        return copy;
    }
}
