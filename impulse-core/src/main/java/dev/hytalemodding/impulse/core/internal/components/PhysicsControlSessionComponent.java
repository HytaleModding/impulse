package dev.hytalemodding.impulse.core.internal.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.joint.PhysicsJointId;
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
    private PhysicsBodyId bodyId;
    @Nullable
    private PhysicsBodyId anchorBodyId;
    @Nullable
    private PhysicsJointId controlJointId;
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

    public PhysicsControlSessionComponent(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodyId anchorBodyId,
        @Nullable PhysicsJointId controlJointId,
        @Nullable Ref<EntityStore> targetRef,
        @Nullable SpaceId spaceId,
        @Nonnull PhysicsBodyType originalBodyType,
        float grabDistance,
        @Nonnull Vector3f viewOffset,
        @Nonnull Vector3f previousTarget) {
        this.bodyId = bodyId;
        this.anchorBodyId = anchorBodyId;
        this.controlJointId = controlJointId;
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
        copy.bodyId = bodyId;
        copy.anchorBodyId = anchorBodyId;
        copy.controlJointId = controlJointId;
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
